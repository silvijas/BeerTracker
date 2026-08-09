# Catalog browse and add-view search, design

Date: 2026-08-09
Status: draft, awaiting user review

## Goal

Two features on top of the phase 2 offline Systembolaget catalog:

1. A catalog browser screen: scroll and search the loaded catalog (about
   1,530 beers), see which ones are already in the beer list, and jump
   straight into adding one.
2. Catalog search inside the add-beer view: typing in the Name field suggests
   matching catalog beers; picking a suggestion prefills the whole form the
   same way the shelf-label scanner does.

## Background

The catalog lives in its own Room database (catalog.db) and is read-only
reference data. `CatalogRepository` currently exposes only
`findByArticleNumber` and `observeStatus`; phase 2 deliberately added no list
or search function because no screen needed one. The add form already knows
how to prefill from a catalog product (`prefillFromCatalog`), and the edit
route already accepts a `prefillArticle` argument. Both new features are read
paths over data already on the phone: no network calls, no schema changes,
no migrations, and the user database is untouched.

## Decisions taken with defaults (user was away during brainstorming)

These were the open questions; each got the recommended answer and is easy to
change before planning:

1. Browser role: full integration. Rows show a grade mark for beers already
   in the list, tapping an unlogged beer opens the prefilled add form, and
   tapping an already-logged beer opens that beer's detail screen instead,
   which also prevents accidental duplicates.
2. Add-view search style: inline suggestions under the existing Name field,
   not a separate search screen or a separate search field.
3. Browser capabilities: search box only, no filter chips or sort menus yet.
   The list is sorted by name with Swedish collation. Filters can come later
   if browsing 1,530 rows feels unwieldy.
4. Entry point: a Catalog text button in the overview top bar, next to Scan.

## UX

### Catalog browser screen (nav route "catalog")

- Top bar: back arrow, title "Catalog".
- Search field under the top bar, same visual style as the overview search,
  placeholder "Search name, brewery, type".
- Below it a LazyColumn of catalog rows, sorted by name (Swedish collator,
  which puts the letters å, ä, ö at the end of the alphabet as Swedes expect).
- Each row: small product thumbnail (Coil, same offline-safe placeholder
  pattern as the detail screen), beer name, a brewery and type line, and a
  price, volume, alcohol line. When the beer is already in the user's list
  the row shows the existing GradeMark component (grade circle, or the
  tried-without-grade mark) at the trailing edge.
- Tap on an unlogged beer: navigate to `edit?prefillArticle=<articleNumber>`,
  the exact route the scanner uses today.
- Tap on a logged beer: navigate to `detail/<beerId>` of the matching beer.
- Empty catalog (cannot happen in practice, the seed ships in the APK):
  message pointing at the catalog update button on the overview.
- No search matches: short "No beers match your search" state.

### Add-view search (add mode only)

- Applies only when adding a new beer (`beerId == null`); editing an existing
  beer never shows suggestions.
- While the Name field is focused and contains at least two characters, an
  inline suggestion card appears directly below the field with up to 8
  catalog matches. Each suggestion row shows name, brewery, and price.
- Tapping a suggestion fills the form from the product with the same field
  mapping `prefillFromCatalog` uses today, records `catalogArticleNumber` and
  the display image URL, and hides the suggestions.
- After a pick, suggestions stay hidden until the name text changes again.
  Losing focus also hides them. Someone adding a beer that is not in the
  catalog just keeps typing past the suggestions.
- An inline card (part of the form column) is used instead of a floating
  dropdown: it cannot steal focus from the keyboard and it is robust in the
  existing Robolectric Compose tests.

### Overview

- The top bar gains a Catalog text button next to Scan. Nothing else on the
  overview changes.

## Architecture

Data layer:

- `CatalogDao` gains `observeAll(): Flow<List<CatalogBeerEntity>>`
  (`SELECT * FROM catalog_beers`). No schema change.
- `CatalogRepository` gains `observeProducts(): Flow<List<CatalogProduct>>`;
  `RoomCatalogRepository` implements it by mapping entities to domain.

