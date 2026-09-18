# BeerTracker Phase 3: Can Photo Text Reading and Catalog Match Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A third way to add a beer: point the camera at a can, the app reads the printed text on the phone, ranks the offline catalog by how well each beer's name and brewery match that text, and lists the best candidates; tapping one lands on the prefilled add form, and when nothing matches the read name is carried into the add form by hand.

**Architecture:** All matching intelligence is pure Kotlin in `domain/` (`CatalogTextMatcher` scores every catalog identity against the accumulated read words; `CanTextParser` picks the most name-like line), fully unit tested with no camera. The camera preview, the ML Kit analyzer and the permission flow are extracted out of `ScanScreen.kt` into a shared file so the new `CanScanScreen` reuses them unchanged. A two-segment mode switch on both scan screens moves between the `scan` and `can` routes. The add form gains a `prefillName` route argument next to the existing `prefillArticle`. Neither database changes.

**Tech Stack:** Existing stack only (Kotlin 2.0.21, Compose BOM 2024.12.01 with Material 3 1.3.1, Room 2.6.1, CameraX 1.4.2, ML Kit text-recognition 16.0.1, Coil 2.7.0, Robolectric 4.14.1, JVM 17, minSdk 26, compile/target 35). No new dependencies.

**Spec:** `docs/superpowers/specs/2026-09-18-can-photo-match-design.md`. The v1 design is `docs/superpowers/specs/2026-07-28-beertracker-v1-design.md` (build order step 3).

**Execution order: 1, 2, 3, 4, 5, 6, 7. Tasks 1, 2 and 3 are independent of each other; 4 depends on nothing but is needed by 6; 5 depends on 1 and 2; 6 depends on 3, 4 and 5; 7 is documentation and comes last.**

## Global Constraints

- Work in the checkout this plan is executed from (a git worktree on a `claude/...` branch, or the main checkout at `C:\Users\SilvijaSubotic\PersonalDevelopment\BeerTracker`). Run every Gradle command from that checkout's root in PowerShell. Gradle does not work from Git Bash on this machine (it fails with "Unable to establish loopback connection"), so never run `gradlew` from a Bash shell.
- Run unit tests with `.\gradlew.bat testDebugUnitTest --console=plain`. The suite starts at 278 tests and must stay green after every task while it grows (roughly 320 by the end). Run a single class with `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.CatalogTextMatcherTest" --console=plain`. The full debug suite takes about 3 to 4 minutes; give Gradle a 10 minute timeout.
- Never run the bare `test` task: the release variant's Compose UI tests fail by design (the Compose test manifest is a debug-only dependency) and that failure is not yours to fix.
- No androidTest source set; every test runs on the JVM (plain JUnit or Robolectric). No device is available. The live camera path is verified by the user on their phone after merge.
- Commits are authored by the user's git identity only. Never add Claude as author or co-author. No `Co-Authored-By` trailers, ever.
- No em dashes or en dashes anywhere: not in code, comments, strings, commit messages, or docs. Use hyphens, commas, or rewrite the sentence.
- Commit message style: `[Scope] Message` where Scope is `App`, `Docs`, `Build`, or `CI`.
- Package `com.beertracker`. Do not add or bump dependencies in `gradle/libs.versions.toml`.
- USER DATA SAFETY: neither `BeerDatabase` nor `CatalogDatabase` changes in this phase. Do not touch entities, DAOs, schemas or migrations.
- TDD for every logic task: write the failing test, watch it fail, implement, watch it pass. UI tasks are verified by Robolectric Compose tests in the house pattern (see `app/src/test/java/com/beertracker/ui/scan/ScanScreenTest.kt`: `@RunWith(RobolectricTestRunner::class)`, `@Config(application = Application::class, sdk = [35])`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, `createComposeRule()`, a `MainDispatcherRule` at order 0).
- New UI reuses the existing building blocks: `ui/components/` (`ErrorState`, `SectionHeader`, `CatalogListItem`, `CatalogRow`, `beerListSubtitle`) and theme tokens (`BeerTrackerSpacing`, `MaterialTheme.shapes`, `MaterialTheme.colorScheme`). No hardcoded colors.
- The user works on the repo in parallel with Claude sessions. Re-read a file immediately before editing it.

## User provisioning

None. No accounts, keys, secrets or workflows. The ML Kit Latin model is already bundled in the APK.

---

### Task 1: CatalogTextMatcher

The whole matching rule set from the spec, as one pure class built once per catalog list and asked once per camera frame.

**Files:**
- Create: `app/src/main/java/com/beertracker/domain/CatalogTextMatcher.kt`
- Test: `app/src/test/java/com/beertracker/CatalogTextMatcherTest.kt`

**Interfaces:**
- Consumes: `CatalogProduct` (existing), `catalogProduct(...)` builder from `app/src/test/java/com/beertracker/TestData.kt` (existing).
- Produces, relied on by Task 5:
  - `data class CatalogMatch(val product: CatalogProduct, val score: Double)` in package `com.beertracker.domain`.
  - `class CatalogTextMatcher(products: List<CatalogProduct>)` with `fun match(tokens: Set<String>): List<CatalogMatch>`, `fun match(text: String): List<CatalogMatch>`, and companion `const val MAX_MATCHES = 5`, `const val MIN_SCORE = 0.6`, `fun tokenize(text: String): Set<String>`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/beertracker/CatalogTextMatcherTest.kt`:

```kotlin
package com.beertracker

