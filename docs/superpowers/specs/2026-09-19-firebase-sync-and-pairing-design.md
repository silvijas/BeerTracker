# Firebase sync and invite-code pairing, design

Date: 2026-09-19
Status: approved with defaults (the user asked for phase 4 to be built and
was away during design; every open question below got the recommended
answer and each is easy to change)

## Goal

Phase 4 of the v1 design (build order step 4, "Firebase sync and the
invite-code pairing between two phones"). Two phones share one beer list.
One phone creates a shared cellar and receives a short invite code; the
other phone types that code and joins. From then on every beer added,
edited or deleted on either phone shows up on the other, with no account,
no email and no password. The app keeps working with no signal inside a
Systembolaget store and catches up when the connection returns.

## Background

- Every screen talks to the `BeerRepository` interface (observe, get, add,
  update, delete). The one implementation, `RoomBeerRepository`, stores
  beers in the Room database `beertracker.db` (schema version 4). Beer ids
  are already random UUID strings made on the add form, so an id is unique
  across phones without any coordination.
- `TriedBeer` already carries `addedBy: String?`, meant for "which member
  added it"; nothing fills it today. `photoUri` is a `file://` URI into the
  phone's private storage (`BeerPhotoStore`), meaningless on another phone.
- `AppContainer` (in `BeerApp.kt`) wires the repositories; `BeerApp` owns an
  application coroutine scope used for startup work (catalog import, photo
  sweep, catalog auto refresh).
- Settings live in SharedPreferences (`PrefsSettingsRepository`, one theme
  value).
- The signed release APK is built by `.github/workflows/release.yml` on
  every push to main. The signing keystore is restored from a base64
  GitHub secret; the workflow fails loudly when any signing secret is
  missing.
- The repository is public. Firebase's `google-services.json` holds the
  project id, the Android app id and a Firebase API key. Google documents
  those as identifiers rather than secrets, but keeping the file out of a
  public repository costs nothing and matches how the keystore is handled.
- Firebase artifacts available on Google's Maven repository at design
  time: firebase-bom 34.19.0, google-services Gradle plugin 4.5.0. In BOM
  34 the Kotlin extensions ship inside `firebase-auth` and
  `firebase-firestore` themselves (the separate `-ktx` artifacts are
  retired). Found while building: BOM 34.13.0 and later ship firebase-auth built with Kotlin 2.3, which this project's Kotlin 2.0.21 cannot read, so the build pins BOM 34.12.0 (firebase-auth 24.0.1, firebase-firestore 26.2.0); moving past it means upgrading Kotlin, KSP, AGP, Gradle and Room together.

The Room database does not change in this phase: no new column, no new
table, no migration. The catalog database is untouched too.

## Decisions taken with defaults (user was away during design)

1. Room stays the source of truth on each phone; Firestore mirrors it.
   Every screen keeps reading and writing Room exactly as today. A sync
   engine pushes local writes to Firestore and applies Firestore changes
   back into Room. The v1 design spoke of swapping a Firestore repository
   in behind the interface; three phases of Room-specific work (migrations,
   photo files, DAO tests) and the need to work with no signal on a first
   launch make mirroring the safer shape. The interface stays, so the
   swap remains possible later.
2. The Firebase configuration file is not committed. `app/google-services.json`
   is git-ignored; the user keeps it locally and stores it base64-encoded
   in the GitHub secret `GOOGLE_SERVICES_JSON_BASE64`, which the release
   workflow restores before building, the way the keystore is restored.
   A build without the file still compiles, tests and runs; only the sync
   screen changes, to say that this build has no sync. The workflow warns
   instead of failing when the secret is absent, so main stays releasable
   before the Firebase project exists.
3. Nothing talks to Firebase until the user taps Create or Join on the
   sync screen. There is no sign-in at first launch, so the first launch
   in a store with no signal works exactly as today.
4. The invite code is eight characters from an alphabet without the
   look-alikes 0, O, 1, I and L, shown and shared as `ABCD-EFGH`. One code
   per cellar, valid for as long as the cellar exists, no expiry and no QR
   code. The v1 design listed QR and links as optional conveniences; both
   are deferred.
5. Conflicts: the last write to reach the server wins, per beer, as a
   whole record. Two people grading the same beer offline at the same
   moment is rare; the loser sees the other grade and can change it. An
   edit saved after the other phone deleted the beer brings the beer
   back, which is visible and easy to undo; the alternative (silently
   dropping the edit) is not.
6. Photos stay on the phone that took them. Only the catalog image URL is
   shared, so the other phone shows the catalog picture. A remote change
   never clears a local photo.
7. The entry point is a new "Sync between phones" item at the bottom of
   the gear menu on the overview, which becomes a settings menu (theme
   choices, a divider, then sync). The top bar is already full on a
   narrow phone, as the phase 3 design noted, and the menu's content
   description changes from "Theme" to "Settings".
8. The Firestore listener runs for the lifetime of the app process and is
   started at app launch when the phone is paired. There is no background
   service and no periodic job; opening the app is what syncs it.
9. `addedBy` is filled with the phone's anonymous user id when a beer is
   added while the phone is paired, null otherwise. Nothing displays it
   yet; it costs three lines and matches the v1 data model.
10. Pairing merges: when a phone joins a cellar, everything already on
    the phone is uploaded and everything already in the cellar is
    downloaded. Nothing is deduplicated; two entries for one beer are
    two entries the users can clean up by hand.
11. "Stop syncing on this phone" only forgets the pairing on this phone.
    The beers stay in Room, the other phone keeps its copy, and the
    phone can pair again later with the same or another code.
12. Security rules are pasted into the Firebase console by the user; the
    repository holds them as `firebase/firestore.rules` so the text is
    versioned. No Firebase CLI, no Cloud Functions (which would need a
    paid plan), no App Check.

## UX

### Settings menu (overview top bar)

- The gear icon's content description becomes "Settings".
- Its dropdown keeps the three theme items, then a divider, then
  "Sync between phones", which opens the sync screen.

### Sync screen (nav route `sync`)

Top bar: back arrow, title "Sync between phones". The body follows the
sync status.

Build without Firebase configuration:

- The shared `ErrorState` with title "Sync is not set up in this build"
  and message "This copy of BeerTracker was built without its Firebase
  configuration, so it cannot share a cellar. Install a release build
  that includes it." No buttons.

Not paired:

- Intro text: "Share one cellar between two phones. One phone creates the
  shared cellar and gets a code; the other phone joins with that code.
  Both phones keep working offline and catch up when they are back
  online."
- A filled button "Create a shared cellar".
- Section "Join with a code" (the shared `SectionHeader`), supporting text
  "Enter the code shown on the other phone." An `OutlinedTextField`
  labelled "Invite code" with placeholder "ABCD-EFGH", single line,
  capitalised characters keyboard, and a "Join" button enabled once the
  field is not blank.
- While Create or Join is running, a progress indicator shows and both
  buttons and the field are disabled.
- A failure shows one line of error text under the buttons, in the error
  colour, until the next attempt or until the user edits the code:
  - code not eight letters and digits: "Codes have eight letters and
    digits, like ABCD-EFGH."
  - unknown code: "No cellar has that code. Check it on the other phone
    and try again."
  - no connection: "Could not reach the server. Check the connection and
    try again."
  - anything else: "Something went wrong while syncing. Try again."

Paired:

- Section "Invite code", supporting text "Enter this code on the other
  phone to share this cellar." The code in large type as `ABCD-EFGH`.
- An outlined button "Share code" that opens the Android share sheet with
  the text "Join my BeerTracker cellar with the code ABCD-EFGH."
- A status block of one to three lines:
  - members: "Connecting." until the cellar record has been read, then
    "Only this phone so far." for one member, otherwise "N phones share
    this cellar."
  - last sync: "Waiting for the first sync." until the first server
    snapshot, then "Last synced <date and time>" in the phone's locale.
  - only when changes are waiting: "Changes on this phone are waiting to
    upload."
- A text button "Stop syncing on this phone" in the error colour. It
  opens a dialog titled "Stop syncing?" with the text "Your beers stay on
  this phone, and the other phone keeps its copy. Changes are no longer
  shared until you pair again.", confirm "Stop syncing", dismiss
  "Cancel".

Nothing else in the app changes its appearance. There is no sync badge
on the overview and no per-beer sync marker.

## Architecture

```
screens ---> SyncingBeerRepository ---> RoomBeerRepository ---> Room
                     |    (local write first, then push)     ^
                     v                                       | apply remote
              CellarSyncEngine  <--- observeBeers ----  CellarRemote
                     |                                (FirestoreCellarRemote
              SyncMembershipStore                      or Unavailable)
              (SharedPreferences)
```

### Domain (pure Kotlin, JVM tested)

`domain/InviteCodes.kt`

```kotlin
object InviteCodes {
    const val LENGTH = 8
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    fun generate(random: Random = Random.Default): String
    /** Upper-cases and drops spaces and hyphens; null unless the result is LENGTH characters of ALPHABET. */
    fun normalize(input: String): String?
    /** "ABCDEFGH" becomes "ABCD-EFGH". */
    fun format(code: String): String
}
```

Thirty-one symbols to the power of eight is about 8.5 times 10 to the 11
codes, plenty against guessing when every guess is one authenticated
read (see the rules below).

`domain/CellarSync.kt`

```kotlin
data class CellarMembership(val cellarId: String, val inviteCode: String)

data class CellarInfo(val inviteCode: String, val memberCount: Int)

sealed interface RemoteChange {
    data class Upsert(val beer: TriedBeer) : RemoteChange
    data class Remove(val beerId: String) : RemoteChange
}

/** One listener callback: the document changes since the previous one. */
data class RemoteBeersUpdate(
    val changes: List<RemoteChange>,
    val fromServer: Boolean,
    val hasPendingWrites: Boolean,
)

sealed class SyncException(message: String) : Exception(message) {
    class Unavailable : SyncException("Sync is not configured in this build")
    class Offline : SyncException("Could not reach the server")
    class InvalidCode : SyncException("The code is not eight letters and digits")
    class UnknownCode : SyncException("No cellar has this code")
    class Failed(cause: Throwable) : SyncException(cause.message ?: "Sync failed")
}

/** Everything the app needs from Firebase, so the engine and the UI never see Firebase types. */
interface CellarRemote {
    val isAvailable: Boolean
    val currentUserId: String?
    /** Anonymous sign-in, or the existing user; returns the user id. */
    suspend fun signIn(): String
    /** Creates the cellar and its invite record; returns the cellar id. */
    suspend fun createCellar(inviteCode: String): String
    /** Resolves the code and adds this user to the cellar; returns the cellar id. */
    suspend fun joinCellar(inviteCode: String): String
    fun observeCellar(cellarId: String): Flow<CellarInfo>
    fun observeBeers(cellarId: String): Flow<RemoteBeersUpdate>
    /** Enqueues; returns at once. Firestore delivers it when it can. */
    fun putBeer(cellarId: String, beer: TriedBeer)
    fun deleteBeer(cellarId: String, beerId: String)
}

interface SyncMembershipStore {
    val membership: StateFlow<CellarMembership?>
    fun save(membership: CellarMembership)
    fun clear()
}

sealed interface SyncStatus {
    data object Unavailable : SyncStatus
    data object NotPaired : SyncStatus
    data class Paired(
        val inviteCode: String,
        /** Null until the cellar record has been read. */
        val memberCount: Int?,
        /** Null until the first snapshot from the server. */
        val lastSyncedUtc: Long?,
        val hasPendingUploads: Boolean,
    ) : SyncStatus
}
```

`domain/RemoteBeerCodec.kt`

```kotlin
object RemoteBeerCodec {
    /** Every TriedBeer field except id and photoUri, keyed by property name; absent values are explicit nulls. */
    fun toFields(beer: TriedBeer): Map<String, Any?>
    /** Null when the record cannot become a legal TriedBeer (no name, grade outside 1..5, graded but not tried). */
    fun fromFields(id: String, fields: Map<String, Any?>): TriedBeer?
}
```

Whole numbers (`volumeMl`, `grade`, `dateAdded`) are accepted back as
`Long`, `Int` or `Double`; decimals (`alcoholPercent`, `price`) as any
`Number`. `goesWellWith` is a list of strings; other element types are
dropped. Missing text fields read as empty strings, missing flags as
false, a missing `dateAdded` as 0. Explicit nulls on the way out matter
because a whole-record write must clear a value the other phone set.

### Data

`data/PrefsSyncMembershipStore.kt`: `SyncMembershipStore` over the
SharedPreferences file `sync` with keys `cellar_id` and `invite_code`;
both present means paired, anything else means not paired.

`data/CellarSyncEngine.kt`

```kotlin
class CellarSyncEngine(
    private val local: BeerRepository,          // the Room-backed repository
    private val remote: CellarRemote,
    private val membershipStore: SyncMembershipStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {
    val status: StateFlow<SyncStatus>
    /** The anonymous user id while paired, else null; stamped onto addedBy. */
    val pairedUserId: String?
    /** Starts following the stored membership; idempotent. */
    fun start()
    suspend fun createCellar()
    suspend fun joinCellar(input: String)
    fun stopSyncing()
    fun pushBeer(beer: TriedBeer)
    fun pushDelete(beerId: String)
}
```

- `start()` launches one job in `scope` that follows `membership`. With
  no membership, or when `remote.isAvailable` is false, nothing is
  observed. With a membership, it collects `remote.observeBeers(cellarId)`
  and `remote.observeCellar(cellarId)`. Each `RemoteChange.Upsert` is
  applied as `local.addBeer` when the id is new, otherwise as
  `local.updateBeer` with the existing row's `photoUri` kept (decision 6).
  Each `RemoteChange.Remove` is applied as `local.deleteBeer`, which also
  deletes the local photo file because the beer is gone for good. An
  update with `fromServer` true sets `lastSyncedUtc` to `clock()`;
  `hasPendingWrites` becomes `hasPendingUploads`.
- Remote changes are applied to the Room repository directly, never
  through `SyncingBeerRepository`, so nothing that came from Firestore is
  pushed back to Firestore. The echo of a local write (Firestore reports
  pending writes back through the listener) is applied too; it carries
  the same values, so the row does not change.
- If a listener flow fails (for example a rules change denying access)
  the failure is logged and the listener restarts after 30 seconds.
  `status` stays `Paired` in the meantime with whatever it last knew.
- `createCellar()`: throw `Unavailable` when the remote is unavailable;
  `signIn()`; generate a code; `remote.createCellar(code)`; save the
  membership (which starts the listener); then upload every local beer
  with `putBeer`.
- `joinCellar(input)`: `Unavailable` check; `InviteCodes.normalize`, or
  throw `InvalidCode`; `signIn()`; `remote.joinCellar(code)`, which throws
  `UnknownCode`; save the membership; upload every local beer.
- `stopSyncing()`: `membershipStore.clear()`, which stops the listener.
  The anonymous user stays signed in; pairing again reuses it.
- `pushBeer` and `pushDelete` do nothing without a membership; with one
  they call the remote at once, without waiting for the server.
- `status` is `Unavailable` when the remote is unavailable, `NotPaired`
  without a membership, otherwise `Paired` assembled from the membership's
  code, the last `CellarInfo`, and the listener's last-sync and pending
  flags.

`data/SyncingBeerRepository.kt`

```kotlin
class SyncingBeerRepository(
    private val local: BeerRepository,
    private val engine: CellarSyncEngine,
) : BeerRepository
```

`observeBeers` and `getBeer` delegate. `addBeer` fills a null `addedBy`
with `engine.pairedUserId`, writes locally, then `engine.pushBeer`.
`updateBeer` writes locally then pushes. `deleteBeer` deletes locally then
`engine.pushDelete`. The local write always comes first, so a Firestore
problem can never lose a local save.

`data/FirestoreCellarRemote.kt`

- `FirestoreCellarRemote.create(context): CellarRemote` returns the
  Firestore implementation when `FirebaseApp.getApps(context)` is not
  empty (the google-services plugin generated the resources that
  auto-initialise Firebase) and `UnavailableCellarRemote` otherwise, whose
  `isAvailable` is false, whose suspend functions throw
  `SyncException.Unavailable`, and whose flows are empty.
- `signIn()`: the current user, else `signInAnonymously()`. A
  `FirebaseNetworkException` becomes `SyncException.Offline`; any other
  failure `SyncException.Failed`.
- `createCellar(code)`: one batch writing `cellars/{newId}` with
  `{ inviteCode, members: [uid], createdAt: serverTimestamp }` and
  `invites/{code}` with `{ cellarId, createdAt: serverTimestamp }`,
  awaited with a 20 second timeout. A timeout is reported as `Offline`
  (Firestore queues offline writes without ever completing them, so a
  hanging commit means no server).
- `joinCellar(code)`: `invites/{code}` fetched from the server
  (`Source.SERVER`, so a stale cache can never answer); missing means
  `UnknownCode`; then `cellars/{cellarId}` updated with
  `members: arrayUnion(uid)`, awaited with the same timeout.
- `observeCellar`: a document listener mapped to `CellarInfo`.
- `observeBeers`: a collection listener on `cellars/{id}/beers` with
  metadata changes included, each callback mapped to one
  `RemoteBeersUpdate` from `documentChanges` (ADDED and MODIFIED become
  `Upsert`, REMOVED becomes `Remove`), `fromServer =
  !metadata.isFromCache`, `hasPendingWrites = metadata.hasPendingWrites`.
  A document that `RemoteBeerCodec.fromFields` rejects is logged and
  skipped, never thrown.
- `putBeer`: `set` on `cellars/{id}/beers/{beerId}` with
  `RemoteBeerCodec.toFields`. `deleteBeer`: `delete`. Neither awaits.
- Task-to-coroutine bridging uses `kotlinx-coroutines-play-services`.
  Firestore's offline persistence stays at its Android default (on).

### Firestore layout and security rules

```
cellars/{cellarId}                 { inviteCode: string, members: [uid], createdAt }
cellars/{cellarId}/beers/{beerId}  the RemoteBeerCodec fields
invites/{inviteCode}               { cellarId: string, createdAt }
```

`firebase/firestore.rules`:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function signedIn() {
      return request.auth != null;
    }

    function cellarPath(cellarId) {
      return /databases/$(database)/documents/cellars/$(cellarId);
    }

    function isMemberOf(cellarId) {
      return signedIn() && request.auth.uid in get(cellarPath(cellarId)).data.members;
    }

    // Any signed-in phone may look up one code it was given; nobody can
    // list codes. Created only by a member of the cellar it points at,
    // in the same batch as the cellar itself (hence getAfter).
    match /invites/{code} {
      allow get: if signedIn();
      allow list: if false;
      allow create: if signedIn()
        && request.resource.data.keys().hasOnly(['cellarId', 'createdAt'])
        && request.resource.data.cellarId is string
        && request.auth.uid in getAfter(cellarPath(request.resource.data.cellarId)).data.members;
      allow update, delete: if false;
    }

    match /cellars/{cellarId} {
      allow get: if signedIn() && request.auth.uid in resource.data.members;
      allow list: if false;
      allow create: if signedIn()
        && request.resource.data.keys().hasOnly(['inviteCode', 'members', 'createdAt'])
        && request.resource.data.inviteCode is string
        && request.resource.data.members == [request.auth.uid];
      // Members may change their cellar. Anyone else may only add
      // themselves, and nothing but themselves, to members: that is how
      // joining with a code works.
      allow update: if signedIn() && (
        request.auth.uid in resource.data.members
        || (
          request.resource.data.diff(resource.data).affectedKeys().hasOnly(['members'])
          && request.resource.data.members.hasAll(resource.data.members)
          && request.resource.data.members.size() == resource.data.members.size() + 1
          && request.auth.uid in request.resource.data.members
        )
      );
      allow delete: if false;

      match /beers/{beerId} {
        allow read, write: if isMemberOf(cellarId);
      }
    }
  }
}
```

A reinstalled phone gets a new anonymous id and simply joins again with
the code; `members` grows by one, which is why the rules do not cap it.

### UI

`ui/sync/SyncViewModel.kt`

```kotlin
enum class SyncError { INVALID_CODE, UNKNOWN_CODE, OFFLINE, UNAVAILABLE, FAILED }

