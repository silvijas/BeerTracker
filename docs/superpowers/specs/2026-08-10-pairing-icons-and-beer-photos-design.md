# Pairing icons and beer photos, design

Date: 2026-08-10
Status: draft, awaiting user review

## Goal

Two related changes to how a beer is presented:

1. Replace the app's ad hoc pairing list with Systembolaget's own food
   pairing vocabulary, shown as icons the way
   `systembolaget.se/produkt/ol/fatamorgana-3118615/` shows them (a pig, a
   bird, a sheep, two clinking glasses), and fill those pairings in
   automatically from the catalog while still letting the user change them.
2. Show the beer's picture in the main list and on the add/edit screen, and
   let the user attach their own photo.

## Background

### Pairings today

`Presets.pairings` (`domain/Presets.kt:8`) is six invented English values:
`Red meat`, `Pasta white sauce`, `Pasta tomato sauce`, `Salmon`,
`White fish`, `Dessert`. They are offered as plain `FilterChip`s in
`AddEditScreen` (`AddEditScreen.kt:414`), merged with any values already on
saved beers by `AddEditBeerViewModel.pairingOptions`
(`AddEditBeerViewModel.kt:86`), and rendered on the detail screen as a
comma joined string (`DetailScreen.kt:281`). Storage is
`TriedBeer.goesWellWith: List<String>`, persisted through the unit separator
`Converters` in `BeerEntity.kt:30`.

### What Systembolaget actually publishes

The product search API the catalog already fetches
(`api-extern.systembolaget.se/sb-api-ecommerce/v1/productsearch/search`,
see `CatalogFetcher.kt`) returns a `tasteSymbols` array per product, for
example `["Fläsk", "Fågel", "Lamm", "Sällskapsdryck"]` for Fatamorgana. We
do not read the field today.

Sampling 750 beers from that endpoint on 2026-08-10 found exactly 15
distinct symbols across the beer category, with these frequencies:

| Symbol | Beers | English label |
| --- | --- | --- |
| Sällskapsdryck | 509 | Social drink |
| Fläsk | 317 | Pork |
| Lamm | 254 | Lamb |
| Grönsaker | 208 | Vegetables |
| Nöt | 206 | Beef |
| Fågel | 159 | Poultry |
| Fisk | 94 | Fish |
| Skaldjur | 24 | Shellfish |
| Vilt | 22 | Game |
| Kryddstarkt | 18 | Spicy food |
| Aperitif | 16 | Aperitif |
| Dessert | 15 | Dessert |
| Buffémat | 12 | Buffet |
| Ost | 12 | Cheese |
| Asiatiskt | 3 | Asian food |

### Images today

`TriedBeer.imageUrl` exists and already holds the catalog product image for
any beer added from the catalog (`AddEditBeerViewModel.formFilledFrom`,
`AddEditBeerViewModel.kt:201`, copies `CatalogProduct.displayImageUrl`).
It is rendered on the detail screen (`DetailScreen.kt:179`) and in the
catalog browser row (`CatalogBrowserScreen.kt:185`), both through Coil's
`AsyncImage`. It is not rendered in `BeerListItem` or on the add/edit
screen, which is the gap this spec closes. There is no way to attach a
photo of one's own.

## UX

### Pairing icons

Fifteen flat, single colour silhouettes, one per pairing, in the spirit of
the Systembolaget set but drawn by us. Systembolaget's SVG files are their
artwork and are not copied; the icons here are original paths authored in
this repo, following the same construction as the existing `BeerCanIcon`
(`ui/components/BeerCan.kt:18`).

- Detail screen: the comma joined string becomes a `FlowRow` of cells, each
  a 32dp icon above its label in `bodySmall`, centred. The `no_pairings`
  empty state is unchanged.
- Add/edit screen: each `FilterChip` gains an 18dp leading icon. All 15 are
  always listed, in enum order, followed by any custom values already saved
  on the beer. Ticking and unticking is unchanged, so a catalog filled
  pairing can be removed and any other added.
- A pairing string that matches no enum label (the user's own "Other
  pairing" text) renders with its label only and no icon, in both places.
- Icons are tinted by the caller from the theme, so they follow light and
  dark mode like every other icon in the app.

Display order is the enum's declaration order, not alphabetical, so related
foods stay together and the occasion values land last:

```
Pork, Poultry, Lamb, Beef, Game, Fish, Shellfish, Vegetables, Cheese,
Dessert, Spicy food, Asian food, Buffet, Aperitif, Social drink
```

### Beer image in the main list

`BeerListItem` gains a leading 48dp thumbnail, identical in size, shape, and
background to the catalog browser's, so the two lists read as one app. When
a beer has no image the same 48dp box draws a muted beer can glyph on
`surfaceVariant`, so every row keeps the same left edge and the same height.

### Photos on the add/edit screen

An image block at the top of the form, above the Basics section:

- Shows the beer's current image (the user's photo if there is one, else the
  catalog image), 160dp tall, same clip and background treatment as the
  detail screen's.