import com.beertracker.domain.CatalogTextMatcher
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogTextMatcherTest {

    private val prodigal = catalogProduct(
        articleNumber = "1000101", articleNumberShort = "10001",
        name = "Omnipollo Prodigal Pale Ale", brewery = "Omnipollo",
    )
    private val bianca = catalogProduct(
        articleNumber = "1000201", articleNumberShort = "10002",
        name = "Omnipollo Bianca", brewery = "Omnipollo",
    )
    private val zodiak = catalogProduct(
        articleNumber = "1000301", articleNumberShort = "10003",
        name = "Omnipollo Zodiak IPA", brewery = "Omnipollo",
    )
    private val punk = catalogProduct(
        articleNumber = "1000401", articleNumberShort = "10004",
        name = "Brewdog Punk IPA", brewery = "BrewDog",
    )
    private val mariestadsCan = catalogProduct(
        articleNumber = "1000601", articleNumberShort = "10006",
        name = "Mariestads Export", brewery = "Spendrups", volumeMl = 500,
    )
    private val mariestadsBottle = catalogProduct(
        articleNumber = "1000501", articleNumberShort = "10005",
        name = "Mariestads Export", brewery = "Spendrups", volumeMl = 330,
    )
    private val skanes = catalogProduct(
        articleNumber = "1000701", articleNumberShort = "10007",
        name = "Skånes Pale Ale", brewery = "Skånebryggeriet",
    )
    private val passion = catalogProduct(
        articleNumber = "1000801", articleNumberShort = "10008",
        name = "Poppels Passion Pale Ale", brewery = "Poppels Bryggeri",
    )
    private val toOl = catalogProduct(
        articleNumber = "1000901", articleNumberShort = "10009",
        name = "To Øl Gose to Hollywood", brewery = "To Øl",
    )
    private val fager = catalogProduct(
        articleNumber = "1001001", articleNumberShort = "10010",
        name = "Fager Lager", brewery = "Fager",
    )

    /**
     * Filler beers stand in for the thousands of catalog products that
     * share the everyday words. They make "pale", "ale", "ipa", "lager"
     * and "bryggeri" common, so the distinctive rule behaves as it does on
     * the real catalog. Each filler has a unique two letter tag so it stays
     * a separate identity.
     */
    private val fillers = (0 until 104).map { i ->
        val tag = ("" + ('a' + i / 4) + ('a' + i % 4)).uppercase()
        val style = when (i % 3) {
            0 -> "Pale Ale"
            1 -> "IPA"
            else -> "Lager"
        }
        catalogProduct(
            articleNumber = (9000000 + i).toString(),
            articleNumberShort = null,
            name = "Filler $style $tag",
            brewery = "Filler Bryggeri",
        )
    }

    private val matcher = CatalogTextMatcher(
        listOf(
            prodigal, bianca, zodiak, punk, mariestadsCan, mariestadsBottle,
            skanes, passion, toOl, fager,
        ) + fillers,
    )

    private fun names(text: String) = matcher.match(text).map { it.product.name }

    @Test
    fun `the full name and brewery rank that beer first with a perfect score`() {
        val matches = matcher.match("OMNIPOLLO\nPRODIGAL PALE ALE\n5,2% VOL 330 ML")

        assertEquals("Omnipollo Prodigal Pale Ale", matches.first().product.name)
        assertEquals(1.0, matches.first().score, 0.0001)
    }

    @Test
    fun `name words alone match when one of them is distinctive`() {
        assertEquals(listOf("Omnipollo Prodigal Pale Ale"), names("PRODIGAL PALE ALE"))
    }

    @Test
    fun `a brewery with several beers does not match on its name alone`() {
        assertEquals(emptyList<String>(), names("OMNIPOLLO"))
    }

    @Test
    fun `everyday words alone match nothing`() {
        assertEquals(emptyList<String>(), names("PALE ALE IPA BRYGGERI"))
    }

    @Test
    fun `one wrong letter in a long word still matches through the fuzzy rule`() {
        assertEquals(listOf("Brewdog Punk IPA"), names("BREWDOQ PUNK IPA"))
    }

    @Test
    fun `short words only match exactly`() {
        assertEquals(emptyList<String>(), names("PUNQ IPA"))
    }

    @Test
    fun `a common word cannot reach a distinctive word through the fuzzy rule`() {
        // "lager" is within distance 1 of "fager", but the anchor must be exact.
        assertEquals(emptyList<String>(), names("LAGER"))
        assertEquals(listOf("Fager Lager"), names("FAGER LAGER"))
    }

    @Test
    fun `swedish letters fold so a plain ascii reading still matches`() {
        assertEquals(listOf("Skånes Pale Ale"), names("SKANES PALE ALE"))
    }

    @Test
    fun `the danish o folds so a plain ascii reading still matches`() {
        assertEquals(listOf("To Øl Gose to Hollywood"), names("TO OL GOSE TO HOLLYWOOD"))
    }

    @Test
    fun `digits and units never contribute`() {
        assertEquals(emptyList<String>(), names("5,2 % VOL 330 ML 7310401012345"))
    }

    @Test
    fun `two packagings of the same beer give one result with the lowest article number`() {
        val matches = matcher.match("MARIESTADS EXPORT")

        assertEquals(1, matches.size)
        assertEquals("1000501", matches.single().product.articleNumber)
    }

    @Test
    fun `results are ordered by score`() {
        assertEquals(
            listOf("Omnipollo Prodigal Pale Ale", "Omnipollo Zodiak IPA"),
            names("OMNIPOLLO PRODIGAL PALE ALE ZODIAK"),
        )
    }

    @Test
    fun `results are capped at five and equal scores follow swedish name order`() {
        val house = (0 until 7).map { i ->
            val tag = ("" + ('a' + i) + ('a' + i)).uppercase()
            catalogProduct(
                articleNumber = (2000000 + i).toString(),
                articleNumberShort = null,
                name = "Husbryggeriet $tag",
                brewery = "Husbryggeriet",
            )
        }
        val small = CatalogTextMatcher(house)

        val matched = small.match("HUSBRYGGERIET AA BB CC DD EE FF").map { it.product.name }

        assertEquals(
            listOf(
                "Husbryggeriet AA", "Husbryggeriet BB", "Husbryggeriet CC",
                "Husbryggeriet DD", "Husbryggeriet EE",
            ),
            matched,
        )
    }

    @Test
    fun `empty text and an empty catalog give an empty list`() {
        assertEquals(emptyList<String>(), names(""))
        assertEquals(0, CatalogTextMatcher(emptyList()).match("OMNIPOLLO PRODIGAL").size)
    }

    @Test
    fun `tokenize lower cases, folds letters, and drops digits and single letters`() {
        assertEquals(
            setOf("prodigal", "pale", "ale", "ml"),
            CatalogTextMatcher.tokenize("Prodigal Pale Ale 5,2% 330 ml x"),
        )
        assertEquals(
            setOf("skanebryggeriet", "to", "ol"),
            CatalogTextMatcher.tokenize("Skånebryggeriet To Øl"),
        )
        assertEquals(emptySet<String>(), CatalogTextMatcher.tokenize("7310401012345"))
    }
}
```

- [ ] **Step 2: Run the test class to verify it fails to compile**

Run (PowerShell, repo root): `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.CatalogTextMatcherTest" --console=plain`
Expected: compilation FAILS with unresolved reference `CatalogTextMatcher`.

- [ ] **Step 3: Implement the matcher**

`app/src/main/java/com/beertracker/domain/CatalogTextMatcher.kt`:

```kotlin
package com.beertracker.domain

import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min

/** One catalog beer and how much of its name and brewery was read off the can, 0 to 1. */
data class CatalogMatch(val product: CatalogProduct, val score: Double)

/**
 * Ranks the catalog against free text read off a can or bottle.
 *
 * Products with the same tokenized name and brewery are one identity (the
 * same beer in another packaging), represented by the lowest article
 * number, the same rule the shelf lookup uses. Every identity token gets a
 * weight ln(1 + N / df): words shared by hundreds of beers ("bryggeri",
 * "ipa") weigh little, a word unique to one beer weighs a lot. Tokens that
 * appear only in the brewery, not in the name, count at half weight,
 * because the name is what a can prints biggest while the brewery is often
 * a logo the recognizer cannot read.
 *
 * An identity's score is the weight of its tokens that were read divided
 * by the weight of all its tokens. A token is read when an identical word
 * was read (an exact hit) or, for tokens of five letters or more, a word
 * within a small Levenshtein distance was read (a fuzzy hit). An identity
 * is a candidate only when its score reaches [MIN_SCORE] and at least one
 * distinctive token (used by at most one in fifty identities) was an exact
 * hit; without the exact requirement "bryggeri" would reach "bryggerier"
 * and "lager" would reach "fager". Candidates are ordered by score, then by
 * name in Swedish order, and capped at [MAX_MATCHES].
 *
 * Built once per catalog list; matching runs once per camera frame and is
 * a linear pass over the vocabulary followed by a linear pass over the
 * identities, so it belongs on a background dispatcher.
 */
class CatalogTextMatcher(products: List<CatalogProduct>) {

    private class Identity(
        val product: CatalogProduct,
        val nameTokens: Set<String>,
        val tokens: Set<String>,
    )

    private val identities: List<Identity>
    private val weights: Map<String, Double>
    private val distinctive: Set<String>

    init {
        val byKey = LinkedHashMap<Pair<String, String>, Identity>()
        for (product in products) {
            val nameTokens = tokenize(product.name)
            val breweryTokens = tokenize(product.brewery)
            val key = nameTokens.joinToString(" ") to breweryTokens.joinToString(" ")
            val existing = byKey[key]
            if (existing == null || product.articleNumber < existing.product.articleNumber) {
                byKey[key] = Identity(product, nameTokens, nameTokens + breweryTokens)
            }
        }
        identities = byKey.values.toList()

        val frequency = HashMap<String, Int>()
        for (identity in identities) {
            for (token in identity.tokens) frequency[token] = (frequency[token] ?: 0) + 1
        }
        val count = identities.size
        weights = frequency.mapValues { (_, df) -> ln(1.0 + count.toDouble() / df) }
        val distinctiveLimit = maxOf(1, count / DISTINCTIVE_DIVISOR)
        distinctive = frequency.filterValues { it <= distinctiveLimit }.keys
    }

    fun match(text: String): List<CatalogMatch> = match(tokenize(text))

    fun match(tokens: Set<String>): List<CatalogMatch> {
        if (tokens.isEmpty() || identities.isEmpty()) return emptyList()

        val exactHits = HashSet<String>()
        val seen = HashSet<String>()
        for (token in weights.keys) {
            if (token in tokens) {
                exactHits += token
                seen += token
                continue
            }
            val tolerance = fuzzyTolerance(token)
            if (tolerance > 0 && tokens.any { read -> withinDistance(token, read, tolerance) }) {
                seen += token
            }
        }

        val matches = ArrayList<CatalogMatch>()
        for (identity in identities) {
            var total = 0.0
            var hit = 0.0
            var anchored = false
            for (token in identity.tokens) {
                val factor = if (token in identity.nameTokens) 1.0 else BREWERY_ONLY_FACTOR
                val weight = weights.getValue(token) * factor
                total += weight
                if (token in seen) {
                    hit += weight
                    if (token in exactHits && token in distinctive) anchored = true
                }
            }
            if (!anchored || total == 0.0) continue
            val score = hit / total
            if (score >= MIN_SCORE) matches += CatalogMatch(identity.product, score)
        }

        val collator = Collator.getInstance(Locale("sv", "SE"))
        return matches
            .sortedWith(compareByDescending<CatalogMatch> { it.score }.thenBy(collator) { it.product.name })
            .take(MAX_MATCHES)
    }

    companion object {
        const val MAX_MATCHES = 5
        const val MIN_SCORE = 0.6
        private const val BREWERY_ONLY_FACTOR = 0.5
        private const val DISTINCTIVE_DIVISOR = 50

        // Letters that NFD does not decompose into a base letter plus a mark.
        private val SPECIAL_LETTERS = mapOf('ø' to "o", 'æ' to "ae", 'ß' to "ss", 'ł' to "l", 'đ' to "d")
        private val COMBINING_MARKS = Regex("\\p{M}+")
        private val NON_LETTERS = Regex("[^a-z]+")

        /**
         * Lower-cases, folds accented and special letters to plain a to z,
         * splits on everything else, and keeps words of two or more letters.
         * Digits vanish: on a can they are alcohol, volume and dates.
         */
        fun tokenize(text: String): Set<String> {
            val lowered = text.lowercase(Locale.ROOT)
            val replaced = buildString(lowered.length) {
                for (c in lowered) append(SPECIAL_LETTERS[c] ?: c.toString())
            }
            val folded = Normalizer.normalize(replaced, Normalizer.Form.NFD)
                .replace(COMBINING_MARKS, "")
            return folded.split(NON_LETTERS).filterTo(LinkedHashSet()) { it.length >= 2 }
        }

        private fun fuzzyTolerance(token: String): Int = when {
            token.length >= 8 -> 2
            token.length >= 5 -> 1
            else -> 0
        }

        /** Levenshtein distance of [a] and [b] is at most [max]; gives up early when it cannot be. */
        internal fun withinDistance(a: String, b: String, max: Int): Boolean {
            if (abs(a.length - b.length) > max) return false
            var previous = IntArray(b.length + 1) { it }
            var current = IntArray(b.length + 1)
            for (i in 1..a.length) {
                current[0] = i
                var rowMin = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = min(min(previous[j] + 1, current[j - 1] + 1), previous[j - 1] + cost)
                    if (current[j] < rowMin) rowMin = current[j]
                }
                if (rowMin > max) return false
                val swap = previous
                previous = current
                current = swap
            }
            return previous[b.length] <= max
        }
    }
}
```

