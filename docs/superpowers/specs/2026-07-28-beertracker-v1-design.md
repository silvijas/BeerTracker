# BeerTracker v1 Design

Date: 2026-07-28
Status: Approved for planning

## 1. Purpose

An Android app for two people (the user and their partner) to log, grade, and
browse beers they have tried while exploring Systembolaget in Sweden. The list
is shared and syncs between the two phones. Adding a beer is quick, with three
input paths: manual entry, scanning a Systembolaget shelf label, and taking a
photo of a can.

The app must be usable inside a Systembolaget store, where mobile signal can be
poor, so it works offline and syncs when connectivity returns.

## 2. Scope

### In scope for v1

- Browse the shared list of tried beers with search, filters, and sorting.
- Add a beer three ways: manual, Systembolaget shelf-label scan, can photo.
- Grade and annotate each beer.
- Add a beer without having tried it, for example a bottle spotted on a shelf
  that is worth buying later, then grade it once it has been tasted.
- Cloud sync of the shared list across two phones (Firebase).
- Pair the two phones with a simple invite code (WhatsApp-style: one phone
  authorizes the other to share the same list). A QR code or link is an
  optional convenience on top of the same code.
- Bundled, offline, read-only beer catalog snapshot from Systembolaget for
  reliable in-store lookups.
- GitHub Actions pipeline that builds a signed release APK and publishes it to
  the repository Releases page for download and install on a phone.

### Deferred to later versions

- Live prices, stock levels, and "which stores have this beer now".
- Hybrid catalog (bundled snapshot plus live calls for volatile data).
- Wines tab.
- Cocktails tab (recipes with clickable spirits linking to Systembolaget pages).
- Export and sharing outside the two-person cellar.

## 3. Data model

Two data stores with two different jobs.

### 3.1 Cellar (synced, read-write)

The shared list of tried beers, stored in Firebase Firestore, synced between the
paired phones, cached offline on each device.

TriedBeer:
- id
- name
- brewery
- type (one value, chosen from a managed list, custom values allowed)
- alcoholPercent
- volumeMl
- price
- grade (optional integer: 5, 6, 7, 8, 9, or 10; Serbian university scale; empty
  while the beer has no score)
- tried (boolean: whether the beer has actually been tasted)
- note (free text)
- aftertaste (free text)
- goesWellWith (list of chips plus optional free text; preset chips: Red meat,
  Pasta white sauce, Pasta tomato sauce, Salmon, White fish, Dessert; new chips
  are remembered for reuse)
- buyAgain (boolean)
- favourite (boolean, shown as a star)
- dateAdded
- catalogArticleNumber (optional link to a CatalogProduct)
- userPhotoRef (optional, a photo the user took)
- addedBy (which member added it; useful once two people share the list)

Grade and tried rules:
- A grade only means something for a beer that was tasted, so a grade implies
  tried is true.
- Valid grade values stay 5 to 10.
- Three legal states, and no others:
  1. Not tried: no grade, tried false. A beer noted in the store to buy later.
  2. Tried without a grade: no grade, tried true. Tasted, not scored yet.
  3. Tried with a grade: grade 5 to 10, tried true.
- The domain model rejects any other combination, so the list can never hold a
  graded beer that is marked as not tried.

Cellar (the shared group):
- id
- inviteCode (short, used for pairing)
- members (list of anonymous user ids)
- createdAt

### 3.2 Catalog snapshot (bundled, read-only)

A beer-only snapshot of the Systembolaget assortment, bundled inside the app and
queried locally. Sourced from a community data mirror at build time. Not synced
(identical for both users).

CatalogProduct:
- articleNumber
- name
- brewery
- type
- alcoholPercent
- volumeMl
- price (as of snapshot date; treated as approximate)
- imageUrl (image loaded on demand when online, not bundled)

### 3.3 Reference lists

- Beer types: a managed list, extendable by the user.
- Food-pairing chips: preset list plus user additions, remembered across entries.

## 4. Architecture

- Repository pattern. The app talks to a `BeerRepository` interface. The v1
  implementation is backed by Firebase Firestore (with its offline cache). If
  Firebase does not work out, a different implementation can replace it behind
  the same interface without rewriting the app. This is the "redesign if it is
  not working" safety valve the user asked for.
- The catalog snapshot sits behind a separate `CatalogRepository` interface,
  backed by a local database in v1.
- MVVM: screens talk to view-models, view-models talk to repositories. UI is
  kept thin so logic is testable without the UI.

## 5. Sync and pairing

- Authentication: Firebase Anonymous Auth. Each phone gets an anonymous user id.
  No email, no password, no account creation. This keeps pairing frictionless
  and avoids handling any credentials.