- With no image at all, shows the same muted beer can placeholder.
- Two buttons under it: "Take photo" and "Choose photo". Once the beer has a
  photo of the user's own, a third appears: "Remove photo".
- Removing a photo falls back to the catalog image if the beer has one,
  rather than leaving the beer blank. This is why the photo is a separate
  field rather than an overwrite of `imageUrl`.

## Architecture

### Domain

New `domain/Pairing.kt`:

```kotlin
enum class Pairing(val symbol: String, val label: String) {
    PORK("Fläsk", "Pork"),
    POULTRY("Fågel", "Poultry"),
    LAMB("Lamm", "Lamb"),
    BEEF("Nöt", "Beef"),
    GAME("Vilt", "Game"),
    FISH("Fisk", "Fish"),
    SHELLFISH("Skaldjur", "Shellfish"),
    VEGETABLES("Grönsaker", "Vegetables"),
    CHEESE("Ost", "Cheese"),
    DESSERT("Dessert", "Dessert"),
    SPICY("Kryddstarkt", "Spicy food"),
    ASIAN("Asiatiskt", "Asian food"),
    BUFFET("Buffémat", "Buffet"),
    APERITIF("Aperitif", "Aperitif"),
    SOCIAL("Sällskapsdryck", "Social drink"),
    ;
    companion object {
        fun fromSymbol(symbol: String): Pairing?
        fun fromLabel(label: String): Pairing?
    }
}
```

`symbol` is the Swedish key used only for matching API responses; `label` is
what is stored and displayed. Storing the label rather than the enum name
keeps `goesWellWith` a plain `List<String>` and keeps free text pairings
working with no schema change.

`Presets.pairings` is deleted. `Presets.beerTypes` is untouched.
`AddEditBeerViewModel.pairingOptions` stops merging presets with saved
values and instead exposes the 15 enum labels followed by any value found on
a saved beer that matches no enum label, deduplicated. It keeps observing
every saved beer, not just the one being edited, so a custom pairing typed
once stays reusable, which is what it does today.

`TriedBeer` gains `photoUri: String?`, documented as a local `file://` URI
for a photo the user attached, distinct from `imageUrl`, the remote catalog
image. A `displayImageUrl` accessor returns `photoUri ?: imageUrl` and is
the single place every screen reads for "which picture do I show".

`CatalogProduct` gains `pairings: List<String>`, already mapped to English
labels at parse time.

### Data

`CatalogFetcher.mapProduct` (`CatalogFetcher.kt:121`) reads the
`tasteSymbols` array, maps each entry through `Pairing.fromSymbol`, drops
anything unknown, and emits the English labels in enum order. Dropping
unknown symbols rather than passing them through means a symbol
Systembolaget adds later cannot leak Swedish text into an English UI; it
simply does not appear until we add it to the enum.

`scripts/fetch_catalog.py` gets the identical mapping, including the same
drop-unknown rule and the same ordering. The file comment at
`CatalogFetcher.kt:116` requires the two mappers stay field for field
identical, and both test suites share one sample product to hold them
together; the sample gains a `tasteSymbols` array so that guarantee keeps
covering the new field.

`app/src/main/assets/catalog/beers.json` is regenerated with the new field.

`CatalogBeerEntity` gains a `pairings: List<String>` column, using the
existing `Converters`. `CatalogDatabase` goes from version 1 to version 2.
No hand written migration is needed or wanted: that database is explicitly
a disposable cache rebuilt from the bundled asset, and it already carries
`fallbackToDestructiveMigration` for exactly this case
(`CatalogDatabase.kt:26`).

`BeerEntity` gains `photoUri: String?`. `BeerDatabase` takes the next
unused version with a non destructive migration, following the rule stated
at `BeerDatabase.kt:17`: never `fallbackToDestructiveMigration`, because
this database holds the user's real beers.

Version numbering note: the unimplemented
`2026-08-10-five-point-grade-scale-design.md` spec also claims version 3
for its grade clearing migration. Whichever lands second takes version 4.
At implementation time, read the current `@Database(version = ...)` and take
the next integer; do not assume 3.

That migration does two things in one step:

1. `ALTER TABLE tried_beers ADD COLUMN photoUri TEXT`.
2. Remaps stored pairing values to the new vocabulary.

The remap is done in Kotlin inside the migration, iterating rows with a
cursor and rewriting each `goesWellWith` value, not with SQL string
replacement. `goesWellWith` is a unit separator joined string, and two old
values collapse onto one new value, so correct deduplication needs real
list handling:

| Old value | New value |
| --- | --- |
| Red meat | Beef |
| Salmon | Fish |
| White fish | Fish |
| Dessert | Dessert (unchanged) |
| Pasta white sauce | removed |
| Pasta tomato sauce | removed |

A beer carrying both `Salmon` and `White fish` ends with a single `Fish`.
Values the table above does not mention are left exactly as they are, so
anything typed into "Other pairing" survives. Removing the two pasta values
is deliberate and destructive, chosen by the user in preference to keeping
them as iconless text.

### Photo storage

Photos are copied into `context.filesDir/beer-photos/<uuid>.jpg` and
referenced by `file://` URI. App private storage means no storage
permission and no media scanner involvement, and the file survives catalog
refreshes and app updates.

- Camera: `ActivityResultContracts.TakePicture`, writing to a
  `FileProvider` URI. Requires a `<provider>` entry in `AndroidManifest.xml`
  and a `res/xml/file_paths.xml` declaring the `beer-photos` subdirectory.
  The manifest already declares `android.permission.CAMERA` for the scanner,
  so no new permission is added.
- Gallery: `ActivityResultContracts.PickVisualMedia`, the Android photo
  picker. It needs no permission on API 33 and above and is backported by
  `androidx.activity` on older versions, down to the app's `minSdk` of 26.
  The picked content URI is copied into `beer-photos` rather than persisted
  as a content URI, so the beer cannot lose its picture when the user
  deletes the original from their gallery.

File lifecycle, all owned by one small `BeerPhotoStore` class in `data` so
no screen deals with files directly:

- Replacing a photo writes the new file first, then deletes the old one.
- "Remove photo" clears `photoUri` and deletes the file.
- Deleting a beer deletes its photo file, in `RoomBeerRepository.deleteBeer`.
- A photo taken or picked on an add form that is then abandoned without
  saving leaves an orphan file. `BeerPhotoStore` gets a `deleteOrphans`
  sweep run on app start from `BeerApp`, removing files in `beer-photos`
  that no row references.

### UI

New `ui/components/PairingIcons.kt`:

- 15 `ImageVector` values, 24dp default size, 24 by 24 viewport, one
  `SolidColor(Color.Black)` path each, built exactly like `BeerCanIcon`.
- `pairingIcon(pairing: Pairing): ImageVector` maps enum to vector, so the
  `when` is exhaustive at compile time and a new enum entry cannot ship
  without its icon.
- `PairingIcon(pairing: Pairing, size: Dp, modifier: Modifier)` composable
  wrapping `Icon`, `contentDescription = null` because the label is always
  adjacent text.
- A `@Preview` showing all 15 with labels at both 18dp and 32dp, in light
  and dark, so the drawings can be checked at real size.

New `ui/components/BeerThumbnail.kt`: a 48dp `AsyncImage` with the muted
beer can placeholder, used by `BeerListItem` and by
`CatalogBrowserScreen.CatalogListItem`, replacing the ad hoc `AsyncImage`
in the latter so the two cannot drift.

New `ui/components/PairingRow.kt`: the detail screen's `FlowRow` of icon
above label cells, taking `List<String>` and resolving each through
`Pairing.fromLabel`.

`DetailScreen`: the pairings `DetailText` is replaced by `PairingRow`; the
image `AsyncImage` reads `beer.displayImageUrl` instead of `beer.imageUrl`.

`AddEditScreen`: gains the image block described above; `FilterChip`s gain
leading icons. The file is already 609 lines and is doing several jobs, so
the image block goes in its own `ui/components/BeerPhotoField.kt` rather
than growing `AddEditScreen` further.

`AddEditBeerViewModel`: `BeerFormState` gains `photoUri: String?`; `load`,
`formFilledFrom`, and `save` carry it; `formFilledFrom` also seeds
`pairings = product.pairings.toSet()` so a scanned or picked beer arrives
with its chips already ticked. New `setPhoto(uri: String?)` handles the
store write and the old file delete.

### New strings

`take_photo`, `choose_photo`, `remove_photo`, `photo_error` (a snackbar
message for a failed copy), and one string per pairing label
(`pairing_pork` and so on) so the labels are translatable rather than hard
coded.