- [ ] **Step 4: Run the test class to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.CatalogTextMatcherTest" --console=plain`
Expected: BUILD SUCCESSFUL, 15 tests, 0 failures.

If `name words alone match when one of them is distinctive` or `swedish letters fold` fails on the score threshold, check the fixture first (the filler count and the three filler styles set the weights of "pale" and "ale"); do not lower `MIN_SCORE`.

- [ ] **Step 5: Run the whole debug suite**

Run: `.\gradlew.bat testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL, 293 tests, 0 failures.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/beertracker/domain/CatalogTextMatcher.kt app/src/test/java/com/beertracker/CatalogTextMatcherTest.kt
git commit -m "[App] Match text read off a can against the catalog by name and brewery"
```

---

### Task 2: CanTextParser

Picks the line of recognized text that looks most like a beer name, for the no-match path that carries a name into the add form.

**Files:**
- Create: `app/src/main/java/com/beertracker/domain/CanTextParser.kt`
- Test: `app/src/test/java/com/beertracker/CanTextParserTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces, relied on by Task 5: `object CanTextParser { fun guessName(text: String): String? }` in package `com.beertracker.domain`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/beertracker/CanTextParserTest.kt`:

```kotlin
package com.beertracker

import com.beertracker.domain.CanTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CanTextParserTest {

    @Test
    fun `picks the line with the most letters`() {
        val text = "OMNIPOLLO\nPRODIGAL PALE ALE\n5,2 % VOL\n330 ML"
        assertEquals("PRODIGAL PALE ALE", CanTextParser.guessName(text))
    }

    @Test
    fun `skips lines that are mostly digits and units`() {
        assertEquals("BREWDOG PUNK IPA", CanTextParser.guessName("BREWDOG PUNK IPA\nALC 5,2% VOL"))
        assertNull(CanTextParser.guessName("5,2 % VOL\n330 ML\n7310401012345"))
    }

    @Test
    fun `returns null for empty text`() {
        assertNull(CanTextParser.guessName(""))
        assertNull(CanTextParser.guessName("\n\n"))
    }

    @Test
    fun `the earlier of two equal lines wins`() {
        assertEquals("ABC", CanTextParser.guessName("ABC\nXYZ"))
    }

    @Test
    fun `the chosen line is trimmed`() {
        assertEquals("Punk IPA", CanTextParser.guessName("  Punk IPA  \n"))
    }
}
```

- [ ] **Step 2: Run the test class to verify it fails to compile**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.CanTextParserTest" --console=plain`
Expected: compilation FAILS with unresolved reference `CanTextParser`.

- [ ] **Step 3: Implement the parser**

`app/src/main/java/com/beertracker/domain/CanTextParser.kt`:

```kotlin
package com.beertracker.domain

/**
 * Finds the line of text read off a can that looks most like the beer's
 * name, for the case where nothing in the catalog matched and the user
 * finishes the entry by hand.
 *
 * A line qualifies when it has at least three letters and letters make up
 * more than half of its non-space characters; that drops "5,2 % VOL",
 * "330 ML" and barcode digits. Of the qualifying lines the one with the
 * most letters wins, and the earlier line wins a tie. The text is returned
 * as read, including its case: the user edits the field anyway, and title
 * casing would turn "IPA" into "Ipa".
 */
object CanTextParser {

    private const val MIN_LETTERS = 3

    fun guessName(text: String): String? =
        text.lineSequence()
            .map(String::trim)
            .filter(::looksLikeName)
            .maxByOrNull { line -> line.count(Char::isLetter) }

    private fun looksLikeName(line: String): Boolean {
        val letters = line.count(Char::isLetter)
        val nonSpace = line.count { !it.isWhitespace() }
        return letters >= MIN_LETTERS && letters * 2 > nonSpace
    }
}
```

`maxByOrNull` returns the first element that reaches the maximum, which is what gives the earlier line the tie.

- [ ] **Step 4: Run the test class to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.CanTextParserTest" --console=plain`
Expected: BUILD SUCCESSFUL, 5 tests, 0 failures.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/beertracker/domain/CanTextParser.kt app/src/test/java/com/beertracker/CanTextParserTest.kt
git commit -m "[App] Pick the most name-like line out of the text read off a can"
```

---

### Task 3: The add form takes a name read off a can

The `prefillName` route argument, its view model method, and the screen wiring. Independent of the camera work, so it ships first and is fully usable on its own.

**Files:**
- Modify: `app/src/main/java/com/beertracker/ui/AddEditBeerViewModel.kt` (add a field after `prefilledArticle` and a method after `prefillFromCatalog`)
- Modify: `app/src/main/java/com/beertracker/ui/AddEditScreen.kt:91-105` (the `AddEditScreen` signature and its `LaunchedEffect`s)
- Modify: `app/src/main/java/com/beertracker/MainActivity.kt:72-93` (the `edit` route)
- Test: `app/src/test/java/com/beertracker/AddEditBeerViewModelTest.kt` (append four tests)
- Test: `app/src/test/java/com/beertracker/ui/AddEditCatalogSearchTest.kt` (append one test)

**Interfaces:**
- Consumes: `AddEditBeerViewModel.update`, `load`, the private `formContent()` extension and `loadedBeerId` (all existing in the same file).
- Produces, relied on by Task 6:
  - `AddEditBeerViewModel.prefillName(name: String)`.
  - `AddEditScreen(viewModel, beerId, prefillArticle: String? = null, prefillName: String? = null, onDone)`.
  - The `edit` route accepts `edit?prefillName=<encoded text>`.

- [ ] **Step 1: Write the failing view model tests**

Append inside the `AddEditBeerViewModelTest` class (the file already imports `runTest`, `assertEquals`, `assertTrue`, `assertNull`, `FakeBeerRepository`, `FakeCatalogRepository`, `beer`):

```kotlin
    @Test
    fun `prefill name fills an empty add form and marks unsaved changes`() = runTest {
        val vm = AddEditBeerViewModel(FakeBeerRepository(), FakeCatalogRepository())

        vm.prefillName("PRODIGAL PALE ALE")

        assertEquals("PRODIGAL PALE ALE", vm.form.value.name)
        assertTrue(vm.form.value.hasUnsavedChanges)
        assertNull(vm.form.value.catalogArticleNumber)
    }

    @Test
    fun `prefill name never overwrites text the user already typed`() = runTest {
        val vm = AddEditBeerViewModel(FakeBeerRepository(), FakeCatalogRepository())

        vm.update { it.copy(name = "My own name") }
        vm.prefillName("PRODIGAL PALE ALE")

        assertEquals("My own name", vm.form.value.name)
    }

    @Test
    fun `prefill name is ignored while editing an existing beer`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "b1", name = "Saved"))
        val vm = AddEditBeerViewModel(repo, FakeCatalogRepository())
        vm.load("b1")

        vm.prefillName("PRODIGAL PALE ALE")

        assertEquals("Saved", vm.form.value.name)
    }

    @Test
    fun `prefill name runs once per value`() = runTest {
        val vm = AddEditBeerViewModel(FakeBeerRepository(), FakeCatalogRepository())

        vm.prefillName("PRODIGAL PALE ALE")
        vm.update { it.copy(name = "") }
        vm.prefillName("PRODIGAL PALE ALE")

        assertEquals("", vm.form.value.name)
    }
