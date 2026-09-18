# BeerTracker Phase 4: Firebase Sync and Invite-Code Pairing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Two phones share one beer list through a Firestore "cellar": one phone creates the cellar and gets an eight character invite code, the other joins with that code, and from then on every add, edit and delete on either phone reaches the other, online or after the connection returns.

**Architecture:** Room stays the source of truth on each phone. A `CellarSyncEngine` in `data/` pushes every local write to Firestore (through a `SyncingBeerRepository` decorator around the existing `RoomBeerRepository`) and applies Firestore listener changes back into Room, never echoing them out again. Firebase is reached only through the `CellarRemote` port defined in `domain/`, with one Firestore implementation and one "unavailable" stand-in for builds without a Firebase configuration file, so every piece of logic is JVM-tested against a fake. A new sync screen behind the overview's settings menu handles create, join, share and stop. Neither Room database changes.

**Tech Stack:** Existing stack (Kotlin 2.0.21, Compose BOM 2024.12.01 with Material 3 1.3.1, Room 2.6.1, Coil 2.7.0, Robolectric 4.14.1, JVM 17, minSdk 26, compile/target 35) plus Firebase BOM 34.19.0 (`firebase-auth`, `firebase-firestore`), the google-services Gradle plugin 4.5.0 applied only when `app/google-services.json` exists, and `kotlinx-coroutines-play-services` 1.9.0 for Task-to-coroutine bridging.

**Spec:** `docs/superpowers/specs/2026-09-19-firebase-sync-and-pairing-design.md`. The v1 design is `docs/superpowers/specs/2026-07-28-beertracker-v1-design.md` (build order step 4).

**Execution order: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, strictly one task at a time (two Gradle builds in one checkout collide).** Tasks 1, 2 and 3 are independent of each other; 4 depends on 1; 5 depends on 4; 6 depends on 2, 3, 4 and 5; 7 depends on 6 (its factory reads `app.container.syncEngine`); 8 depends on 7; 9 depends on 8; 10 is documentation and comes last.

## Global Constraints

- Work in the checkout this plan is executed from (a git worktree on a `claude/...` branch, or the main checkout at `C:\Users\SilvijaSubotic\PersonalDevelopment\BeerTracker`). Run every Gradle command from that checkout's root in PowerShell. Gradle does not work from Git Bash on this machine (it fails with "Unable to establish loopback connection"), so never run `gradlew` from a Bash shell.
- Run unit tests with `.\gradlew.bat testDebugUnitTest --console=plain`. The suite starts at 319 tests and must stay green after every task while it grows (roughly 385 by the end). Run a single class with `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.InviteCodesTest" --console=plain`. The full debug suite takes about 2 to 4 minutes; give Gradle a 10 minute timeout. Task 6 downloads the Firebase artifacts on its first build, which takes a few extra minutes and needs the network.
- Never run the bare `test` task: the release variant's Compose UI tests fail by design (the Compose test manifest is a debug-only dependency) and that failure is not yours to fix.
- No androidTest source set; every test runs on the JVM (plain JUnit or Robolectric). No device, no Firebase project and no Firebase emulator are available. The live Firebase path is verified by the user on their phones after the Firebase project exists.
- Commits are authored by the user's git identity only. Never add Claude as author or co-author. No `Co-Authored-By` trailers, ever.
- No em dashes or en dashes anywhere: not in code, comments, strings, commit messages, or docs. Use hyphens, commas, or rewrite the sentence.
- Commit message style: `[Scope] Message` where Scope is `App`, `Docs`, `Build`, or `CI`.
- Package `com.beertracker`. The only dependency changes allowed are the ones spelled out in Task 6.
- NEVER commit `app/google-services.json`. It is git-ignored from Task 6 on; before that it should not exist in the checkout at all. Always `git add` explicit paths, never `git add -A` or `git add .`.
- USER DATA SAFETY: neither `BeerDatabase` nor `CatalogDatabase` changes in this phase. Do not touch entities, DAOs, schemas or migrations. If a file `app/schemas/com.beertracker.data.BeerDatabase/5.json` ever appears, something went wrong: stop and undo.
- TDD for every logic task: write the failing test, watch it fail, implement, watch it pass. UI tasks are verified by Robolectric Compose tests in the house pattern (see `app/src/test/java/com/beertracker/ui/scan/ScanScreenTest.kt`: `@RunWith(RobolectricTestRunner::class)`, `@Config(application = Application::class, sdk = [35])`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, `createComposeRule()`, a `MainDispatcherRule` at order 0).
- Classes that call `android.util.Log` are tested under Robolectric (`@RunWith(RobolectricTestRunner::class)`, `@Config(application = Application::class, sdk = [35])`); a plain JUnit test would fail with "Method w in android.util.Log not mocked".
- New UI reuses the existing building blocks: `ui/components/` (`ErrorState`, `SectionHeader`) and theme tokens (`BeerTrackerSpacing`, `MaterialTheme.shapes`, `MaterialTheme.colorScheme`). No hardcoded colors.
- The user works on the repo in parallel with Claude sessions. Re-read a file immediately before editing it.

## User provisioning

Nothing is needed to build, test or merge this plan. The Firebase project, the `google-services.json` file and the `GOOGLE_SERVICES_JSON_BASE64` GitHub secret are created by the user afterwards, following `docs/firebase-setup.md` (written in Task 10). Until then the release workflow prints a warning and ships a build whose sync screen says sync is not set up.

---

### Task 1: Sync domain types and invite codes

The vocabulary every later task shares (`CellarSync.kt`, types only) and the invite code rules (`InviteCodes`), all pure Kotlin.

**Files:**
- Create: `app/src/main/java/com/beertracker/domain/InviteCodes.kt`
- Create: `app/src/main/java/com/beertracker/domain/CellarSync.kt`
- Test: `app/src/test/java/com/beertracker/InviteCodesTest.kt`

**Interfaces:**
- Consumes: `TriedBeer` (existing, `domain/TriedBeer.kt`).
- Produces, relied on by every later task:
  - `object InviteCodes { const val LENGTH = 8; const val ALPHABET: String; fun generate(random: Random = Random.Default): String; fun normalize(input: String): String?; fun format(code: String): String }`
  - `data class CellarMembership(val cellarId: String, val inviteCode: String)`
  - `data class CellarInfo(val inviteCode: String, val memberCount: Int)`
  - `sealed interface RemoteChange { data class Upsert(val beer: TriedBeer); data class Remove(val beerId: String) }`
  - `data class RemoteBeersUpdate(val changes: List<RemoteChange>, val fromServer: Boolean, val hasPendingWrites: Boolean)`
  - `sealed class SyncException(message: String) : Exception(message)` with `Unavailable`, `Offline`, `InvalidCode`, `UnknownCode`, `Failed(cause: Throwable)`
  - `interface CellarRemote` (isAvailable, currentUserId, signIn, createCellar, joinCellar, observeCellar, observeBeers, putBeer, deleteBeer)
  - `interface SyncMembershipStore { val membership: StateFlow<CellarMembership?>; fun save(membership: CellarMembership); fun clear() }`
  - `sealed interface SyncStatus { Unavailable; NotPaired; data class Paired(inviteCode: String, memberCount: Int?, lastSyncedUtc: Long?, hasPendingUploads: Boolean) }`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/beertracker/InviteCodesTest.kt`:

```kotlin
package com.beertracker

