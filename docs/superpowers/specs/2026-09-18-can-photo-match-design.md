# Can photo text reading and catalog match, design

Date: 2026-09-18
Status: approved with defaults (the user asked for phase 3 to be built and
was away during design; every open question below got the recommended
answer and each is easy to change)

## Goal

Phase 3 of the v1 design (build order step 3, "Can-photo text reading and
fuzzy match"). The third way to add a beer: point the camera at a can or
bottle, the app reads the printed text on the phone, matches that text
against the offline Systembolaget catalog by beer name and brewery, and
lists the best matches. Tapping a match lands on the add form prefilled
from the catalog, exactly like the shelf-label scanner does today. When
nothing in the catalog matches, the most name-like text that was read is
carried into the add form's Name field so the user finishes by hand.

## Background

- The shelf-label scanner (`ScanScreen`, `ScanViewModel`,
  `ArticleNumberParser`) reads digits from live camera frames, looks a
  number up exactly, and navigates to `edit?prefillArticle=<number>` on
  the first hit. Its camera preview and ML Kit analyzer live inside
  `ScanScreen.kt` and would be duplicated by a second camera screen.
- The catalog holds about 4,960 beers. Names very often start with the
  brewery (2,279 of 4,962 in the current seed), and a few words carry
  almost no information: "bryggeri" appears in 1,081 products,
  "brewing" in 722, "ipa" in 510, "brewery" in 403, "lager" in 366,
  "ale" in 357. Name plus brewery use about 5,200 distinct words in total.
- Many beers exist in several packagings with the same name and brewery
  but different article numbers (the shelf lookup already picks the
  lowest article number in that case).
- ML Kit's Latin recognizer is already bundled and offline. It returns
  the frame's text as lines joined by newlines.
- Matching runs in Kotlin over the in-memory product list, never in SQL,
  for the reason given in the catalog browse spec: SQLite only case-folds
  ASCII, so Swedish letters would match inconsistently.
- A throwaway Python prototype of the matching rules below was run over
  the real seed during design. With 30 percent of the words on a
  simulated can corrupted by one wrong letter each, the right beer ranked
  first for 265 of 300 random products and within the top five for 294.
  When the simulated can showed only the beer name and no brewery, the
  right beer ranked first for 267 and within the top five for 293. Ten
  negative controls such as "PALE ALE IPA BRYGGERI", "BREWING COMPANY
  LAGER", "SVERIGE ALKOHOL 5,2 % VOL 330 ML" and lone brewery names
  ("OMNIPOLLO", "MIKKELLER", "CARLSBERG", "STIGBERGETS") produced no
  candidates at all. The prototype is not part of the repo.

Neither database changes. No schema, no migration, no change to the
catalog refresh pipeline or the seed script.

## Decisions taken with defaults (user was away during design)

1. Capture mode: live camera with continuous recognition and no shutter
   button, the same interaction as the shelf scanner. Text read across
   frames is accumulated, so slowly turning the can adds the words
   printed around it, and a blurry frame simply contributes nothing.
2. Entry point: a two-segment switch, "Shelf label" and "Can", at the top
   of both scan screens, instead of a third button in the overview top
   bar (which already holds Scan, Catalog, a refresh icon and a settings
   icon and would overflow on a narrow phone).
3. Result presentation: a live list of up to five candidates that the user
   taps. There is no automatic jump on a single hit: a fuzzy match is not
   an exact article number, and a wrong jump costs more than one tap.
4. No match: an "Add manually" button carries the most name-like line that
   was read into the add form's Name field, where the existing inline
   catalog suggestions take over. When nothing readable was captured it
   opens the plain empty form.
5. Beers already in the user's list show their grade mark in the candidate
   list and tapping them opens the beer's detail screen, the same rule the
   catalog browser uses, so the can path cannot create duplicates.
6. The camera frame is not attached as the beer's own photo. The add
   form's photo field already covers that, and capturing a still from the
   analysis stream needs extra CameraX plumbing that nothing else wants.
7. Nothing that was read is persisted. The recognized text lives in the
   screen's view model and is gone when the screen closes.

## UX

### Scan mode switch (both scan screens)

- Directly below the top bar, a single-choice segmented button row with
  two segments: "Shelf label" and "Can". The current screen's segment is
  selected.
- Both screens now use the title "Scan" (the shelf screen's title was
  "Scan shelf label"; the segment label carries that word now).
- Choosing the other segment replaces the current screen with the other
  route, popping the current one, so the back arrow on either screen
  returns to the overview and never to the other scan mode.

### Can screen (nav route `can`)

- Top bar: back arrow, title "Scan". Then the mode switch with "Can"
  selected.
- Camera permission handling is identical to the shelf scanner: the same
  request on first open, the same "Waiting for camera permission." text,
  and a denied state. The denied message on this screen reads "Reading a
  can needs the camera. Allow camera access in system settings, or add
  the beer manually."
- With permission granted: the same camera preview (full width, 320 dp
  tall, large shape) and the hint "Point the camera at the can's label
  and turn the can slowly. Beers whose name and brewery match the text
  appear below."
- Section "Matches" (the shared `SectionHeader`), whose supporting text
  follows the state:
  - nothing readable captured yet: "Nothing read yet."
  - text captured, no candidate: "No catalog beer matches the text read
    so far. Turn the can, or add the beer manually."
  - candidates present: no supporting text, the rows speak.
- Candidate rows reuse `CatalogListItem` (thumbnail, name, a brewery and
  type line, price, volume and alcohol, grade mark when the beer is
  already logged). Tapping an unlogged row navigates to
  `edit?prefillArticle=<articleNumber>` and pops the can screen; tapping a
  logged row opens `detail/<beerId>`.
- Below the list, two buttons side by side: "Start over" (enabled only
  when something readable has been captured; clears the accumulated text
  and the candidates) and "Add manually" (always enabled; navigates to
  `edit?prefillName=<guessed name>` when a name was guessed, otherwise to
  `edit`, and pops the can screen).
- Permission denied: the `ErrorState` described above followed by the
  "Add manually" button. The camera hint and the Matches section are not
  shown.

### Add form

- The edit route gains an optional `prefillName` query argument next to
  `prefillArticle`. On an add form (`beerId == null`) whose content is
  still untouched, the Name field is filled with that text once and the
  form is marked as having unsaved changes. Text the user has already
  typed is never overwritten, the same rule `prefillFromCatalog` follows.
- The inline catalog suggestions behave as they do for typed text: they
  show for the guessed name once the field is focused, so a guessed
  "MIKKELLER" still leads to the right catalog beer with one more tap.

## Architecture

### Domain (pure Kotlin, JVM tested)

`domain/CatalogTextMatcher.kt`

```kotlin
data class CatalogMatch(val product: CatalogProduct, val score: Double)

class CatalogTextMatcher(products: List<CatalogProduct>) {
    fun match(tokens: Set<String>): List<CatalogMatch>
    fun match(text: String): List<CatalogMatch> = match(tokenize(text))

    companion object {
        const val MAX_MATCHES = 5
        fun tokenize(text: String): Set<String>
    }
}
```

Built once per catalog list (the view model rebuilds it when the product
flow emits, which happens on subscribe and after a catalog refresh).

Rules, in order:

1. Tokenizing (`tokenize`, also applied to every product's name and
   brewery): lower-case, the letters ø, æ, ß, ł and đ (which do not
   decompose) replaced by o, ae, ss, l and d, then Unicode NFD
   decomposition with combining marks removed (so å and ä read as a, ö as
   o, é as e), split on anything that is not a to z, keep tokens of two or
   more letters. Digits vanish completely: on a can they are alcohol,
   volume and dates, never the name.
2. Products whose tokenized name and tokenized brewery are both equal are
   one identity (the same beer in another packaging), represented by the
   product with the lowest article number, the shelf lookup's rule. An
   identity's tokens are the distinct tokens of its name and brewery
   together (the union, so a brewery repeated in the name counts once).
   N below is the number of identities, about 4,590 in the current seed.
3. Every token gets a weight `ln(1 + N / df)`, where df is the number of
   identities containing the token. "bryggeri" weighs about 1.7, a word
   unique to one beer about 8.4. Inside an identity, a token that appears
   only in the brewery and not in the name counts at half weight: the
   name is what a can prints biggest, while the brewery is often a logo
   the recognizer cannot read. In the prototype this raised name-only
   recall from 280 to 293 of 300 without adding a single false candidate.
4. A token is distinctive when `df <= max(1, N / 50)` (at most 91
   identities in the current catalog use it; the floor of 1 keeps the rule
   meaningful for tiny catalogs, such as tests with one product).
5. An identity token counts as seen when a read token equals it (an exact
   hit), or, for tokens of 5 to 7 letters, a read token is within
   Levenshtein distance 1 of it, or, for tokens of 8 or more letters,
   within distance 2 (a fuzzy hit). Tokens shorter than 5 letters only
   match exactly.
6. An identity's score is the summed weight of its seen tokens divided by
   the summed weight of all its tokens, so it runs from 0 to 1 and reads
   as "how much of this beer's identity was on the can".
7. An identity is a candidate only when its score is at least 0.6 and at
   least one of its distinctive tokens was an exact hit. The exact
   requirement is what stops "bryggeri" from reaching "bryggerier" and
   "lager" from reaching "fager"; both slipped through in the prototype
   before it was added.
8. Order by score descending, then by name with the Swedish collator, and
   keep the first `MAX_MATCHES`.

Cost: about 5,200 distinct catalog tokens are compared against the read
tokens once per frame, with the fuzzy comparison skipped whenever the
lengths already differ by more than the allowed distance, then about 4,590
identities are scored from the resulting seen set. The unoptimized Python
prototype took 49 ms per frame on a laptop; the Kotlin version runs on
`Dispatchers.Default`, never on the main thread.

`domain/CanTextParser.kt`

```kotlin
object CanTextParser {
    fun guessName(text: String): String?
}
```

Among the lines of the recognized text, keep those with at least three
letters where letters make up more than half of the non-space characters
(this drops "5,2 % VOL", "33 CL" and barcode digits), and return the one
with the most letters, trimmed; the earlier line wins a tie. Null when no
line qualifies. The text is returned as read, including its case: the
user edits the field anyway, and title-casing would turn "IPA" into "Ipa".

### UI

`ui/scan/TextRecognitionCamera.kt` (extracted from `ScanScreen.kt`, no
behavior change)

- `internal enum class CameraPermission { UNKNOWN, GRANTED, DENIED }`
- `@Composable internal fun rememberCameraPermission(): CameraPermission`,
  the saved permission state plus the launcher and the first-open
  request that `ScanScreen` runs today.
- `@Composable internal fun TextRecognitionCameraPreview(onTextDetected:
  (String) -> Unit)`, the preview view, the analysis use case, and the
  ML Kit analyzer that `ScanScreen` runs today.

`ui/scan/ScanModeSwitch.kt`

- `enum class ScanMode { SHELF_LABEL, CAN }`
- `@Composable internal fun ScanModeSwitch(selected: ScanMode, onSelect:
  (ScanMode) -> Unit)`, a `SingleChoiceSegmentedButtonRow` with the two
  labels. Material 3 in this project is 1.3.1, where the segmented button
  is behind `ExperimentalMaterial3Api`, which the scan screens already opt
  in to.

`ui/scan/ScanScreen.kt`

- `ScanScreen` gains `onSwitchToCan: () -> Unit`; `ScanContent` gains the
  same parameter and renders the mode switch with "Shelf label" selected.
  Everything else stays.

`ui/scan/CanScanViewModel.kt`

```kotlin
data class CanScanUiState(
    val hasText: Boolean = false,
    val guessedName: String? = null,
    val matches: List<CatalogRow> = emptyList(),
)

class CanScanViewModel(
    catalogRepository: CatalogRepository,
    beerRepository: BeerRepository,
    matchDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val uiState: StateFlow<CanScanUiState>
    fun onTextDetected(rawText: String)
    fun startOver()
}
```

- Holds the accumulated read tokens and the current best name guess. A
  frame whose tokens are all already known and whose name guess is not
  longer than the current one changes nothing, so a still camera costs
  nothing.
- `uiState` combines the catalog products (turned into a
  `CatalogTextMatcher`), the user's beers, the accumulated tokens and the
  guess; the matching runs on `matchDispatcher` via `flowOn`. Matches are
  mapped to `CatalogRow` with the logged beer's id, grade and tried flag,
  the same join the catalog browser does.
- `hasText` is true once any token or name guess has been captured.

`ui/scan/CanScanScreen.kt`

- `CanScanScreen(viewModel, onAddProduct: (String) -> Unit, onOpenBeer:
  (String) -> Unit, onAddManually: (String?) -> Unit, onSwitchToShelfLabel:
  () -> Unit, onBack: () -> Unit)`.
- `internal fun CanScanContent(state, permission, onPickRow: (CatalogRow)
  -> Unit, onStartOver, onAddManually, onSwitchToShelfLabel, onBack,
  cameraPreview: @Composable () -> Unit)`, the testable layout, in the
  shape of `ScanContent`.

`MainActivity.kt`

- New route `can`. Its callbacks: `onAddProduct` navigates to
  `edit?prefillArticle=<n>` popping `can`; `onOpenBeer` to `detail/<id>`;
  `onAddManually` to `edit?prefillName=<Uri.encode(name)>` or `edit`,
  popping `can`; `onSwitchToShelfLabel` to `scan` popping `can`.
- The `scan` route passes `onSwitchToCan`, navigating to `can` and
  popping `scan`.
- The edit route declares the nullable `prefillName` argument and passes
  it to `AddEditScreen`. Navigation Compose decodes query arguments
  itself; the value is read without a second decode, the lesson from the
  brewery route fix.

`ui/AddEditScreen.kt` and `ui/AddEditBeerViewModel.kt`

- `AddEditScreen` gains `prefillName: String? = null` and a
  `LaunchedEffect(prefillName)` that calls `viewModel.prefillName(it)` when
  `beerId == null`.
- `AddEditBeerViewModel.prefillName(name: String)`: ignored while editing,
  runs once per value, and only fills the Name field when the form content
  still equals the empty form. Marks unsaved changes. Does not touch
  `appliedCatalogName`, so the suggestion list may show for the guess.

### New strings

- `scan_title` becomes "Scan".
- `scan_mode_shelf_label` "Shelf label", `scan_mode_can` "Can".
- `can_hint`, `can_matches_section` "Matches", `can_nothing_read`,
  `can_no_match`, `can_start_over` "Start over", `can_add_manually`
  "Add manually", `can_camera_denied_message`.

## Error handling and edge cases

- Camera permission denied: the friendly error plus "Add manually", as on
  the shelf scanner where typing the number stays available.
- Catalog still importing on a fresh install: the product flow is empty,
  the matcher has nothing to match, the screen shows "Nothing read yet."
  or "No catalog beer matches" and "Add manually" works throughout.
- Nothing readable in the frames: `hasText` stays false, "Start over" is
  disabled, "Add manually" opens the empty form.
- Late analyzer callbacks after leaving the screen: the preview's dispose
  closes the recognizer; a callback already in flight only touches the
  view model's state, which nobody observes any more.
- Several packagings of one beer: one row, lowest article number.
- A guessed name containing characters such as `%`, `&` or `/`: encoded
  once with `Uri.encode` when navigating and decoded once by Navigation
  Compose, never decoded by hand.
- A catalog refresh while the screen is open: the product flow re-emits,
  the matcher is rebuilt, and the candidates are recomputed from the
  already accumulated text.

## Testing

Same JVM-only strategy as the rest of the app, Robolectric where Android
is involved, no device.

- `CatalogTextMatcherTest`, run against a fixture of nine named beers plus
  about a hundred filler beers that make "pale", "ale", "ipa", "lager"
  and "bryggeri" common the way they are in the real catalog: a full name
  and brewery ranks that beer first with score 1.0; name words alone still
  match when a distinctive word is present; a brewery name alone does not
  match when the brewery has several beers; everyday words alone ("pale
  ale ipa bryggeri") match nothing; one wrong letter in a long word still
  matches (fuzzy hit); short words only match exactly; a common word
  cannot reach a distinctive one through the fuzzy rule; å, ä and ö fold
  so "SKANES" matches "Skånes", and ø folds so "TO OL" matches "To Øl";
  digits and units never contribute; two packagings of the same beer give
  one result carrying the lowest article number; results are capped at
  five, ordered by score and then Swedish name order; empty text and an
  empty catalog give an empty list; `tokenize` drops one-letter tokens
  and digits.
- `CanTextParserTest`: picks the line with the most letters; skips lines
  that are mostly digits and units; returns null for digits-only text and
  for empty text; keeps the earlier of two equal lines; trims.
- `CanScanViewModelTest`: text with a known beer produces a `CatalogRow`
  for it; tokens accumulate across two frames so a name split over two
  frames still matches; a repeated frame leaves the state unchanged; a
  logged beer's row carries its id and grade; `startOver` clears matches,
  guess and `hasText`; the guessed name is the longest name-like line seen
  in any frame.
- `CanScanScreenTest` (Robolectric Compose, on `CanScanContent`): denied
  permission shows the error and "Add manually" still fires; a candidate
  row shows the beer name and tapping it passes its row; "Start over" is
  disabled without text and enabled with it; the no-match message shows
  when text was read but nothing matched; the mode switch reports "Shelf
  label".
- `ScanScreenTest`: the existing five tests keep passing with the new
  parameter; one new test taps "Can" on the mode switch and asserts the
  callback.
- `AddEditBeerViewModelTest`: `prefillName` fills an empty add form and
  marks unsaved changes; it is ignored after the user typed; it is ignored
  while editing an existing beer; it runs once per value.
- `AddEditCatalogSearchTest`: a screen opened with `prefillName = "Omni"`
  shows the Omnipollo suggestion once the Name field is focused.

## Alternatives considered

- A shutter button and a single still photo: rejected. One extra tap, one
  possibly blurry frame decides everything, and the text printed around
  a can can only be gathered by turning it under a live camera.
- Jumping straight to the add form on a single strong match, like the
  shelf scanner does: rejected. The shelf number is exact; a text match is
  a ranked guess, and a wrong automatic jump is worse than one tap.
- Searching in SQL with LIKE or an FTS table: rejected, same reasons as in
  the catalog browse spec (ASCII-only case folding, overkill at this size).
- A third text button in the overview top bar: rejected, the bar is full
  on a narrow phone and the two scan modes belong together anyway.
- Matching each frame on its own instead of accumulating: rejected, the
  list would flicker as the camera moved and the far side of the can
  would never be seen together with the near side.
- Attaching the analyzed frame as the beer's photo: deferred, see
  decision 6.
- Title-casing all-caps text before prefilling the name: rejected for
  now, see `CanTextParser`.

## Out of scope

- Phase 4: Firebase sync and pairing.
- Barcode scanning (the catalog carries no EAN codes).
- Any change to the catalog fetch pipeline, the seed script, or either
  database schema.
- Learning from picks (for example remembering which candidate the user
  chose for a given text).

## Verification by the user after merge

No device is available while building. After the release ships:

- Scan opens with the mode switch; choosing "Can" shows the live preview.
- Pointing at a can from the catalog lists it within a few frames; the
  right row is at or near the top; tapping it lands on the prefilled form.
- Pointing at a can Systembolaget does not sell yields no rows, and "Add
  manually" opens the form with the can's name text in the Name field.
- A beer already in the list shows its grade mark and opens its detail.
- Denying the camera still allows "Add manually".