The enum's `label` stays the storage value and lives in `domain`, which is
plain Kotlin with no Android dependencies so it stays JVM testable. The
resource mapping therefore lives in the UI layer, next to the icon mapping:
`pairingLabelRes(pairing: Pairing): Int` in `PairingIcons.kt`, an exhaustive
`when` like `pairingIcon`. A custom text pairing has no resource and is
displayed verbatim.

## Error handling and edge cases

- A product with no `tasteSymbols`, or with the key absent entirely, yields
  an empty list, and the add form simply has nothing ticked.
- A camera or picker result that fails to copy leaves `photoUri` unchanged
  and shows the `photo_error` snackbar; no partial file is left behind,
  since the copy writes to a temporary name and renames on success.
- A `photoUri` whose file has vanished (manual clearing of app data,
  restore from a backup that excluded files) fails to load in Coil and
  falls through to the placeholder. It is not auto cleared, because a
  transient read failure should not silently discard the reference.
- The pairing remap migration runs once at database open, exactly like
  `MIGRATION_1_2`. No runtime code path special cases old values, so a beer
  that somehow still holds `Pasta white sauce` would just render as an
  iconless text chip rather than crash.
- `Pairing.fromLabel` and `fromSymbol` are case sensitive exact matches
  against a map built once, not fuzzy matches, so a rename on
  Systembolaget's side surfaces as a missing pairing rather than a wrong one.

## Testing

Same JVM only strategy as the rest of the app, Robolectric for anything
needing Android:

- `PairingTest`: every enum entry round trips through `fromSymbol` and
  `fromLabel`; unknown input returns null; the 15 labels are unique.
- `PairingIconsTest`: `pairingIcon` returns a distinct vector for every enum
  entry, so no two pairings share a drawing by accident.
- `CatalogFetcherTest`: the shared sample product with a `tasteSymbols`
  array maps to the expected English labels in enum order; unknown symbols
  are dropped; a missing key yields an empty list.
- `scripts` test suite: the same three assertions against the Python mapper,
  on the same sample, keeping the two mappers in step.
- `BeerDatabaseMigrationTest`: a new migration test in the shape of the
  existing `migrating 1 to 2` one. Seeds rows covering every remap case,
  including a beer holding both `Salmon` and `White fish` (asserting a
  single `Fish` results), a beer holding only the two pasta values
  (asserting an empty list results), and a beer with custom text (asserting
  it is untouched). Asserts `photoUri` is added as null and no other column
  changes.
- `AddEditBeerViewModelTest`: prefill from a catalog product with pairings
  ticks those chips; the user can untick a catalog filled pairing and the
  change survives save; `photoUri` round trips through load and save.
- `BeerPhotoStoreTest`: writing a photo creates a file; replacing deletes
  the old one; removing deletes the file; `deleteOrphans` removes an
  unreferenced file and keeps a referenced one.
- Compose tests: `BeerListItem` renders a thumbnail placeholder for a beer
  with no image; the detail screen shows a pairing's label for a known
  pairing and for a custom text value.

## Alternatives considered

- Reusing Systembolaget's icon SVGs directly: rejected. They are
  Systembolaget's artwork, and this app is not affiliated with them. The
  same visual idea drawn ourselves carries no such problem.
- Storing pairings as enum names rather than labels: rejected. It would
  force a data migration of every existing value and break the free text
  "Other pairing" field, which stores arbitrary strings in the same list.
- Overwriting `imageUrl` with the user's photo instead of adding
  `photoUri`: rejected. Removing the photo would then leave a catalog beer
  with no picture at all, and there would be no way to get the catalog
  image back short of re-adding the beer.
- Persisting the gallery's content URI instead of copying the file:
  rejected. The permission grant is not durable across reboots without
  `takePersistableUriPermission`, and the user deleting the original photo
  would silently blank the beer.
- Showing pairing icons on main list rows too: rejected by the user, to
  keep rows readable now that they carry a thumbnail.

## Out of scope

- The half landed five point grade scale work (`GradeCanPicker` still
  offering 5 to 10 while validation accepts 1 to 5). Tracked by
  `2026-08-10-five-point-grade-scale-design.md`; this spec only avoids
  colliding with its database version.
- Filtering or sorting the main list by pairing.
- Systembolaget's `usage` free text ("Serveras vid 8-10°C ...") and the
  taste clock values (`tasteClockBitter` and siblings), both available in
  the same API response. Worth a later spec, not this one.
- Any change to the catalog refresh flow, scanning, or the brewery beers
  screen.