```

- [ ] **Step 2: Run the test class to verify it fails to compile**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.AddEditBeerViewModelTest" --console=plain`
Expected: compilation FAILS with unresolved reference `prefillName`.

- [ ] **Step 3: Implement `prefillName`**

In `AddEditBeerViewModel.kt`, directly under `private var prefilledArticle: String? = null` add:

```kotlin
    private var prefilledName: String? = null
```

Directly after the `prefillFromCatalog` function (before `private fun formFilledFrom`) add:

```kotlin
    /**
     * Fills the Name field of an untouched add form with text read off a
     * can that matched nothing in the catalog, so the user finishes the
     * entry by hand with the inline suggestions still available. Runs at
     * most once per value, so a configuration change cannot put the text
     * back after the user cleared it, and never touches a form the user
     * has already started, nor an existing beer being edited.
     */
    fun prefillName(name: String) {
        if (loadedBeerId != null) return
        if (prefilledName == name) return
        prefilledName = name
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        if (_form.value.formContent() != BeerFormState().formContent()) return
        update { it.copy(name = trimmed) }
    }
```

- [ ] **Step 4: Run the view model tests**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.AddEditBeerViewModelTest" --console=plain`
Expected: BUILD SUCCESSFUL, all tests in the class pass including the four new ones.

- [ ] **Step 5: Write the failing screen test**

Append inside `AddEditCatalogSearchTest` (add `import androidx.compose.ui.test.requestFocus` to the imports):

```kotlin
    @Test
    fun `a name carried in from the can screen shows suggestions once the field is focused`() {
        val catalog = FakeCatalogRepository().apply { add(catalogProduct()) }
        val vm = AddEditBeerViewModel(FakeBeerRepository(), catalog)
        composeRule.setContent {
            BeerTrackerTheme {
                AddEditScreen(viewModel = vm, beerId = null, prefillName = "Omni", onDone = {})
            }
        }

        composeRule.runOnIdle { assertEquals("Omni", vm.form.value.name) }
        composeRule.onNodeWithText("Name *").requestFocus()

        composeRule.onNodeWithText("Omnipollo Prodigal Pale Ale").assertIsDisplayed()
    }
```

- [ ] **Step 6: Run the screen test to verify it fails to compile**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.ui.AddEditCatalogSearchTest" --console=plain`
Expected: compilation FAILS, no parameter named `prefillName`.

- [ ] **Step 7: Wire the screen and the route**

In `AddEditScreen.kt`, change the signature and the effects at the top of `AddEditScreen` to:

```kotlin
fun AddEditScreen(
    viewModel: AddEditBeerViewModel,
    beerId: String?,
    prefillArticle: String? = null,
    prefillName: String? = null,
    onDone: () -> Unit,
) {
    LaunchedEffect(beerId) {
        if (beerId != null) viewModel.load(beerId)
    }
    LaunchedEffect(prefillArticle) {
        if (beerId == null && prefillArticle != null) {
            viewModel.prefillFromCatalog(prefillArticle)
        }
    }
    LaunchedEffect(prefillName) {
        if (beerId == null && prefillName != null) {
            viewModel.prefillName(prefillName)
        }
    }
```

In `MainActivity.kt`, change the `edit` composable to:

```kotlin
        composable(
            route = "edit?beerId={beerId}&prefillArticle={prefillArticle}&prefillName={prefillName}",
            arguments = listOf(
                navArgument("beerId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("prefillArticle") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("prefillName") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            AddEditScreen(
                viewModel = viewModel(factory = AddEditBeerViewModel.Factory),
                beerId = backStackEntry.arguments?.getString("beerId"),
                prefillArticle = backStackEntry.arguments?.getString("prefillArticle"),
                prefillName = backStackEntry.arguments?.getString("prefillName"),
                onDone = { navController.popBackStack() },
            )
        }
```

Navigation Compose decodes query arguments itself. Read the value as is; never call `Uri.decode` on it (the brewery route once did and corrupted names containing a percent sign).

- [ ] **Step 8: Run the whole debug suite**

Run: `.\gradlew.bat testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL, 303 tests, 0 failures.

- [ ] **Step 9: Commit**

```powershell
git add app/src/main/java/com/beertracker/ui/AddEditBeerViewModel.kt app/src/main/java/com/beertracker/ui/AddEditScreen.kt app/src/main/java/com/beertracker/MainActivity.kt app/src/test/java/com/beertracker/AddEditBeerViewModelTest.kt app/src/test/java/com/beertracker/ui/AddEditCatalogSearchTest.kt
git commit -m "[App] Let the add form take a name read off a can"
```

---

### Task 4: Shared camera code and the scan mode switch

A pure extraction: the permission flow, the camera preview and the ML Kit analyzer move out of `ScanScreen.kt` into a file both scan screens use, with no change in behavior. Plus the new segmented mode switch as a standalone component with its own test. `ScanScreen` is not yet given the switch (that happens in Task 6 together with the route it navigates to).

**Files:**
- Create: `app/src/main/java/com/beertracker/ui/scan/TextRecognitionCamera.kt`
- Create: `app/src/main/java/com/beertracker/ui/scan/ScanModeSwitch.kt`
- Modify: `app/src/main/java/com/beertracker/ui/scan/ScanScreen.kt` (remove the moved code, call the shared functions)
- Modify: `app/src/main/res/values/strings.xml` (two new strings)
- Test: `app/src/test/java/com/beertracker/ui/scan/ScanModeSwitchTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces, relied on by Task 6:
  - `internal enum class CameraPermission { UNKNOWN, GRANTED, DENIED }` (moved, same name and package `com.beertracker.ui.scan`).
  - `@Composable internal fun rememberCameraPermission(): CameraPermission`.
  - `@Composable internal fun TextRecognitionCameraPreview(onTextDetected: (String) -> Unit)`.
  - `enum class ScanMode { SHELF_LABEL, CAN }` and `@Composable internal fun ScanModeSwitch(selected: ScanMode, onSelect: (ScanMode) -> Unit, modifier: Modifier = Modifier)`.
  - String resources `scan_mode_shelf_label` ("Shelf label") and `scan_mode_can` ("Can").

- [ ] **Step 1: Write the failing mode switch test**

`app/src/test/java/com/beertracker/ui/scan/ScanModeSwitchTest.kt`:

```kotlin
package com.beertracker.ui.scan

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.beertracker.ui.theme.BeerTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScanModeSwitchTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows both modes and reports a tap on the other one`() {
        var selected: ScanMode? = null
        composeRule.setContent {
            BeerTrackerTheme {
                ScanModeSwitch(selected = ScanMode.SHELF_LABEL, onSelect = { selected = it })
            }
        }

        composeRule.onNodeWithText("Shelf label").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithText("Can").assertIsDisplayed().performClick()

        assertEquals(ScanMode.CAN, selected)
    }

    @Test
    fun `tapping the selected mode does nothing`() {
        var selected: ScanMode? = null
        composeRule.setContent {
            BeerTrackerTheme {
                ScanModeSwitch(selected = ScanMode.CAN, onSelect = { selected = it })
            }
        }

        composeRule.onNodeWithText("Can").performClick()

        assertNull(selected)
    }
}
```

- [ ] **Step 2: Run the test class to verify it fails to compile**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.ui.scan.ScanModeSwitchTest" --console=plain`
Expected: compilation FAILS with unresolved references `ScanMode` and `ScanModeSwitch`.

- [ ] **Step 3: Add the two strings**

In `app/src/main/res/values/strings.xml`, directly after the `scan_title` line add:

```xml
    <string name="scan_mode_shelf_label">Shelf label</string>
    <string name="scan_mode_can">Can</string>
```