- Pairing flow (WhatsApp-style, one device authorizes the other):
  1. Phone A creates a cellar and receives a short invite code (optionally shown
     as a QR code or a share link, both encoding the same code).
  2. Phone B enters the code (or scans the QR / opens the link) and joins the
     same cellar.
  3. Both phones now read and write the same list in real time.
- Firestore security rules restrict each cellar to its member ids.
- Offline: Firestore offline persistence keeps the list available with no
  signal in the store; changes sync when connectivity returns.
- Cost: at two users with occasional writes, usage stays well inside the
  Firebase free tier.

## 6. Screens and flows

### Overview (home)

- Search box matching name, brewery, and type.
- Filter chips: Buy again, Favourites, Not tried, and a beer-type multi-select
  combobox (choose one or more types to show). The Not tried chip shows only the
  beers that have not been tasted yet, which is the in-store shopping list.
- Sort menu: grade (default), price, name or brewery, date added. Grade sort runs
  from highest to lowest and puts beers with no grade last.
- Each row shows: name, brewery, type, the grade prominently, plus star and
  buy-again markers. A beer that has not been tried shows a "Not tried" badge
  where the grade would be, and a tried beer with no score shows "No grade".
- Tap a row to open the detail screen.

### Add beer

Entry point is a plus button offering three paths:
1. Manual: fill any fields directly. Always available, the reliable baseline.
2. Scan Systembolaget label: capture the shelf label, read the article number
   via on-device text recognition, look it up in the catalog snapshot, and
   pre-fill all fields. The user reviews, grades, and saves.
3. Photo of can: capture the can, read the name and brewery text via on-device
   text recognition, fuzzy-match against the catalog snapshot, and pre-fill what
   matches. If there is no match (for example a beer Systembolaget does not
   sell), keep whatever text was read and leave the rest for manual entry.

All fields remain editable regardless of the path used.

The grade is optional, which is what makes the in-store path work: a beer can be
saved with nothing but a name. Selecting a grade marks the beer as tried. A
"tried" toggle sits next to the grade and can be switched on with no grade, for a
beer that was tasted but not scored yet. Switching the toggle off clears the
grade, so a saved beer always lands in one of the three legal states.

### Beer detail

- Shows all fields, with a "Not tried" or "No grade" label in place of the number
  when the beer has no grade. Grading it is done from the edit screen.
- Edit any field, toggle favourite and buy-again, delete the entry.
- Product image loads from its URL when online.

## 7. Catalog snapshot and lookup

- Bundle a beer-only snapshot (roughly 2 to 5 MB) inside the app, produced from
  a community Systembolaget data mirror at build time.
- Shelf label path: read the article number, exact-match in the snapshot, fill
  all fields.
- Can path: read the name and brewery text, fuzzy-match against the snapshot,
  fill matched fields.
- Images are fetched on demand from their URLs, never bundled (bundling all
  images would be hundreds of MB).
- The snapshot is refreshed by rebuilding and shipping a new app version.

## 8. Tech stack

- Kotlin with Jetpack Compose for the UI.
- Room (on-device SQLite) for the bundled catalog snapshot and any local cache.
- Firebase Firestore and Firebase Anonymous Auth for the synced cellar.
- CameraX with ML Kit Text Recognition for on-device, offline text reading.
- Coil for on-demand image loading.
- Minimum Android 8 (API 26).

## 9. Build and distribution pipeline

- GitHub Actions workflow builds a signed release APK.
- The APK is published to the repository Releases page.
- Install on a phone by downloading the APK from Releases (the repository is
  public, so no login is needed) and allowing install from unknown sources.
- Signing uses a keystore stored as GitHub Actions secrets. The user holds the
  keystore passwords; secret values are entered by the user, not by the
  assistant.

## 10. Testing

- Repositories and view-models are unit-tested against a fake in-memory
  repository.
- Text-parsing logic (article-number extraction, name matching) is tested with
  sample input strings.
- UI is kept thin so most behavior is covered without instrumentation tests.

## 11. External dependencies owned by the user

- Create the Firebase project, add an Android app to it, and provide the
  `google-services.json` config file. Step-by-step instructions will be given
  when this step is reached.
- Authenticate the GitHub CLI (`gh auth login`) so the repository can be created
  and the pipeline can publish releases.
- Enter GitHub Actions secret values for APK signing.

## 12. Suggested build order

Sequenced so there is always a working app after each step.

1. Local list: add, grade, browse, filter, and sort tried beers on one phone
   (repository interface in place, local implementation first).
2. Catalog snapshot and the Systembolaget shelf-label scan path.
3. Can-photo text reading and fuzzy match.
4. Firebase sync and the invite-code pairing between two phones.
5. GitHub Actions build-and-release pipeline.

## 13. Notes

- The repository lives at `C:\Users\SilvijaSubotic\PersonalDevelopment\BeerTracker`
  (outside OneDrive, which avoids build and sync conflicts).
- Build outputs are git-ignored so they never sync or bloat the repository.
