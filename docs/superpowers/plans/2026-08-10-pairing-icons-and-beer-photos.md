# Pairing Icons and Beer Photos Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the app's invented pairing list with Systembolaget's own 15 food pairing symbols, drawn as icons and filled in automatically from the catalog, and show the beer's picture in the main list and on the add/edit screen, where the user can also attach a photo of their own.

**Architecture:** A `Pairing` enum in `domain` maps Systembolaget's Swedish `tasteSymbols` keys to English labels. `goesWellWith` stays a `List<String>` of those labels, so free text pairings keep working and the storage type does not change. Fifteen hand authored `ImageVector` silhouettes live in `ui/components/PairingIcons.kt`, tinted by callers so they follow the theme. Catalog products gain a `pairings` list read from the API field we already receive but ignore. Beer photos are a new `photoUri` column holding a `file://` URI into app private storage, kept separate from the catalog `imageUrl` so removing a photo falls back to the catalog picture.

**Tech Stack:** Existing stack only. Kotlin 2.0.21, Compose BOM 2024.12.01, Room 2.6.1 + KSP, AGP 8.7.3, JVM 17, minSdk 26, compile/target 35, Coil 2.7.0, androidx.activity 1.9.3 (its `PickVisualMedia` and `TakePicture` contracts), androidx.core (its `FileProvider`). No new dependencies.

Spec: `docs/superpowers/specs/2026-08-10-pairing-icons-and-beer-photos-design.md`.

**Execution order: 1 through 12, strictly sequential. Every task depends on the state left by the previous one.**

## Global Constraints

- Repo root for this work: `C:\Users\SilvijaSubotic\PersonalDevelopment\BeerTracker\.claude\worktrees\beer-pairing-icons-d66d19`. All commands run from there.
- Run unit tests with `.\gradlew.bat testDebugUnitTest`, in the foreground, with a 10 minute timeout. The suite must be green at the end of every task.
- No androidTest source set. Every test runs on the JVM: plain JUnit for logic, Robolectric for anything touching Android. The house Compose test pattern is in `app/src/test/java/com/beertracker/ui/ComposeUiSmokeTest.kt`: `@RunWith(RobolectricTestRunner::class)`, `@Config(application = Application::class, sdk = [35])`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, `createComposeRule()`.
- No device is available. Camera and photo picker flows are verified by unit testing the file handling and by Robolectric rendering of the surrounding UI; the live pickers are verified by the user on their phone after merge.
- Commits are authored by the user's git identity only. Never add Claude as author or co-author. No `Co-Authored-By` trailers, ever.
- No em dashes or en dashes anywhere: not in code, comments, strings, commit messages, or docs. Use hyphens, commas, or rewrite the sentence.
- Commit message style: `[Scope] Message` where Scope is `App`, `Docs`, `Build`, or `CI`.
- USER DATA SAFETY: `beertracker.db` holds the user's real beers. Its migrations must be non destructive. Never attach `fallbackToDestructiveMigration` to `BeerDatabase`. `catalog.db` is a disposable cache and is the only place destructive behaviour is allowed.
- Database version numbering: `BeerDatabase` is at version 2 today, but the unimplemented `2026-08-10-five-point-grade-scale-design.md` spec also wants version 3. Task 8 says "read the current `@Database(version = ...)` and take the next integer". Do that, do not hardcode 3 blindly.
- No hardcoded colours. Use `MaterialTheme.colorScheme`, `MaterialTheme.shapes`, and `BeerTrackerSpacing`.
- All user visible text goes in `res/values/strings.xml` and is read with `stringResource`.

## User provisioning

One optional step, in Task 7: regenerating the bundled catalog asset needs network access and the public Systembolaget product search key. The key is already a plain constant in the codebase (`SYSTEMBOLAGET_SUBSCRIPTION_KEY` in `CatalogFetcher.kt`). If the network is unavailable, Task 7 is skipped and noted; the app still works, because the in-app catalog refresh fetches pairings from the live API. Only the bundled first launch seed would lack them until the first refresh.

---

### Task 1: The Pairing enum

The vocabulary every later task depends on.

**Files:**
- Create: `app/src/main/java/com/beertracker/domain/Pairing.kt`
- Test: `app/src/test/java/com/beertracker/PairingTest.kt`

**Interfaces:**
- Produces: `enum class Pairing(val symbol: String, val label: String)` with 15 entries, plus `Pairing.fromSymbol(String): Pairing?` and `Pairing.fromLabel(String): Pairing?`. Later tasks use `Pairing.entries`, `.label`, and both lookups.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/beertracker/PairingTest.kt`:

```kotlin
package com.beertracker