- [ ] **Step 4: Create the mode switch**

`app/src/main/java/com/beertracker/ui/scan/ScanModeSwitch.kt`:

```kotlin
package com.beertracker.ui.scan

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.beertracker.R

/** The two things the camera can read: a shelf label's number, or the text on a can. */
enum class ScanMode { SHELF_LABEL, CAN }

/**
 * The switch at the top of both scan screens. Selecting the other segment
 * calls [onSelect]; the caller navigates. Tapping the already selected
 * segment is ignored so a double tap cannot bounce between routes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScanModeSwitch(
    selected: ScanMode,
    onSelect: (ScanMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        ScanMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { if (mode != selected) onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ScanMode.entries.size),
                label = { Text(stringResource(mode.labelRes)) },
            )
        }
    }
}

private val ScanMode.labelRes: Int
    get() = when (this) {
        ScanMode.SHELF_LABEL -> R.string.scan_mode_shelf_label
        ScanMode.CAN -> R.string.scan_mode_can
    }
```

- [ ] **Step 5: Run the mode switch test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.ui.scan.ScanModeSwitchTest" --console=plain`
Expected: BUILD SUCCESSFUL, 2 tests, 0 failures.

- [ ] **Step 6: Create the shared camera file**

`app/src/main/java/com/beertracker/ui/scan/TextRecognitionCamera.kt`:

```kotlin
package com.beertracker.ui.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

internal enum class CameraPermission { UNKNOWN, GRANTED, DENIED }

internal fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

/**
 * The camera permission as the scan screens see it: already granted,
 * denied, or not yet answered. Asks once on first composition when the
 * answer is unknown. Survives configuration changes through
 * rememberSaveable, so rotating the phone does not ask again.
 */
@Composable
internal fun rememberCameraPermission(): CameraPermission {
    val context = LocalContext.current
    var permission by rememberSaveable {
        mutableStateOf(
            if (hasCameraPermission(context)) CameraPermission.GRANTED else CameraPermission.UNKNOWN,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permission = if (granted) CameraPermission.GRANTED else CameraPermission.DENIED
    }
    LaunchedEffect(Unit) {
        if (permission == CameraPermission.UNKNOWN) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    return permission
}

/**
 * A live back-camera preview whose frames run through on-device ML Kit
 * text recognition. Every recognized frame's full text goes to
 * [onTextDetected]; deduplication and interpretation belong to the caller.
 * Shared by the shelf-label and can scan screens.
 */
@Composable
internal fun TextRecognitionCameraPreview(onTextDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(MaterialTheme.shapes.large),
    )

    DisposableEffect(lifecycleOwner) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        val analyzer = TextRecognitionAnalyzer(onTextDetected)
        providerFuture.addListener({
            val cameraProvider = providerFuture.get()
            provider = cameraProvider
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(mainExecutor, analyzer) }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
        }, mainExecutor)
        onDispose {
            analyzer.close()
            provider?.unbindAll()
        }
    }
}

/**
 * Runs ML Kit text recognition on camera frames. KEEP_ONLY_LATEST plus
 * closing the frame only when recognition completes gives natural
 * backpressure: a new frame is analyzed only when the previous one is done.
 */
private class TextRecognitionAnalyzer(private val onText: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        recognizer.process(input)
            .addOnSuccessListener { result -> onText(result.text) }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() {
        recognizer.close()
    }
}
```

- [ ] **Step 7: Slim down `ScanScreen.kt`**

Re-read `app/src/main/java/com/beertracker/ui/scan/ScanScreen.kt` first (the user may have touched it). Then:

1. Delete the line `internal enum class CameraPermission { UNKNOWN, GRANTED, DENIED }`.
2. Replace the body of `ScanScreen` from `val context = LocalContext.current` through the closing brace of `LaunchedEffect(Unit) { ... }` (the permission block) with a single line, so the function starts:

```kotlin
@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onFound: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permission = rememberCameraPermission()
    LaunchedEffect(state) {
        val found = state as? ScanUiState.Found ?: return@LaunchedEffect
        onFound(found.product.articleNumber)
    }
    var manualInput by rememberSaveable { mutableStateOf("") }
```

3. In the `ScanContent(...)` call inside `ScanScreen`, change the `cameraPreview` argument to:

```kotlin
        cameraPreview = {
            TextRecognitionCameraPreview(onTextDetected = viewModel::onTextDetected)
        },
```

4. Delete the `hasCameraPermission` function, the whole `CameraPreviewSection` composable, and the whole `LabelAnalyzer` class from `ScanScreen.kt` (they now live in `TextRecognitionCamera.kt`).
5. Remove the imports that are now unused: `android.Manifest`, `android.content.Context`, `android.content.pm.PackageManager`, `androidx.activity.compose.rememberLauncherForActivityResult`, `androidx.activity.result.contract.ActivityResultContracts`, every `androidx.camera.*` import, `androidx.compose.foundation.layout.height`, `androidx.compose.runtime.DisposableEffect`, `androidx.compose.runtime.remember`, `androidx.compose.ui.draw.clip`, `androidx.compose.ui.platform.LocalContext`, `androidx.compose.ui.viewinterop.AndroidView`, `androidx.core.content.ContextCompat`, `androidx.lifecycle.compose.LocalLifecycleOwner`, and the three `com.google.mlkit.*` imports. Keep `androidx.compose.ui.unit.dp` (still used by the progress indicator), `rememberSaveable`, `mutableStateOf`, `getValue`, `setValue`.

`ScanContent` itself does not change in this task.

- [ ] **Step 8: Run the whole debug suite**

Run: `.\gradlew.bat testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL, 305 tests, 0 failures. The five `ScanScreenTest` tests must pass unchanged; if the build reports an unused import warning as an error, remove that import.

- [ ] **Step 9: Commit**

```powershell
git add app/src/main/java/com/beertracker/ui/scan/TextRecognitionCamera.kt app/src/main/java/com/beertracker/ui/scan/ScanModeSwitch.kt app/src/main/java/com/beertracker/ui/scan/ScanScreen.kt app/src/main/res/values/strings.xml app/src/test/java/com/beertracker/ui/scan/ScanModeSwitchTest.kt
git commit -m "[App] Share the camera and permission code between scan modes"
```

---

### Task 5: CanScanViewModel

Accumulates the words read across frames, keeps the best name guess, and turns the matcher's answer into catalog rows joined with the user's beers.

**Files:**
- Create: `app/src/main/java/com/beertracker/ui/scan/CanScanViewModel.kt`
- Test: `app/src/test/java/com/beertracker/CanScanViewModelTest.kt`

**Interfaces:**
- Consumes: `CatalogTextMatcher`, `CatalogMatch` (Task 1), `CanTextParser` (Task 2), `CatalogRepository`, `BeerRepository`, `CatalogRow` (existing, `com.beertracker.ui.components`), `FakeCatalogRepository`, `FakeBeerRepository`, `catalogProduct(...)`, `beer(...)`, `MainDispatcherRule` (existing test helpers).
- Produces, relied on by Task 6:
  - `data class CanScanUiState(val hasText: Boolean = false, val guessedName: String? = null, val matches: List<CatalogRow> = emptyList())` in package `com.beertracker.ui.scan`.
  - `class CanScanViewModel(catalogRepository: CatalogRepository, beerRepository: BeerRepository, matchDispatcher: CoroutineDispatcher = Dispatchers.Default)` with `uiState: StateFlow<CanScanUiState>`, `onTextDetected(rawText: String)`, `startOver()`, and `CanScanViewModel.Factory`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/beertracker/CanScanViewModelTest.kt`:

