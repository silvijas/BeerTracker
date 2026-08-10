# Brewery beers screen, design

Date: 2026-08-10
Status: draft, awaiting user review

## Goal

From a beer's detail screen, tapping the brewery name opens a new screen
listing every beer from that brewery in the offline Systembolaget catalog
(about 4,970 beers, tried or not), with a way to sort by name or beer style
and to show only tried or only untried beers.

## Background

Brewery is stored as a plain string on both `CatalogProduct` and `TriedBeer`,
duplicated per row; there is no brewery entity, ID, or index anywhere in
either database. The catalog's roughly 805 distinct brewery strings are
consistent enough in practice (2 pairs differ only by case, 5 rows blank out
of about 4,970) that a case-insensitive, trimmed exact match is reliable
without any schema change. This mirrors the catalog browser feature, which
already solved a near-identical problem: matching happens in Kotlin, not
SQL, because SQLite's `LIKE`/`NOCASE` only fold ASCII case and would treat
Swedish letters inconsistently.

The catalog browser screen (`CatalogBrowserScreen` /
`CatalogBrowserViewModel`) is the direct template for this feature: it
already combines `CatalogRepository.observeProducts()` with
`BeerRepository.observeBeers()`, cross-references tried beers by
`catalogArticleNumber`, and routes taps to either the add form (prefilled)
or the existing beer's detail screen.

## UX

### Detail screen change

- The brewery name under the beer name (`DetailContent`, currently plain
  `Text`, already guarded by `isNotBlank()`) becomes tappable. Tapping it
  navigates to the brewery beers screen for that exact brewery string.
  Blank brewery stays non-clickable, unchanged from today.

### Brewery beers screen (nav route `brewery/{breweryName}`)

- Top bar: back arrow, title is the brewery name.
- Below the top bar, a row with two dropdown menu buttons:
  - **Sort**: Name (default, Swedish-collated, matches catalog browser) or
    Type (beer style, then name within each style).
  - **Show**: All (default), Tried, or Not tried. Choosing Tried or Not
    tried hides the other group entirely rather than just reordering.
- Below that, a `LazyColumn` of rows using the same row layout as the
  catalog browser (thumbnail, name, a subtitle line, price/volume/alcohol,
  and a `GradeMark` for tried beers). The subtitle shows only the beer
  style, not the brewery, since the brewery is already the screen title.
- Tap on an untried beer: navigate to `edit?prefillArticle=<articleNumber>`,
  same route the catalog browser and scanner use today.
- Tap on a tried beer: navigate to `detail/<beerId>` of the matching beer.
- Empty states:
  - No catalog beers at all match the brewery (for example a brewery only
    ever entered by hand, not in the catalog): message naming the brewery,
    with a back action.
  - The current Show filter excludes everything (for example a brewery with
    beers but none tried yet, filtered to Tried): a message naming the
    filter, with an action to reset Show to All.

## Architecture

Data layer: no changes. Both databases and DAOs stay as they are.

Domain:

- `CatalogBrowseLogic` gains:
  - `matchesBrewery(product, breweryName)`: trimmed, case-insensitive exact
    equality, not substring.
  - A sort mode (Name / Type) applied with the existing `Collator`-based
    comparison, extended to compare by type first when sorting by Type.
  - A tried filter (All / Tried / Not tried) applied against the same
    tried/grade lookup the catalog browser already builds.

UI:

- New `BreweryBeersViewModel` (`ui/brewery/BreweryBeersViewModel.kt`):
  constructor takes `CatalogRepository`, `BeerRepository`, and the target
  `breweryName`. Combines `observeProducts()` and `observeBeers()` into row
  state exactly like `CatalogBrowserViewModel`, but filtered by brewery
  instead of a free-text query, with `sortMode` and `triedFilter` state
  instead of `query`. `Factory.create(breweryName)` wires it through
  `BeerApp.container`, following `DetailViewModel.factory(beerId)`.
- New `BreweryBeersScreen` composable (`ui/brewery/BreweryBeersScreen.kt`).
- Row UI is extracted, not duplicated: `CatalogListItem` and
  `catalogItemMeta` move out of `CatalogBrowserScreen.kt` into
  `ui/components/CatalogListItem.kt`, taking the subtitle text as a
  parameter so the catalog browser can keep showing brewery and type while
  the new screen shows type only. Both screens call the same component.
- `DetailContent` gains an `onBreweryClick: (String) -> Unit` parameter,
  threaded from `DetailScreen` down from a new `MainActivity` callback.
- New route `"brewery/{breweryName}"` in `BeerNavHost`. The brewery name is
  URL-encoded with `Uri.encode` when navigating (brewery names contain
  spaces and non-ASCII letters, for example "Ölvisholt") and decoded when
  read back out of the nav argument.

## Error handling and edge cases

- A manually logged beer with no `catalogArticleNumber` never appears on
  this screen even if its brewery string matches, because the base list is
  catalog products, not the user's own entries. This matches how the
  catalog browser already treats cross-referencing and avoids a second
  merge path; the beer is still reachable from the overview list as today.
- The two known case-variant brewery pairs in the current catalog data
  (`FUERST WIACEK GmbH` / `Fuerst Wiacek GmbH`, `Hops N Leon of Skaraborg`
  / `HOPS N LEON OF SKARABORG`) match each other under the case-insensitive
  rule, so tapping either variant on a detail screen shows the same
  combined list.
- A catalog refresh while the screen is open re-emits through the existing
  Room flow and the list updates in place, same as the catalog browser.

## Testing

Same JVM-only strategy as the rest of the app (plain unit tests plus
Robolectric Compose tests, no device needed):

- `CatalogBrowseLogicTest` additions: brewery match is case-insensitive and
  trimmed, does not substring-match, excludes blank brewery beers; sort
  mode orders by type then name when set to Type; tried filter keeps only
  the selected group.
- `BreweryBeersViewModelTest`: rows combine catalog and tried beers for the
  given brewery only, Sort and Show change the emitted rows, tried mark and
  navigation targets are correct. Mirrors `CatalogBrowserViewModelTest`
  with `FakeCatalogRepository` and `FakeBeerRepository`.
- `BreweryBeersScreenTest`: Robolectric Compose test asserting rows render,
  the Sort and Show dropdowns change what is shown, tapping a row routes to
  add versus detail correctly, and both empty states render.
- Detail screen test additions: brewery text is clickable and invokes
  `onBreweryClick` with the exact brewery string; stays non-clickable when
  brewery is blank.
- `DetailContentPreview` updated for the new parameter.

## Alternatives considered

- A new `Brewery` entity/table with a foreign key from both beer tables:
  would give an indexed exact join, but nothing in the current data needs
  it (matching is already reliable) and it would require a schema
  migration on both databases for no behavior change. Rejected as premature
  for the size of this data.
- Pre-seeding the existing catalog browser's free-text search field with
  the brewery name instead of a dedicated screen: simpler to wire, but
  substring matching on a search box would both over-match (breweries whose
  names contain another brewery's name) and lacks a natural place for the
  Sort/Show controls or a fixed screen title.

## Out of scope

- Any change to the catalog refresh pipeline, scanner, seed script, or
  either database schema.
- A `Brewery` identity/entity.
- Grouping or filtering by country, price, or other catalog fields.