import com.beertracker.domain.Pairing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingTest {

    @Test
    fun `every entry round trips through fromSymbol and fromLabel`() {
        Pairing.entries.forEach { pairing ->
            assertEquals(pairing, Pairing.fromSymbol(pairing.symbol))
            assertEquals(pairing, Pairing.fromLabel(pairing.label))
        }
    }

    @Test
    fun `labels and symbols are unique`() {
        assertEquals(Pairing.entries.size, Pairing.entries.map { it.label }.toSet().size)
        assertEquals(Pairing.entries.size, Pairing.entries.map { it.symbol }.toSet().size)
    }

    @Test
    fun `unknown input is null rather than a wrong guess`() {
        assertNull(Pairing.fromSymbol("Choklad"))
        assertNull(Pairing.fromLabel("Tacos"))
        assertNull(Pairing.fromLabel(""))
    }

    @Test
    fun `lookups are exact, not case insensitive or trimmed`() {
        assertNull(Pairing.fromSymbol("fläsk"))
        assertNull(Pairing.fromLabel(" Pork"))
    }

    @Test
    fun `declaration order groups the meats first and the occasions last`() {
        assertEquals(
            listOf(
                "Pork", "Poultry", "Lamb", "Beef", "Game", "Fish", "Shellfish",
                "Vegetables", "Cheese", "Dessert", "Spicy food", "Asian food",
                "Buffet", "Aperitif", "Social drink",
            ),
            Pairing.entries.map { it.label },
        )
    }

    @Test
    fun `symbols match the Swedish keys the catalog API sends`() {
        assertEquals(Pairing.PORK, Pairing.fromSymbol("Fläsk"))
        assertEquals(Pairing.BEEF, Pairing.fromSymbol("Nöt"))
        assertEquals(Pairing.SOCIAL, Pairing.fromSymbol("Sällskapsdryck"))
        assertEquals(Pairing.BUFFET, Pairing.fromSymbol("Buffémat"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.PairingTest"`
Expected: FAIL, unresolved reference `Pairing`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/beertracker/domain/Pairing.kt`:

```kotlin
package com.beertracker.domain

/**
 * The food pairing vocabulary Systembolaget publishes per product, in the
 * `tasteSymbols` field of the product search API.
 *
 * [symbol] is the Swedish key used only to match API responses. [label] is
 * what the app stores in `TriedBeer.goesWellWith` and shows to the user.
 * Storing the label rather than the enum name keeps `goesWellWith` a plain
 * list of strings, so a pairing the user types themselves lives in the same
 * list without a separate column.
 *
 * Declaration order is the display order everywhere: meats, then seafood,
 * then the rest of the plate, then the occasion values.
 */
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
        private val bySymbol = entries.associateBy { it.symbol }
        private val byLabel = entries.associateBy { it.label }

        /**
         * Exact match, deliberately not fuzzy. If Systembolaget renames a
         * symbol, that pairing goes missing rather than silently becoming
         * the wrong one.
         */
        fun fromSymbol(symbol: String): Pairing? = bySymbol[symbol]

        fun fromLabel(label: String): Pairing? = byLabel[label]
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.PairingTest"`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/beertracker/domain/Pairing.kt app/src/test/java/com/beertracker/PairingTest.kt
git commit -m "[App] Add the Systembolaget food pairing vocabulary"
```

---

### Task 2: Pairing icons and label resources

Fifteen silhouettes plus the two exhaustive mappings that guarantee no pairing can ship without an icon or a label.

The vector path data in Appendix A was authored as SVG, rendered at 40, 32, 24, and 18 pixels, visually checked, and converted mechanically to `ImageVector` calls. Copy it verbatim; do not retype the numbers.

**Files:**
- Create: `app/src/main/java/com/beertracker/ui/components/PairingIcons.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/beertracker/ui/components/PairingIconsTest.kt`

**Interfaces:**
- Consumes: `Pairing` from Task 1.
- Produces: `pairingIcon(pairing: Pairing): ImageVector`, `pairingLabelRes(pairing: Pairing): Int`, and `@Composable PairingIcon(pairing: Pairing, size: Dp, modifier: Modifier = Modifier)`, which tints from `MaterialTheme.colorScheme.onSurfaceVariant`. Task 3 appends `pairingLabel(pairing: Pairing): String` to the same file.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/beertracker/ui/components/PairingIconsTest.kt`:

```kotlin
package com.beertracker.ui.components

import com.beertracker.domain.Pairing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PairingIconsTest {

    @Test
    fun `every pairing has its own icon`() {
        val icons = Pairing.entries.map { pairingIcon(it) }
        assertEquals(Pairing.entries.size, icons.map { it.name }.toSet().size)
    }

    @Test
    fun `every pairing has its own label resource`() {
        val ids = Pairing.entries.map { pairingLabelRes(it) }
        assertEquals(Pairing.entries.size, ids.toSet().size)
        assertEquals(0, ids.count { it == 0 })
    }

    @Test
    fun `icons are drawn on the same 24 by 24 grid`() {
        Pairing.entries.forEach { pairing ->
            val icon = pairingIcon(pairing)
            assertEquals(24f, icon.viewportWidth, 0f)
            assertEquals(24f, icon.viewportHeight, 0f)
        }
    }

    @Test
    fun `icon names are prefixed so they cannot collide with other vectors`() {
        Pairing.entries.forEach { pairing ->
            assertNotEquals("", pairingIcon(pairing).name)
            assert(pairingIcon(pairing).name.startsWith("Pairing"))
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.ui.components.PairingIconsTest"`
Expected: FAIL, unresolved reference `pairingIcon`.

- [ ] **Step 3: Add the label strings**

In `app/src/main/res/values/strings.xml`, replace the line

```xml
    <string name="no_pairings">No pairings recorded.</string>
```

with

```xml
    <string name="no_pairings">No pairings recorded.</string>
    <string name="pairing_pork">Pork</string>
    <string name="pairing_poultry">Poultry</string>
    <string name="pairing_lamb">Lamb</string>
    <string name="pairing_beef">Beef</string>
    <string name="pairing_game">Game</string>
    <string name="pairing_fish">Fish</string>
    <string name="pairing_shellfish">Shellfish</string>
    <string name="pairing_vegetables">Vegetables</string>
    <string name="pairing_cheese">Cheese</string>
    <string name="pairing_dessert">Dessert</string>
    <string name="pairing_spicy">Spicy food</string>
    <string name="pairing_asian">Asian food</string>
    <string name="pairing_buffet">Buffet</string>
    <string name="pairing_aperitif">Aperitif</string>
    <string name="pairing_social">Social drink</string>
```

- [ ] **Step 4: Create the icon file**

Create `app/src/main/java/com/beertracker/ui/components/PairingIcons.kt` with the content in **Appendix A** of this plan, verbatim.

- [ ] **Step 5: Run the test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.ui.components.PairingIconsTest"`
Expected: PASS, 4 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/beertracker/ui/components/PairingIcons.kt app/src/main/res/values/strings.xml app/src/test/java/com/beertracker/ui/components/PairingIconsTest.kt
git commit -m "[App] Draw a food pairing icon for each Systembolaget symbol"
```

---

### Task 3: The pairing row on the detail screen

**Files:**
- Create: `app/src/main/java/com/beertracker/ui/components/PairingRow.kt`
- Modify: `app/src/main/java/com/beertracker/ui/DetailScreen.kt` (the pairings block at lines 277 to 284)
- Test: `app/src/test/java/com/beertracker/ui/components/PairingRowTest.kt`

**Interfaces:**
- Consumes: `Pairing`, `pairingIcon`, `pairingLabelRes`, `PairingIcon`.
- Produces: `@Composable PairingRow(pairings: List<String>, emptyText: String, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/beertracker/ui/components/PairingRowTest.kt`:

```kotlin
package com.beertracker.ui.components

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.beertracker.ui.theme.BeerTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PairingRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows a label for a known pairing`() {
        composeRule.setContent {
            BeerTrackerTheme { PairingRow(listOf("Pork", "Social drink"), emptyText = "none") }
        }
        composeRule.onNodeWithText("Pork").assertIsDisplayed()
        composeRule.onNodeWithText("Social drink").assertIsDisplayed()
    }

    @Test
    fun `shows a custom pairing verbatim`() {
        composeRule.setContent {
            BeerTrackerTheme { PairingRow(listOf("Tacos"), emptyText = "none") }
        }
        composeRule.onNodeWithText("Tacos").assertIsDisplayed()
    }

    @Test
    fun `shows the empty text when there are no pairings`() {
        composeRule.setContent {
            BeerTrackerTheme { PairingRow(emptyList(), emptyText = "No pairings recorded.") }
        }
        composeRule.onNodeWithText("No pairings recorded.").assertIsDisplayed()
    }

    @Test
    fun `orders known pairings by the vocabulary, custom values last`() {
        composeRule.setContent {
            BeerTrackerTheme {
                PairingRow(listOf("Tacos", "Social drink", "Pork"), emptyText = "none")
            }
        }
        composeRule.onNodeWithText("Pork").assertIsDisplayed()
        composeRule.onNodeWithText("Tacos").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.ui.components.PairingRowTest"`
Expected: FAIL, unresolved reference `PairingRow`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/beertracker/ui/components/PairingRow.kt`:

```kotlin
package com.beertracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.beertracker.domain.Pairing
import com.beertracker.ui.theme.BeerTrackerSpacing

/**
 * The pairings of one beer, as an icon above its label, the way
 * systembolaget.se presents them. Values in the known vocabulary come first,
 * in vocabulary order; anything the user typed themselves follows, with its
 * label alone and no icon.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PairingRow(
    pairings: List<String>,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    if (pairings.isEmpty()) {
        Text(
            text = emptyText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    val known = Pairing.entries.filter { entry -> pairings.any { it == entry.label } }
    val custom = pairings.filter { Pairing.fromLabel(it) == null }.distinct()
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.medium),
    ) {
        known.forEach { PairingCell(label = pairingLabel(it), pairing = it) }
        custom.forEach { PairingCell(label = it, pairing = null) }
    }
}

@Composable
private fun PairingCell(label: String, pairing: Pairing?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.xSmall),
        modifier = Modifier.width(72.dp),
    ) {
        if (pairing != null) {
            PairingIcon(pairing = pairing, size = 32.dp)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
```

- [ ] **Step 4: Add the `pairingLabel` helper**

Append to `app/src/main/java/com/beertracker/ui/components/PairingIcons.kt`:

```kotlin
/** The translated display label for a pairing. */
@Composable
internal fun pairingLabel(pairing: Pairing): String = stringResource(pairingLabelRes(pairing))
```

and add `import androidx.compose.ui.res.stringResource` to that file's imports.

- [ ] **Step 5: Use it on the detail screen**

In `app/src/main/java/com/beertracker/ui/DetailScreen.kt`, replace

```kotlin
        DetailText(
            value = beer.goesWellWith.joinToString(", "),
            emptyText = stringResource(R.string.no_pairings),
        )
```

with

```kotlin
        PairingRow(
            pairings = beer.goesWellWith,
            emptyText = stringResource(R.string.no_pairings),
        )
```

and add `import com.beertracker.ui.components.PairingRow` to the imports.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS, whole suite green.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/beertracker/ui/components/PairingRow.kt app/src/main/java/com/beertracker/ui/components/PairingIcons.kt app/src/main/java/com/beertracker/ui/DetailScreen.kt app/src/test/java/com/beertracker/ui/components/PairingRowTest.kt
git commit -m "[App] Show pairings as icons on the beer detail screen"
```

---

### Task 4: Icon chips on the add/edit screen

**Files:**
- Modify: `app/src/main/java/com/beertracker/ui/AddEditBeerViewModel.kt` (the `pairingOptions` flow at lines 86 to 88)
- Modify: `app/src/main/java/com/beertracker/ui/AddEditScreen.kt` (the chip `FlowRow` at lines 410 to 433)
- Modify: `app/src/main/java/com/beertracker/domain/Presets.kt` (delete `pairings`)
- Test: `app/src/test/java/com/beertracker/AddEditBeerViewModelTest.kt`

**Interfaces:**
- Consumes: `Pairing`, `PairingIcon`, `pairingLabel`.
- Produces: `pairingOptions` now emits the 15 vocabulary labels followed by custom values found on saved beers.

- [ ] **Step 1: Write the failing test**

Append to `app/src/test/java/com/beertracker/AddEditBeerViewModelTest.kt` (inside the existing class), and add `import com.beertracker.domain.Pairing` at the top:

```kotlin
    @Test
    fun `pairing options lead with the whole vocabulary in order`() = runTest {
        val vm = AddEditBeerViewModel(FakeBeerRepository())
        vm.pairingOptions.test()
        assertEquals(
            Pairing.entries.map { it.label },
            vm.pairingOptions.value.take(Pairing.entries.size),
        )
    }

    @Test
    fun `pairing options append custom values saved on other beers`() = runTest {
        val repository = FakeBeerRepository()
        repository.addBeer(
            testBeer(id = "a", name = "X").copy(goesWellWith = listOf("Pork", "Tacos")),
        )
        val vm = AddEditBeerViewModel(repository)
        vm.pairingOptions.test()
        assertEquals(Pairing.entries.map { it.label } + "Tacos", vm.pairingOptions.value)
    }
```

If the existing test file has no `StateFlow.test()` helper for warming a `WhileSubscribed` flow, collect it instead with the pattern already used for `typeOptions` in that file. Read the file first and follow whatever it already does; do not invent a second pattern.

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.AddEditBeerViewModelTest"`
Expected: FAIL, the options still start with `Red meat`.

- [ ] **Step 3: Rewrite `pairingOptions`**

In `app/src/main/java/com/beertracker/ui/AddEditBeerViewModel.kt`, replace

```kotlin
    val pairingOptions: StateFlow<List<String>> = repository.observeBeers()
        .map { beers -> (Presets.pairings + beers.flatMap { it.goesWellWith }).distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Presets.pairings)
```

with

```kotlin
    /**
     * The whole pairing vocabulary, always, followed by any value a saved
     * beer carries that is not in it, so a pairing typed once stays reusable.
     */
    val pairingOptions: StateFlow<List<String>> = repository.observeBeers()
        .map { beers ->
            val custom = beers
                .flatMap { it.goesWellWith }
                .filter { Pairing.fromLabel(it) == null }
                .distinct()
            Pairing.entries.map { it.label } + custom
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            Pairing.entries.map { it.label },
        )
```

Add `import com.beertracker.domain.Pairing` and remove the now unused `Presets` import if `Presets.beerTypes` is no longer referenced (it still is, so keep it).

- [ ] **Step 4: Delete the old preset list**

In `app/src/main/java/com/beertracker/domain/Presets.kt`, delete the `pairings` property so the file reads:

```kotlin
package com.beertracker.domain

object Presets {
    val beerTypes = listOf(
        "Lager", "Pilsner", "IPA", "Pale Ale", "Wheat",
        "Stout", "Porter", "Sour", "Amber Ale", "Dark Lager",
    )
}
```

- [ ] **Step 5: Put an icon on each chip**

In `app/src/main/java/com/beertracker/ui/AddEditScreen.kt`, replace the chip body inside the pairing `FlowRow`

```kotlin
                    label = { Text(option) },
                    enabled = enabled,
                )
```

with

```kotlin
                    label = { Text(option) },
                    leadingIcon = {
                        val pairing = Pairing.fromLabel(option)
                        if (pairing != null) {
                            PairingIcon(pairing = pairing, size = 18.dp)
                        }
                    },
                    enabled = enabled,
                )
```

and add these imports:

```kotlin
import com.beertracker.domain.Pairing
import com.beertracker.ui.components.PairingIcon
```

- [ ] **Step 6: Run the whole suite**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS. If any existing test asserts on `Presets.pairings` or on the string `Red meat` being offered as a chip, update it to the new vocabulary; do not reintroduce the old list.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/beertracker/ui/AddEditBeerViewModel.kt app/src/main/java/com/beertracker/ui/AddEditScreen.kt app/src/main/java/com/beertracker/domain/Presets.kt app/src/test/java/com/beertracker/AddEditBeerViewModelTest.kt
git commit -m "[App] Offer the pairing vocabulary as icon chips"
```

---

### Task 5: The catalog mapping carries pairings

Both mappers, Kotlin and Python, in one task, because the tests that hold them together share a sample.

**Files:**
- Modify: `app/src/main/java/com/beertracker/domain/CatalogProduct.kt`
- Modify: `app/src/main/java/com/beertracker/data/CatalogFetcher.kt` (`mapProduct`, lines 121 to 145)
- Modify: `app/src/main/java/com/beertracker/data/CatalogJson.kt` (`toCatalogProduct`, lines 25 to 36)
- Modify: `scripts/fetch_catalog.py` (`map_product`, lines 62 to 91)
- Test: `app/src/test/java/com/beertracker/SystembolagetCatalogFetcherTest.kt`, `app/src/test/java/com/beertracker/CatalogJsonTest.kt`, `scripts/test_fetch_catalog.py`

**Interfaces:**
- Consumes: `Pairing.fromSymbol`.
- Produces: `CatalogProduct.pairings: List<String>` (English labels, vocabulary order, unknown symbols dropped). Task 6 stores it, Task 6 also prefills the form from it.

- [ ] **Step 1: Write the failing Kotlin tests**

In `app/src/test/java/com/beertracker/SystembolagetCatalogFetcherTest.kt`, add `"tasteSymbols": ["Lamm", "Fläsk", "Sällskapsdryck"],` to `sampleBeer` immediately after the `"country": "Sverige",` line, and add these tests:

```kotlin
    @Test
    fun `mapProduct maps taste symbols to labels in vocabulary order`() {
        assertEquals(
            listOf("Pork", "Lamb", "Social drink"),
            mapProduct(JSONObject(sampleBeer)).pairings,
        )
    }

    @Test
    fun `mapProduct drops a symbol the app does not know`() {
        val json = JSONObject(sampleBeer)
            .put("tasteSymbols", JSONArray(listOf("Fläsk", "Choklad")))
        assertEquals(listOf("Pork"), mapProduct(json).pairings)
    }

    @Test
    fun `mapProduct gives an empty pairing list when the key is absent`() {
        val mapped = mapProduct(JSONObject("""{"productNumber": "42", "categoryLevel1": "Öl"}"""))
        assertEquals(emptyList<String>(), mapped.pairings)
    }
```

Also extend the existing `mapProduct fills fallbacks for missing fields` test with `assertEquals(emptyList<String>(), mapped.pairings)`.

The existing `mapProduct maps every field exactly like the seed script` test compares against a `catalogProduct()` helper. Find it in the file and add `pairings = listOf("Pork", "Lamb", "Social drink")` to the expected value.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.SystembolagetCatalogFetcherTest"`
Expected: FAIL, unresolved reference `pairings`.

- [ ] **Step 3: Add the field to the model**

In `app/src/main/java/com/beertracker/domain/CatalogProduct.kt`, add to the constructor, after `imageUrl`:

```kotlin
    val pairings: List<String> = emptyList(),
```

Giving it a default keeps every existing construction site compiling.

- [ ] **Step 4: Map it in the Kotlin fetcher**

In `app/src/main/java/com/beertracker/data/CatalogFetcher.kt`, add this helper above `mapProduct`:

```kotlin
/**
 * Maps Systembolaget's Swedish `tasteSymbols` to the app's English pairing
 * labels, in vocabulary order. A symbol the app does not know is dropped
 * rather than passed through, so a symbol added upstream cannot leak Swedish
 * text into an English UI; it simply does not appear until Pairing gains it.
 */
internal fun mapPairings(product: JSONObject): List<String> {
    val symbols = product.optJSONArray("tasteSymbols") ?: return emptyList()
    val found = (0 until symbols.length())
        .mapNotNull { Pairing.fromSymbol(symbols.optString(it)) }
        .toSet()
    return Pairing.entries.filter { it in found }.map { it.label }
}
```

add `import com.beertracker.domain.Pairing`, and add to the `CatalogProduct(...)` returned by `mapProduct`, after `imageUrl = imageUrl,`:

```kotlin
        pairings = mapPairings(product),
```

- [ ] **Step 5: Read it back from the bundled asset**

In `app/src/main/java/com/beertracker/data/CatalogJson.kt`, add to `toCatalogProduct()`, after `imageUrl = optStringOrNull("imageUrl"),`:

```kotlin
    pairings = optStringList("pairings"),
```

and add this helper at the bottom of the file:

```kotlin
/** Absent keys and JSON nulls become an empty list. */
internal fun JSONObject.optStringList(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).map { array.optString(it) }.filter { it.isNotEmpty() }
}
```

- [ ] **Step 6: Run the Kotlin tests to verify they pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.SystembolagetCatalogFetcherTest" --tests "com.beertracker.CatalogJsonTest"`
Expected: PASS.

- [ ] **Step 7: Write the failing Python tests**

In `scripts/test_fetch_catalog.py`, add `"tasteSymbols": ["Lamm", "Fläsk", "Sällskapsdryck"],` to `SAMPLE_BEER` after the `"country": "Sverige",` line, add `"pairings": ["Pork", "Lamb", "Social drink"],` to the expected dict in `test_maps_all_fields`, add `self.assertEqual(mapped["pairings"], [])` to `test_missing_optional_fields_become_none_or_fallbacks`, and add:

```python
    def test_drops_a_symbol_the_app_does_not_know(self):
        product = dict(SAMPLE_BEER, tasteSymbols=["Fläsk", "Choklad"])
        self.assertEqual(fetch_catalog.map_product(product)["pairings"], ["Pork"])

    def test_pairings_come_out_in_vocabulary_order(self):
        product = dict(SAMPLE_BEER, tasteSymbols=["Sällskapsdryck", "Fläsk"])
        self.assertEqual(
            fetch_catalog.map_product(product)["pairings"], ["Pork", "Social drink"]
        )
```

- [ ] **Step 8: Run the Python tests to verify they fail**

Run: `python scripts/test_fetch_catalog.py`
Expected: FAIL, `KeyError: 'pairings'`.

- [ ] **Step 9: Mirror the mapping in Python**

In `scripts/fetch_catalog.py`, add above `map_product`:

```python
# Must stay identical to the Pairing enum in
# app/src/main/java/com/beertracker/domain/Pairing.kt: same entries, same
# order, same English labels. Order here is the display order in the app.
PAIRING_LABELS = [
    ("Fläsk", "Pork"),
    ("Fågel", "Poultry"),
    ("Lamm", "Lamb"),
    ("Nöt", "Beef"),
    ("Vilt", "Game"),
    ("Fisk", "Fish"),
    ("Skaldjur", "Shellfish"),
    ("Grönsaker", "Vegetables"),
    ("Ost", "Cheese"),
    ("Dessert", "Dessert"),
    ("Kryddstarkt", "Spicy food"),
    ("Asiatiskt", "Asian food"),
    ("Buffémat", "Buffet"),
    ("Aperitif", "Aperitif"),
    ("Sällskapsdryck", "Social drink"),
]


def map_pairings(product):
    """Maps tasteSymbols to English labels in vocabulary order, dropping
    any symbol the app does not know."""
    symbols = set(product.get("tasteSymbols") or [])
    return [label for symbol, label in PAIRING_LABELS if symbol in symbols]
```

and add to the dict `map_product` returns, after the `"imageUrl": image_url,` line:

```python
        "pairings": map_pairings(product),
```

- [ ] **Step 10: Run the Python tests to verify they pass**

Run: `python scripts/test_fetch_catalog.py`
Expected: OK, all tests pass.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/beertracker/domain/CatalogProduct.kt app/src/main/java/com/beertracker/data/CatalogFetcher.kt app/src/main/java/com/beertracker/data/CatalogJson.kt scripts/fetch_catalog.py app/src/test/java/com/beertracker/SystembolagetCatalogFetcherTest.kt app/src/test/java/com/beertracker/CatalogJsonTest.kt scripts/test_fetch_catalog.py
git commit -m "[App] Read the food pairing symbols the catalog API already sends"
```

---

### Task 6: Store catalog pairings and prefill the form from them

**Files:**
- Modify: `app/src/main/java/com/beertracker/data/CatalogBeerEntity.kt`
- Modify: `app/src/main/java/com/beertracker/data/CatalogDatabase.kt` (version 1 to 2)
- Modify: `app/src/main/java/com/beertracker/ui/AddEditBeerViewModel.kt` (`formFilledFrom`, lines 201 to 210)
- Test: `app/src/test/java/com/beertracker/RoomCatalogRepositoryTest.kt`, `app/src/test/java/com/beertracker/AddEditBeerViewModelTest.kt`

**Interfaces:**
- Consumes: `CatalogProduct.pairings` from Task 5.
- Produces: a catalog row that round trips its pairings, and an add form whose chips arrive ticked.

- [ ] **Step 1: Write the failing tests**

In `app/src/test/java/com/beertracker/RoomCatalogRepositoryTest.kt`, add a test in the shape the file already uses for other fields:

```kotlin
    @Test
    fun `pairings round trip through the catalog database`() = runTest {
        val product = catalogProduct(articleNumber = "1").copy(
            pairings = listOf("Pork", "Social drink"),
        )
        repository.replaceAll(listOf(product), snapshotVersion = "v1", refreshedAtUtc = null)
        assertEquals(
            listOf("Pork", "Social drink"),
            repository.findByArticleNumber("1")?.pairings,
        )
    }
```

Read the file first and match its existing helper names and setup exactly; the names above are illustrative of the shape, not guaranteed to match.

In `app/src/test/java/com/beertracker/AddEditBeerViewModelTest.kt`, add:

```kotlin
    @Test
    fun `prefill from the catalog ticks the product's pairings`() = runTest {
        val catalog = FakeCatalogRepository(
            listOf(catalogProduct(articleNumber = "1324515").copy(
                pairings = listOf("Pork", "Social drink"),
            )),
        )
        val vm = AddEditBeerViewModel(FakeBeerRepository(), catalog)
        vm.prefillFromCatalog("1324515")
        advanceUntilIdle()
        assertEquals(setOf("Pork", "Social drink"), vm.form.value.pairings)
    }

    @Test
    fun `a catalog filled pairing can be unticked and the change survives save`() = runTest {
        val catalog = FakeCatalogRepository(
            listOf(catalogProduct(articleNumber = "1324515").copy(
                pairings = listOf("Pork", "Social drink"),
            )),
        )
        val repository = FakeBeerRepository()
        val vm = AddEditBeerViewModel(repository, catalog)
        vm.prefillFromCatalog("1324515")
        advanceUntilIdle()
        vm.update { it.copy(pairings = it.pairings - "Pork") }
        vm.save()
        advanceUntilIdle()
        assertEquals(listOf("Social drink"), repository.beers().single().goesWellWith)
    }
```

Again, match the file's existing helpers (`catalogProduct`, `FakeCatalogRepository`, the way it advances the dispatcher) rather than the illustrative names here.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.RoomCatalogRepositoryTest" --tests "com.beertracker.AddEditBeerViewModelTest"`
Expected: FAIL.

- [ ] **Step 3: Add the column**

In `app/src/main/java/com/beertracker/data/CatalogBeerEntity.kt`, add `val pairings: List<String>,` to `CatalogBeerEntity` after `imageUrl`, and add `pairings = pairings,` to both `toDomain()` and `toEntity()`.

The catalog database has no `@TypeConverters` today. Add it to the `@Database` annotation in the next step; the existing `Converters` class in `BeerEntity.kt` already handles `List<String>`.

- [ ] **Step 4: Bump the catalog database**

In `app/src/main/java/com/beertracker/data/CatalogDatabase.kt`, change the annotation to:

```kotlin
@Database(
    entities = [CatalogBeerEntity::class, CatalogMetadataEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
```

and add `import androidx.room.TypeConverters`. Extend the existing class comment with a sentence explaining the bump:

```
 * Version 2 adds the pairings column. No hand written migration: the
 * fallbackToDestructiveMigration below drops and reseeds the cache from the
 * bundled asset, which is exactly what a cache should do.
```

- [ ] **Step 5: Prefill the chips**

In `app/src/main/java/com/beertracker/ui/AddEditBeerViewModel.kt`, add to `formFilledFrom`, after `imageUrl = product.displayImageUrl,`:

```kotlin
        pairings = product.pairings.toSet(),
```

- [ ] **Step 6: Run the whole suite**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/beertracker/data/CatalogBeerEntity.kt app/src/main/java/com/beertracker/data/CatalogDatabase.kt app/src/main/java/com/beertracker/ui/AddEditBeerViewModel.kt app/src/test/java/com/beertracker/RoomCatalogRepositoryTest.kt app/src/test/java/com/beertracker/AddEditBeerViewModelTest.kt
git commit -m "[App] Fill a new beer's pairings from the catalog"
```

---

### Task 7: Regenerate the bundled catalog asset

**Files:**
- Modify: `app/src/main/assets/catalog/beers.json`

- [ ] **Step 1: Run the seed generator**

```bash
SYSTEMBOLAGET_API_KEY=cfc702aed3094c86b92d6d4ff7a54c84 python scripts/fetch_catalog.py
```

This makes roughly 340 sequential requests with a 0.3 second delay, so expect 3 to 6 minutes. The script refuses to write if the beer count falls outside 4000 to 6000, which is the guard against a partial sweep.

- [ ] **Step 2: Check the result**

```bash
python -c "import json;d=json.load(open('app/src/main/assets/catalog/beers.json',encoding='utf-8'));b=d['beers'];print(len(b),'beers,',sum(1 for x in b if x.get('pairings')),'with pairings')"
```

Expected: roughly 4900 beers, with a clear majority carrying pairings.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/assets/catalog/beers.json
git commit -m "[App] Regenerate the bundled catalog with pairing symbols"
```

- [ ] **Step 4: If there is no network access**

Skip steps 1 to 3, and say so explicitly in the final report. The app is still correct: `CatalogJson.optStringList` returns an empty list for the old asset, so bundled beers simply have no pairings until the first in-app catalog refresh fills them in. Do not fake the file.

---

### Task 8: The photoUri column and the pairing remap migration

**Files:**
- Modify: `app/src/main/java/com/beertracker/domain/TriedBeer.kt`
- Modify: `app/src/main/java/com/beertracker/data/BeerEntity.kt`
- Modify: `app/src/main/java/com/beertracker/data/BeerDatabase.kt`
- Modify: `app/src/main/java/com/beertracker/ui/DetailScreen.kt` (the image, line 179 to 189)
- Create (generated by KSP): `app/schemas/com.beertracker.data.BeerDatabase/<new version>.json`
- Test: `app/src/test/java/com/beertracker/BeerDatabaseMigrationTest.kt`, `app/src/test/java/com/beertracker/TriedBeerTest.kt`

**Interfaces:**
- Produces: `TriedBeer.photoUri: String?` and `TriedBeer.displayImageUrl: String?` returning `photoUri ?: imageUrl`; `BeerDatabase.MIGRATION_2_3` (or the next free version pair) and `internal fun remapPairings(stored: String): String`.

- [ ] **Step 1: Read the current database version**

Open `app/src/main/java/com/beertracker/data/BeerDatabase.kt` and note the `version = N` in the `@Database` annotation. The new version is `N + 1`. Every `MIGRATION_2_3` name below means `MIGRATION_N_N+1`; rename consistently if `N` is not 2.

- [ ] **Step 2: Write the failing tests**

In `app/src/test/java/com/beertracker/BeerDatabaseMigrationTest.kt`, add:

```kotlin
    @Test
    fun `migrating 2 to 3 adds photoUri and remaps the old pairing values`() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL(
                "INSERT INTO tried_beers (id, name, brewery, type, alcoholPercent, volumeMl, " +
                    "price, grade, tried, note, aftertaste, goesWellWith, buyAgain, favourite, " +
                    "dateAdded, catalogArticleNumber, addedBy) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(
                    "a", "Punk IPA", "BrewDog", "IPA", 5.6, 330, 29.5,
                    4, 1, "hoppy", "citrus bitter",
                    "Red meat\u001FSalmon\u001FWhite fish\u001FDessert\u001FTacos",
                    1, 1, 12345L, "1324515", "Alex",
                ),
            )
            db.execSQL(
                "INSERT INTO tried_beers (id, name, brewery, type, alcoholPercent, volumeMl, " +
                    "price, grade, tried, note, aftertaste, goesWellWith, buyAgain, favourite, " +
                    "dateAdded, catalogArticleNumber, addedBy) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(
                    "b", "Pasta Beer", "", "", null, null, null,
                    null, 0, "", "", "Pasta white sauce\u001FPasta tomato sauce",
                    0, 0, 1L, null, null,
                ),
            )
        }

        val db = helper.runMigrationsAndValidate(
            DB_NAME, 3, true, BeerDatabase.MIGRATION_1_2, BeerDatabase.MIGRATION_2_3,
        )

        db.query("SELECT id, goesWellWith, photoUri FROM tried_beers ORDER BY id").use { cursor ->
            assertEquals(2, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("a", cursor.getString(0))
            assertEquals("Beef\u001FFish\u001FDessert\u001FTacos", cursor.getString(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.moveToNext())
            assertEquals("b", cursor.getString(0))
            assertEquals("", cursor.getString(1))
            assertTrue(cursor.isNull(2))
        }
    }

    @Test
    fun `migrating 2 to 3 leaves every other column alone`() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL(
                "INSERT INTO tried_beers (id, name, brewery, type, alcoholPercent, volumeMl, " +
                    "price, grade, tried, note, aftertaste, goesWellWith, buyAgain, favourite, " +
                    "dateAdded, catalogArticleNumber, addedBy) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(
                    "a", "Punk IPA", "BrewDog", "IPA", 5.6, 330, 29.5,
                    4, 1, "hoppy", "citrus bitter", "Dessert", 1, 1,
                    12345L, "1324515", "Alex",
                ),
            )
        }

        val db = helper.runMigrationsAndValidate(
            DB_NAME, 3, true, BeerDatabase.MIGRATION_1_2, BeerDatabase.MIGRATION_2_3,
        )

        db.query(
            "SELECT name, brewery, type, alcoholPercent, volumeMl, price, grade, tried, " +
                "note, aftertaste, goesWellWith, buyAgain, favourite, dateAdded, " +
                "catalogArticleNumber, addedBy FROM tried_beers",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Punk IPA", cursor.getString(0))
            assertEquals("BrewDog", cursor.getString(1))
            assertEquals("IPA", cursor.getString(2))
            assertEquals(5.6, cursor.getDouble(3), 0.0)
            assertEquals(330, cursor.getInt(4))
            assertEquals(29.5, cursor.getDouble(5), 0.0)
            assertEquals(4, cursor.getInt(6))
            assertEquals(1, cursor.getInt(7))
            assertEquals("hoppy", cursor.getString(8))
            assertEquals("citrus bitter", cursor.getString(9))
            assertEquals("Dessert", cursor.getString(10))
            assertEquals(1, cursor.getInt(11))
            assertEquals(1, cursor.getInt(12))
            assertEquals(12345L, cursor.getLong(13))
            assertEquals("1324515", cursor.getString(14))
            assertEquals("Alex", cursor.getString(15))
        }
    }
```

Create `app/src/test/java/com/beertracker/RemapPairingsTest.kt` for the pure function:

```kotlin
package com.beertracker

import com.beertracker.data.remapPairings
import org.junit.Assert.assertEquals
import org.junit.Test

class RemapPairingsTest {

    @Test
    fun `red meat becomes beef`() {
        assertEquals("Beef", remapPairings("Red meat"))
    }

    @Test
    fun `salmon and white fish collapse to a single fish`() {
        assertEquals("Fish", remapPairings("Salmon\u001FWhite fish"))
    }

    @Test
    fun `both pasta values are dropped`() {
        assertEquals("", remapPairings("Pasta white sauce\u001FPasta tomato sauce"))
    }

    @Test
    fun `dessert and unknown values are left exactly as they are`() {
        assertEquals("Dessert\u001FTacos", remapPairings("Dessert\u001FTacos"))
    }

    @Test
    fun `an empty list stays empty`() {
        assertEquals("", remapPairings(""))
    }

    @Test
    fun `order follows the stored order, first occurrence wins`() {
        assertEquals("Fish\u001FBeef", remapPairings("White fish\u001FRed meat\u001FSalmon"))
    }
}
```

In `app/src/test/java/com/beertracker/TriedBeerTest.kt`, add:

```kotlin
    @Test
    fun `displayImageUrl prefers the user's photo over the catalog image`() {
        val beer = testBeer().copy(imageUrl = "https://cdn/x.jpg", photoUri = "file:///p.jpg")
        assertEquals("file:///p.jpg", beer.displayImageUrl)
    }

    @Test
    fun `displayImageUrl falls back to the catalog image and then to null`() {
        assertEquals(
            "https://cdn/x.jpg",
            testBeer().copy(imageUrl = "https://cdn/x.jpg", photoUri = null).displayImageUrl,
        )
        assertNull(testBeer().copy(imageUrl = null, photoUri = null).displayImageUrl)
    }
```

Match the file's existing helper for building a beer instead of `testBeer()` if it uses a different name.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.BeerDatabaseMigrationTest" --tests "com.beertracker.RemapPairingsTest" --tests "com.beertracker.TriedBeerTest"`
Expected: FAIL, unresolved references.

- [ ] **Step 4: Add the domain field**

In `app/src/main/java/com/beertracker/domain/TriedBeer.kt`, add `val photoUri: String? = null,` as the last constructor parameter, and add the `displayImageUrl` accessor. The default keeps the many test call sites compiling; `BeerEntity.toDomain` and `toEntity` construct positionally and are fixed explicitly in Step 5. The result:

```kotlin
    /**
     * The picture to show for this beer: the user's own photo if they
     * attached one, otherwise the catalog product image. Kept as two
     * separate fields on purpose, so removing a photo falls back to the
     * catalog picture instead of leaving the beer blank.
     */
    val displayImageUrl: String?
        get() = photoUri ?: imageUrl
```

so the class reads:

```kotlin
data class TriedBeer(
    val id: String,
    val name: String,
    val brewery: String,
    val type: String,
    val alcoholPercent: Double?,
    val volumeMl: Int?,
    val price: Double?,
    val grade: Int?,
    val tried: Boolean,
    val note: String,
    val aftertaste: String,
    val goesWellWith: List<String>,
    val buyAgain: Boolean,
    val favourite: Boolean,
    val dateAdded: Long,
    val catalogArticleNumber: String?,
    val addedBy: String?,
    val imageUrl: String?,
    val photoUri: String? = null,
) {
    init {
        require(grade == null || grade in 1..5) { "Grade must be between 1 and 5, was $grade" }
        require(grade == null || tried) { "A graded beer must be tried, grade was $grade" }
    }

    val displayImageUrl: String?
        get() = photoUri ?: imageUrl
}
```

- [ ] **Step 5: Add the entity column**

In `app/src/main/java/com/beertracker/data/BeerEntity.kt`, add `val photoUri: String?,` to `BeerEntity` after `imageUrl`, and add `photoUri,` as the last argument of both `toDomain()` and `toEntity()`.

- [ ] **Step 6: Write the migration**

In `app/src/main/java/com/beertracker/data/BeerDatabase.kt`, bump `version` to `N + 1`, register the migration in `build`, and add:

```kotlin
private const val PAIRING_SEPARATOR = "\u001F"

/**
 * The app's old invented pairing list, mapped onto the Systembolaget
 * vocabulary that replaced it. A null value means the old value has no
 * equivalent and is dropped, which the user chose over keeping it as an
 * iconless text chip.
 */
private val LEGACY_PAIRINGS = mapOf(
    "Red meat" to "Beef",
    "Salmon" to "Fish",
    "White fish" to "Fish",
    "Pasta white sauce" to null,
    "Pasta tomato sauce" to null,
)

/**
 * Rewrites one stored goesWellWith value. Done in Kotlin rather than SQL
 * string replacement because two old values collapse onto one new value, so
 * the result needs real deduplication.
 */
internal fun remapPairings(stored: String): String {
    if (stored.isEmpty()) return stored
    return stored.split(PAIRING_SEPARATOR)
        .mapNotNull { if (LEGACY_PAIRINGS.containsKey(it)) LEGACY_PAIRINGS[it] else it }
        .distinct()
        .joinToString(PAIRING_SEPARATOR)
}
```

and inside the companion object:

```kotlin
        /**
         * v2 to v3 adds the nullable photoUri column and rewrites stored
         * pairings onto the Systembolaget vocabulary. Non destructive on
         * every other column: this database holds the user's real beers.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tried_beers ADD COLUMN photoUri TEXT")
                // Collected first, then applied, so no UPDATE runs against
                // the table while a cursor is still open on it.
                val updates = mutableListOf<Pair<String, String>>()
                db.query("SELECT id, goesWellWith FROM tried_beers").use { cursor ->
                    while (cursor.moveToNext()) {
                        val stored = cursor.getString(1) ?: ""
                        val remapped = remapPairings(stored)
                        if (remapped != stored) updates.add(cursor.getString(0) to remapped)
                    }
                }
                updates.forEach { (id, value) ->
                    db.execSQL(
                        "UPDATE tried_beers SET goesWellWith = ? WHERE id = ?",
                        arrayOf(value, id),
                    )
                }
            }
        }
```

and change `build` to `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`.

- [ ] **Step 7: Read the display URL on the detail screen**

In `app/src/main/java/com/beertracker/ui/DetailScreen.kt`, change the image block to read `beer.displayImageUrl`:

```kotlin
        val image = beer.displayImageUrl
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = stringResource(R.string.beer_image_description, beer.name),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
```

- [ ] **Step 8: Run the whole suite and check the schema was exported**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS. Then confirm `app/schemas/com.beertracker.data.BeerDatabase/3.json` exists (or `<N+1>.json`); `runMigrationsAndValidate` needs it and it must be committed.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/beertracker/domain/TriedBeer.kt app/src/main/java/com/beertracker/data/BeerEntity.kt app/src/main/java/com/beertracker/data/BeerDatabase.kt app/src/main/java/com/beertracker/ui/DetailScreen.kt app/schemas app/src/test/java/com/beertracker
git commit -m "[App] Add a photo column and remap saved pairings"
```

---

### Task 9: BeerPhotoStore

All file handling in one small class, so no screen deals with files directly.

**Files:**
- Create: `app/src/main/java/com/beertracker/data/BeerPhotoStore.kt`
- Test: `app/src/test/java/com/beertracker/BeerPhotoStoreTest.kt`

**Interfaces:**
- Produces: `class BeerPhotoStore(private val root: File)` with `fun newPhotoFile(): File`, `fun save(input: InputStream): String`, `fun delete(uri: String?)`, `fun deleteOrphans(referenced: Set<String>)`, and `fun uriFor(file: File): String`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/beertracker/BeerPhotoStoreTest.kt`:

```kotlin
package com.beertracker

import com.beertracker.data.BeerPhotoStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BeerPhotoStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun store() = BeerPhotoStore(temporaryFolder.root)

    @Test
    fun `save writes the bytes and returns a file uri that points at them`() {
        val uri = store().save("hello".byteInputStream())
        assertTrue(uri.startsWith("file:"))
        assertEquals("hello", File(java.net.URI(uri)).readText())
    }

    @Test
    fun `every save gets its own file`() {
        val store = store()
        assertTrue(store.save("a".byteInputStream()) != store.save("b".byteInputStream()))
    }

    @Test
    fun `newPhotoFile creates an empty file inside the photo directory`() {
        val file = store().newPhotoFile()
        assertTrue(file.exists())
        assertEquals(0L, file.length())
        assertEquals("beer-photos", file.parentFile?.name)
    }

    @Test
    fun `delete removes the file and tolerates null and unknown uris`() {
        val store = store()
        val uri = store.save("a".byteInputStream())
        store.delete(uri)
        assertFalse(File(java.net.URI(uri)).exists())
        store.delete(null)
        store.delete("file:///nowhere/missing.jpg")
        store.delete("not a uri at all")
    }

    @Test
    fun `deleteOrphans keeps referenced files and removes the rest`() {
        val store = store()
        val kept = store.save("keep".byteInputStream())
        val dropped = store.save("drop".byteInputStream())
        store.deleteOrphans(setOf(kept))
        assertTrue(File(java.net.URI(kept)).exists())
        assertFalse(File(java.net.URI(dropped)).exists())
    }

    @Test
    fun `deleteOrphans on an empty store does nothing and does not throw`() {
        store().deleteOrphans(emptySet())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.BeerPhotoStoreTest"`
Expected: FAIL, unresolved reference `BeerPhotoStore`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/beertracker/data/BeerPhotoStore.kt`:

```kotlin
package com.beertracker.data

import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * Owns the photos the user attaches to their beers. Files live in app
 * private storage, so no storage permission is needed and the media scanner
 * never sees them, and they are referenced by `file://` URI in
 * `TriedBeer.photoUri`.
 *
 * Superseded files are NOT deleted eagerly when a photo is replaced or
 * removed in the form: the user can still abandon that edit, and the saved
 * row would then point at a file we had already deleted. Reclamation is
 * [deleteOrphans], run at app start, which only removes files no row
 * references. Deleting a beer is different, and does delete eagerly, because
 * the row is definitively gone by then.
 */
class BeerPhotoStore(root: File) {

    private val directory = File(root, DIRECTORY)

    /**
     * An empty file for the camera to write into. Created up front because
     * `ActivityResultContracts.TakePicture` needs a destination URI before
     * the picture is taken.
     */
    fun newPhotoFile(): File {
        directory.mkdirs()
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        file.createNewFile()
        return file
    }

    fun uriFor(file: File): String = file.toURI().toString()

    /** Copies a picked photo in, so deleting the original never blanks the beer. */
    fun save(input: InputStream): String {
        directory.mkdirs()
        val target = File(directory, "${UUID.randomUUID()}.jpg")
        val partial = File(directory, "${target.name}.part")
        input.use { source -> partial.outputStream().use { source.copyTo(it) } }
        partial.renameTo(target)
        return uriFor(target)
    }

    fun delete(uri: String?) {
        fileOf(uri)?.delete()
    }

    /** Removes every photo file that no saved beer points at. */
    fun deleteOrphans(referenced: Set<String>) {
        val keep = referenced.mapNotNull { fileOf(it)?.canonicalPath }.toSet()
        directory.listFiles()?.forEach { file ->
            if (file.canonicalPath !in keep) file.delete()
        }
    }

    /** A URI that is null, malformed, or not a file URI simply has no file. */
    private fun fileOf(uri: String?): File? = try {
        uri?.let { File(java.net.URI(it)) }
    } catch (error: IllegalArgumentException) {
        null
    }

    private companion object {
        const val DIRECTORY = "beer-photos"
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.BeerPhotoStoreTest"`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/beertracker/data/BeerPhotoStore.kt app/src/test/java/com/beertracker/BeerPhotoStoreTest.kt
git commit -m "[App] Store beer photos in app private files"
```

---

### Task 10: The shared thumbnail, in both lists

**Files:**
- Create: `app/src/main/java/com/beertracker/ui/components/BeerThumbnail.kt`
- Modify: `app/src/main/java/com/beertracker/ui/components/BeerListItem.kt`
- Modify: `app/src/main/java/com/beertracker/ui/catalog/CatalogBrowserScreen.kt` (the `AsyncImage` at lines 185 to 193)
- Test: `app/src/test/java/com/beertracker/ui/components/BeerThumbnailTest.kt`

**Interfaces:**
- Produces: `@Composable fun BeerThumbnail(model: String?, modifier: Modifier = Modifier, size: Dp = 48.dp)`. Every call site passes `model` by name.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/beertracker/ui/components/BeerThumbnailTest.kt`:

```kotlin
package com.beertracker.ui.components

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.beertracker.domain.TriedBeer
import com.beertracker.ui.theme.BeerTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BeerThumbnailTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders without an image and without crashing`() {
        composeRule.setContent { BeerTrackerTheme { BeerThumbnail(model = null) } }
        composeRule.waitForIdle()
    }

    @Test
    fun `a list row still shows its beer with no image attached`() {
        composeRule.setContent {
            BeerTrackerTheme {
                BeerListItem(
                    beer = TriedBeer(
                        id = "a",
                        name = "Cellar Reserve",
                        brewery = "Nordic Field",
                        type = "Lager",
                        alcoholPercent = null,
                        volumeMl = null,
                        price = null,
                        grade = 4,
                        tried = true,
                        note = "",
                        aftertaste = "",
                        goesWellWith = emptyList(),
                        buyAgain = false,
                        favourite = false,
                        dateAdded = 0,
                        catalogArticleNumber = null,
                        addedBy = null,
                        imageUrl = null,
                        photoUri = null,
                    ),
                    onClick = {},
                )
            }
        }
        composeRule.onNodeWithText("Cellar Reserve").assertIsDisplayed()
    }
}
```

Note: `BeerListItem` sets `clearAndSetSemantics` on its row, so the name is only findable via the row's content description in some configurations. If `onNodeWithText` fails for that reason, assert on `onNodeWithContentDescription` with the row description instead, matching what `ComposeUiSmokeTest` already does for this component.

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.ui.components.BeerThumbnailTest"`
Expected: FAIL, unresolved reference `BeerThumbnail`.

- [ ] **Step 3: Write the component**

Create `app/src/main/java/com/beertracker/ui/components/BeerThumbnail.kt`:

```kotlin
package com.beertracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * The product picture for one beer, at a fixed size in a fixed box, so
 * every row in every list keeps the same left edge whether or not its beer
 * has an image. A beer added by hand falls back to the app's beer can
 * glyph rather than an empty square.
 */
@Composable
fun BeerThumbnail(
    model: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            BeerCan(filled = false, height = size * 0.6f, alpha = 0.6f)
        } else {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size),
            )
        }
    }
}
```

- [ ] **Step 4: Use it in the main list**

In `app/src/main/java/com/beertracker/ui/components/BeerListItem.kt`, add as the first child of the `Row`, before `Column(Modifier.weight(1f))`:

```kotlin
        BeerThumbnail(model = beer.displayImageUrl)
```

- [ ] **Step 5: Use it in the catalog browser**

In `app/src/main/java/com/beertracker/ui/catalog/CatalogBrowserScreen.kt`, replace the `AsyncImage(...)` block with:

```kotlin
        BeerThumbnail(model = product.displayImageUrl)
```

and add `import com.beertracker.ui.components.BeerThumbnail`. Remove the now unused imports (`coil.compose.AsyncImage`, `androidx.compose.foundation.background`, `androidx.compose.ui.draw.clip`, `androidx.compose.ui.layout.ContentScale`, and `androidx.compose.foundation.layout.size` if nothing else in the file uses them). Let the compiler warnings guide you; do not leave unused imports behind.

- [ ] **Step 6: Run the whole suite**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS. Any existing test constructing a `TriedBeer` positionally will need `photoUri` added; that is expected fallout from Task 8 and should already be handled.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/beertracker/ui/components/BeerThumbnail.kt app/src/main/java/com/beertracker/ui/components/BeerListItem.kt app/src/main/java/com/beertracker/ui/catalog/CatalogBrowserScreen.kt app/src/test/java/com/beertracker/ui/components/BeerThumbnailTest.kt
git commit -m "[App] Show the beer picture on every list row"
```

---

### Task 11: The FileProvider and the photo field

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/file_paths.xml`
- Create: `app/src/main/java/com/beertracker/ui/components/BeerPhotoField.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `BeerPhotoStore` from Task 9.
- Produces: `@Composable fun BeerPhotoField(imageUrl: String?, hasPhoto: Boolean, enabled: Boolean, photoStore: BeerPhotoStore, onPhotoPicked: (String) -> Unit, onRemovePhoto: () -> Unit, onError: () -> Unit, modifier: Modifier = Modifier)`. The only call site passes every argument by name.

- [ ] **Step 1: Declare the provider**

In `app/src/main/AndroidManifest.xml`, add inside `<application>`, after the `</activity>` closing tag:

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.photos"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

- [ ] **Step 2: Declare the shared directory**

Create `app/src/main/res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <!-- Matches BeerPhotoStore.DIRECTORY. The camera needs a content URI to
         write into; nothing else in files/ is shared. -->
    <files-path
        name="beer-photos"
        path="beer-photos/" />
</paths>
```

- [ ] **Step 3: Add the strings**

In `app/src/main/res/values/strings.xml`, after `<string name="pairing_social">Social drink</string>`:

```xml
    <string name="beer_photo_section">Photo</string>
    <string name="take_photo">Take photo</string>
    <string name="choose_photo">Choose photo</string>
    <string name="remove_photo">Remove photo</string>
    <string name="photo_error">Could not save that photo. Try again.</string>
```

- [ ] **Step 4: Write the field**

Create `app/src/main/java/com/beertracker/ui/components/BeerPhotoField.kt`:

```kotlin
package com.beertracker.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.beertracker.R
import com.beertracker.data.BeerPhotoStore
import com.beertracker.ui.theme.BeerTrackerSpacing
import java.io.File

/**
 * The beer's picture on the add/edit form, plus the two ways to replace it
 * with one of your own. Camera writes straight into app private storage
 * through a FileProvider URI; the photo picker's result is copied in, so
 * deleting the original from the gallery can never blank the beer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BeerPhotoField(
    imageUrl: String?,
    hasPhoto: Boolean,
    enabled: Boolean,
    photoStore: BeerPhotoStore,
    onPhotoPicked: (String) -> Unit,
    onRemovePhoto: () -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val file = pendingCameraFile
        pendingCameraFile = null
        when {
            file == null -> Unit
            saved && file.length() > 0L -> onPhotoPicked(photoStore.uriFor(file))
            // A cancelled shot leaves the empty file we created behind.
            else -> file.delete()
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { picked: Uri? ->
        if (picked == null) return@rememberLauncherForActivityResult
        val stream = runCatching { context.contentResolver.openInputStream(picked) }.getOrNull()
        if (stream == null) {
            onError()
            return@rememberLauncherForActivityResult
        }
        runCatching { photoStore.save(stream) }
            .onSuccess(onPhotoPicked)
            .onFailure { onError() }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.small),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl == null) {
                BeerCan(filled = false, height = 72.dp, alpha = 0.6f)
            } else {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.small)) {
            OutlinedButton(
                enabled = enabled,
                onClick = {
                    val file = photoStore.newPhotoFile()
                    pendingCameraFile = file
                    cameraLauncher.launch(
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.photos",
                            file,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.take_photo))
            }
            OutlinedButton(
                enabled = enabled,
                onClick = {
                    pickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            ) {
                Text(stringResource(R.string.choose_photo))
            }
            if (hasPhoto) {
                TextButton(enabled = enabled, onClick = onRemovePhoto) {
                    Text(stringResource(R.string.remove_photo))
                }
            }
        }
    }
}
```

- [ ] **Step 5: Verify it compiles**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res/xml/file_paths.xml app/src/main/java/com/beertracker/ui/components/BeerPhotoField.kt app/src/main/res/values/strings.xml
git commit -m "[App] Add the beer photo field with camera and picker"
```

---

### Task 12: Wire the photo through the form, the container, and deletion

**Files:**
- Modify: `app/src/main/java/com/beertracker/ui/AddEditBeerViewModel.kt`
- Modify: `app/src/main/java/com/beertracker/ui/AddEditScreen.kt`
- Modify: `app/src/main/java/com/beertracker/data/RoomBeerRepository.kt`
- Modify: `app/src/main/java/com/beertracker/BeerApp.kt`
- Test: `app/src/test/java/com/beertracker/AddEditBeerViewModelTest.kt`, `app/src/test/java/com/beertracker/BeerDaoTest.kt`

**Interfaces:**
- Consumes: everything from Tasks 8, 9, and 11.
- Produces: the finished feature.

- [ ] **Step 1: Write the failing tests**

In `app/src/test/java/com/beertracker/AddEditBeerViewModelTest.kt`:

```kotlin
    @Test
    fun `a photo survives save and load`() = runTest {
        val repository = FakeBeerRepository()
        val vm = AddEditBeerViewModel(repository)
        vm.update { it.copy(name = "Punk IPA") }
        vm.setPhoto("file:///photos/a.jpg")
        vm.save()
        advanceUntilIdle()
        assertEquals("file:///photos/a.jpg", repository.beers().single().photoUri)
    }

    @Test
    fun `removing a photo clears the field and leaves the catalog image alone`() = runTest {
        val vm = AddEditBeerViewModel(FakeBeerRepository())
        vm.update { it.copy(name = "X", imageUrl = "https://cdn/x.jpg") }
        vm.setPhoto("file:///photos/a.jpg")
        assertEquals("file:///photos/a.jpg", vm.form.value.photoUri)
        vm.setPhoto(null)
        assertNull(vm.form.value.photoUri)
        assertEquals("https://cdn/x.jpg", vm.form.value.imageUrl)
    }

    @Test
    fun `attaching a photo counts as an unsaved change`() = runTest {
        val vm = AddEditBeerViewModel(FakeBeerRepository())
        vm.setPhoto("file:///photos/a.jpg")
        assertTrue(vm.form.value.hasUnsavedChanges)
    }
```

In `app/src/test/java/com/beertracker/BeerDaoTest.kt`, add a test that deleting a beer deletes its photo file, following the file's existing setup:

```kotlin
    @Test
    fun `deleting a beer deletes its photo file`() = runTest {
        val root = createTempDirectory().toFile()
        val photoStore = BeerPhotoStore(root)
        val uri = photoStore.save("photo".byteInputStream())
        val repository = RoomBeerRepository(database.beerDao(), photoStore)
        repository.addBeer(testBeer(id = "a").copy(photoUri = uri))
        repository.deleteBeer("a")
        assertFalse(File(java.net.URI(uri)).exists())
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.AddEditBeerViewModelTest" --tests "com.beertracker.BeerDaoTest"`
Expected: FAIL, unresolved reference `setPhoto`.

- [ ] **Step 3: Carry the photo through the form state**

In `app/src/main/java/com/beertracker/ui/AddEditBeerViewModel.kt`:

Add `val photoUri: String? = null,` to `BeerFormState` after `imageUrl`.

Add `photoUri = loaded.photoUri,` to the `loadedForm` built in `load`.

Add `photoUri = f.photoUri,` to the `TriedBeer(...)` built in `save`.

Add this function next to `setGrade`:

```kotlin
    /**
     * Attaches or clears the user's own photo. Clearing leaves imageUrl
     * alone, so the beer falls back to its catalog picture. The superseded
     * file is not deleted here: the user can still discard this edit, and
     * the saved row would then point at a file we had removed. Reclamation
     * is BeerPhotoStore.deleteOrphans at app start.
     */
    fun setPhoto(uri: String?) = update { it.copy(photoUri = uri) }
```

- [ ] **Step 4: Put the field on the form**

In `app/src/main/java/com/beertracker/ui/AddEditScreen.kt`:

Add `photoStore: BeerPhotoStore,` and `onSetPhoto: (String?) -> Unit,` to `BeerForm`'s parameters, and thread them from `AddEditScreen`, which reads the store from the application container:

```kotlin
    val photoStore = (LocalContext.current.applicationContext as BeerApp).container.beerPhotoStore
```

At the top of `BeerForm`'s `Column`, before `SectionHeader(stringResource(R.string.basics_section))`:

```kotlin
        SectionHeader(stringResource(R.string.beer_photo_section))
        BeerPhotoField(
            imageUrl = form.photoUri ?: form.imageUrl,
            hasPhoto = form.photoUri != null,
            enabled = enabled,
            photoStore = photoStore,
            onPhotoPicked = onSetPhoto,
            onRemovePhoto = { onSetPhoto(null) },
            onError = onPhotoError,
        )
```

Add an `onPhotoError: () -> Unit` parameter too, and in `AddEditScreen` wire it to the existing snackbar:

```kotlin
    val photoErrorMessage = stringResource(R.string.photo_error)
    val scope = rememberCoroutineScope()
```

with `onPhotoError = { scope.launch { snackbarHostState.showSnackbar(photoErrorMessage) } }`.

Add the needed imports: `androidx.compose.ui.platform.LocalContext`, `androidx.compose.runtime.rememberCoroutineScope`, `kotlinx.coroutines.launch`, `com.beertracker.BeerApp`, `com.beertracker.data.BeerPhotoStore`, `com.beertracker.ui.components.BeerPhotoField`.

- [ ] **Step 5: Delete the file when the beer goes**

In `app/src/main/java/com/beertracker/data/RoomBeerRepository.kt`:

```kotlin
class RoomBeerRepository(
    private val dao: BeerDao,
    private val photoStore: BeerPhotoStore? = null,
) : BeerRepository {

    ...

    /** The row is definitively gone, so its photo can go with it. */
    override suspend fun deleteBeer(id: String) {
        val photoUri = dao.getById(id)?.photoUri
        dao.deleteById(id)
        photoStore?.delete(photoUri)
    }
}
```

- [ ] **Step 6: Build the store and sweep orphans at start**

In `app/src/main/java/com/beertracker/BeerApp.kt`, in `AppContainer`:

```kotlin
    val beerPhotoStore = BeerPhotoStore(context.filesDir)
    val beerRepository: BeerRepository = RoomBeerRepository(db.beerDao(), beerPhotoStore)
```

(replacing the existing `beerRepository` line, and keeping `private val db` above it), and in `BeerApp.onCreate`'s launch block, after `importIfNeeded()`:

```kotlin
            deleteOrphanPhotos()
```

with:

```kotlin
    /**
     * Reclaims photo files no beer points at any more: replaced photos,
     * removed photos, and photos taken on an add form that was abandoned.
     * Doing it here rather than at the moment of replacement means an
     * abandoned edit can never delete a file the saved row still uses.
     */
    private suspend fun deleteOrphanPhotos() {
        try {
            val referenced = container.beerRepository.observeBeers().first()
                .mapNotNull { it.photoUri }
                .toSet()
            container.beerPhotoStore.deleteOrphans(referenced)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w(TAG, "Orphan photo sweep failed, files stay as they are", error)
        }
    }
```

Add `import com.beertracker.data.BeerPhotoStore`.

- [ ] **Step 7: Run the whole suite**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS.

- [ ] **Step 8: Build the debug APK**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/beertracker app/src/test/java/com/beertracker
git commit -m "[App] Let a beer carry a photo of your own"
```

---

## Appendix A: `PairingIcons.kt`

Generated from SVG prototypes that were rendered and visually checked at 40, 32, 24, and 18 pixels. Copy verbatim.

The 15 vector definitions are long and mechanical, so they are generated rather than typed. Task 2 also commits the generator and the prototype sheet under `scripts/pairing_icons/`, both because the artwork's provenance matters (these are ours, not Systembolaget's files) and because the icons will need tweaking after the user sees them on a real phone.

```bash
python scripts/pairing_icons/gen.py
```

writes `scripts/pairing_icons/PairingIcons.kt.txt`, whose contents become the top of `PairingIcons.kt`. `scripts/pairing_icons/icons.html` is the prototype sheet: open it in any browser to see all 15 at 40, 32, 24, and 18 pixels and as chips. To change an icon, edit the SVG path in `gen.py`, check it in the sheet, rerun the generator.

After the 15 `internal val Pairing*Icon` definitions, append:

```kotlin
/**
 * Exhaustive on purpose: a new [Pairing] cannot compile until it has an
 * icon and a label.
 */
internal fun pairingIcon(pairing: Pairing): ImageVector = when (pairing) {
    Pairing.PORK -> PairingPorkIcon
    Pairing.POULTRY -> PairingPoultryIcon
    Pairing.LAMB -> PairingLambIcon
    Pairing.BEEF -> PairingBeefIcon
    Pairing.GAME -> PairingGameIcon
    Pairing.FISH -> PairingFishIcon
    Pairing.SHELLFISH -> PairingShellfishIcon
    Pairing.VEGETABLES -> PairingVegetablesIcon
    Pairing.CHEESE -> PairingCheeseIcon
    Pairing.DESSERT -> PairingDessertIcon
    Pairing.SPICY -> PairingSpicyFoodIcon
    Pairing.ASIAN -> PairingAsianFoodIcon
    Pairing.BUFFET -> PairingBuffetIcon
    Pairing.APERITIF -> PairingAperitifIcon
    Pairing.SOCIAL -> PairingSocialDrinkIcon
}

internal fun pairingLabelRes(pairing: Pairing): Int = when (pairing) {
    Pairing.PORK -> R.string.pairing_pork
    Pairing.POULTRY -> R.string.pairing_poultry
    Pairing.LAMB -> R.string.pairing_lamb
    Pairing.BEEF -> R.string.pairing_beef
    Pairing.GAME -> R.string.pairing_game
    Pairing.FISH -> R.string.pairing_fish
    Pairing.SHELLFISH -> R.string.pairing_shellfish
    Pairing.VEGETABLES -> R.string.pairing_vegetables
    Pairing.CHEESE -> R.string.pairing_cheese
    Pairing.DESSERT -> R.string.pairing_dessert
    Pairing.SPICY -> R.string.pairing_spicy
    Pairing.ASIAN -> R.string.pairing_asian
    Pairing.BUFFET -> R.string.pairing_buffet
    Pairing.APERITIF -> R.string.pairing_aperitif
    Pairing.SOCIAL -> R.string.pairing_social
}

@Composable
internal fun PairingIcon(
    pairing: Pairing,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = pairingIcon(pairing),
        contentDescription = null,
        modifier = modifier.size(size),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
```
