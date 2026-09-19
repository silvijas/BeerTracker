# Setting up Firebase for BeerTracker sync

One-time setup, done by the project owner in a browser and one PowerShell
window. About fifteen minutes. Nothing here needs a credit card; the Spark
(free) plan covers two phones many times over.

## 1. Create the Firebase project

1. Open https://console.firebase.google.com and sign in with a Google account.
2. "Create a project" (or "Add project"), name it `BeerTracker`.
3. Google Analytics can be turned off; the app does not use it.
4. Wait for the project to be created and open it.

## 2. Register the Android app

1. On the project overview, click the Android icon ("Add app").
2. Android package name: `com.beertracker` (must match exactly).
3. App nickname: anything, for example `BeerTracker`. Leave the SHA-1 field
   empty; anonymous sign-in and Firestore do not need it.
4. Click "Register app", then "Download google-services.json".
5. Move the downloaded file to `app/google-services.json` inside the
   repository checkout. It is git-ignored on purpose; never commit it.
6. Skip the remaining console steps ("Add Firebase SDK" and so on); the app
   already contains them.

## 3. Turn on anonymous sign-in

1. Left menu: Build > Authentication > "Get started".
2. "Sign-in method" tab > "Add new provider" > "Anonymous" > enable > Save.

## 4. Create the Firestore database

1. Build > Firestore Database > "Create database".
2. Location: pick a European one, for example `eur3` (Europe multi-region)
   or `europe-north1`. This cannot be changed later.
3. Start in production mode (the rules below replace the defaults anyway).
   Create.
4. Open the "Rules" tab, delete everything there, paste the whole content of
   `firebase/firestore.rules` from this repository, and click "Publish".

## 5. Give the release pipeline the config

From PowerShell at the repository root (the GitHub CLI must be signed in;
`gh auth status` shows that):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("app\google-services.json")) | gh secret set GOOGLE_SERVICES_JSON_BASE64
```

The workflow decodes this secret into `app/google-services.json` before
building. Until the secret exists, the workflow prints a warning and ships
a build whose sync screen says sync is not set up.

## 6. Ship and pair

1. Push to main, or re-run the latest "Release signed APK" workflow, and
   install the new APK on both phones.
2. Phone A: gear icon on the overview > "Sync between phones" > "Create a
   shared cellar". Share the code (WhatsApp is fine).
3. Phone B: same screen > type the code > "Join". Both lists merge within
   seconds while online.

Keep the shared message with the code. A reinstalled phone needs it to join
again.

## Local builds

A local build picks up `app/google-services.json` automatically when it is
present and prints "building without Firebase sync" when it is not. Both
build fine; only the sync screen differs.

## If something goes wrong

- "No cellar has that code": the code was mistyped, or the invite record
  was never created (check Firestore > Data > `invites`).
- "Could not reach the server": no connection, or the Firestore database
  was not created yet, or the rules were not published.
- The sync screen says sync is not set up: the APK was built without the
  config. Check the workflow run for the warning and the secret's name.
- A write was rejected by the rules: Firestore > Rules > "Rules playground"
  replays a request against the published rules; the shipped rules are in
  `firebase/firestore.rules`.