data class SyncUiState(
    val status: SyncStatus = SyncStatus.NotPaired,
    val codeInput: String = "",
    val working: Boolean = false,
    val error: SyncError? = null,
)

class SyncViewModel(private val engine: CellarSyncEngine) : ViewModel() {
    val uiState: StateFlow<SyncUiState>
    fun setCodeInput(value: String)   // upper-cased as typed; clears the error
    fun createCellar()
    fun join()
    fun stopSyncing()
    fun dismissError()
    companion object { val Factory: ViewModelProvider.Factory }
}
```

`createCellar` and `join` ignore taps while `working`, set `working`,
call the engine, and map a `SyncException` onto `SyncError` (any other
exception onto `FAILED`, logged). `join` clears `codeInput` on success.

`ui/sync/SyncScreen.kt`

- `SyncScreen(viewModel, onBack)`: collects the state, owns the share
  intent (`Intent.ACTION_SEND`, plain text, via `Intent.createChooser`),
  and renders `SyncContent`.
- `internal fun SyncContent(state, onCodeChange, onCreate, onJoin,
  onStopSyncing, onShare: (String) -> Unit, onBack)`, the testable
  layout described under UX, including the stop dialog.

`ui/OverviewScreen.kt`: `OverviewScreen` gains `onSyncClick: () -> Unit
= {}`; the theme menu becomes the settings menu described above.

`MainActivity.kt`: new route `sync`; the overview passes `onSyncClick =
{ navController.navigate("sync") }`.

### App wiring

`AppContainer(context, scope)`:

```kotlin
private val localBeerRepository: BeerRepository = RoomBeerRepository(db.beerDao(), beerPhotoStore)
val cellarRemote: CellarRemote = FirestoreCellarRemote.create(context)
val syncMembershipStore: SyncMembershipStore = PrefsSyncMembershipStore(context)
val syncEngine = CellarSyncEngine(localBeerRepository, cellarRemote, syncMembershipStore, scope)
val beerRepository: BeerRepository = SyncingBeerRepository(localBeerRepository, syncEngine)
```

`BeerApp.onCreate` passes its application scope to the container and
calls `container.syncEngine.start()` before the existing startup work.

### Build and CI

- `gradle/libs.versions.toml`: versions `firebaseBom = "34.12.0"`,
  `googleServices = "4.5.0"`; libraries `firebase-bom` (platform),
  `firebase-auth`, `firebase-firestore`, `kotlinx-coroutines-play-services`
  (version ref `coroutines`), `guava` 32.1.3-android (Firestore brings Guava at runtime only, and CameraX needs ListenableFuture on the compile classpath); plugin `google-services`.
- Root `build.gradle.kts`: `alias(libs.plugins.google.services) apply false`.
- `app/build.gradle.kts`: the three Firebase dependencies plus the
  coroutines bridge, and after the `android` block:

  ```kotlin
  // Firebase is wired in only when its config file is present, so the app
  // still builds and tests without a Firebase project. The file is
  // git-ignored; the release workflow restores it from a secret.
  if (file("google-services.json").exists()) {
      apply(plugin = libs.plugins.google.services.get().pluginId)
  } else {
      logger.warn("app/google-services.json not found: building without Firebase sync")
  }
  ```

- `.gitignore`: `app/google-services.json`.
- `release.yml`: a "Restore Firebase config" step after "Set up Gradle"
  that decodes `GOOGLE_SERVICES_JSON_BASE64` into `app/google-services.json`,
  or prints a `::warning::` and continues when the secret is empty.
- Firestore brings gRPC and protobuf; expect the APK to grow by a few
  megabytes. Minification stays off.

## Sync behaviour, spelled out

- Offline add, edit, delete: Room first, so the screens update at once;
  the Firestore write waits in the SDK's persistent queue and goes out
  when the connection returns, even across app restarts.
- Remote add or edit: the listener applies it to Room; the overview
  re-renders through the existing flow.
- Remote delete: the row and its local photo are deleted.
- Local photo on a beer the other phone edits: kept (decision 6).
- Two phones edit one beer: the write that reaches the server last wins
  as a whole record (decision 5).
- Edit after the other phone deleted: the beer comes back (decision 5).
- Join with beers on both phones: union of both lists (decision 10).
- Reinstall, or cleared app data: the phone forgets the pairing and its
  anonymous identity; join again with the code from the other phone or
  from the shared message. If both phones lose the code, the cellar is
  still in Firestore and the project owner can read the code in the
  Firebase console. Keep the shared message.
- Stop syncing: local copy kept, pairing forgotten (decision 11).
- App in the background: Firestore keeps its listener while the process
  lives; Android may end the process, and the next launch catches up
  (decision 8).

## Error handling

- No Firebase configuration in the build: the sync screen explains it;
  everything else works as today.
- Create or Join with no connection: sign-in fails or the commit times
  out; the screen says to check the connection. Nothing is saved, so the
  phone stays not paired.
- Unknown or malformed code: an explicit message, the phone stays not
  paired.
- Rules deny a write (for example after the user edits the rules): the
  SDK rolls the local write back in its cache and the listener reports
  the server state, which the engine applies; the local Room row follows
  the server. The listener error path logs and retries.
- A malformed beer document in Firestore: skipped and logged; it can
  never crash the app or block the other documents.
- Firestore unavailable at startup while paired: the listener serves the
  cache and reconnects by itself; the screen shows the last sync time.

## Testing

Same JVM-only strategy as the rest of the app, Robolectric where Android
is involved, no device, no Firebase emulator. Firebase itself is reached
only through `CellarRemote`, so a `FakeCellarRemote` in the test sources
stands in for it everywhere.

- `InviteCodesTest`: generated codes have the length and alphabet; the
  generator uses the injected random; normalize upper-cases and strips
  spaces and hyphens, rejects wrong lengths and characters outside the
  alphabet; format inserts the hyphen.
- `RemoteBeerCodecTest`: a full beer round-trips without id and photoUri;
  nulls are written explicitly; whole numbers come back from Long and
  Double; a record without a name, with grade 7, or graded but not tried
  yields null; a missing pairing list reads as empty and non-string
  elements are dropped.
- `CellarSyncEngineTest` (with `FakeBeerRepository` as the local store and
  `FakeCellarRemote`): status is Unavailable when the remote is; NotPaired
  without a membership; createCellar signs in, creates with a generated
  code, saves the membership, uploads every local beer and reports
  Paired; joinCellar with a bad code throws InvalidCode and leaves the
  phone not paired; with an unknown code throws UnknownCode; with a good
  code joins, saves and uploads; a remote Upsert of a new id adds
  locally; of a known id updates and keeps the local photoUri; a remote
  Remove deletes locally; pushBeer and pushDelete reach the remote only
  while paired; a server update sets lastSyncedUtc from the clock and
  mirrors hasPendingWrites; the cellar record's member count shows in the
  status; stopSyncing clears the membership, reports NotPaired and stops
  applying remote changes; an engine started with a stored membership
  listens without any user action; a failing listener restarts after 30
  seconds of virtual time.
- `SyncingBeerRepositoryTest`: add stamps addedBy with the paired user id
  when it is null and leaves an existing value alone; add, update and
  delete write locally first and push; nothing is pushed while not
  paired.
- `PrefsSyncMembershipStoreTest`: starts empty, keeps a saved membership
  across instances, clear empties it, a half-written pair reads as not
  paired.
- `SyncViewModelTest`: mirrors the engine status; create sets working and
  lands on Paired; a bad code shows INVALID_CODE; an unknown code shows
  UNKNOWN_CODE; an offline failure shows OFFLINE; an unexpected exception
  shows FAILED; editing the code clears the error; taps while working are
  ignored; join clears the code on success; stopSyncing lands on
  NotPaired.
- `SyncScreenTest` (Robolectric Compose, on `SyncContent`): the
  unavailable state shows its title; the not-paired state shows Create
  and Join, and Join fires with the typed code; each error text renders;
  the paired state shows the formatted code, the member line for one and
  for two members, the pending-upload line only when set, and Share
  passes the share text; Stop opens the dialog and confirming fires the
  callback; the working state disables the buttons.
- `OverviewThemeMenuTest`: the existing test opens the menu by its new
  content description "Settings"; a new test taps "Sync between phones"
  and asserts the callback.
- `BeerDatabaseMigrationTest` and every existing test stay green; the
  Room schema does not change, so no schema JSON changes either.

## User provisioning

Written up step by step in `docs/firebase-setup.md`, which the user
follows once:

1. Create a Firebase project (Analytics can be left off).
2. Add an Android app with package name `com.beertracker` (no SHA
   certificate is needed for anonymous sign-in) and download
   `google-services.json` into `app/`.
3. Authentication: enable the Anonymous sign-in method.
4. Firestore: create the database in production mode in a European
   location, then paste `firebase/firestore.rules` into the Rules tab and
   publish.
5. Store the config for CI: from PowerShell at the repository root,
   `[Convert]::ToBase64String([IO.File]::ReadAllBytes("app\google-services.json")) | gh secret set GOOGLE_SERVICES_JSON_BASE64`.
6. Push to main (or re-run the last release workflow) and install the new
   APK on both phones.

Two users writing a few beers a week stay far inside Firestore's free
quota.

## Alternatives considered

- A Firestore-backed `BeerRepository` replacing Room, as the v1 design
  first sketched: rejected for now, see decision 1. Room's migrations,
  DAO tests and photo handling stay untouched, and a first launch needs no
  network.
- Committing `google-services.json`: rejected, see decision 2.
- Signing in at first launch so the code is ready before the user opens
  the sync screen: rejected, see decision 3.
- Per-field merging or timestamps to resolve conflicts: rejected, see
  decision 5. `update` instead of `set` would make deletes beat edits but
  would drop the edit silently.
- A Cloud Function to redeem codes so invites are never readable by
  clients: rejected, needs the paid plan; the rules restrict lookups to
  one exact code per authenticated request instead.
- Syncing photos through Firebase Storage: deferred, see decision 6.
- A QR code or a share link for the code: deferred, see decision 4. The
  share sheet covers the practical case (sending the code over WhatsApp).
- A foreground or background sync service: rejected, see decision 8.

## Out of scope

- Photo sync, QR codes, links, App Check, Cloud Functions.
- Any change to either Room database, the catalog pipeline or the seed
  script.
- Showing who added a beer, or per-member anything, on any screen.
- Removing a member, transferring a cellar, or deleting a cellar.

## Verification by the user after merge

No device is available while building. After the Firebase project exists
and the release ships:

- Phone A: gear menu, "Sync between phones", "Create a shared cellar";
  a code appears within a few seconds and A's beers show up in the
  Firestore console under `cellars/<id>/beers`.
- Phone B: enter the code, "Join"; B lists A's beers and A lists B's.
- Add, grade and delete a beer on either phone; the other follows within
  seconds while online.
- Turn off networking on B, add a beer, turn it back on; the beer reaches
  A, and B's status line stops saying that changes are waiting.
- A build without the secret shows "Sync is not set up in this build" and
  everything else works.