```kotlin
package com.beertracker

import com.beertracker.ui.scan.CanScanUiState
import com.beertracker.ui.scan.CanScanViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CanScanViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private fun catalogWithSample() = FakeCatalogRepository().apply { add(catalogProduct()) }

    private fun viewModel(
        catalog: FakeCatalogRepository = catalogWithSample(),
        beers: FakeBeerRepository = FakeBeerRepository(),
    ) = CanScanViewModel(catalog, beers, dispatcher)

    @Test
    fun `starts with nothing read and no matches`() = runTest {
        val state = viewModel().uiState.first()

        assertEquals(CanScanUiState(), state)
    }

    @Test
    fun `text naming a catalog beer produces a row for it`() = runTest {
        val vm = viewModel()

        vm.onTextDetected("OMNIPOLLO\nPRODIGAL PALE ALE\n5,2% VOL 330 ML")
        val state = vm.uiState.first { it.matches.isNotEmpty() }

        assertEquals("1324515", state.matches.single().product.articleNumber)
        assertNull(state.matches.single().triedBeerId)
        assertTrue(state.hasText)
    }

    @Test
    fun `words accumulate across frames so a name split over two frames still matches`() = runTest {
        val vm = viewModel()

        vm.onTextDetected("OMNIPOLLO PRODIGAL")
        val afterFirst = vm.uiState.first { it.hasText }
        assertEquals(0, afterFirst.matches.size)

        vm.onTextDetected("PALE ALE")
        val afterSecond = vm.uiState.first { it.matches.isNotEmpty() }
        assertEquals("Omnipollo Prodigal Pale Ale", afterSecond.matches.single().product.name)
    }

    @Test
    fun `a repeated frame does not change the state`() = runTest {
        val vm = viewModel()
        val emissions = mutableListOf<CanScanUiState>()
        backgroundScope.launch { vm.uiState.toList(emissions) }

        vm.onTextDetected("OMNIPOLLO PRODIGAL PALE ALE")
        val countAfterFirst = emissions.size
        vm.onTextDetected("OMNIPOLLO PRODIGAL PALE ALE")

        assertEquals(countAfterFirst, emissions.size)
        assertEquals(1, emissions.last().matches.size)
    }

    @Test
    fun `a logged beer's row carries its id grade and tried flag`() = runTest {
        val beers = FakeBeerRepository()
        beers.addBeer(beer(id = "b1", grade = 4).copy(catalogArticleNumber = "1324515"))
        val vm = viewModel(beers = beers)

        vm.onTextDetected("OMNIPOLLO PRODIGAL PALE ALE")
        val row = vm.uiState.first { it.matches.isNotEmpty() }.matches.single()

        assertEquals("b1", row.triedBeerId)
        assertEquals(4, row.grade)
        assertTrue(row.tried)
    }

    @Test
    fun `the guessed name is the most name-like line seen in any frame`() = runTest {
        val vm = viewModel()

        vm.onTextDetected("OMNIPOLLO\n5,2% VOL")
        assertEquals("OMNIPOLLO", vm.uiState.first { it.hasText }.guessedName)

        vm.onTextDetected("PRODIGAL PALE ALE\n33 CL")
        assertEquals(
            "PRODIGAL PALE ALE",
            vm.uiState.first { it.guessedName == "PRODIGAL PALE ALE" }.guessedName,
        )

        vm.onTextDetected("IPA")
        assertEquals("PRODIGAL PALE ALE", vm.uiState.first { it.hasText }.guessedName)
    }

    @Test
    fun `start over clears matches guess and text`() = runTest {
        val vm = viewModel()
        vm.onTextDetected("OMNIPOLLO PRODIGAL PALE ALE")
        vm.uiState.first { it.matches.isNotEmpty() }

        vm.startOver()
        val state = vm.uiState.first { !it.hasText }

        assertFalse(state.hasText)
        assertNull(state.guessedName)
        assertEquals(0, state.matches.size)
    }

    @Test
    fun `an empty catalog never matches but still records that text was read`() = runTest {
        val vm = viewModel(catalog = FakeCatalogRepository())

        vm.onTextDetected("OMNIPOLLO PRODIGAL PALE ALE")
        val state = vm.uiState.first { it.hasText }

        assertEquals(0, state.matches.size)
        assertEquals("OMNIPOLLO PRODIGAL PALE ALE", state.guessedName)
    }
}
```

- [ ] **Step 2: Run the test class to verify it fails to compile**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.CanScanViewModelTest" --console=plain`
Expected: compilation FAILS with unresolved references `CanScanUiState` and `CanScanViewModel`.

- [ ] **Step 3: Implement the view model**

`app/src/main/java/com/beertracker/ui/scan/CanScanViewModel.kt`:

```kotlin
package com.beertracker.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.beertracker.BeerApp
import com.beertracker.domain.BeerRepository
import com.beertracker.domain.CanTextParser
import com.beertracker.domain.CatalogRepository
import com.beertracker.domain.CatalogTextMatcher
import com.beertracker.ui.components.CatalogRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class CanScanUiState(
    /** True once any word or name-like line has been read, even if nothing matched. */
    val hasText: Boolean = false,
    /** The most name-like line read so far, for the "Add manually" path. */
    val guessedName: String? = null,
    /** Up to five catalog candidates, best first, joined with the user's logged beers. */
    val matches: List<CatalogRow> = emptyList(),
)

/**
 * Drives the can scan screen. Words read from the camera accumulate across
 * frames, so turning the can slowly adds the text printed around it and a
 * blurry frame contributes nothing. Matching runs on [matchDispatcher]
 * (a background dispatcher in the app, the test dispatcher in tests).
 */
class CanScanViewModel(
    catalogRepository: CatalogRepository,
    beerRepository: BeerRepository,
    matchDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private data class Reading(
        val tokens: Set<String> = emptySet(),
        val guessedName: String? = null,
    ) {
        val hasText: Boolean get() = tokens.isNotEmpty() || guessedName != null
    }

    private val reading = MutableStateFlow(Reading())

    val uiState: StateFlow<CanScanUiState> = combine(
        catalogRepository.observeProducts().map { products -> CatalogTextMatcher(products) },
        beerRepository.observeBeers(),
        reading,
    ) { matcher, beers, current ->
        // First match wins if the same article was somehow logged twice.
        val loggedByArticle = buildMap {
            for (beer in beers) {
                val number = beer.catalogArticleNumber ?: continue
                putIfAbsent(number, beer)
            }
        }
        val rows = if (current.tokens.isEmpty()) {
            emptyList()
        } else {
            matcher.match(current.tokens).map { match ->
                val logged = loggedByArticle[match.product.articleNumber]
                CatalogRow(
                    product = match.product,
                    triedBeerId = logged?.id,
                    grade = logged?.grade,
                    tried = logged?.tried ?: false,
                )
            }
        }
        CanScanUiState(
            hasText = current.hasText,
            guessedName = current.guessedName,
            matches = rows,
        )
    }
        .flowOn(matchDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CanScanUiState())

    /**
     * Feed of raw recognized text from the camera analyzer. Safe to call on
     * every frame: a frame that brings no new word and no longer name-like
     * line leaves the state object untouched, so nothing is recomputed.
     */
    fun onTextDetected(rawText: String) {
        val tokens = CatalogTextMatcher.tokenize(rawText)
        val guess = CanTextParser.guessName(rawText)
        reading.update { current ->
            val mergedTokens = if (tokens.all { it in current.tokens }) current.tokens else current.tokens + tokens
            val currentGuess = current.guessedName
            val bestGuess = when {
                guess == null -> currentGuess
                currentGuess == null -> guess
                guess.count(Char::isLetter) > currentGuess.count(Char::isLetter) -> guess
                else -> currentGuess
            }
            if (mergedTokens === current.tokens && bestGuess == currentGuess) {
                current
            } else {
                Reading(mergedTokens, bestGuess)
            }
        }
    }

    /** Forgets everything read so far so the next frames start from a clean slate. */
    fun startOver() {
        reading.value = Reading()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BeerApp
                CanScanViewModel(app.container.catalogRepository, app.container.beerRepository)
            }
        }
    }
}
```

- [ ] **Step 4: Run the test class to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.CanScanViewModelTest" --console=plain`
Expected: BUILD SUCCESSFUL, 8 tests, 0 failures.

- [ ] **Step 5: Run the whole debug suite**

Run: `.\gradlew.bat testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL, 313 tests, 0 failures.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/beertracker/ui/scan/CanScanViewModel.kt app/src/test/java/com/beertracker/CanScanViewModelTest.kt
git commit -m "[App] Can scan view model: accumulate read text and rank catalog matches"
```

---

### Task 6: The can scan screen and the switch between scan modes

The new screen, the mode switch on both scan screens, and all navigation. After this task the feature is complete in the app.