Domain:

- New pure object `CatalogBrowseLogic` (same pattern as `BeerListLogic`):
  - `filter(products, query)`: locale-aware case-insensitive substring match
    on name, brewery, and type. When the query is digits only it also
    matches article numbers (full and short) by prefix.
  - `sort(products)`: by name using `java.text.Collator` for locale sv.
- Matching happens in Kotlin, not SQL, because SQLite LIKE and NOCASE fold
  case for ASCII only and would miss Swedish letters. Filtering 1,530 small
  objects in memory is trivial and matches how the overview list already
  filters.

UI:

- New `CatalogBrowserViewModel`: combines `observeProducts()`, a query
  StateFlow, and `beerRepository.observeBeers()` into row state. Logged
  beers are looked up by `catalogArticleNumber` (first match wins if the
  same article was somehow logged twice). Factory wired through
  `BeerApp.container` like the other view models.
- New `CatalogBrowserScreen` composable; route "catalog" added in
  `MainActivity`'s NavHost.
- `AddEditBeerViewModel` additions:
  - `catalogSuggestions: StateFlow<List<CatalogProduct>>` derived from the
    form name and `observeProducts()` through `CatalogBrowseLogic`, capped
    at 8, emitting only in add mode. No debounce: the source list is
    already in memory.
  - `applyCatalogProduct(product)`: fills the form; the product-to-form
    mapping is extracted into one private function shared with
    `prefillFromCatalog`, whose behavior does not change.
- All new user-visible text goes through strings.xml.

## Error handling and edge cases

- Product images need network; the Coil placeholder and error handling from
  the detail screen is reused, and tests stay fully offline.
- The same beer in multiple volumes is multiple catalog articles and shows
  as multiple rows; the volume on the row disambiguates them.
- Manually added beers have no `catalogArticleNumber`, cannot be
  cross-referenced, and simply show no mark in the browser.
- A catalog refresh while the browser is open makes the Room flow re-emit
  and the list update in place; a beer disappearing from the assortment
  disappears from the browser but never from the user's list.
- Saving from a browse-initiated add pops back to the browser, so adding
  several beers in a row is natural.

## Testing

Same JVM-only strategy as phases 1 and 2 (plain unit tests plus Robolectric
Compose tests, no device needed):

- `CatalogBrowseLogicTest`: matching by name, brewery, type; case folding
  including Swedish letters; digit queries against article numbers; sort
  order; empty query returns everything.
- `RoomCatalogRepositoryTest`: `observeProducts` emits mapped rows and
  reflects a new import.
- `CatalogBrowserViewModelTest`: rows combine catalog and tried beers,
  query narrows rows, tried mark and navigation targets are correct. Uses
  `FakeCatalogRepository` (gains `observeProducts`) and `FakeBeerRepository`.
- `AddEditBeerViewModelTest` additions: suggestions appear in add mode only,
  are capped at 8, `applyCatalogProduct` fills every mapped field and
  records the article number, suggestions stay hidden after a pick until
  the name changes.
- Robolectric Compose tests: browser screen renders rows and empty states,
  search narrows the list, tapping routes to add versus detail correctly;
  add screen shows suggestions while typing and prefills on pick.

## Alternatives considered

- SQL LIKE search in Room: rejected because SQLite case folding is ASCII
  only, so Swedish names would match inconsistently; fixing that needs a
  normalized shadow column and importer changes for no gain at this size.
- FTS table: overkill for 1,530 rows.
- Separate full-screen catalog search opened from the add view: heavier
  navigation for the same outcome; inline suggestions match the actual
  request wording.
- Read-only browser with no add or tried integration: cheaper, but
  disconnected from what the app is for.

## Out of scope

- Filter chips, sort menus, or country grouping on the browser.
- Any change to the catalog refresh pipeline, the scanner, the seed script,
  or either database schema.
- Phase 3 (can-photo OCR fuzzy match) and phase 4 (sync) remain separate.