import com.beertracker.domain.InviteCodes
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteCodesTest {

    @Test
    fun `generated codes have eight characters from the alphabet`() {
        repeat(50) {
            val code = InviteCodes.generate()
            assertEquals(InviteCodes.LENGTH, code.length)
            assertTrue(code, code.all { it in InviteCodes.ALPHABET })
        }
    }

    @Test
    fun `the alphabet has no look-alike characters`() {
        for (c in "0O1IL") {
            assertTrue("$c must not be in the alphabet", c !in InviteCodes.ALPHABET)
        }
    }

    @Test
    fun `generation uses the given random source`() {
        assertEquals(InviteCodes.generate(Random(7)), InviteCodes.generate(Random(7)))
    }

    @Test
    fun `normalize upper-cases and drops spaces and hyphens`() {
        assertEquals("ABCDEFGH", InviteCodes.normalize(" abcd-efgh "))
        assertEquals("ABCD2345", InviteCodes.normalize("abcd 2345"))
    }

    @Test
    fun `normalize rejects the wrong length`() {
        assertNull(InviteCodes.normalize("ABCDEFG"))
        assertNull(InviteCodes.normalize("ABCDEFGHJ"))
        assertNull(InviteCodes.normalize(""))
    }

    @Test
    fun `normalize rejects characters outside the alphabet`() {
        assertNull(InviteCodes.normalize("ABCDEFG0"))
        assertNull(InviteCodes.normalize("ABCDEFGI"))
        assertNull(InviteCodes.normalize("ABCD.FGH"))
    }

    @Test
    fun `format groups the code in two halves`() {
        assertEquals("ABCD-EFGH", InviteCodes.format("ABCDEFGH"))
    }

    @Test
    fun `format leaves an odd value alone`() {
        assertEquals("ABC", InviteCodes.format("ABC"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.InviteCodesTest" --console=plain`
Expected: compilation FAILS with "Unresolved reference: InviteCodes".

- [ ] **Step 3: Write InviteCodes**

`app/src/main/java/com/beertracker/domain/InviteCodes.kt`:

```kotlin
package com.beertracker.domain

import kotlin.random.Random

/**
 * The short code one phone shows and the other types to share a cellar.
 * The alphabet leaves out 0, O, 1, I and L, which look alike on a screen
 * and in handwriting. Thirty-one symbols to the power of eight is about
 * 850 billion codes.
 */
object InviteCodes {
    const val LENGTH = 8
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    fun generate(random: Random = Random.Default): String = buildString(LENGTH) {
        repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    /**
     * What the user typed, cleaned up: upper-cased, spaces and hyphens
     * removed. Null unless exactly LENGTH characters of ALPHABET remain.
     */
    fun normalize(input: String): String? {
        val cleaned = input.uppercase().filterNot { it.isWhitespace() || it == '-' }
        return cleaned.takeIf { it.length == LENGTH && it.all { c -> c in ALPHABET } }
    }

    /** "ABCDEFGH" becomes "ABCD-EFGH"; anything not LENGTH long is returned as is. */
    fun format(code: String): String =
        if (code.length == LENGTH) code.substring(0, LENGTH / 2) + "-" + code.substring(LENGTH / 2) else code
}
```

- [ ] **Step 4: Write the shared sync types**

`app/src/main/java/com/beertracker/domain/CellarSync.kt`:

```kotlin
package com.beertracker.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The shared cellar this phone belongs to, as remembered on the phone. */
data class CellarMembership(val cellarId: String, val inviteCode: String)

/** What the cellar record says: its code and how many phones have joined. */
data class CellarInfo(val inviteCode: String, val memberCount: Int)

sealed interface RemoteChange {
    data class Upsert(val beer: TriedBeer) : RemoteChange
    data class Remove(val beerId: String) : RemoteChange
}

/** One listener callback: the document changes since the previous one. */
data class RemoteBeersUpdate(
    val changes: List<RemoteChange>,
    /** False while the snapshot comes from the local cache only. */
    val fromServer: Boolean,
    /** True while this phone has writes the server has not confirmed. */
    val hasPendingWrites: Boolean,
)

sealed class SyncException(message: String) : Exception(message) {
    /** The build has no Firebase configuration. */
    class Unavailable : SyncException("Sync is not configured in this build")
    class Offline : SyncException("Could not reach the server")
    class InvalidCode : SyncException("The code is not eight letters and digits")
    class UnknownCode : SyncException("No cellar has this code")
    class Failed(cause: Throwable) : SyncException(cause.message ?: "Sync failed") {
        init {
            initCause(cause)
        }
    }
}

/**
 * Everything the app needs from Firebase, so the sync engine and the UI
 * never see a Firebase type and can be tested against a fake.
 */
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

    /** Enqueues and returns at once; the remote delivers it when it can. */
    fun putBeer(cellarId: String, beer: TriedBeer)

    /** Enqueues and returns at once; the remote delivers it when it can. */
    fun deleteBeer(cellarId: String, beerId: String)
}

interface SyncMembershipStore {
    val membership: StateFlow<CellarMembership?>
    fun save(membership: CellarMembership)
    fun clear()
}

sealed interface SyncStatus {
    /** The build has no Firebase configuration. */
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

- [ ] **Step 5: Run the test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.InviteCodesTest" --console=plain`
Expected: BUILD SUCCESSFUL, 8 tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/beertracker/domain/InviteCodes.kt app/src/main/java/com/beertracker/domain/CellarSync.kt app/src/test/java/com/beertracker/InviteCodesTest.kt
git commit -m "[App] Add the sync domain types and the invite code rules"
```

---

### Task 2: RemoteBeerCodec

Translates a `TriedBeer` to and from the field map stored in a Firestore document. Pure Kotlin; Firestore gives numbers back as `Long` or `Double`, which is why the reading side accepts any `Number`.

**Files:**
- Create: `app/src/main/java/com/beertracker/domain/RemoteBeerCodec.kt`
- Test: `app/src/test/java/com/beertracker/RemoteBeerCodecTest.kt`

**Interfaces:**
- Consumes: `TriedBeer` (existing), `beer(...)` builder from `app/src/test/java/com/beertracker/TestData.kt` (existing; it has no `catalogArticleNumber`, `addedBy` or `photoUri` parameters, so tests use `.copy(...)` for those).
- Produces, relied on by Task 6:
  - `object RemoteBeerCodec { fun toFields(beer: TriedBeer): Map<String, Any?>; fun fromFields(id: String, fields: Map<String, Any?>): TriedBeer? }`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/beertracker/RemoteBeerCodecTest.kt`:

```kotlin
package com.beertracker

import com.beertracker.domain.RemoteBeerCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteBeerCodecTest {

    private val full = beer(
        id = "b1", name = "Punk IPA", brewery = "BrewDog", type = "IPA",
        alcoholPercent = 5.6, volumeMl = 330, price = 29.5, grade = 4, tried = true,
        note = "hoppy", aftertaste = "bitter", goesWellWith = listOf("Beef", "Fish"),
        buyAgain = true, favourite = true, dateAdded = 12345L, imageUrl = "https://cdn/x.jpg",
    ).copy(catalogArticleNumber = "1324515", addedBy = "user-a", photoUri = "file:///photos/p.jpg")

    @Test
    fun `a full beer round-trips without its id and photo`() {
        val fields = RemoteBeerCodec.toFields(full)

        assertFalse(fields.containsKey("id"))
        assertFalse(fields.containsKey("photoUri"))
        assertEquals(full.copy(photoUri = null), RemoteBeerCodec.fromFields("b1", fields))
    }

    @Test
    fun `absent values are written as explicit nulls`() {
        val fields = RemoteBeerCodec.toFields(
            beer(id = "b2", alcoholPercent = null, volumeMl = null, price = null, grade = null, imageUrl = null),
        )

        for (key in listOf("alcoholPercent", "volumeMl", "price", "grade", "catalogArticleNumber", "addedBy", "imageUrl")) {
            assertTrue("$key must be present", fields.containsKey(key))
            assertNull(key, fields[key])
        }
    }

    @Test
    fun `whole numbers are read back from Long and from Double`() {
        val fromLong = RemoteBeerCodec.fromFields(
            "b", mapOf("name" to "A", "tried" to true, "volumeMl" to 330L, "grade" to 4L, "dateAdded" to 99L),
        )
        val fromDouble = RemoteBeerCodec.fromFields(
            "b", mapOf("name" to "A", "tried" to true, "volumeMl" to 330.0, "grade" to 4.0, "dateAdded" to 99.0),
        )

        assertEquals(330, fromLong?.volumeMl)
        assertEquals(4, fromLong?.grade)
        assertEquals(99L, fromLong?.dateAdded)
        assertEquals(fromLong, fromDouble)
    }

    @Test
    fun `decimals are read back from any number`() {
        val beer = RemoteBeerCodec.fromFields("b", mapOf("name" to "A", "alcoholPercent" to 5L, "price" to 29.5))

        assertEquals(5.0, beer?.alcoholPercent)
        assertEquals(29.5, beer?.price)
    }

    @Test
    fun `missing text reads as empty, missing flags as false, missing date as zero`() {
        val beer = RemoteBeerCodec.fromFields("b", mapOf("name" to "A"))!!

        assertEquals("", beer.brewery)
        assertEquals("", beer.type)
        assertEquals("", beer.note)
        assertEquals("", beer.aftertaste)
        assertEquals(emptyList<String>(), beer.goesWellWith)
        assertFalse(beer.tried)
        assertFalse(beer.buyAgain)
        assertFalse(beer.favourite)
        assertEquals(0L, beer.dateAdded)
        assertNull(beer.grade)
        assertNull(beer.photoUri)
    }

    @Test
    fun `a record without a usable name is rejected`() {
        assertNull(RemoteBeerCodec.fromFields("b", emptyMap()))
        assertNull(RemoteBeerCodec.fromFields("b", mapOf("name" to "   ")))
        assertNull(RemoteBeerCodec.fromFields("b", mapOf("name" to 42L)))
    }

    @Test
    fun `an illegal grade or a graded untried beer is rejected`() {
        assertNull(RemoteBeerCodec.fromFields("b", mapOf("name" to "A", "tried" to true, "grade" to 7L)))
        assertNull(RemoteBeerCodec.fromFields("b", mapOf("name" to "A", "tried" to false, "grade" to 3L)))
    }

    @Test
    fun `non-string pairing entries are dropped`() {
        val beer = RemoteBeerCodec.fromFields(
            "b", mapOf("name" to "A", "goesWellWith" to listOf("Beef", 3L, null, "Fish")),
        )

        assertEquals(listOf("Beef", "Fish"), beer?.goesWellWith)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.RemoteBeerCodecTest" --console=plain`
Expected: compilation FAILS with "Unresolved reference: RemoteBeerCodec".

- [ ] **Step 3: Write the codec**

`app/src/main/java/com/beertracker/domain/RemoteBeerCodec.kt`:

```kotlin
package com.beertracker.domain

/**
 * The shape of a beer inside the shared cellar: every TriedBeer field except
 * the id (which is the document id) and photoUri (a file on one phone).
 * Absent values are written as explicit nulls, so a whole-record write
 * clears a value the other phone had set.
 */
object RemoteBeerCodec {

    fun toFields(beer: TriedBeer): Map<String, Any?> = mapOf(
        "name" to beer.name,
        "brewery" to beer.brewery,
        "type" to beer.type,
        "alcoholPercent" to beer.alcoholPercent,
        "volumeMl" to beer.volumeMl,
        "price" to beer.price,
        "grade" to beer.grade,
        "tried" to beer.tried,
        "note" to beer.note,
        "aftertaste" to beer.aftertaste,
        "goesWellWith" to beer.goesWellWith,
        "buyAgain" to beer.buyAgain,
        "favourite" to beer.favourite,
        "dateAdded" to beer.dateAdded,
        "catalogArticleNumber" to beer.catalogArticleNumber,
        "addedBy" to beer.addedBy,
        "imageUrl" to beer.imageUrl,
    )

    /**
     * Null when the record cannot become a legal TriedBeer: no usable name,
     * a grade outside 1 to 5, or a grade on a beer that is not tried. Numbers
     * are accepted as any Number because Firestore returns Long or Double.
     */
    fun fromFields(id: String, fields: Map<String, Any?>): TriedBeer? {
        val name = fields.text("name")?.takeIf { it.isNotBlank() } ?: return null
        return try {
            TriedBeer(
                id = id,
                name = name,
                brewery = fields.text("brewery") ?: "",
                type = fields.text("type") ?: "",
                alcoholPercent = fields.decimal("alcoholPercent"),
                volumeMl = fields.whole("volumeMl")?.toInt(),
                price = fields.decimal("price"),
                grade = fields.whole("grade")?.toInt(),
                tried = fields.flag("tried"),
                note = fields.text("note") ?: "",
                aftertaste = fields.text("aftertaste") ?: "",
                goesWellWith = fields.strings("goesWellWith"),
                buyAgain = fields.flag("buyAgain"),
                favourite = fields.flag("favourite"),
                dateAdded = fields.whole("dateAdded") ?: 0L,
                catalogArticleNumber = fields.text("catalogArticleNumber"),
                addedBy = fields.text("addedBy"),
                imageUrl = fields.text("imageUrl"),
                photoUri = null,
            )
        } catch (error: IllegalArgumentException) {
            null
        }
    }

    private fun Map<String, Any?>.text(key: String): String? = this[key] as? String

    private fun Map<String, Any?>.whole(key: String): Long? = (this[key] as? Number)?.toLong()

    private fun Map<String, Any?>.decimal(key: String): Double? = (this[key] as? Number)?.toDouble()

    private fun Map<String, Any?>.flag(key: String): Boolean = this[key] as? Boolean ?: false

    private fun Map<String, Any?>.strings(key: String): List<String> =
        (this[key] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.RemoteBeerCodecTest" --console=plain`
Expected: BUILD SUCCESSFUL, 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/beertracker/domain/RemoteBeerCodec.kt app/src/test/java/com/beertracker/RemoteBeerCodecTest.kt
git commit -m "[App] Translate beers to and from their shared record shape"
```

---

### Task 3: PrefsSyncMembershipStore

Remembers which cellar this phone belongs to, in a SharedPreferences file of its own, following `PrefsSettingsRepository`.

**Files:**
- Create: `app/src/main/java/com/beertracker/data/PrefsSyncMembershipStore.kt`
- Test: `app/src/test/java/com/beertracker/PrefsSyncMembershipStoreTest.kt`

**Interfaces:**
- Consumes: `SyncMembershipStore`, `CellarMembership` (Task 1).
- Produces, relied on by Task 6: `class PrefsSyncMembershipStore(context: Context) : SyncMembershipStore`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/beertracker/PrefsSyncMembershipStoreTest.kt`:

```kotlin
package com.beertracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.beertracker.data.PrefsSyncMembershipStore
import com.beertracker.domain.CellarMembership
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrefsSyncMembershipStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `starts with no membership`() {
        assertNull(PrefsSyncMembershipStore(context).membership.value)
    }

    @Test
    fun `keeps a saved membership across instances`() {
        PrefsSyncMembershipStore(context).save(CellarMembership("cellar-1", "ABCDEFGH"))

        assertEquals(
            CellarMembership("cellar-1", "ABCDEFGH"),
            PrefsSyncMembershipStore(context).membership.value,
        )
    }

    @Test
    fun `clear forgets the membership for this and later instances`() {
        val store = PrefsSyncMembershipStore(context)
        store.save(CellarMembership("cellar-1", "ABCDEFGH"))

        store.clear()

        assertNull(store.membership.value)
        assertNull(PrefsSyncMembershipStore(context).membership.value)
    }

    @Test
    fun `a half-written pair reads as not paired`() {
        context.getSharedPreferences("sync", Context.MODE_PRIVATE)
            .edit()
            .putString("cellar_id", "cellar-1")
            .commit()

        assertNull(PrefsSyncMembershipStore(context).membership.value)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.PrefsSyncMembershipStoreTest" --console=plain`
Expected: compilation FAILS with "Unresolved reference: PrefsSyncMembershipStore".

- [ ] **Step 3: Write the store**

`app/src/main/java/com/beertracker/data/PrefsSyncMembershipStore.kt`:

```kotlin
package com.beertracker.data

import android.content.Context
import com.beertracker.domain.CellarMembership
import com.beertracker.domain.SyncMembershipStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SharedPreferences-backed, two small strings, so no DataStore. Both keys
 * present means paired; anything else means not paired.
 */
class PrefsSyncMembershipStore(context: Context) : SyncMembershipStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _membership = MutableStateFlow(load())
    override val membership: StateFlow<CellarMembership?> = _membership.asStateFlow()

    override fun save(membership: CellarMembership) {
        prefs.edit()
            .putString(KEY_CELLAR_ID, membership.cellarId)
            .putString(KEY_INVITE_CODE, membership.inviteCode)
            .apply()
        _membership.value = membership
    }

    override fun clear() {
        prefs.edit()
            .remove(KEY_CELLAR_ID)
            .remove(KEY_INVITE_CODE)
            .apply()
        _membership.value = null
    }

    private fun load(): CellarMembership? {
        val cellarId = prefs.getString(KEY_CELLAR_ID, null) ?: return null
        val inviteCode = prefs.getString(KEY_INVITE_CODE, null) ?: return null
        return CellarMembership(cellarId, inviteCode)
    }

    private companion object {
        const val PREFS_NAME = "sync"
        const val KEY_CELLAR_ID = "cellar_id"
        const val KEY_INVITE_CODE = "invite_code"
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.PrefsSyncMembershipStoreTest" --console=plain`
Expected: BUILD SUCCESSFUL, 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/beertracker/data/PrefsSyncMembershipStore.kt app/src/test/java/com/beertracker/PrefsSyncMembershipStoreTest.kt
git commit -m "[App] Remember which shared cellar this phone belongs to"
```

---

### Task 4: CellarSyncEngine

The heart of the phase: follows the stored membership, applies remote changes into the Room repository, pushes local changes out, and runs create, join and stop. Tested entirely against `FakeBeerRepository` (existing) plus two new fakes.

**Files:**
- Create: `app/src/main/java/com/beertracker/data/CellarSyncEngine.kt`
- Create: `app/src/test/java/com/beertracker/FakeCellarRemote.kt`
- Create: `app/src/test/java/com/beertracker/FakeSyncMembershipStore.kt`
- Test: `app/src/test/java/com/beertracker/CellarSyncEngineTest.kt`

**Interfaces:**
- Consumes: everything in `domain/CellarSync.kt` and `InviteCodes` (Task 1), `BeerRepository` and `TriedBeer` (existing), `FakeBeerRepository` and `beer(...)` (existing test sources).
- Produces, relied on by Tasks 5, 6 and 7:
  - `class CellarSyncEngine(local: BeerRepository, remote: CellarRemote, membershipStore: SyncMembershipStore, scope: CoroutineScope, clock: () -> Long = System::currentTimeMillis, random: Random = Random.Default)` with `val status: StateFlow<SyncStatus>`, `val pairedUserId: String?`, `fun start()`, `suspend fun createCellar()`, `suspend fun joinCellar(input: String)`, `fun stopSyncing()`, `fun pushBeer(beer: TriedBeer)`, `fun pushDelete(beerId: String)`.
  - Test fakes `FakeCellarRemote(isAvailable: Boolean = true)` and `FakeSyncMembershipStore(initial: CellarMembership? = null)` in package `com.beertracker`.

- [ ] **Step 1: Write the fakes**

`app/src/test/java/com/beertracker/FakeSyncMembershipStore.kt`:

```kotlin
package com.beertracker

import com.beertracker.domain.CellarMembership
import com.beertracker.domain.SyncMembershipStore
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSyncMembershipStore(initial: CellarMembership? = null) : SyncMembershipStore {
    override val membership = MutableStateFlow(initial)

    override fun save(membership: CellarMembership) {
        this.membership.value = membership
    }

    override fun clear() {
        membership.value = null
    }
}
```

`app/src/test/java/com/beertracker/FakeCellarRemote.kt`:

```kotlin
package com.beertracker

import com.beertracker.domain.CellarInfo
import com.beertracker.domain.CellarRemote
import com.beertracker.domain.RemoteBeersUpdate
import com.beertracker.domain.SyncException
import com.beertracker.domain.TriedBeer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Stands in for Firebase. Cellars, invites and every put or delete are
 * recorded in memory; remote changes are fed in by the test through
 * [emitBeers] and [cellarInfo].
 */
class FakeCellarRemote(override var isAvailable: Boolean = true) : CellarRemote {

    var userId: String? = null
    /** Thrown by [signIn] when set; a SyncException or any other Throwable. */
    var signInError: Throwable? = null
    /** When set, [signIn] waits on it, so a test can observe the working state. */
    var signInGate: CompletableDeferred<Unit>? = null
    var signIns = 0
    var nextCellarId = "cellar-1"

    /** Cellar id to member ids. */
    val cellars = mutableMapOf<String, MutableList<String>>()
    /** Invite code to cellar id. */
    val invites = mutableMapOf<String, String>()
    val puts = mutableListOf<Pair<String, TriedBeer>>()
    val deletes = mutableListOf<Pair<String, String>>()

    var beersSubscriptions = 0
    /** How many upcoming [observeBeers] subscriptions fail straight away. */
    var beersFailuresLeft = 0
    val cellarInfo = MutableStateFlow<CellarInfo?>(null)
    private val beerUpdates = MutableSharedFlow<Pair<String, RemoteBeersUpdate>>()

    override val currentUserId: String?
        get() = userId

    override suspend fun signIn(): String {
        signIns++
        signInGate?.await()
        signInError?.let { throw it }
        return userId ?: "user-1".also { userId = it }
    }

    override suspend fun createCellar(inviteCode: String): String {
        val uid = userId ?: throw SyncException.Failed(IllegalStateException("not signed in"))
        val id = nextCellarId
        cellars[id] = mutableListOf(uid)
        invites[inviteCode] = id
        return id
    }

    override suspend fun joinCellar(inviteCode: String): String {
        val uid = userId ?: throw SyncException.Failed(IllegalStateException("not signed in"))
        val id = invites[inviteCode] ?: throw SyncException.UnknownCode()
        cellars.getValue(id).let { members -> if (uid !in members) members.add(uid) }
        return id
    }

    override fun observeCellar(cellarId: String): Flow<CellarInfo> = cellarInfo.filterNotNull()

    override fun observeBeers(cellarId: String): Flow<RemoteBeersUpdate> = flow {
        beersSubscriptions++
        if (beersFailuresLeft > 0) {
            beersFailuresLeft--
            throw IllegalStateException("listener denied")
        }
        emitAll(beerUpdates.filter { it.first == cellarId }.map { it.second })
    }

    override fun putBeer(cellarId: String, beer: TriedBeer) {
        puts += cellarId to beer
    }

    override fun deleteBeer(cellarId: String, beerId: String) {
        deletes += cellarId to beerId
    }

    /** Delivers one listener callback to whoever observes [cellarId]. */
    suspend fun emitBeers(cellarId: String, update: RemoteBeersUpdate) {
        beerUpdates.emit(cellarId to update)
    }
}
```

- [ ] **Step 2: Write the failing tests**

`app/src/test/java/com/beertracker/CellarSyncEngineTest.kt`:

```kotlin
package com.beertracker

import android.app.Application
import com.beertracker.data.CellarSyncEngine
import com.beertracker.domain.CellarInfo
import com.beertracker.domain.CellarMembership
import com.beertracker.domain.InviteCodes
import com.beertracker.domain.RemoteBeersUpdate
import com.beertracker.domain.RemoteChange
import com.beertracker.domain.SyncException
import com.beertracker.domain.SyncStatus
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CellarSyncEngineTest {

    /**
     * The engine's scope shares the test scheduler and runs unconfined, so
     * every emission is applied before the test's next line, and delay()
     * inside the engine moves with advanceTimeBy.
     */
    private class Harness(
        scope: TestScope,
        membership: CellarMembership? = null,
        available: Boolean = true,
        clock: () -> Long = { 1_000L },
    ) {
        val local = FakeBeerRepository()
        val remote = FakeCellarRemote(isAvailable = available)
        val store = FakeSyncMembershipStore(membership)
        val engine = CellarSyncEngine(
            local = local,
            remote = remote,
            membershipStore = store,
            scope = CoroutineScope(
                scope.backgroundScope.coroutineContext + UnconfinedTestDispatcher(scope.testScheduler),
            ),
            clock = clock,
            random = Random(1),
        )
    }

    private val paired = CellarMembership("cellar-1", "ABCDEFGH")

    private fun update(
        vararg changes: RemoteChange,
        fromServer: Boolean = true,
        pending: Boolean = false,
    ) = RemoteBeersUpdate(changes.toList(), fromServer = fromServer, hasPendingWrites = pending)

    @Test
    fun `status is unavailable when the remote is`() = runTest {
        val h = Harness(this, membership = paired, available = false)
        h.engine.start()
        assertEquals(SyncStatus.Unavailable, h.engine.status.value)
    }

    @Test
    fun `status is not paired without a membership`() = runTest {
        val h = Harness(this)
        h.engine.start()
        assertEquals(SyncStatus.NotPaired, h.engine.status.value)
    }

    @Test
    fun `createCellar signs in, creates, saves and uploads every local beer`() = runTest {
        val h = Harness(this)
        h.local.addBeer(beer(id = "a"))
        h.local.addBeer(beer(id = "b"))
        h.engine.start()

        h.engine.createCellar()

        assertEquals(1, h.remote.signIns)
        val membership = h.store.membership.value!!
        assertEquals("cellar-1", membership.cellarId)
        assertEquals(InviteCodes.generate(Random(1)), membership.inviteCode)
        assertEquals(listOf("user-1"), h.remote.cellars["cellar-1"])
        assertEquals("cellar-1", h.remote.invites[membership.inviteCode])
        assertEquals(setOf("a", "b"), h.remote.puts.map { it.second.id }.toSet())
        assertTrue(h.remote.puts.all { it.first == "cellar-1" })
        val status = h.engine.status.value as SyncStatus.Paired
        assertEquals(membership.inviteCode, status.inviteCode)
        assertNull(status.memberCount)
        assertNull(status.lastSyncedUtc)
        assertFalse(status.hasPendingUploads)
    }

    @Test
    fun `createCellar refuses when the remote is unavailable`() = runTest {
        val h = Harness(this, available = false)
        h.engine.start()

        try {
            h.engine.createCellar()
            fail("expected Unavailable")
        } catch (expected: SyncException.Unavailable) {
        }
        assertEquals(0, h.remote.signIns)
        assertNull(h.store.membership.value)
    }

    @Test
    fun `joinCellar with a malformed code fails before touching the remote`() = runTest {
        val h = Harness(this)
        h.engine.start()

        try {
            h.engine.joinCellar("ABC")
            fail("expected InvalidCode")
        } catch (expected: SyncException.InvalidCode) {
        }
        assertEquals(0, h.remote.signIns)
        assertEquals(SyncStatus.NotPaired, h.engine.status.value)
    }

    @Test
    fun `joinCellar with an unknown code leaves the phone not paired`() = runTest {
        val h = Harness(this)
        h.engine.start()

        try {
            h.engine.joinCellar("ABCD-EFGH")
            fail("expected UnknownCode")
        } catch (expected: SyncException.UnknownCode) {
        }
        assertNull(h.store.membership.value)
        assertEquals(SyncStatus.NotPaired, h.engine.status.value)
    }

    @Test
    fun `joinCellar with a good code joins, saves and uploads`() = runTest {
        val h = Harness(this)
        h.remote.cellars["cellar-9"] = mutableListOf("user-other")
        h.remote.invites["ABCDEFGH"] = "cellar-9"
        h.local.addBeer(beer(id = "a"))
        h.engine.start()

        h.engine.joinCellar(" abcd-efgh ")

        assertEquals(CellarMembership("cellar-9", "ABCDEFGH"), h.store.membership.value)
        assertEquals(listOf("user-other", "user-1"), h.remote.cellars["cellar-9"])
        assertEquals(listOf("cellar-9" to "a"), h.remote.puts.map { it.first to it.second.id })
        assertEquals("ABCDEFGH", (h.engine.status.value as SyncStatus.Paired).inviteCode)
    }

    @Test
    fun `a remote upsert of a new id adds the beer locally`() = runTest {
        val h = Harness(this, membership = paired)
        h.engine.start()

        h.remote.emitBeers("cellar-1", update(RemoteChange.Upsert(beer(id = "r1", name = "Remote"))))

        assertEquals("Remote", h.local.getBeer("r1")?.name)
    }

    @Test
    fun `a remote upsert of a known id updates it and keeps the local photo`() = runTest {
        val h = Harness(this, membership = paired)
        h.local.addBeer(beer(id = "a", grade = 3).copy(photoUri = "file:///p.jpg"))
        h.engine.start()

        h.remote.emitBeers("cellar-1", update(RemoteChange.Upsert(beer(id = "a", grade = 5))))

        val stored = h.local.getBeer("a")!!
        assertEquals(5, stored.grade)
        assertEquals("file:///p.jpg", stored.photoUri)
    }

    @Test
    fun `a remote remove deletes the beer locally`() = runTest {
        val h = Harness(this, membership = paired)
        h.local.addBeer(beer(id = "a"))
        h.engine.start()

        h.remote.emitBeers("cellar-1", update(RemoteChange.Remove("a")))

        assertNull(h.local.getBeer("a"))
    }

    @Test
    fun `remote changes are not pushed back`() = runTest {
        val h = Harness(this, membership = paired)
        h.engine.start()

        h.remote.emitBeers(
            "cellar-1",
            update(RemoteChange.Upsert(beer(id = "r1")), RemoteChange.Remove("x")),
        )

        assertTrue(h.remote.puts.isEmpty())
        assertTrue(h.remote.deletes.isEmpty())
    }

    @Test
    fun `pushBeer and pushDelete reach the remote only while paired`() = runTest {
        val h = Harness(this)
        h.engine.start()

        h.engine.pushBeer(beer(id = "a"))
        h.engine.pushDelete("a")
        assertTrue(h.remote.puts.isEmpty())
        assertTrue(h.remote.deletes.isEmpty())

        h.store.save(paired)
        h.engine.pushBeer(beer(id = "a"))
        h.engine.pushDelete("b")

        assertEquals(listOf("cellar-1" to "a"), h.remote.puts.map { it.first to it.second.id })
        assertEquals(listOf("cellar-1" to "b"), h.remote.deletes)
    }

    @Test
    fun `a server update stamps the sync time and mirrors pending writes`() = runTest {
        val h = Harness(this, membership = paired, clock = { 4_242L })
        h.engine.start()

        h.remote.emitBeers("cellar-1", update(fromServer = false, pending = true))
        var status = h.engine.status.value as SyncStatus.Paired
        assertNull(status.lastSyncedUtc)
        assertTrue(status.hasPendingUploads)

        h.remote.emitBeers("cellar-1", update(fromServer = true, pending = false))
        status = h.engine.status.value as SyncStatus.Paired
        assertEquals(4_242L, status.lastSyncedUtc)
        assertFalse(status.hasPendingUploads)
    }

    @Test
    fun `the cellar record's member count shows in the status`() = runTest {
        val h = Harness(this, membership = paired)
        h.engine.start()

        h.remote.cellarInfo.value = CellarInfo("ABCDEFGH", memberCount = 2)

        assertEquals(2, (h.engine.status.value as SyncStatus.Paired).memberCount)
    }

    @Test
    fun `pairedUserId is the remote user only while paired`() = runTest {
        val h = Harness(this)
        h.remote.userId = "user-1"
        h.engine.start()

        assertNull(h.engine.pairedUserId)
        h.store.save(paired)
        assertEquals("user-1", h.engine.pairedUserId)
    }

    @Test
    fun `stopSyncing forgets the membership and stops applying remote changes`() = runTest {
        val h = Harness(this, membership = paired)
        h.engine.start()

        h.engine.stopSyncing()
        h.remote.emitBeers("cellar-1", update(RemoteChange.Upsert(beer(id = "late"))))

        assertNull(h.store.membership.value)
        assertEquals(SyncStatus.NotPaired, h.engine.status.value)
        assertNull(h.local.getBeer("late"))
    }

    @Test
    fun `an engine started with a stored membership listens without user action`() = runTest {
        val h = Harness(this, membership = paired)

        h.engine.start()

        assertEquals(1, h.remote.beersSubscriptions)
        h.remote.emitBeers("cellar-1", update(RemoteChange.Upsert(beer(id = "r1"))))
        assertEquals(listOf("r1"), h.local.observeBeers().first().map { it.id })
    }

    @Test
    fun `start is idempotent`() = runTest {
        val h = Harness(this, membership = paired)

        h.engine.start()
        h.engine.start()

        assertEquals(1, h.remote.beersSubscriptions)
    }

    @Test
    fun `a failing listener restarts after thirty seconds`() = runTest {
        val h = Harness(this, membership = paired)
        h.remote.beersFailuresLeft = 1

        h.engine.start()
        assertEquals(1, h.remote.beersSubscriptions)
        assertTrue(h.engine.status.value is SyncStatus.Paired)

        advanceTimeBy(30_001)

        assertEquals(2, h.remote.beersSubscriptions)
        h.remote.emitBeers("cellar-1", update(RemoteChange.Upsert(beer(id = "r1"))))
        assertEquals("Beer r1", h.local.getBeer("r1")?.name)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.CellarSyncEngineTest" --console=plain`
Expected: compilation FAILS with "Unresolved reference: CellarSyncEngine".

- [ ] **Step 4: Write the engine**

`app/src/main/java/com/beertracker/data/CellarSyncEngine.kt`:

```kotlin
package com.beertracker.data

import android.util.Log
import com.beertracker.domain.BeerRepository
import com.beertracker.domain.CellarInfo
import com.beertracker.domain.CellarMembership
import com.beertracker.domain.CellarRemote
import com.beertracker.domain.InviteCodes
import com.beertracker.domain.RemoteChange
import com.beertracker.domain.SyncException
import com.beertracker.domain.SyncMembershipStore
import com.beertracker.domain.SyncStatus
import com.beertracker.domain.TriedBeer
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Keeps this phone's Room database and the shared Firestore cellar in step.
 *
 * Room stays the source of truth for every screen. Local writes arrive
 * through [pushBeer] and [pushDelete] (called by SyncingBeerRepository after
 * the Room write) and go straight to the remote, which queues them itself
 * while offline. Remote changes arrive through the remote's listener and
 * are applied to the Room repository directly, never through
 * SyncingBeerRepository, so nothing that came from Firestore is pushed back.
 */
class CellarSyncEngine(
    private val local: BeerRepository,
    private val remote: CellarRemote,
    private val membershipStore: SyncMembershipStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {
    /** What the listeners have learned since the current membership started. */
    private data class ListenerState(
        val cellarInfo: CellarInfo? = null,
        val lastSyncedUtc: Long? = null,
        val hasPendingUploads: Boolean = false,
    )

    private val listenerState = MutableStateFlow(ListenerState())
    private var job: Job? = null

    val status: StateFlow<SyncStatus> =
        combine(membershipStore.membership, listenerState) { membership, listener ->
            statusOf(membership, listener)
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            statusOf(membershipStore.membership.value, listenerState.value),
        )

    /** The anonymous user id while paired, else null; stamped onto addedBy. */
    val pairedUserId: String?
        get() = if (membershipStore.membership.value != null) remote.currentUserId else null

    /** Starts following the stored membership; safe to call more than once. */
    fun start() {
        if (job != null) return
        job = scope.launch {
            membershipStore.membership.collectLatest { membership ->
                listenerState.value = ListenerState()
                if (membership == null || !remote.isAvailable) return@collectLatest
                coroutineScope {
                    launch { followCellar(membership.cellarId) }
                    launch { followBeers(membership.cellarId) }
                }
            }
        }
    }

    suspend fun createCellar() {
        requireAvailable()
        remote.signIn()
        val inviteCode = InviteCodes.generate(random)
        val cellarId = remote.createCellar(inviteCode)
        pair(CellarMembership(cellarId, inviteCode))
    }

    suspend fun joinCellar(input: String) {
        requireAvailable()
        val inviteCode = InviteCodes.normalize(input) ?: throw SyncException.InvalidCode()
        remote.signIn()
        val cellarId = remote.joinCellar(inviteCode)
        pair(CellarMembership(cellarId, inviteCode))
    }

    /** Forgets the pairing on this phone only; the beers stay in Room. */
    fun stopSyncing() = membershipStore.clear()

    fun pushBeer(beer: TriedBeer) {
        val membership = membershipStore.membership.value ?: return
        remote.putBeer(membership.cellarId, beer)
    }

    fun pushDelete(beerId: String) {
        val membership = membershipStore.membership.value ?: return
        remote.deleteBeer(membership.cellarId, beerId)
    }

    /** Saving the membership starts the listeners; then everything already here goes up. */
    private suspend fun pair(membership: CellarMembership) {
        membershipStore.save(membership)
        local.observeBeers().first().forEach { remote.putBeer(membership.cellarId, it) }
    }

    private suspend fun followCellar(cellarId: String) {
        remote.observeCellar(cellarId)
            .retryAfterFailure("cellar")
            .collect { info -> listenerState.update { it.copy(cellarInfo = info) } }
    }

    private suspend fun followBeers(cellarId: String) {
        remote.observeBeers(cellarId)
            .retryAfterFailure("beers")
            .collect { update ->
                update.changes.forEach { change ->
                    try {
                        apply(change)
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        Log.w(TAG, "Could not apply a shared change, skipping it", error)
                    }
                }
                listenerState.update {
                    it.copy(
                        lastSyncedUtc = if (update.fromServer) clock() else it.lastSyncedUtc,
                        hasPendingUploads = update.hasPendingWrites,
                    )
                }
            }
    }

    private suspend fun apply(change: RemoteChange) {
        when (change) {
            is RemoteChange.Upsert -> {
                val existing = local.getBeer(change.beer.id)
                if (existing == null) {
                    local.addBeer(change.beer)
                } else {
                    // The photo lives only on this phone; the shared record never carries it.
                    local.updateBeer(change.beer.copy(photoUri = existing.photoUri))
                }
            }
            is RemoteChange.Remove -> local.deleteBeer(change.beerId)
        }
    }

    /**
     * A listener that fails (for example a rules change denying access) is
     * logged and restarted after a pause, rather than left dead until the
     * next app launch.
     */
    private fun <T> Flow<T>.retryAfterFailure(what: String): Flow<T> = retryWhen { error, _ ->
        if (error is CancellationException) throw error
        Log.w(TAG, "Sync listener for $what failed, retrying in ${RETRY_DELAY_MS / 1000} s", error)
        delay(RETRY_DELAY_MS)
        true
    }

    private fun requireAvailable() {
        if (!remote.isAvailable) throw SyncException.Unavailable()
    }

    private fun statusOf(membership: CellarMembership?, listener: ListenerState): SyncStatus = when {
        !remote.isAvailable -> SyncStatus.Unavailable
        membership == null -> SyncStatus.NotPaired
        else -> SyncStatus.Paired(
            inviteCode = membership.inviteCode,
            memberCount = listener.cellarInfo?.memberCount,
            lastSyncedUtc = listener.lastSyncedUtc,
            hasPendingUploads = listener.hasPendingUploads,
        )
    }

    private companion object {
        const val TAG = "CellarSyncEngine"
        const val RETRY_DELAY_MS = 30_000L
    }
}
```

The guard around `apply` is defensive (the fake local store cannot fail); it stops one bad Room write from killing the whole listener.

- [ ] **Step 5: Run the test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.CellarSyncEngineTest" --console=plain`
Expected: BUILD SUCCESSFUL, 19 tests pass.

If `a failing listener restarts after thirty seconds` fails because the second subscription never happens, check that the engine scope in the harness really uses `UnconfinedTestDispatcher(scope.testScheduler)` (the shared scheduler is what makes `advanceTimeBy` reach the engine's `delay`).

- [ ] **Step 6: Run the whole suite, then commit**

Run: `.\gradlew.bat testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL, 358 tests.

```bash
git add app/src/main/java/com/beertracker/data/CellarSyncEngine.kt app/src/test/java/com/beertracker/FakeCellarRemote.kt app/src/test/java/com/beertracker/FakeSyncMembershipStore.kt app/src/test/java/com/beertracker/CellarSyncEngineTest.kt
git commit -m "[App] Sync engine: mirror the shared cellar into Room and push local changes out"
```

---

### Task 5: SyncingBeerRepository

The decorator every screen will talk to: local write first, then a push through the engine.

**Files:**
- Create: `app/src/main/java/com/beertracker/data/SyncingBeerRepository.kt`
- Test: `app/src/test/java/com/beertracker/SyncingBeerRepositoryTest.kt`

**Interfaces:**
- Consumes: `CellarSyncEngine` (Task 4), `BeerRepository` (existing), the fakes from Task 4.
- Produces, relied on by Task 6: `class SyncingBeerRepository(local: BeerRepository, engine: CellarSyncEngine) : BeerRepository`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/beertracker/SyncingBeerRepositoryTest.kt`:

```kotlin
package com.beertracker

import android.app.Application
import com.beertracker.data.CellarSyncEngine
import com.beertracker.data.SyncingBeerRepository
import com.beertracker.domain.CellarMembership
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SyncingBeerRepositoryTest {

    private class Harness(scope: TestScope, membership: CellarMembership?) {
        val local = FakeBeerRepository()
        val remote = FakeCellarRemote().apply { userId = "user-1" }
        val engine = CellarSyncEngine(
            local = local,
            remote = remote,
            membershipStore = FakeSyncMembershipStore(membership),
            scope = CoroutineScope(
                scope.backgroundScope.coroutineContext + UnconfinedTestDispatcher(scope.testScheduler),
            ),
            random = Random(1),
        )
        val repository = SyncingBeerRepository(local, engine)
    }

    private val paired = CellarMembership("cellar-1", "ABCDEFGH")

    @Test
    fun `add stamps a missing addedBy with the paired user and pushes`() = runTest {
        val h = Harness(this, paired)

        h.repository.addBeer(beer(id = "a"))

        assertEquals("user-1", h.local.getBeer("a")?.addedBy)
        assertEquals("user-1", h.remote.puts.single().second.addedBy)
        assertEquals("cellar-1", h.remote.puts.single().first)
    }

    @Test
    fun `add keeps an existing addedBy`() = runTest {
        val h = Harness(this, paired)

        h.repository.addBeer(beer(id = "a").copy(addedBy = "someone"))

        assertEquals("someone", h.local.getBeer("a")?.addedBy)
        assertEquals("someone", h.remote.puts.single().second.addedBy)
    }

    @Test
    fun `add while not paired writes locally with a null addedBy and pushes nothing`() = runTest {
        val h = Harness(this, membership = null)

        h.repository.addBeer(beer(id = "a"))

        assertNull(h.local.getBeer("a")?.addedBy)
        assertTrue(h.remote.puts.isEmpty())
    }

    @Test
    fun `update writes locally and pushes the same beer`() = runTest {
        val h = Harness(this, paired)
        h.local.addBeer(beer(id = "a", grade = 3))

        h.repository.updateBeer(beer(id = "a", grade = 5))

        assertEquals(5, h.local.getBeer("a")?.grade)
        assertEquals(5, h.remote.puts.single().second.grade)
    }

    @Test
    fun `delete removes locally and pushes the delete`() = runTest {
        val h = Harness(this, paired)
        h.local.addBeer(beer(id = "a"))

        h.repository.deleteBeer("a")

        assertNull(h.local.getBeer("a"))
        assertEquals(listOf("cellar-1" to "a"), h.remote.deletes)
    }

    @Test
    fun `observe and get read straight from the local store`() = runTest {
        val h = Harness(this, paired)
        h.local.addBeer(beer(id = "a"))

        assertEquals(listOf("a"), h.repository.observeBeers().first().map { it.id })
        assertEquals("a", h.repository.getBeer("a")?.id)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.SyncingBeerRepositoryTest" --console=plain`
Expected: compilation FAILS with "Unresolved reference: SyncingBeerRepository".

- [ ] **Step 3: Write the repository**

`app/src/main/java/com/beertracker/data/SyncingBeerRepository.kt`:

```kotlin
package com.beertracker.data

import com.beertracker.domain.BeerRepository
import com.beertracker.domain.TriedBeer
import kotlinx.coroutines.flow.Flow

/**
 * The repository every screen uses once sync exists. Reads come straight
 * from the local (Room) repository. Writes go to Room first, so a save can
 * never be lost to a Firebase problem, and are then pushed through the
 * engine, which ignores them while the phone is not paired.
 */
class SyncingBeerRepository(
    private val local: BeerRepository,
    private val engine: CellarSyncEngine,
) : BeerRepository {

    override fun observeBeers(): Flow<List<TriedBeer>> = local.observeBeers()

    override suspend fun getBeer(id: String): TriedBeer? = local.getBeer(id)

    override suspend fun addBeer(beer: TriedBeer) {
        val stamped = if (beer.addedBy == null) beer.copy(addedBy = engine.pairedUserId) else beer
        local.addBeer(stamped)
        engine.pushBeer(stamped)
    }

    override suspend fun updateBeer(beer: TriedBeer) {
        local.updateBeer(beer)
        engine.pushBeer(beer)
    }

    override suspend fun deleteBeer(id: String) {
        local.deleteBeer(id)
        engine.pushDelete(id)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.SyncingBeerRepositoryTest" --console=plain`
Expected: BUILD SUCCESSFUL, 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/beertracker/data/SyncingBeerRepository.kt app/src/test/java/com/beertracker/SyncingBeerRepositoryTest.kt
git commit -m "[App] Route every beer write through the sync engine"
```

---

### Task 6: Firebase dependencies, optional config, Firestore remote, rules, CI and app wiring

Two commits: first the build side (`[Build]`), then the Firestore implementation of `CellarRemote` plus wiring the engine into `AppContainer` (`[App]`). There is no JVM test for the Firestore class (no emulator, no project); it stays a thin adapter over `RemoteBeerCodec`, and the compile plus the existing suite are the verification. Nothing in the test sources constructs `AppContainer`.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (root)
- Modify: `app/build.gradle.kts`
- Modify: `.gitignore`
- Modify: `.github/workflows/release.yml`
- Create: `firebase/firestore.rules`
- Create: `app/src/main/java/com/beertracker/data/FirestoreCellarRemote.kt`
- Modify: `app/src/main/java/com/beertracker/BeerApp.kt`

**Interfaces:**
- Consumes: `CellarRemote`, `SyncException`, `RemoteChange`, `RemoteBeersUpdate`, `CellarInfo` (Task 1), `RemoteBeerCodec` (Task 2), `PrefsSyncMembershipStore` (Task 3), `CellarSyncEngine` (Task 4), `SyncingBeerRepository` (Task 5).
- Produces, relied on by Tasks 7 and 9:
  - `class FirestoreCellarRemote(auth: FirebaseAuth, firestore: FirebaseFirestore) : CellarRemote` with `companion fun create(context: Context): CellarRemote`.
  - `object UnavailableCellarRemote : CellarRemote`.
  - `AppContainer(context: Context, scope: CoroutineScope)` gains `val cellarRemote: CellarRemote`, `val syncMembershipStore: SyncMembershipStore`, `val syncEngine: CellarSyncEngine`; `beerRepository` becomes the `SyncingBeerRepository`. `BeerApp.onCreate` calls `container.syncEngine.start()`.

- [ ] **Step 1: Add the versions, libraries and plugin to the catalog**

In `gradle/libs.versions.toml`, append to `[versions]` (after `coil = "2.7.0"`):

```toml
firebaseBom = "34.19.0"
googleServices = "4.5.0"
```

Append to `[libraries]` (after the `coil-compose` line):

```toml
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-auth = { group = "com.google.firebase", name = "firebase-auth" }
firebase-firestore = { group = "com.google.firebase", name = "firebase-firestore" }
kotlinx-coroutines-play-services = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-play-services", version.ref = "coroutines" }
```

Append to `[plugins]`:

```toml
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

- [ ] **Step 2: Put the plugin on the classpath without applying it**

Root `build.gradle.kts` becomes:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
}
```

- [ ] **Step 3: Dependencies and the conditional plugin in the app module**

In `app/build.gradle.kts`, add after `implementation(libs.androidx.room.ktx)`:

```kotlin
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)
```

And insert this block between the closing brace of `android { ... }` and the `ksp { ... }` block:

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

- [ ] **Step 4: Ignore the config file**

Append to `.gitignore`:

```
# Firebase config, kept out of the public repository (see docs/firebase-setup.md)
app/google-services.json
```

- [ ] **Step 5: Restore the config in CI when the secret exists**

In `.github/workflows/release.yml`, insert this step directly after the "Set up Gradle" step and before "Calculate build version":

```yaml
      - name: Restore Firebase config
        env:
          GOOGLE_SERVICES_JSON_BASE64: ${{ secrets.GOOGLE_SERVICES_JSON_BASE64 }}
        shell: bash
        run: |
          if [[ -z "$GOOGLE_SERVICES_JSON_BASE64" ]]; then
            echo "::warning::GOOGLE_SERVICES_JSON_BASE64 is not configured; this build ships without Firebase sync"
            exit 0
          fi
          printf '%s' "$GOOGLE_SERVICES_JSON_BASE64" | base64 --decode > app/google-services.json
          echo "Restored app/google-services.json"
```

- [ ] **Step 6: Version the security rules**

Create `firebase/firestore.rules` with exactly this content (the user pastes it into the Firebase console):

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

- [ ] **Step 7: Build to make sure the dependencies resolve**

Run: `.\gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL, with the line `app/google-services.json not found: building without Firebase sync` in the output. The first run downloads the Firebase artifacts.

- [ ] **Step 8: Commit the build side**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts .gitignore .github/workflows/release.yml firebase/firestore.rules
git commit -m "[Build] Add Firebase sync dependencies, the optional google-services config and the Firestore rules"
```

- [ ] **Step 9: Write the Firestore remote and the unavailable stand-in**

`app/src/main/java/com/beertracker/data/FirestoreCellarRemote.kt`:

```kotlin
package com.beertracker.data

import android.content.Context
import android.util.Log
import com.beertracker.domain.CellarInfo
import com.beertracker.domain.CellarRemote
import com.beertracker.domain.RemoteBeerCodec
import com.beertracker.domain.RemoteBeersUpdate
import com.beertracker.domain.RemoteChange
import com.beertracker.domain.SyncException
import com.beertracker.domain.TriedBeer
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * The Firebase side of sync. Firestore's own offline queue carries every
 * put and delete, so those return at once; only pairing waits for the
 * server, with a timeout, because a queued write that never completes
 * means there is no server to talk to.
 */
class FirestoreCellarRemote(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : CellarRemote {

    override val isAvailable: Boolean = true

    override val currentUserId: String?
        get() = auth.currentUser?.uid

    override suspend fun signIn(): String {
        auth.currentUser?.let { return it.uid }
        val result = try {
            auth.signInAnonymously().await()
        } catch (error: FirebaseNetworkException) {
            throw SyncException.Offline()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw SyncException.Failed(error)
        }
        return result.user?.uid
            ?: throw SyncException.Failed(IllegalStateException("Anonymous sign-in returned no user"))
    }

    override suspend fun createCellar(inviteCode: String): String {
        val uid = requireUser()
        val cellar = firestore.collection(CELLARS).document()
        val batch = firestore.batch()
        batch.set(
            cellar,
            mapOf(
                FIELD_INVITE_CODE to inviteCode,
                FIELD_MEMBERS to listOf(uid),
                FIELD_CREATED_AT to FieldValue.serverTimestamp(),
            ),
        )
        batch.set(
            firestore.collection(INVITES).document(inviteCode),
            mapOf(
                FIELD_CELLAR_ID to cellar.id,
                FIELD_CREATED_AT to FieldValue.serverTimestamp(),
            ),
        )
        awaitServer { batch.commit().await() }
        return cellar.id
    }

    override suspend fun joinCellar(inviteCode: String): String {
        val uid = requireUser()
        // Source.SERVER so a stale cache can never answer for a code.
        val invite = awaitServer {
            firestore.collection(INVITES).document(inviteCode).get(Source.SERVER).await()
        }
        val cellarId = invite.takeIf { it.exists() }?.getString(FIELD_CELLAR_ID)
            ?: throw SyncException.UnknownCode()
        awaitServer {
            firestore.collection(CELLARS).document(cellarId)
                .update(FIELD_MEMBERS, FieldValue.arrayUnion(uid))
                .await()
        }
        return cellarId
    }

    override fun observeCellar(cellarId: String): Flow<CellarInfo> = callbackFlow {
        val registration = firestore.collection(CELLARS).document(cellarId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(SyncException.Failed(error))
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val inviteCode = snapshot.getString(FIELD_INVITE_CODE) ?: return@addSnapshotListener
                val members = (snapshot.get(FIELD_MEMBERS) as? List<*>)?.size ?: 0
                trySend(CellarInfo(inviteCode, members))
            }
        awaitClose { registration.remove() }
    }

    override fun observeBeers(cellarId: String): Flow<RemoteBeersUpdate> = callbackFlow {
        val registration = beers(cellarId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(SyncException.Failed(error))
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val changes = snapshot.documentChanges.mapNotNull { change ->
                    when (change.type) {
                        DocumentChange.Type.REMOVED -> RemoteChange.Remove(change.document.id)
                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                            val beer = RemoteBeerCodec.fromFields(change.document.id, change.document.data)
                            if (beer == null) {
                                Log.w(TAG, "Skipping a malformed shared beer ${change.document.id}")
                                null
                            } else {
                                RemoteChange.Upsert(beer)
                            }
                        }
                    }
                }
                trySend(
                    RemoteBeersUpdate(
                        changes = changes,
                        fromServer = !snapshot.metadata.isFromCache,
                        hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                    ),
                )
            }
        awaitClose { registration.remove() }
    }

    override fun putBeer(cellarId: String, beer: TriedBeer) {
        beers(cellarId).document(beer.id).set(RemoteBeerCodec.toFields(beer))
    }

    override fun deleteBeer(cellarId: String, beerId: String) {
        beers(cellarId).document(beerId).delete()
    }

    private fun beers(cellarId: String) =
        firestore.collection(CELLARS).document(cellarId).collection(BEERS)

    private fun requireUser(): String =
        auth.currentUser?.uid ?: throw SyncException.Failed(IllegalStateException("Not signed in"))

    /**
     * Firestore never fails a queued write while offline, it just waits, so
     * a pairing step that has not completed within the timeout is reported
     * as offline.
     */
    private suspend fun <T> awaitServer(block: suspend () -> T): T = try {
        withTimeout(SERVER_TIMEOUT_MS) { block() }
    } catch (error: TimeoutCancellationException) {
        throw SyncException.Offline()
    } catch (error: FirebaseFirestoreException) {
        if (error.code == FirebaseFirestoreException.Code.UNAVAILABLE) throw SyncException.Offline()
        throw SyncException.Failed(error)
    } catch (error: FirebaseNetworkException) {
        throw SyncException.Offline()
    }

    companion object {
        private const val TAG = "FirestoreCellarRemote"
        private const val CELLARS = "cellars"
        private const val INVITES = "invites"
        private const val BEERS = "beers"
        private const val FIELD_INVITE_CODE = "inviteCode"
        private const val FIELD_MEMBERS = "members"
        private const val FIELD_CELLAR_ID = "cellarId"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val SERVER_TIMEOUT_MS = 20_000L

        /**
         * The Firestore remote when the google-services plugin generated
         * Firebase's resources for this build (FirebaseInitProvider then
         * initialises the default app at process start), otherwise the
         * stand-in that reports sync as unavailable.
         */
        fun create(context: Context): CellarRemote =
            if (FirebaseApp.getApps(context).isEmpty()) {
                UnavailableCellarRemote
            } else {
                FirestoreCellarRemote(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance())
            }
    }
}

/** A build without Firebase configuration: nothing to sync with. */
object UnavailableCellarRemote : CellarRemote {
    override val isAvailable: Boolean = false
    override val currentUserId: String? = null
    override suspend fun signIn(): String = throw SyncException.Unavailable()
    override suspend fun createCellar(inviteCode: String): String = throw SyncException.Unavailable()
    override suspend fun joinCellar(inviteCode: String): String = throw SyncException.Unavailable()
    override fun observeCellar(cellarId: String): Flow<CellarInfo> = emptyFlow()
    override fun observeBeers(cellarId: String): Flow<RemoteBeersUpdate> = emptyFlow()
    override fun putBeer(cellarId: String, beer: TriedBeer) = Unit
    override fun deleteBeer(cellarId: String, beerId: String) = Unit
}
```

- [ ] **Step 10: Wire the engine into the app**

Re-read `app/src/main/java/com/beertracker/BeerApp.kt`, then change the top of `AppContainer` and the start of `BeerApp.onCreate`. The class header and the first lines become:

```kotlin
class AppContainer(context: Context, scope: CoroutineScope) {
    private val db = BeerDatabase.build(context)
    val beerPhotoStore = BeerPhotoStore(context.filesDir)
    private val localBeerRepository: BeerRepository = RoomBeerRepository(db.beerDao(), beerPhotoStore)

    val cellarRemote: CellarRemote = FirestoreCellarRemote.create(context)
    val syncMembershipStore: SyncMembershipStore = PrefsSyncMembershipStore(context)
    val syncEngine = CellarSyncEngine(localBeerRepository, cellarRemote, syncMembershipStore, scope)
    /** Every screen writes through here, so each save is mirrored to the shared cellar. */
    val beerRepository: BeerRepository = SyncingBeerRepository(localBeerRepository, syncEngine)
```

Everything from `private val catalogDb = ...` down stays as it is. Add the imports `com.beertracker.data.CellarSyncEngine`, `com.beertracker.data.FirestoreCellarRemote`, `com.beertracker.data.PrefsSyncMembershipStore`, `com.beertracker.data.SyncingBeerRepository`, `com.beertracker.domain.CellarRemote`, `com.beertracker.domain.SyncMembershipStore` (keep them sorted with the existing ones).

In `BeerApp.onCreate`, replace `container = AppContainer(this)` with:

```kotlin
        container = AppContainer(this, applicationScope)
        container.syncEngine.start()
```

`applicationScope` is already declared above `onCreate`; the two lines go before the existing `applicationScope.launch { ... }` block.

- [ ] **Step 11: Run the whole suite**

Run: `.\gradlew.bat testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL, 364 tests (no new tests in this task; the point is that everything compiles and the existing suite is untouched by Firebase on the classpath).

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/com/beertracker/data/FirestoreCellarRemote.kt app/src/main/java/com/beertracker/BeerApp.kt
git commit -m "[App] Talk to Firestore behind the CellarRemote port and start the sync engine at launch"
```

---

### Task 7: SyncViewModel

State and actions for the sync screen: mirrors the engine status, owns the code field, runs create, join and stop, and turns failures into a small error enum the screen maps to strings.

**Files:**
- Create: `app/src/main/java/com/beertracker/ui/sync/SyncViewModel.kt`
- Test: `app/src/test/java/com/beertracker/SyncViewModelTest.kt`

**Interfaces:**
- Consumes: `CellarSyncEngine` (Task 4), `SyncException`, `SyncStatus` (Task 1), `BeerApp.container.syncEngine` (Task 6), fakes from Task 4.
- Produces, relied on by Task 8:
  - `enum class SyncError { INVALID_CODE, UNKNOWN_CODE, OFFLINE, UNAVAILABLE, FAILED }`
  - `data class SyncUiState(val status: SyncStatus = SyncStatus.NotPaired, val codeInput: String = "", val working: Boolean = false, val error: SyncError? = null)`
  - `class SyncViewModel(engine: CellarSyncEngine) : ViewModel()` with `val uiState: StateFlow<SyncUiState>`, `fun setCodeInput(value: String)`, `fun createCellar()`, `fun join()`, `fun stopSyncing()`, `fun dismissError()`, `companion object { val Factory }`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/beertracker/SyncViewModelTest.kt`:

```kotlin
package com.beertracker

import android.app.Application
import com.beertracker.data.CellarSyncEngine
import com.beertracker.domain.CellarMembership
import com.beertracker.domain.SyncException
import com.beertracker.domain.SyncStatus
import com.beertracker.ui.sync.SyncError
import com.beertracker.ui.sync.SyncViewModel
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SyncViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class Harness(
        scope: TestScope,
        membership: CellarMembership? = null,
        available: Boolean = true,
    ) {
        val local = FakeBeerRepository()
        val remote = FakeCellarRemote(isAvailable = available)
        val store = FakeSyncMembershipStore(membership)
        val engine = CellarSyncEngine(
            local = local,
            remote = remote,
            membershipStore = store,
            scope = CoroutineScope(
                scope.backgroundScope.coroutineContext + UnconfinedTestDispatcher(scope.testScheduler),
            ),
            random = Random(1),
        ).also { it.start() }
        val vm = SyncViewModel(engine)

        init {
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { vm.uiState.collect() }
        }
    }

    private val paired = CellarMembership("cellar-1", "ABCDEFGH")

    @Test
    fun `mirrors the engine status`() = runTest {
        val h = Harness(this, membership = paired)
        assertEquals("ABCDEFGH", (h.vm.uiState.value.status as SyncStatus.Paired).inviteCode)

        h.engine.stopSyncing()

        assertEquals(SyncStatus.NotPaired, h.vm.uiState.value.status)
    }

    @Test
    fun `createCellar lands on paired with no error`() = runTest {
        val h = Harness(this)

        h.vm.createCellar()

        val state = h.vm.uiState.value
        assertTrue(state.status is SyncStatus.Paired)
        assertFalse(state.working)
        assertNull(state.error)
    }

    @Test
    fun `a malformed code shows the invalid code error`() = runTest {
        val h = Harness(this)
        h.vm.setCodeInput("abc")

        h.vm.join()

        assertEquals(SyncError.INVALID_CODE, h.vm.uiState.value.error)
        assertEquals(SyncStatus.NotPaired, h.vm.uiState.value.status)
    }

    @Test
    fun `an unknown code shows the unknown code error`() = runTest {
        val h = Harness(this)
        h.vm.setCodeInput("ABCD-EFGH")

        h.vm.join()

        assertEquals(SyncError.UNKNOWN_CODE, h.vm.uiState.value.error)
    }

    @Test
    fun `a good code joins and clears the field`() = runTest {
        val h = Harness(this)
        h.remote.cellars["cellar-9"] = mutableListOf("user-other")
        h.remote.invites["ABCDEFGH"] = "cellar-9"
        h.vm.setCodeInput("abcd-efgh")

        h.vm.join()

        assertEquals("", h.vm.uiState.value.codeInput)
        assertEquals("ABCDEFGH", (h.vm.uiState.value.status as SyncStatus.Paired).inviteCode)
    }

    @Test
    fun `an offline failure shows the offline error`() = runTest {
        val h = Harness(this)
        h.remote.signInError = SyncException.Offline()

        h.vm.createCellar()

        assertEquals(SyncError.OFFLINE, h.vm.uiState.value.error)
        assertFalse(h.vm.uiState.value.working)
    }

    @Test
    fun `an unavailable build shows the unavailable error`() = runTest {
        val h = Harness(this, available = false)

        h.vm.createCellar()

        assertEquals(SyncError.UNAVAILABLE, h.vm.uiState.value.error)
        assertEquals(SyncStatus.Unavailable, h.vm.uiState.value.status)
    }

    @Test
    fun `an unexpected exception shows the generic error`() = runTest {
        val h = Harness(this)
        h.remote.signInError = IllegalStateException("boom")

        h.vm.createCellar()

        assertEquals(SyncError.FAILED, h.vm.uiState.value.error)
        assertFalse(h.vm.uiState.value.working)
    }

    @Test
    fun `typing upper-cases the code and clears the error`() = runTest {
        val h = Harness(this)
        h.vm.setCodeInput("abc")
        h.vm.join()
        assertEquals(SyncError.INVALID_CODE, h.vm.uiState.value.error)

        h.vm.setCodeInput("abcd")

        assertEquals("ABCD", h.vm.uiState.value.codeInput)
        assertNull(h.vm.uiState.value.error)
    }

    @Test
    fun `dismissError clears the error`() = runTest {
        val h = Harness(this)
        h.vm.setCodeInput("abc")
        h.vm.join()

        h.vm.dismissError()

        assertNull(h.vm.uiState.value.error)
    }

    @Test
    fun `taps while working are ignored`() = runTest {
        val h = Harness(this)
        h.remote.signInGate = CompletableDeferred()

        h.vm.createCellar()
        assertTrue(h.vm.uiState.value.working)
        h.vm.createCellar()
        h.remote.signInGate?.complete(Unit)

        assertEquals(1, h.remote.signIns)
        assertFalse(h.vm.uiState.value.working)
    }

    @Test
    fun `stopSyncing lands on not paired`() = runTest {
        val h = Harness(this, membership = paired)

        h.vm.stopSyncing()

        assertEquals(SyncStatus.NotPaired, h.vm.uiState.value.status)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.SyncViewModelTest" --console=plain`
Expected: compilation FAILS with "Unresolved reference: sync" (the package does not exist yet).

- [ ] **Step 3: Write the view model**

`app/src/main/java/com/beertracker/ui/sync/SyncViewModel.kt`:

```kotlin
package com.beertracker.ui.sync

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.beertracker.BeerApp
import com.beertracker.data.CellarSyncEngine
import com.beertracker.domain.SyncException
import com.beertracker.domain.SyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SyncError { INVALID_CODE, UNKNOWN_CODE, OFFLINE, UNAVAILABLE, FAILED }

data class SyncUiState(
    val status: SyncStatus = SyncStatus.NotPaired,
    val codeInput: String = "",
    val working: Boolean = false,
    val error: SyncError? = null,
)

class SyncViewModel(private val engine: CellarSyncEngine) : ViewModel() {

    private val codeInput = MutableStateFlow("")
    private val working = MutableStateFlow(false)
    private val error = MutableStateFlow<SyncError?>(null)

    val uiState: StateFlow<SyncUiState> =
        combine(engine.status, codeInput, working, error) { status, code, busy, problem ->
            SyncUiState(status = status, codeInput = code, working = busy, error = problem)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SyncUiState(status = engine.status.value),
        )

    /** Upper-cased as typed; the code alphabet has no lower case. Clears any error. */
    fun setCodeInput(value: String) {
        codeInput.value = value.uppercase()
        error.value = null
    }

    fun createCellar() = perform { engine.createCellar() }

    fun join() = perform {
        engine.joinCellar(codeInput.value)
        codeInput.value = ""
    }

    fun stopSyncing() {
        engine.stopSyncing()
        error.value = null
    }

    fun dismissError() {
        error.value = null
    }

    private fun perform(action: suspend () -> Unit) {
        if (working.value) return
        working.value = true
        error.value = null
        viewModelScope.launch {
            try {
                action()
            } catch (problem: SyncException) {
                error.value = problem.toSyncError()
            } catch (problem: Exception) {
                if (problem is CancellationException) throw problem
                Log.w(TAG, "Sync action failed unexpectedly", problem)
                error.value = SyncError.FAILED
            } finally {
                working.value = false
            }
        }
    }

    companion object {
        private const val TAG = "SyncViewModel"

        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BeerApp
                SyncViewModel(app.container.syncEngine)
            }
        }
    }
}

private fun SyncException.toSyncError(): SyncError = when (this) {
    is SyncException.Unavailable -> SyncError.UNAVAILABLE
    is SyncException.Offline -> SyncError.OFFLINE
    is SyncException.InvalidCode -> SyncError.INVALID_CODE
    is SyncException.UnknownCode -> SyncError.UNKNOWN_CODE
    is SyncException.Failed -> SyncError.FAILED
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.SyncViewModelTest" --console=plain`
Expected: BUILD SUCCESSFUL, 12 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/beertracker/ui/sync/SyncViewModel.kt app/src/test/java/com/beertracker/SyncViewModelTest.kt
git commit -m "[App] Sync view model: create, join, stop and report errors"
```

---

### Task 8: SyncScreen and its strings

The screen described in the spec's UX section, built the way `ScanScreen` is: a thin `SyncScreen` that owns the view model and the share intent, and a testable `SyncContent`.

**Files:**
- Create: `app/src/main/java/com/beertracker/ui/sync/SyncScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/beertracker/ui/sync/SyncScreenTest.kt`

**Interfaces:**
- Consumes: `SyncViewModel`, `SyncUiState`, `SyncError` (Task 7), `SyncStatus`, `InviteCodes` (Task 1), `ErrorState`, `SectionHeader`, `BeerTrackerSpacing` (existing).
- Produces, relied on by Task 9: `@Composable fun SyncScreen(viewModel: SyncViewModel, onBack: () -> Unit)`; `@Composable internal fun SyncContent(state, onCodeChange, onCreate, onJoin, onStopSyncing, onShare: (String) -> Unit, onBack)`.

- [ ] **Step 1: Add the strings**

Append inside `<resources>` in `app/src/main/res/values/strings.xml`, after the last existing string:

```xml
    <string name="sync_title">Sync between phones</string>
    <string name="sync_unavailable_title">Sync is not set up in this build</string>
    <string name="sync_unavailable_message">This copy of BeerTracker was built without its Firebase configuration, so it cannot share a cellar. Install a release build that includes it.</string>
    <string name="sync_intro">Share one cellar between two phones. One phone creates the shared cellar and gets a code; the other phone joins with that code. Both phones keep working offline and catch up when they are back online.</string>
    <string name="sync_create">Create a shared cellar</string>
    <string name="sync_join_section">Join with a code</string>
    <string name="sync_join_help">Enter the code shown on the other phone.</string>
    <string name="sync_code_label">Invite code</string>
    <string name="sync_code_placeholder">ABCD-EFGH</string>
    <string name="sync_join">Join</string>
    <string name="sync_error_invalid_code">Codes have eight letters and digits, like ABCD-EFGH.</string>
    <string name="sync_error_unknown_code">No cellar has that code. Check it on the other phone and try again.</string>
    <string name="sync_error_offline">Could not reach the server. Check the connection and try again.</string>
    <string name="sync_error_unavailable">Sync is not set up in this build.</string>
    <string name="sync_error_failed">Something went wrong while syncing. Try again.</string>
    <string name="sync_code_section">Invite code</string>
    <string name="sync_code_help">Enter this code on the other phone to share this cellar.</string>
    <string name="sync_share_code">Share code</string>
    <string name="sync_share_text">Join my BeerTracker cellar with the code %1$s.</string>
    <string name="sync_status_section">Status</string>
    <string name="sync_members_connecting">Connecting.</string>
    <string name="sync_members_alone">Only this phone so far.</string>
    <string name="sync_members_count">%1$d phones share this cellar.</string>
    <string name="sync_waiting_first">Waiting for the first sync.</string>
    <string name="sync_last_synced">Last synced %1$s</string>
    <string name="sync_pending_uploads">Changes on this phone are waiting to upload.</string>
    <string name="sync_stop">Stop syncing on this phone</string>
    <string name="sync_stop_title">Stop syncing?</string>
    <string name="sync_stop_message">Your beers stay on this phone, and the other phone keeps its copy. Changes are no longer shared until you pair again.</string>
    <string name="sync_stop_confirm">Stop syncing</string>
```

- [ ] **Step 2: Write the failing tests**

`app/src/test/java/com/beertracker/ui/sync/SyncScreenTest.kt`:

```kotlin
package com.beertracker.ui.sync

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.beertracker.MainDispatcherRule
import com.beertracker.domain.SyncStatus
import com.beertracker.ui.theme.BeerTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SyncScreenTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private fun render(
        state: SyncUiState,
        onCodeChange: (String) -> Unit = {},
        onCreate: () -> Unit = {},
        onJoin: () -> Unit = {},
        onStopSyncing: () -> Unit = {},
        onShare: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            BeerTrackerTheme {
                SyncContent(
                    state = state,
                    onCodeChange = onCodeChange,
                    onCreate = onCreate,
                    onJoin = onJoin,
                    onStopSyncing = onStopSyncing,
                    onShare = onShare,
                    onBack = {},
                )
            }
        }
    }

    private val paired = SyncStatus.Paired(
        inviteCode = "ABCDEFGH",
        memberCount = 1,
        lastSyncedUtc = null,
        hasPendingUploads = false,
    )

    @Test
    fun `unavailable build explains itself`() {
        render(SyncUiState(status = SyncStatus.Unavailable))
        composeRule.onNodeWithText("Sync is not set up in this build").assertIsDisplayed()
    }

    @Test
    fun `not paired offers create and join`() {
        var created = false
        var joined = false
        render(
            SyncUiState(status = SyncStatus.NotPaired, codeInput = "ABCD-EFGH"),
            onCreate = { created = true },
            onJoin = { joined = true },
        )

        composeRule.onNodeWithText("Create a shared cellar").performClick()
        composeRule.onNodeWithText("Join").performScrollTo().performClick()

        assertTrue(created)
        assertTrue(joined)
    }

    @Test
    fun `join is disabled until a code is typed`() {
        render(SyncUiState(status = SyncStatus.NotPaired, codeInput = ""))
        composeRule.onNodeWithText("Join").assertIsNotEnabled()
    }

    @Test
    fun `typing in the code field reports the text`() {
        var typed = ""
        render(SyncUiState(status = SyncStatus.NotPaired), onCodeChange = { typed = it })

        composeRule.onNodeWithText("Invite code").performTextInput("abcd")

        assertEquals("abcd", typed)
    }

    @Test
    fun `each error renders its text`() {
        val error = mutableStateOf<SyncError?>(null)
        composeRule.setContent {
            BeerTrackerTheme {
                SyncContent(
                    state = SyncUiState(status = SyncStatus.NotPaired, error = error.value),
                    onCodeChange = {},
                    onCreate = {},
                    onJoin = {},
                    onStopSyncing = {},
                    onShare = {},
                    onBack = {},
                )
            }
        }
        val expected = mapOf(
            SyncError.INVALID_CODE to "Codes have eight letters and digits, like ABCD-EFGH.",
            SyncError.UNKNOWN_CODE to "No cellar has that code. Check it on the other phone and try again.",
            SyncError.OFFLINE to "Could not reach the server. Check the connection and try again.",
            SyncError.UNAVAILABLE to "Sync is not set up in this build.",
            SyncError.FAILED to "Something went wrong while syncing. Try again.",
        )

        expected.forEach { (value, text) ->
            error.value = value
            composeRule.waitForIdle()
            composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `paired shows the formatted code and shares it`() {
        var shared = ""
        render(SyncUiState(status = paired), onShare = { shared = it })

        composeRule.onNodeWithText("ABCD-EFGH").assertIsDisplayed()
        composeRule.onNodeWithText("Only this phone so far.").assertIsDisplayed()
        composeRule.onNodeWithText("Waiting for the first sync.").assertIsDisplayed()
        composeRule.onNodeWithText("Share code").performClick()

        assertEquals("Join my BeerTracker cellar with the code ABCD-EFGH.", shared)
    }

    @Test
    fun `paired with two members and pending uploads says so`() {
        render(
            SyncUiState(
                status = paired.copy(memberCount = 2, lastSyncedUtc = 1_700_000_000_000L, hasPendingUploads = true),
            ),
        )

        composeRule.onNodeWithText("2 phones share this cellar.").assertIsDisplayed()
        composeRule.onNodeWithText("Changes on this phone are waiting to upload.").assertIsDisplayed()
        composeRule.onNodeWithText("Last synced", substring = true).assertIsDisplayed()
    }

    @Test
    fun `paired while connecting shows the connecting line`() {
        render(SyncUiState(status = paired.copy(memberCount = null)))
        composeRule.onNodeWithText("Connecting.").assertIsDisplayed()
    }

    @Test
    fun `stop syncing asks first and then fires`() {
        var stopped = false
        render(SyncUiState(status = paired), onStopSyncing = { stopped = true })

        composeRule.onNodeWithText("Stop syncing on this phone").performScrollTo().performClick()
        composeRule.onNodeWithText("Stop syncing?").assertIsDisplayed()
        assertFalse(stopped)
        composeRule.onNodeWithText("Stop syncing").performClick()

        assertTrue(stopped)
    }

    @Test
    fun `working disables create and join`() {
        render(SyncUiState(status = SyncStatus.NotPaired, codeInput = "ABCDEFGH", working = true))

        composeRule.onNodeWithText("Create a shared cellar").assertIsNotEnabled()
        composeRule.onNodeWithText("Join").assertIsNotEnabled()
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.ui.sync.SyncScreenTest" --console=plain`
Expected: compilation FAILS with "Unresolved reference: SyncContent".

- [ ] **Step 4: Write the screen**

`app/src/main/java/com/beertracker/ui/sync/SyncScreen.kt`:

```kotlin
package com.beertracker.ui.sync

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beertracker.R
import com.beertracker.domain.InviteCodes
import com.beertracker.domain.SyncStatus
import com.beertracker.ui.components.ErrorState
import com.beertracker.ui.components.SectionHeader
import com.beertracker.ui.theme.BeerTrackerSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SyncScreen(
    viewModel: SyncViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.sync_share_code)
    SyncContent(
        state = state,
        onCodeChange = viewModel::setCodeInput,
        onCreate = viewModel::createCellar,
        onJoin = viewModel::join,
        onStopSyncing = viewModel::stopSyncing,
        onShare = { text ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(send, shareTitle))
        },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SyncContent(
    state: SyncUiState,
    onCodeChange: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onStopSyncing: () -> Unit,
    onShare: (String) -> Unit,
    onBack: () -> Unit,
) {
    var showStopDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_title)) },
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
            when (val status = state.status) {
                SyncStatus.Unavailable -> ErrorState(
                    title = stringResource(R.string.sync_unavailable_title),
                    message = stringResource(R.string.sync_unavailable_message),
                )
                SyncStatus.NotPaired -> NotPairedContent(
                    state = state,
                    onCodeChange = onCodeChange,
                    onCreate = onCreate,
                    onJoin = onJoin,
                )
                is SyncStatus.Paired -> PairedContent(
                    status = status,
                    onShare = onShare,
                    onStopClick = { showStopDialog = true },
                )
            }
        }
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text(stringResource(R.string.sync_stop_title)) },
            text = { Text(stringResource(R.string.sync_stop_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStopDialog = false
                        onStopSyncing()
                    },
                ) {
                    Text(stringResource(R.string.sync_stop_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun NotPairedContent(
    state: SyncUiState,
    onCodeChange: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    Text(
        stringResource(R.string.sync_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        onClick = onCreate,
        enabled = !state.working,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.sync_create))
    }
    SectionHeader(
        title = stringResource(R.string.sync_join_section),
        supportingText = stringResource(R.string.sync_join_help),
        modifier = Modifier.padding(top = BeerTrackerSpacing.large),
    )
    OutlinedTextField(
        value = state.codeInput,
        onValueChange = onCodeChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.working,
        label = { Text(stringResource(R.string.sync_code_label)) },
        placeholder = { Text(stringResource(R.string.sync_code_placeholder)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        shape = MaterialTheme.shapes.medium,
    )
    Button(
        onClick = onJoin,
        enabled = !state.working && state.codeInput.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.sync_join))
    }
    if (state.working) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    state.error?.let { error ->
        Text(
            text = errorText(error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun PairedContent(
    status: SyncStatus.Paired,
    onShare: (String) -> Unit,
    onStopClick: () -> Unit,
) {
    val formattedCode = InviteCodes.format(status.inviteCode)
    val shareText = stringResource(R.string.sync_share_text, formattedCode)
    SectionHeader(
        title = stringResource(R.string.sync_code_section),
        supportingText = stringResource(R.string.sync_code_help),
    )
    Text(
        text = formattedCode,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BeerTrackerSpacing.medium),
        style = MaterialTheme.typography.displaySmall,
        letterSpacing = 4.sp,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
    )
    OutlinedButton(
        onClick = { onShare(shareText) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.sync_share_code))
    }
    SectionHeader(
        title = stringResource(R.string.sync_status_section),
        modifier = Modifier.padding(top = BeerTrackerSpacing.large),
    )
    Text(
        text = when (val count = status.memberCount) {
            null -> stringResource(R.string.sync_members_connecting)
            0, 1 -> stringResource(R.string.sync_members_alone)
            else -> stringResource(R.string.sync_members_count, count)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = status.lastSyncedUtc?.let { stringResource(R.string.sync_last_synced, formatSyncTime(it)) }
            ?: stringResource(R.string.sync_waiting_first),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (status.hasPendingUploads) {
        Text(
            text = stringResource(R.string.sync_pending_uploads),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    TextButton(
        onClick = onStopClick,
        modifier = Modifier.padding(top = BeerTrackerSpacing.large),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text(stringResource(R.string.sync_stop))
    }
}

@Composable
private fun errorText(error: SyncError): String = stringResource(
    when (error) {
        SyncError.INVALID_CODE -> R.string.sync_error_invalid_code
        SyncError.UNKNOWN_CODE -> R.string.sync_error_unknown_code
        SyncError.OFFLINE -> R.string.sync_error_offline
        SyncError.UNAVAILABLE -> R.string.sync_error_unavailable
        SyncError.FAILED -> R.string.sync_error_failed
    },
)

private fun formatSyncTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.ui.sync.SyncScreenTest" --console=plain`
Expected: BUILD SUCCESSFUL, 10 tests pass.

If `each error renders its text` fails to find a node, the error `Text` is off screen in Robolectric's default window; `performScrollTo()` before the assertion is what handles that, so check the assertion still calls it.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/beertracker/ui/sync/SyncScreen.kt app/src/main/res/values/strings.xml app/src/test/java/com/beertracker/ui/sync/SyncScreenTest.kt
git commit -m "[App] Add the sync screen"
```

---

### Task 9: Settings menu entry and the sync route

The gear menu on the overview becomes a settings menu with the theme choices, a divider and "Sync between phones"; the nav host gains the `sync` route.

**Files:**
- Modify: `app/src/main/java/com/beertracker/ui/OverviewScreen.kt`
- Modify: `app/src/main/java/com/beertracker/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/beertracker/ui/OverviewThemeMenuTest.kt`

**Interfaces:**
- Consumes: `SyncScreen`, `SyncViewModel.Factory` (Tasks 7 and 8).
- Produces: `OverviewScreen(..., onSyncClick: () -> Unit = {})`; nav route `sync`.

- [ ] **Step 1: Update the existing test and add the new one**

Re-read `app/src/test/java/com/beertracker/ui/OverviewThemeMenuTest.kt`. In the existing test, change `composeRule.onNodeWithContentDescription("Theme")` to `composeRule.onNodeWithContentDescription("Settings")`. Then add this test after it (inside the class), and `import org.junit.Assert.assertTrue`:

```kotlin
    @Test
    fun `settings menu offers sync between phones`() {
        var opened = false
        composeRule.setContent {
            BeerTrackerTheme {
                OverviewScreen(
                    viewModel = OverviewViewModel(FakeBeerRepository()),
                    catalogViewModel = CatalogRefreshViewModel(
                        FakeCatalogRepository(),
                        FakeCatalogRefresher(),
                    ),
                    onAddClick = {},
                    onBeerClick = {},
                    onSyncClick = { opened = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Sync between phones").performClick()

        assertTrue(opened)
    }
```

- [ ] **Step 2: Run the test class to verify both tests fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.ui.OverviewThemeMenuTest" --console=plain`
Expected: compilation FAILS with "No parameter with name 'onSyncClick' found".

- [ ] **Step 3: Rename the menu string**

In `app/src/main/res/values/strings.xml`, replace the line `<string name="theme_menu">Theme</string>` with `<string name="settings_menu">Settings</string>`. Nothing else uses `theme_menu` (check with a grep over `app/src`).

- [ ] **Step 4: Turn the theme menu into the settings menu**

Re-read `app/src/main/java/com/beertracker/ui/OverviewScreen.kt`. Make these changes:

1. Add the parameter `onSyncClick: () -> Unit = {},` to `OverviewScreen`, directly after `onCatalogClick: () -> Unit = {},`.
2. In the top bar `actions`, replace the `ThemeMenuAction(...)` call with:

```kotlin
                    SettingsMenuAction(
                        themeMode = themeMode,
                        onSetThemeMode = onSetThemeMode,
                        onSyncClick = onSyncClick,
                    )
```

3. Replace the whole `private fun ThemeMenuAction(...)` composable with:

```kotlin
@Composable
private fun SettingsMenuAction(
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSyncClick: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = stringResource(R.string.settings_menu),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            ThemeMode.entries.forEach { mode ->
                val isSelected = mode == themeMode
                DropdownMenuItem(
                    modifier = Modifier.semantics { selected = isSelected },
                    text = { Text(themeModeLabel(mode)) },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    } else {
                        null
                    },
                    onClick = {
                        onSetThemeMode(mode)
                        menuOpen = false
                    },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sync_title)) },
                onClick = {
                    onSyncClick()
                    menuOpen = false
                },
            )
        }
    }
}
```

`HorizontalDivider` is already imported in this file.

- [ ] **Step 5: Add the route**

Re-read `app/src/main/java/com/beertracker/MainActivity.kt`. In the `overview` composable, add `onSyncClick = { navController.navigate("sync") },` after `onCatalogClick = { navController.navigate("catalog") },`. Add this composable after the `catalog` one:

```kotlin
        composable("sync") {
            SyncScreen(
                viewModel = viewModel(factory = SyncViewModel.Factory),
                onBack = { navController.popBackStack() },
            )
        }
```

Add the imports `com.beertracker.ui.sync.SyncScreen` and `com.beertracker.ui.sync.SyncViewModel` next to the other `com.beertracker.ui.*` imports.

- [ ] **Step 6: Run the test class, then the whole suite**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.beertracker.ui.OverviewThemeMenuTest" --console=plain`
Expected: BUILD SUCCESSFUL, 2 tests pass.

Run: `.\gradlew.bat testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL, 387 tests.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/beertracker/ui/OverviewScreen.kt app/src/main/java/com/beertracker/MainActivity.kt app/src/main/res/values/strings.xml app/src/test/java/com/beertracker/ui/OverviewThemeMenuTest.kt
git commit -m "[App] Open the sync screen from the settings menu"
```

---

### Task 10: Documentation

The user's one-time Firebase setup guide, the phase 4 note in the v1 design, and a pointer in the README.

**Files:**
- Create: `docs/firebase-setup.md`
- Modify: `docs/superpowers/specs/2026-07-28-beertracker-v1-design.md`
- Modify: `README.md`

- [ ] **Step 1: Write the setup guide**

`docs/firebase-setup.md`:

````markdown
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
````

- [ ] **Step 2: Record phase 4 in the v1 design**

In `docs/superpowers/specs/2026-07-28-beertracker-v1-design.md`, add this subsection directly after the "Phase 3 details (added 2026-09-18)" subsection and before "## 8. Tech stack":

```markdown
### Phase 4 details (added 2026-09-19)

- Room stays the source of truth on each phone. A sync engine mirrors the
  beers into a Firestore cellar (`cellars/<id>/beers/<beerId>`) and applies
  the cellar's changes back into Room, so every screen keeps working
  exactly as before, online or not.
- Pairing is explicit: nothing talks to Firebase until the user opens
  "Sync between phones" from the settings menu and taps Create or Join.
  Create signs the phone in anonymously, makes a cellar and an eight
  character invite code shown as ABCD-EFGH; Join types that code. Both
  phones' existing beers are merged into the cellar.
- Conflicts resolve as last write to reach the server wins, per beer.
  Photos stay on the phone that took them; the other phone shows the
  catalog picture.
- The Firebase config file is not committed. Builds without it work and
  say so on the sync screen; the release workflow restores it from a
  GitHub secret. Full design:
  docs/superpowers/specs/2026-09-19-firebase-sync-and-pairing-design.md.
  Setup steps for the project owner: docs/firebase-setup.md.
```

- [ ] **Step 3: Point the README at the guide**

Append to `README.md`:

```markdown

## Sync between phones

Two phones can share one cellar. The project owner sets up Firebase once,
following [docs/firebase-setup.md](docs/firebase-setup.md); after that one
phone creates the shared cellar from the settings menu and the other joins
with the code it shows.
```

- [ ] **Step 4: Check for long dashes, then commit**

From the checkout root in PowerShell:

```powershell
Select-String -Path docs/firebase-setup.md, README.md, docs/superpowers/specs/2026-07-28-beertracker-v1-design.md -Pattern "[–—]"
```

Expected: no output.

```bash
git add docs/firebase-setup.md docs/superpowers/specs/2026-07-28-beertracker-v1-design.md README.md
git commit -m "[Docs] Record phase 4 in the v1 design and add the Firebase setup guide"
```

---

## Done when

- `.\gradlew.bat testDebugUnitTest --console=plain` is green with about 387 tests.
- `git status` is clean, `app/google-services.json` does not exist in the checkout (or is ignored if the user has since added it), and no file under `app/schemas/` changed.
- The gear menu opens the sync screen; without a Firebase config the screen says sync is not set up; the rest of the app behaves exactly as before.
- After merge, the user follows `docs/firebase-setup.md`, adds the secret, and verifies the live path on two phones as listed at the end of the spec.