**Files:**
- Create: `app/src/main/java/com/beertracker/ui/scan/CanScanScreen.kt`
- Modify: `app/src/main/java/com/beertracker/ui/scan/ScanScreen.kt` (new `onSwitchToCan` parameter on `ScanScreen` and `ScanContent`, the switch rendered at the top of the column)
- Modify: `app/src/main/java/com/beertracker/MainActivity.kt` (the `scan` route's new callback, the new `can` route)
- Modify: `app/src/main/res/values/strings.xml` (`scan_title` text and seven new strings)
- Test: `app/src/test/java/com/beertracker/ui/scan/CanScanScreenTest.kt`
- Test: `app/src/test/java/com/beertracker/ui/scan/ScanScreenTest.kt` (pass the new parameter, one new test)

**Interfaces:**
- Consumes: `CanScanViewModel`, `CanScanUiState` (Task 5); `CameraPermission`, `rememberCameraPermission`, `TextRecognitionCameraPreview`, `ScanMode`, `ScanModeSwitch` (Task 4); `AddEditScreen` with `prefillName` (Task 3); `CatalogListItem`, `CatalogRow`, `beerListSubtitle`, `ErrorState`, `SectionHeader` (existing).
- Produces: routes `can` and the `scan` route's mode switch. Nothing later depends on this task.

- [ ] **Step 1: Add and change strings**

In `app/src/main/res/values/strings.xml`:

1. Change `<string name="scan_title">Scan shelf label</string>` to `<string name="scan_title">Scan</string>`.
2. Directly after the `scan_found` line add:

```xml
    <string name="can_hint">Point the camera at the can\'s label and turn the can slowly. Beers whose name and brewery match the text appear below.</string>
    <string name="can_camera_denied_message">Reading a can needs the camera. Allow camera access in system settings, or add the beer manually.</string>
    <string name="can_matches_section">Matches</string>
    <string name="can_nothing_read">Nothing read yet.</string>
    <string name="can_no_match">No catalog beer matches the text read so far. Turn the can, or add the beer manually.</string>
    <string name="can_start_over">Start over</string>
    <string name="can_add_manually">Add manually</string>
```

(The apostrophe in `can_hint` must be escaped as `\'`, as shown.)

- [ ] **Step 2: Write the failing can screen tests**

`app/src/test/java/com/beertracker/ui/scan/CanScanScreenTest.kt`:

```kotlin
package com.beertracker.ui.scan

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.SuccessResult
import com.beertracker.MainDispatcherRule
import com.beertracker.catalogProduct
import com.beertracker.ui.components.CatalogRow
import com.beertracker.ui.theme.BeerTrackerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CanScanScreenTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Before
    fun installFakeImageLoader() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Coil.setImageLoader(
            ImageLoader.Builder(context)
                .components {
                    add(
                        Interceptor { chain ->
                            SuccessResult(
                                drawable = ColorDrawable(Color.DKGRAY),
                                request = chain.request,
                                dataSource = DataSource.MEMORY,
                            )
                        },
                    )
                }
                .build(),
        )
    }

    @After
    fun resetImageLoader() {
        Coil.reset()
    }

    private class Actions {
        var pickedRow: CatalogRow? = null
        var startedOver = false
        var addedManually = false
        var switchedToShelfLabel = false
    }

    private fun render(
        state: CanScanUiState = CanScanUiState(),
        permission: CameraPermission = CameraPermission.GRANTED,
    ): Actions {
        val actions = Actions()
        composeRule.setContent {
            BeerTrackerTheme {
                CanScanContent(
                    state = state,
                    permission = permission,
                    onPickRow = { actions.pickedRow = it },
                    onStartOver = { actions.startedOver = true },
                    onAddManually = { actions.addedManually = true },
                    onSwitchToShelfLabel = { actions.switchedToShelfLabel = true },
                    onBack = {},
                    cameraPreview = { Text("Fake camera preview") },
                )
            }
        }
        return actions
    }

    @Test
    fun `denied permission shows the error and keeps add manually usable`() {
        val actions = render(permission = CameraPermission.DENIED)

        composeRule.onNodeWithText("Camera unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Start over").assertDoesNotExist()
        composeRule.onNodeWithText("Add manually").performScrollTo().performClick()

        assertTrue(actions.addedManually)
    }

    @Test
    fun `granted permission composes the camera preview and the waiting hint`() {
        render()

        composeRule.onNodeWithText("Fake camera preview").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing read yet.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Start over").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `text without a match shows the no match message and enables start over`() {
        val actions = render(CanScanUiState(hasText = true, guessedName = "SOMETHING"))

        composeRule
            .onNodeWithText("No catalog beer matches the text read so far. Turn the can, or add the beer manually.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Start over").performScrollTo().assertIsEnabled().performClick()

        assertTrue(actions.startedOver)
    }

    @Test
    fun `a candidate row shows the beer and tapping it passes the row`() {
        val row = CatalogRow(product = catalogProduct(), triedBeerId = null, grade = null, tried = false)
        val actions = render(CanScanUiState(hasText = true, guessedName = "OMNIPOLLO", matches = listOf(row)))

        composeRule.onNodeWithText("Omnipollo Prodigal Pale Ale").performScrollTo().performClick()

        assertEquals(row, actions.pickedRow)
        composeRule.onNodeWithText("Nothing read yet.").assertDoesNotExist()
    }

    @Test
    fun `the mode switch reports shelf label`() {
        val actions = render()

        composeRule.onNodeWithText("Shelf label").performClick()

        assertTrue(actions.switchedToShelfLabel)
    }
}
```

- [ ] **Step 3: Run the test class to verify it fails to compile**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.ui.scan.CanScanScreenTest" --console=plain`
Expected: compilation FAILS with unresolved reference `CanScanContent`.

- [ ] **Step 4: Create the can screen**

`app/src/main/java/com/beertracker/ui/scan/CanScanScreen.kt`:

```kotlin
package com.beertracker.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beertracker.R
import com.beertracker.ui.components.CatalogListItem
import com.beertracker.ui.components.CatalogRow
import com.beertracker.ui.components.ErrorState
import com.beertracker.ui.components.SectionHeader
import com.beertracker.ui.components.beerListSubtitle
import com.beertracker.ui.theme.BeerTrackerSpacing

/**
 * The can scan mode: live camera, on-device text recognition, and a list
 * of catalog beers whose name and brewery match what was read. Picking an
 * unlogged beer goes to the prefilled add form; a logged one opens its
 * detail screen; "Add manually" carries the best name guess into an empty
 * form.
 */
@Composable
fun CanScanScreen(
    viewModel: CanScanViewModel,
    onAddProduct: (String) -> Unit,
    onOpenBeer: (String) -> Unit,
    onAddManually: (String?) -> Unit,
    onSwitchToShelfLabel: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permission = rememberCameraPermission()

    CanScanContent(
        state = state,
        permission = permission,
        onPickRow = { row ->
            val beerId = row.triedBeerId
            if (beerId != null) onOpenBeer(beerId) else onAddProduct(row.product.articleNumber)
        },
        onStartOver = viewModel::startOver,
        onAddManually = { onAddManually(state.guessedName) },
        onSwitchToShelfLabel = onSwitchToShelfLabel,
        onBack = onBack,
        cameraPreview = {
            TextRecognitionCameraPreview(onTextDetected = viewModel::onTextDetected)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CanScanContent(
    state: CanScanUiState,
    permission: CameraPermission,
    onPickRow: (CatalogRow) -> Unit,
    onStartOver: () -> Unit,
    onAddManually: () -> Unit,
    onSwitchToShelfLabel: () -> Unit,
    onBack: () -> Unit,
    cameraPreview: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BeerTrackerSpacing.large),
            verticalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.medium),
        ) {
            ScanModeSwitch(
                selected = ScanMode.CAN,
                onSelect = { if (it == ScanMode.SHELF_LABEL) onSwitchToShelfLabel() },
            )
            when (permission) {
                CameraPermission.GRANTED -> {
                    cameraPreview()
                    Text(
                        stringResource(R.string.can_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MatchesSection(state = state, onPickRow = onPickRow)
                }
                CameraPermission.DENIED -> {
                    ErrorState(
                        title = stringResource(R.string.camera_denied_title),
                        message = stringResource(R.string.can_camera_denied_message),
                    )
                }
                CameraPermission.UNKNOWN -> {
                    Text(
                        stringResource(R.string.camera_waiting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = BeerTrackerSpacing.small),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = BeerTrackerSpacing.section),
                horizontalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.medium),
            ) {
                if (permission == CameraPermission.GRANTED) {
                    OutlinedButton(
                        onClick = onStartOver,
                        enabled = state.hasText,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.can_start_over))
                    }
                }
                Button(
                    onClick = onAddManually,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.can_add_manually))
                }
            }
        }
    }
}

/**
 * The candidate list. A plain Column rather than a LazyColumn because the
 * screen already scrolls and there are at most five rows.
 */
@Composable
private fun MatchesSection(
    state: CanScanUiState,
    onPickRow: (CatalogRow) -> Unit,
) {
    val supporting = when {
        state.matches.isNotEmpty() -> null
        state.hasText -> stringResource(R.string.can_no_match)
        else -> stringResource(R.string.can_nothing_read)
    }
    SectionHeader(
        title = stringResource(R.string.can_matches_section),
        supportingText = supporting,
        modifier = Modifier.padding(top = BeerTrackerSpacing.small),
    )
    state.matches.forEach { row ->
        CatalogListItem(
            row = row,
            subtitle = beerListSubtitle(row.product.brewery, row.product.type),
            onClick = { onPickRow(row) },
            modifier = Modifier.padding(vertical = BeerTrackerSpacing.xSmall),
        )
    }
}
```

- [ ] **Step 5: Run the can screen tests to verify they pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.ui.scan.CanScanScreenTest" --console=plain`
Expected: BUILD SUCCESSFUL, 5 tests, 0 failures.

- [ ] **Step 6: Update `ScanScreenTest` for the new parameter and the switch**

Re-read `app/src/test/java/com/beertracker/ui/scan/ScanScreenTest.kt`. Change `renderContent` to accept and pass the new callback:

```kotlin
    private fun renderContent(
        state: ScanUiState = ScanUiState.Idle,
        permission: CameraPermission = CameraPermission.DENIED,
        onManualLookup: () -> Unit = {},
        onScanAgain: () -> Unit = {},
        onSwitchToCan: () -> Unit = {},
    ) {
        composeRule.setContent {
            BeerTrackerTheme {
                ScanContent(
                    state = state,
                    permission = permission,
                    manualInput = "13245",
                    onManualInputChange = {},
                    onManualLookup = onManualLookup,
                    onScanAgain = onScanAgain,
                    onSwitchToCan = onSwitchToCan,
                    onBack = {},
                    cameraPreview = { Text("Fake camera preview") },
                )
            }
        }
    }
```

In the test `manual lookup through the real screen navigates with the full article number`, add `onSwitchToCan = {},` to the `ScanScreen(...)` call, after `onFound`.

Append one test:

```kotlin
    @Test
    fun `the mode switch reports can`() {
        var switched = false
        renderContent(onSwitchToCan = { switched = true })

        composeRule.onNodeWithText("Can").performClick()

        assertTrue(switched)
    }
```

- [ ] **Step 7: Run `ScanScreenTest` to verify it fails to compile**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.ui.scan.ScanScreenTest" --console=plain`
Expected: compilation FAILS, no parameter named `onSwitchToCan`.

- [ ] **Step 8: Give `ScanScreen` the switch**

Re-read `ScanScreen.kt`. Change the `ScanScreen` signature to:

```kotlin
@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onFound: (String) -> Unit,
    onSwitchToCan: () -> Unit,
    onBack: () -> Unit,
) {
```

and pass `onSwitchToCan = onSwitchToCan,` in its `ScanContent(...)` call (after `onScanAgain`). Change the `ScanContent` signature to:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScanContent(
    state: ScanUiState,
    permission: CameraPermission,
    manualInput: String,
    onManualInputChange: (String) -> Unit,
    onManualLookup: () -> Unit,
    onScanAgain: () -> Unit,
    onSwitchToCan: () -> Unit,
    onBack: () -> Unit,
    cameraPreview: @Composable () -> Unit,
) {
```

and as the first child of its scrolling `Column`, before `when (permission) {`, add:

```kotlin
            ScanModeSwitch(
                selected = ScanMode.SHELF_LABEL,
                onSelect = { if (it == ScanMode.CAN) onSwitchToCan() },
            )
```

- [ ] **Step 9: Wire the routes**

Re-read `MainActivity.kt`. Add `import com.beertracker.ui.scan.CanScanScreen` and `import com.beertracker.ui.scan.CanScanViewModel` next to the other `ui.scan` imports. Replace the `scan` composable with the two routes:

```kotlin
        composable("scan") {
            ScanScreen(
                viewModel = viewModel(factory = ScanViewModel.Factory),
                onFound = { articleNumber ->
                    navController.navigate("edit?prefillArticle=$articleNumber") {
                        popUpTo("scan") { inclusive = true }
                    }
                },
                onSwitchToCan = {
                    navController.navigate("can") {
                        popUpTo("scan") { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable("can") {
            CanScanScreen(
                viewModel = viewModel(factory = CanScanViewModel.Factory),
                onAddProduct = { articleNumber ->
                    navController.navigate("edit?prefillArticle=$articleNumber") {
                        popUpTo("can") { inclusive = true }
                    }
                },
                onOpenBeer = { id -> navController.navigate("detail/$id") },
                onAddManually = { guessedName ->
                    val route = if (guessedName.isNullOrBlank()) {
                        "edit"
                    } else {
                        "edit?prefillName=${Uri.encode(guessedName)}"
                    }
                    navController.navigate(route) {
                        popUpTo("can") { inclusive = true }
                    }
                },
                onSwitchToShelfLabel = {
                    navController.navigate("scan") {
                        popUpTo("can") { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
```

`android.net.Uri` is already imported in `MainActivity.kt`. Opening a logged beer's detail does not pop the can screen, so back from the detail returns to the list with the read text still there; adding pops it, exactly like the shelf scanner.

- [ ] **Step 10: Run the whole debug suite**

Run: `.\gradlew.bat testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL, 319 tests, 0 failures.

- [ ] **Step 11: Build the debug APK**

Run: `.\gradlew.bat assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL. This is the only check of the camera composables and the navigation graph that is possible without a device.

- [ ] **Step 12: Commit**

```powershell
git add app/src/main/java/com/beertracker/ui/scan/CanScanScreen.kt app/src/main/java/com/beertracker/ui/scan/ScanScreen.kt app/src/main/java/com/beertracker/MainActivity.kt app/src/main/res/values/strings.xml app/src/test/java/com/beertracker/ui/scan/CanScanScreenTest.kt app/src/test/java/com/beertracker/ui/scan/ScanScreenTest.kt
git commit -m "[App] Add the can scan screen and the switch between scan modes"
```

---

### Task 7: Record phase 3 in the v1 design spec

The v1 design carries a "Phase 2 details" subsection; phase 3 gets the same treatment so the top-level design stays the map of what was built.

**Files:**
- Modify: `docs/superpowers/specs/2026-07-28-beertracker-v1-design.md` (append a subsection at the end of section 7, after the Phase 2 details list and before `## 8. Tech stack`)

- [ ] **Step 1: Add the subsection**

Directly before the line `## 8. Tech stack`, add:

```markdown
### Phase 3 details (added 2026-09-18)

- The can path is a second mode of the scan screen, chosen with a
  "Shelf label" / "Can" switch. It uses the same live camera and
  on-device text recognition as the shelf-label path.
- Words read across frames accumulate, and the offline catalog is ranked
  by how much of each beer's name and brewery was read, with rare words
  counting more than everyday ones and small spelling differences
  tolerated. Up to five candidates are listed; the user taps one and lands
  on the prefilled add form. There is no automatic jump on a single hit.
- When nothing matches, "Add manually" carries the most name-like line
  that was read into the add form's Name field, where the inline catalog
  suggestions take over.
- Nothing read is stored, neither database changes, and the camera frame
  is not attached as the beer's photo (the add form's photo field does
  that). Full design: docs/superpowers/specs/2026-09-18-can-photo-match-design.md.
```

- [ ] **Step 2: Check for long dashes**

Run (PowerShell): `Select-String -Path docs/superpowers/specs/2026-07-28-beertracker-v1-design.md -Pattern "[\u2013\u2014]"`
Expected: no output.

- [ ] **Step 3: Commit**

```powershell
git add docs/superpowers/specs/2026-07-28-beertracker-v1-design.md
git commit -m "[Docs] Record phase 3 in the v1 design spec"
```

---

## Done criteria

Phase 3 is done when all of the following hold:

1. `.\gradlew.bat testDebugUnitTest` is green with 319 tests (278 baseline plus 41 new), all JVM.
2. `.\gradlew.bat assembleDebug` is green.
3. No file under `app/schemas/` changed and neither database version moved.
4. `gradle/libs.versions.toml` is unchanged.
5. The branch merges to main and the existing release workflow ships the APK.
6. After merge, verified by the user on their phone (no device is available earlier):
   - Scan opens with the "Shelf label" / "Can" switch; the shelf-label path behaves exactly as before,
   - choosing "Can" shows the live preview, and pointing it at a can from the catalog lists that beer within a few frames with the right row at or near the top; tapping the row lands on the prefilled add form,
   - pointing at a can Systembolaget does not sell lists nothing, and "Add manually" opens the add form with the can's name text in the Name field,
   - a beer already in the list shows its grade mark in the candidate list and opens its detail screen,
   - denying the camera still allows "Add manually".
7. No user provisioning was needed at any point.
