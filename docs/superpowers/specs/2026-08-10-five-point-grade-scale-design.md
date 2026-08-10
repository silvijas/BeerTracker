# Five-point grade scale and simplified GradeMark, design

Date: 2026-08-10
Status: draft, awaiting user review

## Goal

Replace the current 5-to-10 grading scale (the Serbian school scale the app's
own help text names today) with a plain 1-to-5 scale, and replace the
ten-can grid used to display a grade with a single number next to a single
beer can icon, for example a grade of 4 shows as "4" beside one filled can.

## Background

The grade scale is enforced in three places today, all assuming 5 to 10:

- `TriedBeer.init` (`domain/TriedBeer.kt:24`): `require(grade in 5..10)`.
- `AddEditBeerViewModel` form validation (`AddEditBeerViewModel.kt:261`):
  `f.grade !in 5..10`.
- `GradeCanPicker` in `AddEditScreen.kt`: a ten-can track in two rows of
  five, `gradeRange = 5..10`, with cans 1-4 shown dimmed and untappable.

Display is `GradeMark` (`ui/components/GradeMark.kt`): the same ten-can grid,
filling cans 1 through the grade value, with content description string
`grade_value` ("Grade %1$d out of 10"). It is used at three call sites
(`DetailScreen`, `CatalogBrowserScreen`, `BeerListItem`), all just passing a
`size` for the grid's total height; none of them need to change, since
`GradeMark`'s public signature stays the same.

No app has real users yet (per the user, nobody is using it day to day yet),
so existing stored grades on the 5-10 scale carry no meaning worth
preserving, and the user asked that they simply be cleared rather than
remapped.

## UX

### Grade display (GradeMark)

- A tried, graded beer shows its grade number followed by one filled beer
  can icon (the same can glyph and fill color used today, `BeerCan(filled =
  true, ...)`, tertiary color), for example "4" then one can. No unfilled
  cans, no ten-can grid.
- The can's color is fixed regardless of grade value; a grade of 1 and a
  grade of 5 both show the same colored can, only the number differs.
- Tried-but-ungraded and not-tried states are unchanged (the existing
  `CompactGradeState` chip).
- Content description changes to "Grade %1$d out of 5" so accessibility
  output matches the real scale.

### Grade input (AddEditScreen)

- `GradeCanPicker` becomes a five-can track (`gradeRange = 1..5`), all five
  tappable, same interaction as today: tap a can to set that grade, tap the
  currently selected can again to clear it back to ungraded.
- Help text under the picker changes from the Serbian-school-scale
  description to something scale-neutral, for example "1 to 5, 5 is the
  best. Tap the selected grade to clear it."
- The validation error message changes to "Grade must be from 1 to 5, or
  left empty".

## Architecture

Domain:

- `TriedBeer.init`: `grade in 5..10` becomes `grade in 1..5`.
- `AddEditBeerViewModel` form validation: `f.grade !in 5..10` becomes
  `f.grade !in 1..5`.

Data / migration:

- `BeerDatabase` version bumps 2 to 3, following the existing non-destructive
  migration pattern in `BeerDatabase.kt` (never
  `fallbackToDestructiveMigration`, since the user's phone holds real
  logged beers).
- `MIGRATION_2_3`: `UPDATE tried_beers SET grade = NULL WHERE grade IS NOT
  NULL`. This clears every existing grade (all of which are on the old 5-10
  scale, since that is the only range the app has ever accepted) and
  touches no other column. Every beer stays tried, favourited, noted, and
  so on exactly as before; only the grade resets to ungraded, matching what
  the user asked for.
- `CatalogBeerEntity` has no grade column; the catalog database is
  untouched.

UI:

- `GradeMark` (`ui/components/GradeMark.kt`): the `grade != null` branch
  becomes a `Row` with the grade number as `Text` followed by one `BeerCan`.
  The two-row can-grid math is deleted. `size` keeps its meaning as the
  can's height so all three call sites (`DetailScreen`, `CatalogBrowserScreen`,
  `BeerListItem`) need no changes.
- `GradeCanPicker` (`ui/AddEditScreen.kt`): `gradeRange` becomes `1..5`; the
  dimmed-and-inert rendering for out-of-range slots is deleted since every
  slot is now in range.
- `strings.xml`: `grade_value`, `grade_scale_help`, `grade_scale_title`
  (kept, still just "Grade"), and `grade_error` updated to describe the
  1-to-5 scale.

## Error handling and edge cases

- Any grade already stored outside 1..5 cannot exist after the migration,
  since it either already satisfied 5..10 (and gets cleared) or was already
  null (and stays null); `TriedBeer.init`'s new `require` never sees stale
  data.
- The migration runs once, on database open, exactly like `MIGRATION_1_2`
  does today; no runtime code path needs to special-case old grades.

## Testing

Same JVM-only strategy as the rest of the app:

- `TriedBeer` validation tests: 1 and 5 accepted, 0 and 6 rejected, null
  always accepted, grade without tried still rejected (unchanged rule).
- `AddEditBeerViewModelTest`: form validation accepts 1..5 and rejects
  outside that range; existing tests referencing 5..10 boundaries update to
  1..5.
- `BeerDatabaseMigrationTest`: new `migrating 2 to 3 clears an existing
  grade` test, mirroring the existing `migrating 1 to 2` test's shape,
  seeding a v2 row with a grade (for example 9), running
  `MIGRATION_1_2` then `MIGRATION_2_3`, and asserting every other column is
  unchanged while `grade` is now null.
- `GradeMark` tests (`ComposeUiSmokeTest.kt` and any others asserting
  `"Grade 8 out of 10"`-style content descriptions): update expected text
  to the new "Grade N out of 5" wording and to grade values that are valid
  under the new scale.
- `AddEditScreen` Compose tests covering the can picker: update tapped-slot
  assertions from the 5..10 range to 1..5, and drop any assertion covering
  the old dimmed/inert 1-4 slots since they no longer exist.

## Alternatives considered

- Proportional rescale of existing grades (spreading old 5-10 values across
  the new 1-5 range): rejected, the user asked for a clean clear instead
  since no real grading history exists yet to preserve.
- Grade-dependent can color (for example redder for low grades, golden for
  high): rejected for now in favor of a single fixed color, matching the
  user's own example literally; easy to revisit later without changing the
  data model.
- Keeping the input picker at ten cans with only 1-5 active: rejected,
  since it would keep dead visual space and the ten-can layout code for no
  benefit once the scale itself is five values.

## Out of scope

- Any change to how grades are sorted (`BeerListLogic.BeerSort.GRADE`
  already compares raw `Int` values and needs no change for a narrower
  range).
- The brewery beers screen design (separate spec,
  `2026-08-10-brewery-beers-design.md`); it reuses `GradeMark` and
  `CatalogListItem` unchanged and will pick up this new look automatically
  once both land.
- Any catalog schema or fetch pipeline change.
