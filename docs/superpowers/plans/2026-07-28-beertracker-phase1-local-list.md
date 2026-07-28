# BeerTracker Phase 1: Local Beer List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A working Android app on one phone where the user can add, grade, browse, filter, and sort tried beers, persisted locally.

**Architecture:** Single-module Android app (package `com.beertracker`). MVVM: Compose screens talk to view-models, view-models talk to a `BeerRepository` interface. Phase 1 backs the interface with Room (local SQLite); Phase 4 will swap in a Firestore implementation behind the same interface. Filtering and sorting are pure Kotlin functions, unit-tested without Android.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), Room 2.6.1 with KSP, Navigation Compose, JUnit 4 + kotlinx-coroutines-test + Robolectric for JVM tests. AGP 8.7.3, Gradle 8.10.2, JDK 17.

This is plan 1 of 5 for BeerTracker v1. Later plans (written when reached): Phase 2 catalog snapshot + shelf-label scan, Phase 3 can-photo text reading, Phase 4 Firebase sync + pairing, Phase 5 GitHub Actions release pipeline. Spec: `docs/superpowers/specs/2026-07-28-beertracker-v1-design.md`.

## Global Constraints

- Repo root: `C:\Users\SilvijaSubotic\PersonalDevelopment\BeerTracker`. All commands run from there in PowerShell.
- Commits are authored by the user's git identity only. Never add Claude as author or co-author. No `Co-Authored-By` trailers.
- No em dashes or en dashes anywhere: not in code, comments, strings, commit messages, or docs. Use hyphens, commas, or rewrite the sentence.
- Commit message style: `[Scope] Message` where Scope is `App`, `Docs`, or `Build`.
- Package: `com.beertracker`. minSdk 26, targetSdk/compileSdk 35, JVM target 17.
- Dependency versions are pinned in `gradle/libs.versions.toml` exactly as written in Task 1. Do not bump versions while implementing.
- Grade is an Int and must be in 5..10 (Serbian university scale). Enforced in the domain model.
- Run unit tests with: `.\gradlew.bat testDebugUnitTest`
- Manual UI verification requires the user's phone with USB debugging enabled, via `adb install`. If no device is attached, verify the build compiles and defer the on-phone check to the user.

---

### Task 1: Toolchain and project scaffold

Installs JDK 17, Android SDK command-line tools, and Gradle, then creates a minimal Compose app that builds.

**Files:**
- Create: `.gitignore`
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/java/com/beertracker/BeerApp.kt`
- Create: `app/src/main/java/com/beertracker/MainActivity.kt`
- Create: `local.properties` (git-ignored)
- Create: gradle wrapper files (`gradlew.bat`, `gradle/wrapper/*`) via the `gradle wrapper` command

**Interfaces:**
- Consumes: nothing.
- Produces: a building Android project. Later tasks add Kotlin files under `app/src/main/java/com/beertracker/` and tests under `app/src/test/java/com/beertracker/`.

- [ ] **Step 1: Install JDK 17**

```powershell
winget install --id EclipseAdoptium.Temurin.17.JDK -e --accept-source-agreements --accept-package-agreements
```

Then in a fresh shell (or after refreshing PATH) verify:

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
$env:JAVA_HOME = [System.Environment]::GetEnvironmentVariable("JAVA_HOME","Machine")
java -version
```

Expected: `openjdk version "17..."`. If winget needs elevation, ask the user to run the install line in an admin PowerShell, then continue.

- [ ] **Step 2: Install Android SDK command-line tools and packages**

```powershell
$sdk = "C:\Android\Sdk"
New-Item -ItemType Directory -Force $sdk | Out-Null
Invoke-WebRequest "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -OutFile "$env:TEMP\cmdtools.zip"
Expand-Archive "$env:TEMP\cmdtools.zip" -DestinationPath "$sdk\cmdline-tools" -Force
Move-Item "$sdk\cmdline-tools\cmdline-tools" "$sdk\cmdline-tools\latest"
```

If the download URL 404s (Google rotates the build number), find the current "Command line tools only" Windows URL at https://developer.android.com/studio and use that instead.

```powershell
$sdkmanager = "C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat"
& $sdkmanager --install "platform-tools" "platforms;android-35" "build-tools;35.0.0"
1..30 | ForEach-Object { "y" } | & $sdkmanager --licenses
```

Expected: packages install, licenses report accepted. Set the environment variable for future sessions:

```powershell
[System.Environment]::SetEnvironmentVariable("ANDROID_HOME", "C:\Android\Sdk", "User")
```

- [ ] **Step 3: Install Gradle 8.10.2 (only needed once, to generate the wrapper)**

```powershell
Invoke-WebRequest "https://services.gradle.org/distributions/gradle-8.10.2-bin.zip" -OutFile "$env:TEMP\gradle.zip"
Expand-Archive "$env:TEMP\gradle.zip" -DestinationPath "C:\Gradle" -Force
```

Gradle is now at `C:\Gradle\gradle-8.10.2\bin\gradle.bat`.

- [ ] **Step 4: Write the project files**

`.gitignore`:

```
.gradle/
build/
local.properties
.idea/
*.iml
.kotlin/
captures/
.DS_Store
```

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "BeerTracker"
include(":app")
```

`build.gradle.kts` (root):

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

`gradle.properties`:

```
org.gradle.jvmargs=-Xmx2g
android.useAndroidX=true
kotlin.code.style=official
```

`gradle/libs.versions.toml`:

```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
coreKtx = "1.15.0"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.12.01"
navigationCompose = "2.8.5"
room = "2.6.1"
coroutines = "1.9.0"
junit = "4.13.2"
robolectric = "4.14.1"
androidxTestCore = "1.6.1"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core = { group = "androidx.test", name = "core-ktx", version.ref = "androidxTestCore" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.beertracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.beertracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
```

`app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:name=".BeerApp"
        android:label="@string/app_name"
        android:theme="@style/Theme.BeerTracker"
        android:allowBackup="true">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">BeerTracker</string>
</resources>
```

`app/src/main/res/values/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.BeerTracker" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

`app/src/main/java/com/beertracker/BeerApp.kt`:

```kotlin
package com.beertracker

import android.app.Application

class BeerApp : Application()
```

`app/src/main/java/com/beertracker/MainActivity.kt`:

```kotlin
package com.beertracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Text("BeerTracker")
            }
        }
    }
}
```

`local.properties` (git-ignored, machine-specific):

```
sdk.dir=C\:\\Android\\Sdk
```

- [ ] **Step 5: Generate the Gradle wrapper**

```powershell
& "C:\Gradle\gradle-8.10.2\bin\gradle.bat" wrapper --gradle-version 8.10.2
```

Expected: creates `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`.

- [ ] **Step 6: Verify the project builds**

```powershell
.\gradlew.bat assembleDebug
```

Expected: `BUILD SUCCESSFUL`. First run downloads dependencies and takes several minutes.

- [ ] **Step 7: Commit**

```powershell
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle app
git commit -m "[App] Project scaffold: Compose app that builds"
```

---

### Task 2: Domain model and repository interface

**Files:**
- Create: `app/src/main/java/com/beertracker/domain/TriedBeer.kt`
- Create: `app/src/main/java/com/beertracker/domain/BeerRepository.kt`
- Create: `app/src/main/java/com/beertracker/domain/Presets.kt`
- Create: `app/src/test/java/com/beertracker/FakeBeerRepository.kt`
- Create: `app/src/test/java/com/beertracker/TestData.kt`
- Test: `app/src/test/java/com/beertracker/TriedBeerTest.kt`
- Test: `app/src/test/java/com/beertracker/FakeBeerRepositoryTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `TriedBeer` data class (fields exactly as below), `BeerRepository` interface (`observeBeers(): Flow<List<TriedBeer>>`, `suspend getBeer(id: String): TriedBeer?`, `suspend addBeer(beer: TriedBeer)`, `suspend updateBeer(beer: TriedBeer)`, `suspend deleteBeer(id: String)`), `Presets.beerTypes: List<String>`, `Presets.pairings: List<String>`, test helpers `FakeBeerRepository` and `beer(...)`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/beertracker/TestData.kt`:

```kotlin
package com.beertracker

import com.beertracker.domain.TriedBeer

fun beer(
    id: String = "id",
    name: String = "Beer $id",
    brewery: String = "Brewery",
    type: String = "Lager",
    alcoholPercent: Double? = 5.0,
    volumeMl: Int? = 330,
    price: Double? = 25.0,
    grade: Int = 7,
    note: String = "",
    aftertaste: String = "",
    goesWellWith: List<String> = emptyList(),
    buyAgain: Boolean = false,
    favourite: Boolean = false,
    dateAdded: Long = 0L,
) = TriedBeer(
    id = id,
    name = name,
    brewery = brewery,
    type = type,
    alcoholPercent = alcoholPercent,
    volumeMl = volumeMl,
    price = price,
    grade = grade,
    note = note,
    aftertaste = aftertaste,
    goesWellWith = goesWellWith,
    buyAgain = buyAgain,
    favourite = favourite,
    dateAdded = dateAdded,
    catalogArticleNumber = null,
    addedBy = null,
)
```

`app/src/test/java/com/beertracker/TriedBeerTest.kt`:

```kotlin
package com.beertracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TriedBeerTest {

    @Test
    fun `grade 5 and 10 are accepted`() {
        assertEquals(5, beer(grade = 5).grade)
        assertEquals(10, beer(grade = 10).grade)
    }

    @Test
    fun `grade below 5 is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { beer(grade = 4) }
    }

    @Test
    fun `grade above 10 is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { beer(grade = 11) }
    }
}
```

`app/src/test/java/com/beertracker/FakeBeerRepositoryTest.kt`:

```kotlin
package com.beertracker

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeBeerRepositoryTest {

    @Test
    fun `added beer appears in observed list and by id`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a"))
        assertEquals(listOf("a"), repo.observeBeers().first().map { it.id })
        assertEquals("a", repo.getBeer("a")?.id)
    }

    @Test
    fun `update replaces the beer with the same id`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a", grade = 6))
        repo.updateBeer(beer(id = "a", grade = 9))
        assertEquals(9, repo.getBeer("a")?.grade)
        assertEquals(1, repo.observeBeers().first().size)
    }

    @Test
    fun `delete removes the beer`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a"))
        repo.deleteBeer("a")
        assertNull(repo.getBeer("a"))
        assertEquals(0, repo.observeBeers().first().size)
    }
}
```

`app/src/test/java/com/beertracker/FakeBeerRepository.kt`:

```kotlin
package com.beertracker

import com.beertracker.domain.BeerRepository
import com.beertracker.domain.TriedBeer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeBeerRepository : BeerRepository {
    private val beers = MutableStateFlow<Map<String, TriedBeer>>(emptyMap())

    override fun observeBeers(): Flow<List<TriedBeer>> = beers.map { it.values.toList() }

    override suspend fun getBeer(id: String): TriedBeer? = beers.value[id]

    override suspend fun addBeer(beer: TriedBeer) = beers.update { it + (beer.id to beer) }

    override suspend fun updateBeer(beer: TriedBeer) = beers.update { it + (beer.id to beer) }

    override suspend fun deleteBeer(id: String) = beers.update { it - id }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: compilation FAILS with unresolved reference `TriedBeer` and `BeerRepository` (the domain files do not exist yet).

- [ ] **Step 3: Write the domain files**

`app/src/main/java/com/beertracker/domain/TriedBeer.kt`:

```kotlin
package com.beertracker.domain

data class TriedBeer(
    val id: String,
    val name: String,
    val brewery: String,
    val type: String,
    val alcoholPercent: Double?,
    val volumeMl: Int?,
    val price: Double?,
    val grade: Int,
    val note: String,
    val aftertaste: String,
    val goesWellWith: List<String>,
    val buyAgain: Boolean,
    val favourite: Boolean,
    val dateAdded: Long,
    val catalogArticleNumber: String?,
    val addedBy: String?,
) {
    init {
        require(grade in 5..10) { "Grade must be between 5 and 10, was $grade" }
    }
}
```

`app/src/main/java/com/beertracker/domain/BeerRepository.kt`:

```kotlin
package com.beertracker.domain

import kotlinx.coroutines.flow.Flow

interface BeerRepository {
    fun observeBeers(): Flow<List<TriedBeer>>
    suspend fun getBeer(id: String): TriedBeer?
    suspend fun addBeer(beer: TriedBeer)
    suspend fun updateBeer(beer: TriedBeer)
    suspend fun deleteBeer(id: String)
}
```

`app/src/main/java/com/beertracker/domain/Presets.kt`:

```kotlin
package com.beertracker.domain

object Presets {
    val beerTypes = listOf(
        "Lager", "Pilsner", "IPA", "Pale Ale", "Wheat",
        "Stout", "Porter", "Sour", "Amber Ale", "Dark Lager",
    )
    val pairings = listOf(
        "Red meat", "Pasta white sauce", "Pasta tomato sauce",
        "Salmon", "White fish", "Dessert",
    )
}
```

- [ ] **Step 4: Run tests to verify they pass**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/beertracker/domain app/src/test/java/com/beertracker
git commit -m "[App] Domain model, repository interface, presets"
```

---

### Task 3: Filter and sort logic

**Files:**
- Create: `app/src/main/java/com/beertracker/domain/BeerListLogic.kt`
- Test: `app/src/test/java/com/beertracker/BeerListLogicTest.kt`

**Interfaces:**
- Consumes: `TriedBeer` from Task 2.
- Produces: `enum class BeerSort { GRADE, PRICE, NAME_BREWERY, DATE_ADDED }`, `data class BeerFilter(query: String = "", buyAgainOnly: Boolean = false, favouritesOnly: Boolean = false, types: Set<String> = emptySet())`, `fun filterAndSort(beers: List<TriedBeer>, filter: BeerFilter, sort: BeerSort): List<TriedBeer>`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/beertracker/BeerListLogicTest.kt`:

```kotlin
package com.beertracker

import com.beertracker.domain.BeerFilter
import com.beertracker.domain.BeerSort
import com.beertracker.domain.filterAndSort
import org.junit.Assert.assertEquals
import org.junit.Test

class BeerListLogicTest {

    private val beers = listOf(
        beer(id = "a", name = "Falcon Export", brewery = "Falcon", type = "Lager",
            grade = 6, price = 15.0, dateAdded = 100L, buyAgain = true),
        beer(id = "b", name = "Punk IPA", brewery = "BrewDog", type = "IPA",
            grade = 9, price = 30.0, dateAdded = 300L, favourite = true),
        beer(id = "c", name = "Guinness Draught", brewery = "Guinness", type = "Stout",
            grade = 8, price = null, dateAdded = 200L),
        beer(id = "d", name = "Mariestads Export", brewery = "Spendrups", type = "Lager",
            grade = 8, price = 18.0, dateAdded = 400L, buyAgain = true, favourite = true),
    )

    @Test
    fun `query matches name brewery and type case-insensitively`() {
        assertEquals(listOf("b"), result(BeerFilter(query = "punk")).map { it.id })
        assertEquals(listOf("c"), result(BeerFilter(query = "GUINN")).map { it.id })
        assertEquals(setOf("a", "d"), result(BeerFilter(query = "lager")).map { it.id }.toSet())
    }

    @Test
    fun `buy again filter keeps only flagged beers`() {
        assertEquals(setOf("a", "d"), result(BeerFilter(buyAgainOnly = true)).map { it.id }.toSet())
    }

    @Test
    fun `favourites filter keeps only starred beers`() {
        assertEquals(setOf("b", "d"), result(BeerFilter(favouritesOnly = true)).map { it.id }.toSet())
    }

    @Test
    fun `type filter matches any selected type`() {
        assertEquals(setOf("a", "d", "c"),
            result(BeerFilter(types = setOf("Lager", "Stout"))).map { it.id }.toSet())
    }

    @Test
    fun `filters combine`() {
        assertEquals(listOf("d"),
            result(BeerFilter(buyAgainOnly = true, favouritesOnly = true)).map { it.id })
    }

    @Test
    fun `sort by grade descends with newest first on ties`() {
        assertEquals(listOf("b", "d", "c", "a"), result(sort = BeerSort.GRADE).map { it.id })
    }

    @Test
    fun `sort by price ascends with null prices last`() {
        assertEquals(listOf("a", "d", "b", "c"), result(sort = BeerSort.PRICE).map { it.id })
    }

    @Test
    fun `sort by name is alphabetical`() {
        assertEquals(listOf("a", "c", "d", "b"), result(sort = BeerSort.NAME_BREWERY).map { it.id })
    }

    @Test
    fun `sort by date added puts newest first`() {
        assertEquals(listOf("d", "b", "c", "a"), result(sort = BeerSort.DATE_ADDED).map { it.id })
    }

    private fun result(filter: BeerFilter = BeerFilter(), sort: BeerSort = BeerSort.GRADE) =
        filterAndSort(beers, filter, sort)
}
```

- [ ] **Step 2: Run tests to verify they fail**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: compilation FAILS with unresolved references `BeerFilter`, `BeerSort`, `filterAndSort`.

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/beertracker/domain/BeerListLogic.kt`:

```kotlin
package com.beertracker.domain

enum class BeerSort { GRADE, PRICE, NAME_BREWERY, DATE_ADDED }

data class BeerFilter(
    val query: String = "",
    val buyAgainOnly: Boolean = false,
    val favouritesOnly: Boolean = false,
    val types: Set<String> = emptySet(),
)

fun filterAndSort(beers: List<TriedBeer>, filter: BeerFilter, sort: BeerSort): List<TriedBeer> {
    val query = filter.query.trim()
    val filtered = beers.filter { beer ->
        val matchesQuery = query.isEmpty() ||
            listOf(beer.name, beer.brewery, beer.type).any { it.contains(query, ignoreCase = true) }
        matchesQuery &&
            (!filter.buyAgainOnly || beer.buyAgain) &&
            (!filter.favouritesOnly || beer.favourite) &&
            (filter.types.isEmpty() || beer.type in filter.types)
    }
    return when (sort) {
        BeerSort.GRADE -> filtered.sortedWith(
            compareByDescending<TriedBeer> { it.grade }.thenByDescending { it.dateAdded })
        BeerSort.PRICE -> filtered.sortedWith(
            compareBy(nullsLast(naturalOrder<Double>())) { it.price })
        BeerSort.NAME_BREWERY -> filtered.sortedWith(
            compareBy({ it.name.lowercase() }, { it.brewery.lowercase() }))
        BeerSort.DATE_ADDED -> filtered.sortedByDescending { it.dateAdded }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/beertracker/domain/BeerListLogic.kt app/src/test/java/com/beertracker/BeerListLogicTest.kt
git commit -m "[App] Filter and sort logic for the beer list"
```

---

### Task 4: Room persistence

**Files:**
- Create: `app/src/main/java/com/beertracker/data/BeerEntity.kt`
- Create: `app/src/main/java/com/beertracker/data/BeerDao.kt`
- Create: `app/src/main/java/com/beertracker/data/BeerDatabase.kt`
- Create: `app/src/main/java/com/beertracker/data/RoomBeerRepository.kt`
- Modify: `app/src/main/java/com/beertracker/BeerApp.kt`
- Test: `app/src/test/java/com/beertracker/BeerDaoTest.kt`

**Interfaces:**
- Consumes: `TriedBeer`, `BeerRepository` from Task 2.
- Produces: `RoomBeerRepository(dao: BeerDao) : BeerRepository`, `BeerDatabase.build(context): BeerDatabase`, `BeerApp.container: AppContainer` where `AppContainer.beerRepository: BeerRepository`. UI tasks obtain the repository via `(application as BeerApp).container.beerRepository`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/beertracker/BeerDaoTest.kt`:

```kotlin
package com.beertracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.beertracker.data.BeerDatabase
import com.beertracker.data.RoomBeerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BeerDaoTest {

    private lateinit var db: BeerDatabase
    private lateinit var repo: RoomBeerRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), BeerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RoomBeerRepository(db.beerDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `round trip preserves all fields`() = runTest {
        val original = beer(
            id = "a", name = "Punk IPA", brewery = "BrewDog", type = "IPA",
            alcoholPercent = 5.6, volumeMl = 330, price = 29.5, grade = 9,
            note = "hoppy", aftertaste = "citrus bitter",
            goesWellWith = listOf("Red meat", "Dessert"),
            buyAgain = true, favourite = true, dateAdded = 12345L)
        repo.addBeer(original)
        assertEquals(original, repo.getBeer("a"))
        assertEquals(listOf(original), repo.observeBeers().first())
    }

    @Test
    fun `update replaces and delete removes`() = runTest {
        repo.addBeer(beer(id = "a", grade = 6))
        repo.updateBeer(beer(id = "a", grade = 10))
        assertEquals(10, repo.getBeer("a")?.grade)
        repo.deleteBeer("a")
        assertNull(repo.getBeer("a"))
        assertEquals(emptyList<Any>(), repo.observeBeers().first())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: compilation FAILS with unresolved references in the `com.beertracker.data` package.

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/beertracker/data/BeerEntity.kt`:

```kotlin
package com.beertracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.beertracker.domain.TriedBeer

@Entity(tableName = "tried_beers")
data class BeerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brewery: String,
    val type: String,
    val alcoholPercent: Double?,
    val volumeMl: Int?,
    val price: Double?,
    val grade: Int,
    val note: String,
    val aftertaste: String,
    val goesWellWith: List<String>,
    val buyAgain: Boolean,
    val favourite: Boolean,
    val dateAdded: Long,
    val catalogArticleNumber: String?,
    val addedBy: String?,
)

class Converters {
    @TypeConverter
    fun listToString(value: List<String>): String = value.joinToString("\u001F")

    @TypeConverter
    fun stringToList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split("\u001F")
}

fun BeerEntity.toDomain() = TriedBeer(
    id, name, brewery, type, alcoholPercent, volumeMl, price, grade,
    note, aftertaste, goesWellWith, buyAgain, favourite, dateAdded,
    catalogArticleNumber, addedBy,
)

fun TriedBeer.toEntity() = BeerEntity(
    id, name, brewery, type, alcoholPercent, volumeMl, price, grade,
    note, aftertaste, goesWellWith, buyAgain, favourite, dateAdded,
    catalogArticleNumber, addedBy,
)
```

`app/src/main/java/com/beertracker/data/BeerDao.kt`:

```kotlin
package com.beertracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BeerDao {
    @Query("SELECT * FROM tried_beers")
    fun observeAll(): Flow<List<BeerEntity>>

    @Query("SELECT * FROM tried_beers WHERE id = :id")
    suspend fun getById(id: String): BeerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BeerEntity)

    @Query("DELETE FROM tried_beers WHERE id = :id")
    suspend fun deleteById(id: String)
}
```

`app/src/main/java/com/beertracker/data/BeerDatabase.kt`:

```kotlin
package com.beertracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [BeerEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class BeerDatabase : RoomDatabase() {
    abstract fun beerDao(): BeerDao

    companion object {
        fun build(context: Context): BeerDatabase =
            Room.databaseBuilder(context, BeerDatabase::class.java, "beertracker.db").build()
    }
}
```

`app/src/main/java/com/beertracker/data/RoomBeerRepository.kt`:

```kotlin
package com.beertracker.data

import com.beertracker.domain.BeerRepository
import com.beertracker.domain.TriedBeer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomBeerRepository(private val dao: BeerDao) : BeerRepository {

    override fun observeBeers(): Flow<List<TriedBeer>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getBeer(id: String): TriedBeer? = dao.getById(id)?.toDomain()

    override suspend fun addBeer(beer: TriedBeer) = dao.upsert(beer.toEntity())

    override suspend fun updateBeer(beer: TriedBeer) = dao.upsert(beer.toEntity())

    override suspend fun deleteBeer(id: String) = dao.deleteById(id)
}
```

Replace the whole content of `app/src/main/java/com/beertracker/BeerApp.kt` with:

```kotlin
package com.beertracker

import android.app.Application
import android.content.Context
import com.beertracker.data.BeerDatabase
import com.beertracker.data.RoomBeerRepository
import com.beertracker.domain.BeerRepository

class AppContainer(context: Context) {
    private val db = BeerDatabase.build(context)
    val beerRepository: BeerRepository = RoomBeerRepository(db.beerDao())
}

class BeerApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass. Robolectric downloads an android-all jar on first run, which needs network and takes a minute.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/beertracker/data app/src/main/java/com/beertracker/BeerApp.kt app/src/test/java/com/beertracker/BeerDaoTest.kt
git commit -m "[App] Room persistence behind BeerRepository"
```

---

### Task 5: Overview view-model

**Files:**
- Create: `app/src/main/java/com/beertracker/ui/OverviewViewModel.kt`
- Create: `app/src/test/java/com/beertracker/MainDispatcherRule.kt`
- Test: `app/src/test/java/com/beertracker/OverviewViewModelTest.kt`

**Interfaces:**
- Consumes: `BeerRepository`, `BeerFilter`, `BeerSort`, `filterAndSort` from Tasks 2 and 3.
- Produces: `OverviewViewModel(repository)` with `uiState: StateFlow<OverviewUiState>` and functions `setQuery(String)`, `toggleBuyAgainOnly()`, `toggleFavouritesOnly()`, `toggleType(String)`, `setSort(BeerSort)`, plus `OverviewViewModel.Factory`. `OverviewUiState(beers: List<TriedBeer>, filter: BeerFilter, sort: BeerSort, availableTypes: List<String>)`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/beertracker/MainDispatcherRule.kt`:

```kotlin
package com.beertracker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
```

`app/src/test/java/com/beertracker/OverviewViewModelTest.kt`:

```kotlin
package com.beertracker

import com.beertracker.domain.BeerSort
import com.beertracker.ui.OverviewViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OverviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun kotlinx.coroutines.test.TestScope.collecting(vm: OverviewViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
    }

    @Test
    fun `default sort is grade descending`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "low", grade = 6))
        repo.addBeer(beer(id = "high", grade = 10))
        val vm = OverviewViewModel(repo)
        collecting(vm)
        assertEquals(listOf("high", "low"), vm.uiState.value.beers.map { it.id })
    }

    @Test
    fun `query narrows the list`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a", name = "Falcon"))
        repo.addBeer(beer(id = "b", name = "Punk IPA"))
        val vm = OverviewViewModel(repo)
        collecting(vm)
        vm.setQuery("punk")
        assertEquals(listOf("b"), vm.uiState.value.beers.map { it.id })
    }

    @Test
    fun `type toggle filters and toggles off again`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a", type = "Lager"))
        repo.addBeer(beer(id = "b", type = "IPA"))
        val vm = OverviewViewModel(repo)
        collecting(vm)
        vm.toggleType("IPA")
        assertEquals(listOf("b"), vm.uiState.value.beers.map { it.id })
        vm.toggleType("IPA")
        assertEquals(2, vm.uiState.value.beers.size)
    }

    @Test
    fun `available types are distinct and sorted`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a", type = "Lager"))
        repo.addBeer(beer(id = "b", type = "IPA"))
        repo.addBeer(beer(id = "c", type = "Lager"))
        val vm = OverviewViewModel(repo)
        collecting(vm)
        assertEquals(listOf("IPA", "Lager"), vm.uiState.value.availableTypes)
    }

    @Test
    fun `sort can be changed`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "old", grade = 10, dateAdded = 1L))
        repo.addBeer(beer(id = "new", grade = 5, dateAdded = 2L))
        val vm = OverviewViewModel(repo)
        collecting(vm)
        vm.setSort(BeerSort.DATE_ADDED)
        assertEquals(listOf("new", "old"), vm.uiState.value.beers.map { it.id })
        assertTrue(vm.uiState.value.sort == BeerSort.DATE_ADDED)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: compilation FAILS with unresolved reference `OverviewViewModel`.

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/beertracker/ui/OverviewViewModel.kt`:

```kotlin
package com.beertracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.beertracker.BeerApp
import com.beertracker.domain.BeerFilter
import com.beertracker.domain.BeerRepository
import com.beertracker.domain.BeerSort
import com.beertracker.domain.TriedBeer
import com.beertracker.domain.filterAndSort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class OverviewUiState(
    val beers: List<TriedBeer> = emptyList(),
    val filter: BeerFilter = BeerFilter(),
    val sort: BeerSort = BeerSort.GRADE,
    val availableTypes: List<String> = emptyList(),
)

class OverviewViewModel(repository: BeerRepository) : ViewModel() {

    private val filter = MutableStateFlow(BeerFilter())
    private val sort = MutableStateFlow(BeerSort.GRADE)

    val uiState: StateFlow<OverviewUiState> =
        combine(repository.observeBeers(), filter, sort) { beers, f, s ->
            OverviewUiState(
                beers = filterAndSort(beers, f, s),
                filter = f,
                sort = s,
                availableTypes = beers.map { it.type }.filter { it.isNotBlank() }.distinct().sorted(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    fun setQuery(query: String) = filter.update { it.copy(query = query) }

    fun toggleBuyAgainOnly() = filter.update { it.copy(buyAgainOnly = !it.buyAgainOnly) }

    fun toggleFavouritesOnly() = filter.update { it.copy(favouritesOnly = !it.favouritesOnly) }

    fun toggleType(type: String) = filter.update {
        it.copy(types = if (type in it.types) it.types - type else it.types + type)
    }

    fun setSort(value: BeerSort) {
        sort.value = value
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BeerApp
                OverviewViewModel(app.container.beerRepository)
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/beertracker/ui/OverviewViewModel.kt app/src/test/java/com/beertracker/MainDispatcherRule.kt app/src/test/java/com/beertracker/OverviewViewModelTest.kt
git commit -m "[App] Overview view-model with search, filters, sort"
```

---

### Task 6: Overview screen and navigation skeleton

UI task: no unit tests, verified by building and running on a device.

**Files:**
- Create: `app/src/main/java/com/beertracker/ui/OverviewScreen.kt`
- Modify: `app/src/main/java/com/beertracker/MainActivity.kt`

**Interfaces:**
- Consumes: `OverviewViewModel` and `OverviewUiState` from Task 5, `BeerSort` from Task 3.
- Produces: `OverviewScreen(viewModel, onAddClick: () -> Unit, onBeerClick: (String) -> Unit)` and `BeerNavHost()` in MainActivity with routes `overview`, `edit?beerId={beerId}` (placeholder until Task 8), `detail/{beerId}` (placeholder until Task 9).

- [ ] **Step 1: Write the Overview screen**

`app/src/main/java/com/beertracker/ui/OverviewScreen.kt`:

```kotlin
package com.beertracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beertracker.domain.BeerSort
import com.beertracker.domain.TriedBeer

private val sortLabels = mapOf(
    BeerSort.GRADE to "Grade",
    BeerSort.PRICE to "Price",
    BeerSort.NAME_BREWERY to "Name",
    BeerSort.DATE_ADDED to "Newest",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel,
    onAddClick: () -> Unit,
    onBeerClick: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("BeerTracker") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = "Add beer")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.filter.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Search name, brewery, type") },
                singleLine = true,
            )
            FilterRow(state, viewModel)
            if (state.beers.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No beers yet. Tap + to add your first one.")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.beers, key = { it.id }) { beer ->
                        BeerRow(beer, onClick = { onBeerClick(beer.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(state: OverviewUiState, viewModel: OverviewViewModel) {
    var typeMenuOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = state.filter.buyAgainOnly,
            onClick = viewModel::toggleBuyAgainOnly,
            label = { Text("Buy again") },
        )
        FilterChip(
            selected = state.filter.favouritesOnly,
            onClick = viewModel::toggleFavouritesOnly,
            label = { Text("Favourites") },
        )
        Box {
            FilterChip(
                selected = state.filter.types.isNotEmpty(),
                onClick = { typeMenuOpen = true },
                label = {
                    val count = state.filter.types.size
                    Text(if (count == 0) "Type" else "Type ($count)")
                },
            )
            DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                if (state.availableTypes.isEmpty()) {
                    DropdownMenuItem(text = { Text("No types yet") }, onClick = {})
                }
                state.availableTypes.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(if (type in state.filter.types) "[x] $type" else type) },
                        onClick = { viewModel.toggleType(type) },
                    )
                }
            }
        }
        Box {
            TextButton(onClick = { sortMenuOpen = true }) {
                Text("Sort: ${sortLabels.getValue(state.sort)}")
            }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                BeerSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(sortLabels.getValue(sort)) },
                        onClick = {
                            viewModel.setSort(sort)
                            sortMenuOpen = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BeerRow(beer: TriedBeer, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(beer.name, style = MaterialTheme.typography.titleMedium)
                if (beer.favourite) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Star, contentDescription = "Favourite",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (beer.buyAgain) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Check, contentDescription = "Buy again",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            val subtitle = listOf(beer.brewery, beer.type)
                .filter { it.isNotBlank() }
                .joinToString(", ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("${beer.grade}", style = MaterialTheme.typography.headlineMedium)
    }
}
```

- [ ] **Step 2: Wire navigation in MainActivity**

Replace the whole content of `app/src/main/java/com/beertracker/MainActivity.kt` with:

```kotlin
package com.beertracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.beertracker.ui.OverviewScreen
import com.beertracker.ui.OverviewViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BeerNavHost()
            }
        }
    }
}

@Composable
fun BeerNavHost() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "overview") {
        composable("overview") {
            OverviewScreen(
                viewModel = viewModel(factory = OverviewViewModel.Factory),
                onAddClick = { navController.navigate("edit") },
                onBeerClick = { id -> navController.navigate("detail/$id") },
            )
        }
        composable(
            route = "edit?beerId={beerId}",
            arguments = listOf(navArgument("beerId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) {
            Text("Add or edit screen arrives in Task 8")
        }
        composable("detail/{beerId}") {
            Text("Detail screen arrives in Task 9")
        }
    }
}
```

- [ ] **Step 3: Build and verify**

```powershell
.\gradlew.bat assembleDebug
```

Expected: BUILD SUCCESSFUL. If the user's phone is connected with USB debugging:

```powershell
C:\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

Manual check: app opens, shows the search box, filter chips, sort button, and the empty-state text. The + button navigates to the Task 8 placeholder. If no device is available, the successful build is the gate and the on-phone check is deferred to the user.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/java/com/beertracker/ui/OverviewScreen.kt app/src/main/java/com/beertracker/MainActivity.kt
git commit -m "[App] Overview screen with navigation skeleton"
```

---

### Task 7: Add and edit view-model

**Files:**
- Create: `app/src/main/java/com/beertracker/ui/AddEditBeerViewModel.kt`
- Test: `app/src/test/java/com/beertracker/AddEditBeerViewModelTest.kt`

**Interfaces:**
- Consumes: `BeerRepository`, `TriedBeer`, `Presets` from Task 2.
- Produces: `AddEditBeerViewModel(repository, clock)` with `form: StateFlow<BeerFormState>`, `typeOptions: StateFlow<List<String>>`, `pairingOptions: StateFlow<List<String>>`, `load(beerId: String)`, `update(transform: (BeerFormState) -> BeerFormState)`, `save()`, and `AddEditBeerViewModel.Factory`. `BeerFormState` fields exactly as written below.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/beertracker/AddEditBeerViewModelTest.kt`:

```kotlin
package com.beertracker

import com.beertracker.ui.AddEditBeerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AddEditBeerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `save with blank name sets error and stores nothing`() = runTest {
        val repo = FakeBeerRepository()
        val vm = AddEditBeerViewModel(repo)
        vm.update { it.copy(name = "  ") }
        vm.save()
        assertTrue(vm.form.value.nameError)
        assertFalse(vm.form.value.saved)
        assertEquals(0, repo.observeBeers().first().size)
    }

    @Test
    fun `save parses numbers and comma decimals`() = runTest {
        val repo = FakeBeerRepository()
        val vm = AddEditBeerViewModel(repo, clock = { 777L })
        vm.update {
            it.copy(
                name = "Punk IPA", brewery = "BrewDog", type = "IPA",
                alcoholPercent = "5,6", volumeMl = "330", price = "29.50", grade = 9,
            )
        }
        vm.save()
        val saved = repo.observeBeers().first().single()
        assertEquals("Punk IPA", saved.name)
        assertEquals(5.6, saved.alcoholPercent!!, 0.001)
        assertEquals(330, saved.volumeMl)
        assertEquals(29.5, saved.price!!, 0.001)
        assertEquals(9, saved.grade)
        assertEquals(777L, saved.dateAdded)
        assertTrue(vm.form.value.saved)
    }

    @Test
    fun `blank numeric fields save as null`() = runTest {
        val repo = FakeBeerRepository()
        val vm = AddEditBeerViewModel(repo)
        vm.update { it.copy(name = "Mystery Beer") }
        vm.save()
        val saved = repo.observeBeers().first().single()
        assertNull(saved.alcoholPercent)
        assertNull(saved.volumeMl)
        assertNull(saved.price)
    }

    @Test
    fun `custom pairing is appended to selected chips`() = runTest {
        val repo = FakeBeerRepository()
        val vm = AddEditBeerViewModel(repo)
        vm.update {
            it.copy(name = "X", pairings = setOf("Salmon"), customPairing = "Tacos")
        }
        vm.save()
        val saved = repo.observeBeers().first().single()
        assertEquals(setOf("Salmon", "Tacos"), saved.goesWellWith.toSet())
    }

    @Test
    fun `editing preserves id and dateAdded`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a", name = "Old Name", grade = 6, dateAdded = 111L))
        val vm = AddEditBeerViewModel(repo, clock = { 999L })
        vm.load("a")
        vm.update { it.copy(name = "New Name", grade = 8) }
        vm.save()
        val saved = repo.observeBeers().first().single()
        assertEquals("a", saved.id)
        assertEquals("New Name", saved.name)
        assertEquals(8, saved.grade)
        assertEquals(111L, saved.dateAdded)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: compilation FAILS with unresolved reference `AddEditBeerViewModel`.

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/beertracker/ui/AddEditBeerViewModel.kt`:

```kotlin
package com.beertracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.beertracker.BeerApp
import com.beertracker.domain.BeerRepository
import com.beertracker.domain.Presets
import com.beertracker.domain.TriedBeer
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BeerFormState(
    val id: String? = null,
    val name: String = "",
    val brewery: String = "",
    val type: String = "",
    val alcoholPercent: String = "",
    val volumeMl: String = "",
    val price: String = "",
    val grade: Int = 7,
    val note: String = "",
    val aftertaste: String = "",
    val pairings: Set<String> = emptySet(),
    val customPairing: String = "",
    val buyAgain: Boolean = false,
    val favourite: Boolean = false,
    val nameError: Boolean = false,
    val saved: Boolean = false,
)

class AddEditBeerViewModel(
    private val repository: BeerRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val _form = MutableStateFlow(BeerFormState())
    val form: StateFlow<BeerFormState> = _form.asStateFlow()

    val typeOptions: StateFlow<List<String>> = repository.observeBeers()
        .map { beers ->
            (Presets.beerTypes + beers.map { it.type }).filter { it.isNotBlank() }.distinct()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Presets.beerTypes)

    val pairingOptions: StateFlow<List<String>> = repository.observeBeers()
        .map { beers -> (Presets.pairings + beers.flatMap { it.goesWellWith }).distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Presets.pairings)

    private var existing: TriedBeer? = null

    fun load(beerId: String) {
        viewModelScope.launch {
            val loaded = repository.getBeer(beerId) ?: return@launch
            existing = loaded
            _form.value = BeerFormState(
                id = loaded.id,
                name = loaded.name,
                brewery = loaded.brewery,
                type = loaded.type,
                alcoholPercent = loaded.alcoholPercent?.toString() ?: "",
                volumeMl = loaded.volumeMl?.toString() ?: "",
                price = loaded.price?.toString() ?: "",
                grade = loaded.grade,
                note = loaded.note,
                aftertaste = loaded.aftertaste,
                pairings = loaded.goesWellWith.toSet(),
                buyAgain = loaded.buyAgain,
                favourite = loaded.favourite,
            )
        }
    }

    fun update(transform: (BeerFormState) -> BeerFormState) = _form.update(transform)

    fun save() {
        val f = _form.value
        if (f.name.isBlank()) {
            _form.update { it.copy(nameError = true) }
            return
        }
        val pairings = buildList {
            addAll(f.pairings)
            f.customPairing.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
        }
        val beer = TriedBeer(
            id = f.id ?: UUID.randomUUID().toString(),
            name = f.name.trim(),
            brewery = f.brewery.trim(),
            type = f.type.trim(),
            alcoholPercent = f.alcoholPercent.replace(',', '.').toDoubleOrNull(),
            volumeMl = f.volumeMl.trim().toIntOrNull(),
            price = f.price.replace(',', '.').toDoubleOrNull(),
            grade = f.grade,
            note = f.note.trim(),
            aftertaste = f.aftertaste.trim(),
            goesWellWith = pairings,
            buyAgain = f.buyAgain,
            favourite = f.favourite,
            dateAdded = existing?.dateAdded ?: clock(),
            catalogArticleNumber = existing?.catalogArticleNumber,
            addedBy = existing?.addedBy,
        )
        viewModelScope.launch {
            if (existing == null) repository.addBeer(beer) else repository.updateBeer(beer)
            _form.update { it.copy(saved = true) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BeerApp
                AddEditBeerViewModel(app.container.beerRepository)
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/beertracker/ui/AddEditBeerViewModel.kt app/src/test/java/com/beertracker/AddEditBeerViewModelTest.kt
git commit -m "[App] Add and edit view-model with validation and parsing"
```

---

### Task 8: Add and edit screen

UI task: verified by building and running on a device.

**Files:**
- Create: `app/src/main/java/com/beertracker/ui/AddEditScreen.kt`
- Modify: `app/src/main/java/com/beertracker/MainActivity.kt` (replace the `edit?beerId={beerId}` placeholder)

**Interfaces:**
- Consumes: `AddEditBeerViewModel`, `BeerFormState` from Task 7.
- Produces: `AddEditScreen(viewModel, beerId: String?, onDone: () -> Unit)`.

- [ ] **Step 1: Write the screen**

`app/src/main/java/com/beertracker/ui/AddEditScreen.kt`:

```kotlin
package com.beertracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditScreen(
    viewModel: AddEditBeerViewModel,
    beerId: String?,
    onDone: () -> Unit,
) {
    LaunchedEffect(beerId) {
        if (beerId != null) viewModel.load(beerId)
    }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val typeOptions by viewModel.typeOptions.collectAsStateWithLifecycle()
    val pairingOptions by viewModel.pairingOptions.collectAsStateWithLifecycle()

    LaunchedEffect(form.saved) {
        if (form.saved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (beerId == null) "Add beer" else "Edit beer") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v, nameError = false) } },
                label = { Text("Name *") },
                isError = form.nameError,
                supportingText = { if (form.nameError) Text("Name is required") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.brewery,
                onValueChange = { v -> viewModel.update { it.copy(brewery = v) } },
                label = { Text("Brewery") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.type,
                onValueChange = { v -> viewModel.update { it.copy(type = v) } },
                label = { Text("Type") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                typeOptions.forEach { option ->
                    SuggestionChip(
                        onClick = { viewModel.update { it.copy(type = option) } },
                        label = { Text(option) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = form.alcoholPercent,
                    onValueChange = { v -> viewModel.update { it.copy(alcoholPercent = v) } },
                    label = { Text("Alc %") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = form.volumeMl,
                    onValueChange = { v -> viewModel.update { it.copy(volumeMl = v) } },
                    label = { Text("Volume ml") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = form.price,
                    onValueChange = { v -> viewModel.update { it.copy(price = v) } },
                    label = { Text("Price kr") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Text("Grade", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (5..10).forEach { g ->
                    FilterChip(
                        selected = form.grade == g,
                        onClick = { viewModel.update { it.copy(grade = g) } },
                        label = { Text("$g") },
                    )
                }
            }
            OutlinedTextField(
                value = form.note,
                onValueChange = { v -> viewModel.update { it.copy(note = v) } },
                label = { Text("Note") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.aftertaste,
                onValueChange = { v -> viewModel.update { it.copy(aftertaste = v) } },
                label = { Text("Aftertaste") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Goes well with", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pairingOptions.forEach { option ->
                    FilterChip(
                        selected = option in form.pairings,
                        onClick = {
                            viewModel.update {
                                val s = it.pairings
                                it.copy(pairings = if (option in s) s - option else s + option)
                            }
                        },
                        label = { Text(option) },
                    )
                }
            }
            OutlinedTextField(
                value = form.customPairing,
                onValueChange = { v -> viewModel.update { it.copy(customPairing = v) } },
                label = { Text("Other pairing") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = form.buyAgain,
                    onCheckedChange = { v -> viewModel.update { it.copy(buyAgain = v) } },
                )
                Text("  Buy again")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = form.favourite,
                    onCheckedChange = { v -> viewModel.update { it.copy(favourite = v) } },
                )
                Text("  Favourite")
            }
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
        }
    }
}
```

- [ ] **Step 2: Replace the placeholder route in MainActivity**

In `app/src/main/java/com/beertracker/MainActivity.kt`, replace the `edit?beerId={beerId}` composable body:

```kotlin
composable(
    route = "edit?beerId={beerId}",
    arguments = listOf(navArgument("beerId") {
        type = NavType.StringType
        nullable = true
        defaultValue = null
    }),
) { backStackEntry ->
    AddEditScreen(
        viewModel = viewModel(factory = AddEditBeerViewModel.Factory),
        beerId = backStackEntry.arguments?.getString("beerId"),
        onDone = { navController.popBackStack() },
    )
}
```

Add the imports `com.beertracker.ui.AddEditScreen` and `com.beertracker.ui.AddEditBeerViewModel`, and remove the now-unused `androidx.compose.material3.Text` import if nothing else uses it.

- [ ] **Step 3: Build and verify**

```powershell
.\gradlew.bat assembleDebug
```

Expected: BUILD SUCCESSFUL. On a device: add a beer with name, type from a suggestion chip, grade 8, a pairing chip plus a custom pairing, save, and confirm it appears on the overview sorted correctly. Saving with an empty name must show the inline error instead of saving.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/java/com/beertracker/ui/AddEditScreen.kt app/src/main/java/com/beertracker/MainActivity.kt
git commit -m "[App] Add and edit screen"
```

---

### Task 9: Detail screen with edit, delete, and toggles

**Files:**
- Create: `app/src/main/java/com/beertracker/ui/DetailViewModel.kt`
- Create: `app/src/main/java/com/beertracker/ui/DetailScreen.kt`
- Modify: `app/src/main/java/com/beertracker/MainActivity.kt` (replace the `detail/{beerId}` placeholder)
- Test: `app/src/test/java/com/beertracker/DetailViewModelTest.kt`

**Interfaces:**
- Consumes: `BeerRepository` from Task 2, `AddEditScreen` route from Task 8 (edit navigates to `edit?beerId=<id>`).
- Produces: `DetailViewModel(repository, beerId)` with `beer: StateFlow<TriedBeer?>`, `toggleFavourite()`, `toggleBuyAgain()`, `delete(onDeleted: () -> Unit)`, and `DetailViewModel.factory(beerId)`. `DetailScreen(viewModel, onEdit: (String) -> Unit, onBack: () -> Unit)`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/beertracker/DetailViewModelTest.kt`:

```kotlin
package com.beertracker

import com.beertracker.ui.DetailViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `beer flow emits the beer with the given id`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a", name = "Falcon"))
        repo.addBeer(beer(id = "b", name = "Punk IPA"))
        val vm = DetailViewModel(repo, "b")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.beer.collect() }
        assertEquals("Punk IPA", vm.beer.value?.name)
    }

    @Test
    fun `toggles flip favourite and buy again`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a", favourite = false, buyAgain = false))
        val vm = DetailViewModel(repo, "a")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.beer.collect() }
        vm.toggleFavourite()
        assertTrue(repo.getBeer("a")!!.favourite)
        vm.toggleBuyAgain()
        assertTrue(repo.getBeer("a")!!.buyAgain)
    }

    @Test
    fun `delete removes the beer and invokes the callback`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(beer(id = "a"))
        val vm = DetailViewModel(repo, "a")
        var deleted = false
        vm.delete { deleted = true }
        assertTrue(deleted)
        assertEquals(0, repo.observeBeers().first().size)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: compilation FAILS with unresolved reference `DetailViewModel`.

- [ ] **Step 3: Write the view-model**

`app/src/main/java/com/beertracker/ui/DetailViewModel.kt`:

```kotlin
package com.beertracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.beertracker.BeerApp
import com.beertracker.domain.BeerRepository
import com.beertracker.domain.TriedBeer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(
    private val repository: BeerRepository,
    private val beerId: String,
) : ViewModel() {

    val beer: StateFlow<TriedBeer?> = repository.observeBeers()
        .map { list -> list.find { it.id == beerId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleFavourite() {
        viewModelScope.launch {
            val current = repository.getBeer(beerId) ?: return@launch
            repository.updateBeer(current.copy(favourite = !current.favourite))
        }
    }

    fun toggleBuyAgain() {
        viewModelScope.launch {
            val current = repository.getBeer(beerId) ?: return@launch
            repository.updateBeer(current.copy(buyAgain = !current.buyAgain))
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteBeer(beerId)
            onDeleted()
        }
    }

    companion object {
        fun factory(beerId: String) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BeerApp
                DetailViewModel(app.container.beerRepository, beerId)
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Write the detail screen**

`app/src/main/java/com/beertracker/ui/DetailScreen.kt`:

```kotlin
package com.beertracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
) {
    val beer by viewModel.beer.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val b = beer

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(b?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (b != null) {
                        IconButton(onClick = { onEdit(b.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (b == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    if (b.brewery.isNotBlank()) {
                        Text(b.brewery, style = MaterialTheme.typography.titleMedium)
                    }
                    if (b.type.isNotBlank()) {
                        Text(b.type, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("${b.grade}", style = MaterialTheme.typography.displayMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = b.favourite,
                    onClick = viewModel::toggleFavourite,
                    label = { Text("Favourite") },
                )
                FilterChip(
                    selected = b.buyAgain,
                    onClick = viewModel::toggleBuyAgain,
                    label = { Text("Buy again") },
                )
            }
            b.alcoholPercent?.let { InfoRow("Alcohol", "$it %") }
            b.volumeMl?.let { InfoRow("Volume", "$it ml") }
            b.price?.let { InfoRow("Price", "$it kr") }
            InfoRow(
                "Added",
                Instant.ofEpochMilli(b.dateAdded).atZone(ZoneId.systemDefault())
                    .toLocalDate().toString(),
            )
            if (b.note.isNotBlank()) InfoRow("Note", b.note)
            if (b.aftertaste.isNotBlank()) InfoRow("Aftertaste", b.aftertaste)
            if (b.goesWellWith.isNotEmpty()) {
                InfoRow("Goes well with", b.goesWellWith.joinToString(", "))
            }
        }
    }

    if (showDeleteDialog && b != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${b.name}?") },
            text = { Text("This removes the beer from your list.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete(onDeleted = onBack)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
```

- [ ] **Step 6: Replace the placeholder route in MainActivity**

In `app/src/main/java/com/beertracker/MainActivity.kt`, replace the `detail/{beerId}` composable body:

```kotlin
composable("detail/{beerId}") { backStackEntry ->
    val beerId = backStackEntry.arguments?.getString("beerId") ?: return@composable
    DetailScreen(
        viewModel = viewModel(factory = DetailViewModel.factory(beerId)),
        onEdit = { id -> navController.navigate("edit?beerId=$id") },
        onBack = { navController.popBackStack() },
    )
}
```

Add imports `com.beertracker.ui.DetailScreen` and `com.beertracker.ui.DetailViewModel`. All placeholder `Text` routes are now gone; remove the `androidx.compose.material3.Text` import from MainActivity if unused.

- [ ] **Step 7: Build and verify end to end**

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

Expected: both BUILD SUCCESSFUL. On a device, full pass: add two beers, search for one, filter by type, sort by newest, open detail, toggle favourite, edit the grade, delete one beer, confirm the overview updates each time.

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/java/com/beertracker/ui/DetailViewModel.kt app/src/main/java/com/beertracker/ui/DetailScreen.kt app/src/main/java/com/beertracker/MainActivity.kt app/src/test/java/com/beertracker/DetailViewModelTest.kt
git commit -m "[App] Detail screen with edit, delete, and toggles"
```

---

## Done Criteria for Phase 1

- All unit tests pass: `.\gradlew.bat testDebugUnitTest`
- `.\gradlew.bat assembleDebug` produces an installable APK
- On the user's phone: beers can be added manually with every field, graded 5 to 10, searched, filtered by buy-again and favourites and multi-selected types, sorted four ways, viewed, edited, and deleted; data survives app restart
- Push to GitHub: `git push`
