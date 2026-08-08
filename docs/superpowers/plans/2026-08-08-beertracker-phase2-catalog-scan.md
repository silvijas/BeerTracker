# BeerTracker Phase 2: Catalog Snapshot and Shelf-Label Scan Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The app carries an offline Systembolaget beer catalog that it keeps fresh by itself, a shelf-label scanner that pre-fills the add-beer form from that catalog, and on-demand product images on the detail screen.

**Architecture:** The catalog lives in its own Room database (`catalog.db`), completely separate from the user database (`beertracker.db`), so no catalog operation can ever touch user data. A bundled JSON seed asset is imported at first launch; after that the app refreshes the catalog directly from Systembolaget's public product-search endpoint (weekly in the background, and on demand from an overview action), replacing the catalog table only after a complete successful fetch (last good wins). The scanner is CameraX plus on-device ML Kit text recognition; all parsing and lookup logic lives in plain testable classes, and the camera itself is the only part not covered by JVM tests. The user database gains one nullable `imageUrl` column via a non-destructive migration to version 2.

**Tech Stack:** Existing stack (Kotlin 2.0.21, Compose BOM 2024.12.01, Room 2.6.1 + KSP, AGP 8.7.3, JVM 17, minSdk 26, compile/target 35) plus these new pinned dependencies: CameraX 1.4.2, ML Kit `text-recognition` 16.0.1 (bundled Latin model, fully offline), Coil 2.7.0, Room-testing 2.6.1. These are the newest stable versions compatible with compileSdk 35 and AGP 8.7.3 (CameraX 1.5 and Coil 3 target newer toolchains, so they are deliberately not used). Networking uses `HttpURLConnection` and JSON parsing uses `org.json`, both part of Android, so no network or JSON library is added.

This is plan 2 of 5 for BeerTracker v1. Phase 1 (local list) and the Phase 5 release pipeline (`.github/workflows/release.yml`, signed APK on every push to main) are already merged, so merging this phase automatically ships the catalog and scanner to the Releases page. Later plans: Phase 3 can-photo text reading, Phase 4 Firebase sync and pairing. Spec: `docs/superpowers/specs/2026-07-28-beertracker-v1-design.md` (see its "Phase 2 details" subsection).

**Execution order: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12. Strictly sequential; every task depends on the state left by the previous one.**

## Global Constraints

- Repo root: `C:\Users\SilvijaSubotic\PersonalDevelopment\BeerTracker`. All commands run from there in PowerShell. Run gradle in the foreground with a long timeout (10 minutes); first runs download dependencies.
- Run unit tests with: `.\gradlew.bat testDebugUnitTest`. The suite starts at 88 tests and must stay green after every task while it grows (roughly 150 by the end). No androidTest source set; every test must run on the JVM (plain JUnit or Robolectric).
- No device is available during implementation. Camera behavior is verified by unit tests on the parsing and lookup logic plus Robolectric rendering of the non-camera UI states; the live camera path is verified by the user on their phone after merge.
- Commits are authored by the user's git identity only. Never add Claude as author or co-author. No `Co-Authored-By` trailers, ever.
- No em dashes or en dashes anywhere: not in code, comments, strings, commit messages, or docs. Use hyphens, commas, or rewrite the sentence.
- Commit message style: `[Scope] Message` where Scope is `App`, `Docs`, `Build`, or `CI`.
- Package `com.beertracker`. Dependency versions stay pinned in `gradle/libs.versions.toml`; do not bump existing entries, only add the new pinned entries given in the tasks below.
- USER DATA SAFETY: the user's phone holds real data on user database version 1. The user database migration must be a non-destructive `ALTER TABLE ADD COLUMN`. Never attach `fallbackToDestructiveMigration` to `BeerDatabase`. The catalog database is a disposable cache and is the only place destructive behavior is allowed. A catalog refresh must never touch `beertracker.db`.
- TDD for every logic task: write the failing test, watch it fail, implement, watch it pass. UI tasks are verified by build plus Robolectric Compose tests where the pattern fits (see `app/src/test/java/com/beertracker/ui/ComposeUiSmokeTest.kt` for the house pattern: `@RunWith(RobolectricTestRunner::class)`, `@Config(application = Application::class, sdk = [35])`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, `createComposeRule()`).
- New screens must reuse the redesign building blocks: components from `ui/components/` (`ErrorState`, `LoadingState`, `EmptyState`, `SectionHeader`, `GradeMark`, `FlagToggleRow`, `BeerListItem`) and theme tokens (`BeerTrackerSpacing`, `MaterialTheme.shapes`, `MaterialTheme.colorScheme`). No hardcoded colors.

## User provisioning

None. There are zero one-time user actions in this phase: no accounts, no secrets to set, no workflows to enable. The Systembolaget product-search key that the app uses is the public key from Systembolaget's own website JavaScript (every visitor's browser sends it in the open); it ships inside the APK as a plain constant and is not treated as a secret.

---

### Task 1: Export the Room schema history while the user database is still version 1

The user database is about to gain a column. Before anything changes, turn on Room schema export and commit the version 1 schema JSON, so the migration in Task 7 can be tested against the real recorded v1 schema.

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/beertracker/data/BeerDatabase.kt`
- Create (generated): `app/schemas/com.beertracker.data.BeerDatabase/1.json`

**Interfaces:**
- Consumes: the existing `BeerDatabase` (version 1, `exportSchema = false`).
- Produces: `app/schemas/com.beertracker.data.BeerDatabase/1.json` committed to git. Task 7 loads it through `MigrationTestHelper`.

- [ ] **Step 1: Point KSP at a schema directory**

In `app/build.gradle.kts`, add this block at the top level of the file, after the closing brace of the `android { ... }` block and before `dependencies { ... }`:

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

This is the KSP argument syntax that works with AGP 8.7.3 and KSP 2.0.21-1.0.28.

- [ ] **Step 2: Turn on schema export**

In `app/src/main/java/com/beertracker/data/BeerDatabase.kt`, change the `@Database` line from:

```kotlin
@Database(entities = [BeerEntity::class], version = 1, exportSchema = false)
```

to:

```kotlin
@Database(entities = [BeerEntity::class], version = 1, exportSchema = true)
```

- [ ] **Step 3: Build so KSP writes the schema, and verify the suite is still green**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, 88 tests pass, and the file `app/schemas/com.beertracker.data.BeerDatabase/1.json` now exists (KSP writes it during compilation). Open it and confirm `"version": 1` and that the `tried_beers` table lists the 17 existing columns ending with `addedBy` (no `imageUrl` yet).

- [ ] **Step 4: Commit**

```powershell
git add app/build.gradle.kts app/src/main/java/com/beertracker/data/BeerDatabase.kt app/schemas
git commit -m "[Build] Export Room schema history at user database v1"
```

---

### Task 2: Catalog seed generator script with its own tests

A Python 3 stdlib-only script that walks Systembolaget's product search, keeps only beer, and writes the minimal snapshot JSON that the app bundles as an asset. This script is a development tool for creating and regenerating the seed; routine catalog updates happen inside the app (Tasks 5 and 6). The script's field mapping and the app's in-app fetcher mapping (Task 5) must stay field for field identical; both files say so in comments, and both test suites use the same sample product.

**Files:**
- Create: `scripts/fetch_catalog.py`
- Test: `scripts/test_fetch_catalog.py`

**Interfaces:**
- Consumes: nothing in the repo.
- Produces: `scripts/fetch_catalog.py` with pure functions `is_beer(product)`, `map_product(product)`, `to_snapshot(raw_products, today_utc, previous_snapshot=None)` and a `main()` that writes `app/src/main/assets/catalog/beers.json`. Task 3 runs it. The output JSON shape is `{"snapshotVersion": "YYYY-MM-DD", "beers": [{"articleNumber", "articleNumberShort", "name", "brewery", "type", "alcoholPercent", "volumeMl", "price", "country", "imageUrl"}, ...]}` sorted by `articleNumber`, which Task 4's `parseCatalogAsset` reads.

- [ ] **Step 1: Check that Python 3 is available**

```powershell
python --version
```

Expected: `Python 3.9` or newer. If the command is missing, install it and start a fresh shell:

```powershell
winget install --id Python.Python.3.12 -e --accept-source-agreements --accept-package-agreements
```

- [ ] **Step 2: Write the failing tests**

`scripts/test_fetch_catalog.py`:

```python
"""Tests for the seed generator's pure mapping and snapshot logic.

The SAMPLE_BEER product below is intentionally the same sample used by
SystembolagetCatalogFetcherTest.kt so the two mappers are checked against
identical input.
"""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))

import fetch_catalog

SAMPLE_BEER = {
    "productId": "50786609",
    "productNumber": "1324515",
    "productNumberShort": "13245",
    "productNameBold": "Omnipollo",
    "productNameThin": "Prodigal Pale Ale",
    "producerName": "Omnipollo",
    "categoryLevel1": "Öl",
    "categoryLevel2": "Ale",
    "categoryLevel3": "Pale Ale",
    "alcoholPercentage": 5.2,
    "volume": 330.0,
    "price": 25.9,
    "country": "Sverige",
    "images": [
        {"imageUrl": "https://product-cdn.systembolaget.se/productimages/50786609/50786609"}
    ],
}

SAMPLE_WINE = {
    "productNumber": "7000101",
    "productNameBold": "Some Wine",
    "categoryLevel1": "Vin",
}


class IsBeerTest(unittest.TestCase):
    def test_beer_is_beer(self):
        self.assertTrue(fetch_catalog.is_beer(SAMPLE_BEER))

    def test_wine_is_not_beer(self):
        self.assertFalse(fetch_catalog.is_beer(SAMPLE_WINE))


class MapProductTest(unittest.TestCase):
    def test_maps_all_fields(self):
        self.assertEqual(
            fetch_catalog.map_product(SAMPLE_BEER),
            {
                "articleNumber": "1324515",
                "articleNumberShort": "13245",
                "name": "Omnipollo Prodigal Pale Ale",
                "brewery": "Omnipollo",
                "type": "Ale",
                "alcoholPercent": 5.2,
                "volumeMl": 330,
                "price": 25.9,
                "country": "Sverige",
                "imageUrl": "https://product-cdn.systembolaget.se/productimages/50786609/50786609",
            },
        )

    def test_missing_optional_fields_become_none_or_fallbacks(self):
        mapped = fetch_catalog.map_product({"productNumber": 42, "categoryLevel1": "Öl"})
        self.assertEqual(mapped["articleNumber"], "42")
        self.assertIsNone(mapped["articleNumberShort"])
        self.assertEqual(mapped["name"], "")
        self.assertEqual(mapped["brewery"], "")
        self.assertEqual(mapped["type"], "Öl")
        self.assertIsNone(mapped["alcoholPercent"])
        self.assertIsNone(mapped["volumeMl"])
        self.assertIsNone(mapped["price"])
        self.assertIsNone(mapped["country"])
        self.assertIsNone(mapped["imageUrl"])

    def test_type_falls_back_to_category_level_3(self):
        product = dict(SAMPLE_BEER, categoryLevel2=None)
        self.assertEqual(fetch_catalog.map_product(product)["type"], "Pale Ale")

    def test_empty_image_list_gives_none(self):
        product = dict(SAMPLE_BEER, images=[])
        self.assertIsNone(fetch_catalog.map_product(product)["imageUrl"])


class ToSnapshotTest(unittest.TestCase):
    def test_filters_to_beer_only(self):
        snapshot = fetch_catalog.to_snapshot([SAMPLE_BEER, SAMPLE_WINE], "2026-08-08")
        self.assertEqual(len(snapshot["beers"]), 1)
        self.assertEqual(snapshot["snapshotVersion"], "2026-08-08")

    def test_sorts_and_deduplicates_by_article_number(self):
        a = dict(SAMPLE_BEER, productNumber="9")
        b = dict(SAMPLE_BEER, productNumber="10")
        duplicate = dict(SAMPLE_BEER, productNumber="9")
        snapshot = fetch_catalog.to_snapshot([a, duplicate, b], "2026-08-08")
        self.assertEqual(
            [beer["articleNumber"] for beer in snapshot["beers"]],
            ["10", "9"],
        )

    def test_keeps_previous_version_when_content_is_unchanged(self):
        first = fetch_catalog.to_snapshot([SAMPLE_BEER], "2026-08-01")
        second = fetch_catalog.to_snapshot([SAMPLE_BEER], "2026-08-08", previous_snapshot=first)
        self.assertEqual(second["snapshotVersion"], "2026-08-01")

    def test_bumps_version_when_content_changes(self):
        first = fetch_catalog.to_snapshot([SAMPLE_BEER], "2026-08-01")
        changed = dict(SAMPLE_BEER, price=30.0)
        second = fetch_catalog.to_snapshot([changed], "2026-08-08", previous_snapshot=first)
        self.assertEqual(second["snapshotVersion"], "2026-08-08")


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 3: Run the tests to verify they fail**

```powershell
python -m unittest discover -s scripts -p "test_*.py" -v
```

Expected: FAIL with `ModuleNotFoundError: No module named 'fetch_catalog'`.

- [ ] **Step 4: Write the script**

`scripts/fetch_catalog.py`:

```python
"""Regenerates the bundled Systembolaget beer catalog seed.

Walks the public product-search API page by page, keeps only beer
(categoryLevel1 == "Öl"), maps each product to the minimal schema the app
bundles, and writes app/src/main/assets/catalog/beers.json.

This is a development tool for creating or regenerating the seed asset that
ships inside the APK. Routine catalog updates happen inside the app itself,
which fetches the same endpoint with the same mapping (see
app/src/main/java/com/beertracker/data/CatalogFetcher.kt). The map_product
function here and mapProduct there must stay field for field identical.

Usage:
    $env:SYSTEMBOLAGET_API_KEY = "<public product-search key>"
    python scripts/fetch_catalog.py

    python scripts/fetch_catalog.py --input-file assortment.json

The optional --input-file skips the network and reads a JSON array of raw
product objects using the same field names as the live API, for example the
community mirror file data/assortment.json from the
AlexGustafsson/systembolaget-api-data repository.

The key is the public product-search key visible in Systembolaget's own
website JavaScript; it is not a secret. It is read from the environment here
only to keep this script copy-paste safe in shell history and logs.
"""

import argparse
import json
import os
import sys
import time
import urllib.request
from urllib.parse import urlencode

BASE_URL = "https://api-extern.systembolaget.se/sb-api-ecommerce/v1/productsearch/search"
REFERER = "https://www.systembolaget.se/"
PAGE_SIZE = 30
PAGE_DELAY_SECONDS = 0.3
MAX_PAGES = 500
OUTPUT_PATH = os.path.join("app", "src", "main", "assets", "catalog", "beers.json")
MIN_EXPECTED_BEERS = 1000
MAX_EXPECTED_BEERS = 3000


def is_beer(product):
    return product.get("categoryLevel1") == "Öl"


def map_product(product):
    """Maps one raw API product to the minimal bundled schema.

    Must stay field for field identical to mapProduct in
    app/src/main/java/com/beertracker/data/CatalogFetcher.kt.
    """
    images = product.get("images") or []
    image_url = None
    if images:
        candidate = (images[0] or {}).get("imageUrl")
        if candidate:
            image_url = candidate
    name_parts = [
        (product.get("productNameBold") or "").strip(),
        (product.get("productNameThin") or "").strip(),
    ]
    name = " ".join(part for part in name_parts if part)
    volume = product.get("volume")
    return {
        "articleNumber": str(product.get("productNumber") or ""),
        "articleNumberShort": str(product.get("productNumberShort") or "") or None,
        "name": name,
        "brewery": product.get("producerName") or "",
        "type": product.get("categoryLevel2") or product.get("categoryLevel3") or "Öl",
        "alcoholPercent": product.get("alcoholPercentage"),
        "volumeMl": int(round(volume)) if volume is not None else None,
        "price": product.get("price"),
        "country": product.get("country"),
        "imageUrl": image_url,
    }


def to_snapshot(raw_products, today_utc, previous_snapshot=None):
    """Filters, maps, deduplicates, and sorts. Keeps the previous
    snapshotVersion when the beer list is byte-for-byte unchanged, so an
    unchanged assortment produces an unchanged file."""
    unique = {}
    for product in raw_products:
        if not is_beer(product):
            continue
        beer = map_product(product)
        if beer["articleNumber"]:
            unique[beer["articleNumber"]] = beer
    ordered = [unique[key] for key in sorted(unique)]
    version = today_utc
    if previous_snapshot is not None and previous_snapshot.get("beers") == ordered:
        version = previous_snapshot.get("snapshotVersion", today_utc)
    return {"snapshotVersion": version, "beers": ordered}


def fetch_all_products(api_key):
    """Polite sequential pagination: size 30, a small delay between requests,
    stop at the first empty page."""
    products = []
    for page in range(1, MAX_PAGES + 1):
        params = urlencode({"size": PAGE_SIZE, "page": page, "categoryLevel1": "Öl"})
        request = urllib.request.Request(
            BASE_URL + "?" + params,
            headers={
                "ocp-apim-subscription-key": api_key,
                "Referer": REFERER,
                "Accept": "application/json",
                "User-Agent": "BeerTracker seed generator (personal project)",
            },
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            payload = json.load(response)
        page_products = payload.get("products") or []
        if not page_products:
            break
        products.extend(page_products)
        sys.stderr.write(
            "page %d: %d products, %d total\n" % (page, len(page_products), len(products))
        )
        time.sleep(PAGE_DELAY_SECONDS)
    return products


def main():
    parser = argparse.ArgumentParser(
        description="Regenerate the bundled Systembolaget beer catalog seed."
    )
    parser.add_argument(
        "--input-file",
        help="read raw products from a JSON file instead of calling the API",
    )
    parser.add_argument("--output", default=OUTPUT_PATH)
    args = parser.parse_args()

    if args.input_file:
        with open(args.input_file, encoding="utf-8") as handle:
            raw = json.load(handle)
        raw_products = raw if isinstance(raw, list) else raw.get("products") or []
    else:
        api_key = os.environ.get("SYSTEMBOLAGET_API_KEY")
        if not api_key:
            sys.exit("SYSTEMBOLAGET_API_KEY is not set")
        raw_products = fetch_all_products(api_key)

    previous = None
    if os.path.exists(args.output):
        with open(args.output, encoding="utf-8") as handle:
            previous = json.load(handle)

    today = time.strftime("%Y-%m-%d", time.gmtime())
    snapshot = to_snapshot(raw_products, today, previous)

    count = len(snapshot["beers"])
    if not MIN_EXPECTED_BEERS <= count <= MAX_EXPECTED_BEERS:
        sys.exit(
            "Implausible beer count %d (expected %d to %d); refusing to write"
            % (count, MIN_EXPECTED_BEERS, MAX_EXPECTED_BEERS)
        )

    os.makedirs(os.path.dirname(args.output), exist_ok=True)
    with open(args.output, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(snapshot, handle, ensure_ascii=False, indent=1, sort_keys=True)
        handle.write("\n")
    print(
        "Wrote %d beers (snapshotVersion %s) to %s"
        % (count, snapshot["snapshotVersion"], args.output)
    )


if __name__ == "__main__":
    main()
```

- [ ] **Step 5: Run the tests to verify they pass**

```powershell
python -m unittest discover -s scripts -p "test_*.py" -v
```

Expected: `OK`, 10 tests pass.

- [ ] **Step 6: Commit**

```powershell
git add scripts/fetch_catalog.py scripts/test_fetch_catalog.py
git commit -m "[Build] Systembolaget catalog seed generator with tests"
```

---

### Task 3: Fetch and commit the bundled seed snapshot

Run the seed generator once against the live endpoint (the user approved this one-time fetch; this machine has network) and commit the asset. The APK bundles this file so the catalog works offline from the very first launch.

**Files:**
- Create (generated): `app/src/main/assets/catalog/beers.json`

**Interfaces:**
- Consumes: `scripts/fetch_catalog.py` from Task 2.
- Produces: `app/src/main/assets/catalog/beers.json` with `snapshotVersion` and roughly 1,534 beers. Task 4's importer reads it via `context.assets.open("catalog/beers.json")`.

- [ ] **Step 1: Run the seed generator against the live endpoint**

The key below is the public product-search key from Systembolaget's own website JavaScript, also published in the MIT-licensed C4illin/systembolaget-data repository (`getAllProducts.js`). Task 5 ships the same value as a constant in the app.

```powershell
$env:SYSTEMBOLAGET_API_KEY = "cfc702aed3094c86b92d6d4ff7a54c84"
python scripts/fetch_catalog.py
```

Expected: page-by-page progress on stderr, then `Wrote <count> beers (snapshotVersion 2026-...) to app\src\main\assets\catalog\beers.json` with a count between 1000 and 3000 (about 1,534 as of August 2026). The run takes a few minutes because of the polite 300 ms delay between pages.

FALLBACK if the endpoint refuses or the key has rotated: use the community mirror, which republishes the same raw product objects, then rerun through the same mapping logic:

```powershell
Invoke-WebRequest "https://raw.githubusercontent.com/AlexGustafsson/systembolaget-api-data/main/data/assortment.json" -OutFile "$env:TEMP\assortment.json"
python scripts/fetch_catalog.py --input-file "$env:TEMP\assortment.json"
```

If both paths fail, commit nothing, report the failure, and continue with Task 4: every later task tests against inline JSON fixtures, and only the final on-phone verification needs the real asset. If the key value itself has rotated, the current one can be read from the request headers on systembolaget.se (browser DevTools, network tab, any request to api-extern.systembolaget.se) and must then also replace the constant in Task 5.

- [ ] **Step 2: Verify the snapshot**

```powershell
python -c "import io, json; d = json.load(io.open('app/src/main/assets/catalog/beers.json', encoding='utf-8')); n = len(d['beers']); print(d['snapshotVersion'], n); assert 1000 <= n <= 3000"
python -c "import io, json; d = json.load(io.open('app/src/main/assets/catalog/beers.json', encoding='utf-8')); hits = [b for b in d['beers'] if 'Mariestads' in b['name']]; print(hits[0]); assert hits"
```

Expected: the first line prints the version and count; the second prints a well-known Swedish staple beer (Mariestads) with sensible `articleNumber`, `type`, `alcoholPercent`, `volumeMl`, `price`, and an `imageUrl` starting with `https://product-cdn.systembolaget.se/productimages/`.

- [ ] **Step 3: Confirm the asset packs into the APK**

```powershell
.\gradlew.bat assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/assets/catalog/beers.json
git commit -m "[App] Bundle Systembolaget beer catalog seed"
```

---
### Task 4: Catalog storage and lookup (domain model, catalog database, seed importer)

The catalog gets its own Room database class and file (`catalog.db`) so nothing that happens to the catalog can ever touch the user database. The bundled seed is parsed with `org.json` (part of Android, real under Robolectric) and imported on app start whenever the database is empty or the seed version changed. The imported version is recorded in a one-row metadata table inside the catalog database itself, not in SharedPreferences.

**Files:**
- Create: `app/src/main/java/com/beertracker/domain/CatalogProduct.kt`
- Create: `app/src/main/java/com/beertracker/domain/CatalogRepository.kt`
- Create: `app/src/main/java/com/beertracker/data/CatalogJson.kt`
- Create: `app/src/main/java/com/beertracker/data/CatalogBeerEntity.kt`
- Create: `app/src/main/java/com/beertracker/data/CatalogDao.kt`
- Create: `app/src/main/java/com/beertracker/data/CatalogDatabase.kt`
- Create: `app/src/main/java/com/beertracker/data/RoomCatalogRepository.kt`
- Create: `app/src/main/java/com/beertracker/data/CatalogImporter.kt`
- Modify: `app/src/main/java/com/beertracker/BeerApp.kt`
- Modify: `app/src/test/java/com/beertracker/TestData.kt`
- Create: `app/src/test/java/com/beertracker/FakeCatalogRepository.kt`
- Test: `app/src/test/java/com/beertracker/CatalogProductTest.kt`
- Test: `app/src/test/java/com/beertracker/CatalogJsonTest.kt`
- Test: `app/src/test/java/com/beertracker/RoomCatalogRepositoryTest.kt`
- Test: `app/src/test/java/com/beertracker/CatalogImporterTest.kt`

**Interfaces:**
- Consumes: the seed JSON shape from Task 2 (`snapshotVersion`, `beers` array with fields `articleNumber`, `articleNumberShort`, `name`, `brewery`, `type`, `alcoholPercent`, `volumeMl`, `price`, `country`, `imageUrl`).
- Produces, relied on by later tasks:
  - `CatalogProduct(articleNumber: String, articleNumberShort: String?, name: String, brewery: String, type: String, alcoholPercent: Double?, volumeMl: Int?, price: Double?, country: String?, imageUrl: String?)` with `val displayImageUrl: String?` (base URL plus `_400.jpg`).
  - `CatalogStatus(beerCount: Int, lastRefreshUtc: Long?)`.
  - `interface CatalogRepository { suspend fun findByArticleNumber(raw: String): CatalogProduct?; fun observeStatus(): Flow<CatalogStatus?> }` (no search function: no Phase 2 screen needs one, so per YAGNI it does not exist).
  - `parseCatalogAsset(text: String): CatalogSeed` where `CatalogSeed(snapshotVersion: String, beers: List<CatalogProduct>)`.
  - `CatalogDatabase.build(context)`, `CatalogDao`, `CatalogBeerEntity`, `CatalogMetadataEntity(id: Int = 1, snapshotVersion: String?, beerCount: Int, lastRefreshUtc: Long?)`, mappers `CatalogBeerEntity.toDomain()` and `CatalogProduct.toEntity()`.
  - `CatalogImporter(readAsset: () -> String, database: CatalogDatabase)` with `suspend fun importIfNeeded()`.
  - `AppContainer.catalogRepository: CatalogRepository` and `BeerApp` launching the import on an application scope.
  - Test helpers: `catalogProduct(...)` in TestData.kt and `FakeCatalogRepository` (with a `lookups` call counter and a mutable `status` flow).

- [ ] **Step 1: Write the failing tests and test helpers**

Append to `app/src/test/java/com/beertracker/TestData.kt` (keep the existing `beer(...)` function; add the import and the new function):

```kotlin
import com.beertracker.domain.CatalogProduct

fun catalogProduct(
    articleNumber: String = "1324515",
    articleNumberShort: String? = "13245",
    name: String = "Omnipollo Prodigal Pale Ale",
    brewery: String = "Omnipollo",
    type: String = "Ale",
    alcoholPercent: Double? = 5.2,
    volumeMl: Int? = 330,
    price: Double? = 25.9,
    country: String? = "Sverige",
    imageUrl: String? = "https://product-cdn.systembolaget.se/productimages/50786609/50786609",
) = CatalogProduct(
    articleNumber = articleNumber,
    articleNumberShort = articleNumberShort,
    name = name,
    brewery = brewery,
    type = type,
    alcoholPercent = alcoholPercent,
    volumeMl = volumeMl,
    price = price,
    country = country,
    imageUrl = imageUrl,
)
```

`app/src/test/java/com/beertracker/FakeCatalogRepository.kt`:

```kotlin
package com.beertracker

import com.beertracker.domain.CatalogProduct
import com.beertracker.domain.CatalogRepository
import com.beertracker.domain.CatalogStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeCatalogRepository : CatalogRepository {

    private val products = mutableListOf<CatalogProduct>()
    val status = MutableStateFlow<CatalogStatus?>(null)
    var lookups = 0
        private set

    fun add(product: CatalogProduct) {
        products.add(product)
    }

    override suspend fun findByArticleNumber(raw: String): CatalogProduct? {
        lookups += 1
        val digits = raw.filter(Char::isDigit)
        if (digits.isEmpty()) return null
        return products.find { it.articleNumber == digits || it.articleNumberShort == digits }
    }

    override fun observeStatus(): Flow<CatalogStatus?> = status
}
```

`app/src/test/java/com/beertracker/CatalogProductTest.kt`:

```kotlin
package com.beertracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogProductTest {

    @Test
    fun `display image url appends the 400 pixel jpg suffix`() {
        assertEquals(
            "https://product-cdn.systembolaget.se/productimages/50786609/50786609_400.jpg",
            catalogProduct().displayImageUrl,
        )
    }

    @Test
    fun `display image url is null when there is no image`() {
        assertNull(catalogProduct(imageUrl = null).displayImageUrl)
    }
}
```

`app/src/test/java/com/beertracker/CatalogJsonTest.kt` (Robolectric, because `org.json` is only real on the Robolectric classpath):

```kotlin
package com.beertracker

import android.app.Application
import com.beertracker.data.parseCatalogAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CatalogJsonTest {

    private val sampleAsset = """
        {
         "snapshotVersion": "2026-08-08",
         "beers": [
          {
           "articleNumber": "1324515",
           "articleNumberShort": "13245",
           "name": "Omnipollo Prodigal Pale Ale",
           "brewery": "Omnipollo",
           "type": "Ale",
           "alcoholPercent": 5.2,
           "volumeMl": 330,
           "price": 25.9,
           "country": "Sverige",
           "imageUrl": "https://product-cdn.systembolaget.se/productimages/50786609/50786609"
          },
          {
           "articleNumber": "1000501",
           "articleNumberShort": null,
           "name": "Nameless Lager",
           "brewery": "",
           "type": "Ljus lager",
           "alcoholPercent": null,
           "volumeMl": null,
           "price": null,
           "country": null,
           "imageUrl": null
          }
         ]
        }
    """.trimIndent()

    @Test
    fun `parses version and every field`() {
        val seed = parseCatalogAsset(sampleAsset)
        assertEquals("2026-08-08", seed.snapshotVersion)
        assertEquals(2, seed.beers.size)
        assertEquals(catalogProduct(), seed.beers[0])
    }

    @Test
    fun `json nulls become kotlin nulls`() {
        val second = parseCatalogAsset(sampleAsset).beers[1]
        assertEquals("1000501", second.articleNumber)
        assertNull(second.articleNumberShort)
        assertNull(second.alcoholPercent)
        assertNull(second.volumeMl)
        assertNull(second.price)
        assertNull(second.country)
        assertNull(second.imageUrl)
    }

    @Test
    fun `missing optional keys also become kotlin nulls`() {
        val seed = parseCatalogAsset(
            """{"snapshotVersion": "v", "beers": [{"articleNumber": "42", "name": "X", "brewery": "", "type": "Ale"}]}""",
        )
        val beer = seed.beers.single()
        assertNull(beer.articleNumberShort)
        assertNull(beer.alcoholPercent)
        assertNull(beer.volumeMl)
        assertNull(beer.price)
        assertNull(beer.country)
        assertNull(beer.imageUrl)
    }
}
```

`app/src/test/java/com/beertracker/RoomCatalogRepositoryTest.kt`:

```kotlin
package com.beertracker

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.beertracker.data.CatalogDatabase
import com.beertracker.data.CatalogMetadataEntity
import com.beertracker.data.RoomCatalogRepository
import com.beertracker.data.toEntity
import com.beertracker.domain.CatalogStatus
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
@Config(application = Application::class, sdk = [35])
class RoomCatalogRepositoryTest {

    private lateinit var db: CatalogDatabase
    private lateinit var repo: RoomCatalogRepository

    @Before
    fun setup() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), CatalogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RoomCatalogRepository(db.catalogDao())
        db.catalogDao().insertAll(
            listOf(
                catalogProduct().toEntity(),
                catalogProduct(
                    articleNumber = "1000501",
                    articleNumberShort = "10005",
                    name = "Second Beer",
                ).toEntity(),
            ),
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `finds by full article number`() = runTest {
        assertEquals("Omnipollo Prodigal Pale Ale", repo.findByArticleNumber("1324515")?.name)
    }

    @Test
    fun `finds by short article number`() = runTest {
        assertEquals("Omnipollo Prodigal Pale Ale", repo.findByArticleNumber("13245")?.name)
    }

    @Test
    fun `ignores surrounding whitespace and non digits`() = runTest {
        assertEquals("Second Beer", repo.findByArticleNumber(" Nr 10005 ")?.name)
    }

    @Test
    fun `strips leading zeros as a second attempt`() = runTest {
        assertEquals("Omnipollo Prodigal Pale Ale", repo.findByArticleNumber("013245")?.name)
    }

    @Test
    fun `unknown or empty input gives null`() = runTest {
        assertNull(repo.findByArticleNumber("999999"))
        assertNull(repo.findByArticleNumber(""))
        assertNull(repo.findByArticleNumber("no digits here"))
    }

    @Test
    fun `status is null before metadata exists and mirrors it afterwards`() = runTest {
        assertNull(repo.observeStatus().first())
        db.catalogDao().setMetadata(
            CatalogMetadataEntity(snapshotVersion = "2026-08-08", beerCount = 2, lastRefreshUtc = 123L),
        )
        assertEquals(CatalogStatus(beerCount = 2, lastRefreshUtc = 123L), repo.observeStatus().first())
    }
}
```

`app/src/test/java/com/beertracker/CatalogImporterTest.kt`:

```kotlin
package com.beertracker

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.beertracker.data.CatalogDatabase
import com.beertracker.data.CatalogImporter
import com.beertracker.data.toEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CatalogImporterTest {

    private lateinit var db: CatalogDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), CatalogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun seedJson(version: String, vararg articleNumbers: String): String {
        val beers = articleNumbers.joinToString(",") { number ->
            """{"articleNumber": "$number", "articleNumberShort": null, "name": "Beer $number",
                "brewery": "Brew", "type": "Ale", "alcoholPercent": 5.0, "volumeMl": 330,
                "price": 20.0, "country": "Sverige", "imageUrl": null}"""
        }
        return """{"snapshotVersion": "$version", "beers": [$beers]}"""
    }

    @Test
    fun `first import fills the catalog and records the version`() = runTest {
        CatalogImporter({ seedJson("2026-08-08", "101", "202") }, db).importIfNeeded()
        assertEquals(2, db.catalogDao().count())
        val metadata = db.catalogDao().getMetadata()
        assertEquals("2026-08-08", metadata?.snapshotVersion)
        assertEquals(2, metadata?.beerCount)
        assertNull(metadata?.lastRefreshUtc)
    }

    @Test
    fun `import with the same version is skipped`() = runTest {
        CatalogImporter({ seedJson("2026-08-08", "101") }, db).importIfNeeded()
        // A marker row inserted behind the importer's back survives a rerun
        // with the same seed version, proving the rerun was skipped.
        db.catalogDao().insertAll(listOf(catalogProduct(articleNumber = "999").toEntity()))
        CatalogImporter({ seedJson("2026-08-08", "101") }, db).importIfNeeded()
        assertNotNull(db.catalogDao().findByNumber("999"))
        assertEquals(2, db.catalogDao().count())
    }

    @Test
    fun `import with a new version wipes and replaces the catalog`() = runTest {
        CatalogImporter({ seedJson("2026-08-08", "101", "202") }, db).importIfNeeded()
        CatalogImporter({ seedJson("2026-09-01", "303") }, db).importIfNeeded()
        assertEquals(1, db.catalogDao().count())
        assertNull(db.catalogDao().findByNumber("101"))
        assertNotNull(db.catalogDao().findByNumber("303"))
        assertEquals("2026-09-01", db.catalogDao().getMetadata()?.snapshotVersion)
    }

    @Test
    fun `a broken asset never throws and leaves the catalog untouched`() = runTest {
        CatalogImporter({ "this is not json" }, db).importIfNeeded()
        assertEquals(0, db.catalogDao().count())
        CatalogImporter({ seedJson("2026-08-08", "101") }, db).importIfNeeded()
        CatalogImporter({ "this is not json" }, db).importIfNeeded()
        assertEquals(1, db.catalogDao().count())
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: compilation FAILS with unresolved references in `com.beertracker.domain` (`CatalogProduct`, `CatalogRepository`, `CatalogStatus`) and `com.beertracker.data` (`CatalogDatabase`, `CatalogImporter`, `parseCatalogAsset`, ...).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/beertracker/domain/CatalogProduct.kt`:

```kotlin
package com.beertracker.domain

/**
 * One beer from the read-only Systembolaget catalog snapshot. This is
 * reference data: saving a beer copies the fields the user cares about onto
 * the TriedBeer, so catalog refreshes never change anything the user saved.
 */
data class CatalogProduct(
    val articleNumber: String,
    val articleNumberShort: String?,
    val name: String,
    val brewery: String,
    val type: String,
    val alcoholPercent: Double?,
    val volumeMl: Int?,
    val price: Double?,
    val country: String?,
    val imageUrl: String?,
) {
    /**
     * The catalog stores the CDN base URL without an extension. Appending
     * _400.jpg yields a 400 pixel JPEG, verified working in August 2026, for
     * example https://product-cdn.systembolaget.se/productimages/50786609/50786609_400.jpg
     */
    val displayImageUrl: String?
        get() = imageUrl?.let { base -> base + "_400.jpg" }
}
```

`app/src/main/java/com/beertracker/domain/CatalogRepository.kt`:

```kotlin
package com.beertracker.domain

import kotlinx.coroutines.flow.Flow

data class CatalogStatus(
    val beerCount: Int,
    /** Null until the first successful in-app refresh; the seed does not count. */
    val lastRefreshUtc: Long?,
)

interface CatalogRepository {
    /**
     * Looks a product up by article number. The input may be raw scanner or
     * keyboard text: everything except digits is dropped, then the digits are
     * matched exactly against the full article number and the short shelf
     * number, with one retry without leading zeros.
     */
    suspend fun findByArticleNumber(raw: String): CatalogProduct?

    fun observeStatus(): Flow<CatalogStatus?>
}
```

`app/src/main/java/com/beertracker/data/CatalogJson.kt`:

```kotlin
package com.beertracker.data

import com.beertracker.domain.CatalogProduct
import org.json.JSONObject

data class CatalogSeed(
    val snapshotVersion: String,
    val beers: List<CatalogProduct>,
)

/** Parses the bundled assets/catalog/beers.json written by scripts/fetch_catalog.py. */
fun parseCatalogAsset(text: String): CatalogSeed {
    val root = JSONObject(text)
    val beersJson = root.getJSONArray("beers")
    val beers = ArrayList<CatalogProduct>(beersJson.length())
    for (index in 0 until beersJson.length()) {
        beers.add(beersJson.getJSONObject(index).toCatalogProduct())
    }
    return CatalogSeed(
        snapshotVersion = root.getString("snapshotVersion"),
        beers = beers,
    )
}

private fun JSONObject.toCatalogProduct() = CatalogProduct(
    articleNumber = getString("articleNumber"),
    articleNumberShort = optStringOrNull("articleNumberShort"),
    name = optString("name"),
    brewery = optString("brewery"),
    type = optString("type"),
    alcoholPercent = optDoubleOrNull("alcoholPercent"),
    volumeMl = optDoubleOrNull("volumeMl")?.toInt(),
    price = optDoubleOrNull("price"),
    country = optStringOrNull("country"),
    imageUrl = optStringOrNull("imageUrl"),
)

/** Absent keys, JSON nulls, and empty strings all become Kotlin null. */
internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

/** Absent keys and JSON nulls become Kotlin null instead of NaN. */
internal fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }
```

`app/src/main/java/com/beertracker/data/CatalogBeerEntity.kt`:

```kotlin
package com.beertracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.beertracker.domain.CatalogProduct

@Entity(tableName = "catalog_beers")
data class CatalogBeerEntity(
    @PrimaryKey val articleNumber: String,
    val articleNumberShort: String?,
    val name: String,
    val brewery: String,
    val type: String,
    val alcoholPercent: Double?,
    val volumeMl: Int?,
    val price: Double?,
    val country: String?,
    val imageUrl: String?,
)

/**
 * One row (id is always 1) describing where the catalog contents came from:
 * which bundled seed version was imported, how many beers are loaded, and
 * when the last successful in-app refresh ran (null if never).
 */
@Entity(tableName = "catalog_metadata")
data class CatalogMetadataEntity(
    @PrimaryKey val id: Int = 1,
    val snapshotVersion: String?,
    val beerCount: Int,
    val lastRefreshUtc: Long?,
)

fun CatalogBeerEntity.toDomain() = CatalogProduct(
    articleNumber = articleNumber,
    articleNumberShort = articleNumberShort,
    name = name,
    brewery = brewery,
    type = type,
    alcoholPercent = alcoholPercent,
    volumeMl = volumeMl,
    price = price,
    country = country,
    imageUrl = imageUrl,
)

fun CatalogProduct.toEntity() = CatalogBeerEntity(
    articleNumber = articleNumber,
    articleNumberShort = articleNumberShort,
    name = name,
    brewery = brewery,
    type = type,
    alcoholPercent = alcoholPercent,
    volumeMl = volumeMl,
    price = price,
    country = country,
    imageUrl = imageUrl,
)
```

`app/src/main/java/com/beertracker/data/CatalogDao.kt`:

```kotlin
package com.beertracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {

    @Query(
        "SELECT * FROM catalog_beers " +
            "WHERE articleNumber = :number OR articleNumberShort = :number LIMIT 1",
    )
    suspend fun findByNumber(number: String): CatalogBeerEntity?

    @Query("SELECT COUNT(*) FROM catalog_beers")
    suspend fun count(): Int

    @Query("DELETE FROM catalog_beers")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(beers: List<CatalogBeerEntity>)

    @Query("SELECT * FROM catalog_metadata WHERE id = 1")
    suspend fun getMetadata(): CatalogMetadataEntity?

    @Query("SELECT * FROM catalog_metadata WHERE id = 1")
    fun observeMetadata(): Flow<CatalogMetadataEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMetadata(metadata: CatalogMetadataEntity)
}
```

`app/src/main/java/com/beertracker/data/CatalogDatabase.kt`:

```kotlin
package com.beertracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The catalog database is a disposable cache, fed by the bundled seed asset
 * and the in-app refresh. It lives in its own file on purpose: nothing that
 * happens here can touch the user's beers in beertracker.db. Because every
 * row can be rebuilt from the asset, destructive migration is fine HERE and
 * only here; schema history is not tracked (exportSchema false).
 */
@Database(
    entities = [CatalogBeerEntity::class, CatalogMetadataEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    companion object {
        fun build(context: Context): CatalogDatabase =
            Room.databaseBuilder(context, CatalogDatabase::class.java, "catalog.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
```

`app/src/main/java/com/beertracker/data/RoomCatalogRepository.kt`:

```kotlin
package com.beertracker.data

import com.beertracker.domain.CatalogProduct
import com.beertracker.domain.CatalogRepository
import com.beertracker.domain.CatalogStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCatalogRepository(private val dao: CatalogDao) : CatalogRepository {

    override suspend fun findByArticleNumber(raw: String): CatalogProduct? {
        val digits = raw.filter(Char::isDigit)
        if (digits.isEmpty()) return null
        val candidates = buildList {
            add(digits)
            val withoutLeadingZeros = digits.trimStart('0')
            if (withoutLeadingZeros.isNotEmpty() && withoutLeadingZeros != digits) {
                add(withoutLeadingZeros)
            }
        }
        for (candidate in candidates) {
            dao.findByNumber(candidate)?.let { return it.toDomain() }
        }
        return null
    }

    override fun observeStatus(): Flow<CatalogStatus?> =
        dao.observeMetadata().map { metadata ->
            metadata?.let {
                CatalogStatus(beerCount = it.beerCount, lastRefreshUtc = it.lastRefreshUtc)
            }
        }
}
```

`app/src/main/java/com/beertracker/data/CatalogImporter.kt`:

```kotlin
package com.beertracker.data

import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException

class CatalogImporter(
    private val readAsset: () -> String,
    private val database: CatalogDatabase,
) {

    /**
     * Imports the bundled seed when the catalog is empty or was built from a
     * different seed version. A refresh (Task 6) keeps the seed version and
     * only bumps lastRefreshUtc, so refreshed data survives relaunches and is
     * only replaced when a new APK ships a new seed. Never throws: an app
     * without a catalog is still a working beer tracker.
     */
    suspend fun importIfNeeded() {
        try {
            val seed = parseCatalogAsset(readAsset())
            val dao = database.catalogDao()
            val metadata = dao.getMetadata()
            if (metadata?.snapshotVersion == seed.snapshotVersion && dao.count() > 0) return
            database.withTransaction {
                dao.deleteAll()
                dao.insertAll(seed.beers.map { it.toEntity() })
                dao.setMetadata(
                    CatalogMetadataEntity(
                        snapshotVersion = seed.snapshotVersion,
                        beerCount = seed.beers.size,
                        lastRefreshUtc = null,
                    ),
                )
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w("CatalogImporter", "Seed import failed, catalog lookups stay empty", error)
        }
    }
}
```

Replace the whole content of `app/src/main/java/com/beertracker/BeerApp.kt` with:

```kotlin
package com.beertracker

import android.app.Application
import android.content.Context
import com.beertracker.data.BeerDatabase
import com.beertracker.data.CatalogDatabase
import com.beertracker.data.CatalogImporter
import com.beertracker.data.RoomBeerRepository
import com.beertracker.data.RoomCatalogRepository
import com.beertracker.domain.BeerRepository
import com.beertracker.domain.CatalogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val db = BeerDatabase.build(context)
    val beerRepository: BeerRepository = RoomBeerRepository(db.beerDao())

    private val catalogDb = CatalogDatabase.build(context)
    val catalogRepository: CatalogRepository = RoomCatalogRepository(catalogDb.catalogDao())
    val catalogImporter = CatalogImporter(
        readAsset = {
            context.assets.open("catalog/beers.json").bufferedReader().use { it.readText() }
        },
        database = catalogDb,
    )
}

class BeerApp : Application() {
    lateinit var container: AppContainer
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applicationScope.launch {
            container.catalogImporter.importIfNeeded()
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass (88 old plus 15 new). The pre-existing tests must be untouched and green.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/beertracker/domain/CatalogProduct.kt app/src/main/java/com/beertracker/domain/CatalogRepository.kt app/src/main/java/com/beertracker/data/CatalogJson.kt app/src/main/java/com/beertracker/data/CatalogBeerEntity.kt app/src/main/java/com/beertracker/data/CatalogDao.kt app/src/main/java/com/beertracker/data/CatalogDatabase.kt app/src/main/java/com/beertracker/data/RoomCatalogRepository.kt app/src/main/java/com/beertracker/data/CatalogImporter.kt app/src/main/java/com/beertracker/BeerApp.kt app/src/test/java/com/beertracker
git commit -m "[App] Offline catalog database with seed import and lookup"
```

---

### Task 5: In-app Systembolaget catalog fetcher

The app fetches the live assortment itself, with the same endpoint, the same politeness, and the same field mapping as the seed script. `HttpURLConnection` and `org.json` only; the HTTP call is injected as a function so tests feed canned JSON pages. The subscription key ships as a plain constant: it is the public key from Systembolaget's own website JavaScript, sent openly by every visitor's browser, so bundling it in the APK discloses nothing new.

**Files:**
- Create: `app/src/main/java/com/beertracker/data/CatalogFetcher.kt`
- Test: `app/src/test/java/com/beertracker/SystembolagetCatalogFetcherTest.kt`

**Interfaces:**
- Consumes: `CatalogProduct` from Task 4 and the `optStringOrNull` / `optDoubleOrNull` helpers from `CatalogJson.kt`.
- Produces, relied on by Task 6:
  - `interface CatalogFetcher { suspend fun fetchAllBeers(): List<CatalogProduct> }` (throws `IOException` or `JSONException` on any network or parse problem).
  - `class SystembolagetCatalogFetcher(httpGet: (String) -> String = ::defaultHttpGet, ioDispatcher: CoroutineDispatcher = Dispatchers.IO, pageDelayMillis: Long = 300L) : CatalogFetcher`.
  - `internal fun mapProduct(product: JSONObject): CatalogProduct` and `internal fun pageUrl(page: Int): String` (test seams).

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/beertracker/SystembolagetCatalogFetcherTest.kt` (Robolectric for real `org.json`; the sample product is the same one used in `scripts/test_fetch_catalog.py`, keeping the two mappers verifiably parallel):

```kotlin
package com.beertracker

import android.app.Application
import com.beertracker.data.SystembolagetCatalogFetcher
import com.beertracker.data.mapProduct
import java.io.IOException
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SystembolagetCatalogFetcherTest {

    private val sampleBeer = """
        {
         "productId": "50786609",
         "productNumber": "1324515",
         "productNumberShort": "13245",
         "productNameBold": "Omnipollo",
         "productNameThin": "Prodigal Pale Ale",
         "producerName": "Omnipollo",
         "categoryLevel1": "Öl",
         "categoryLevel2": "Ale",
         "categoryLevel3": "Pale Ale",
         "alcoholPercentage": 5.2,
         "volume": 330.0,
         "price": 25.9,
         "country": "Sverige",
         "images": [{"imageUrl": "https://product-cdn.systembolaget.se/productimages/50786609/50786609"}]
        }
    """.trimIndent()

    private val secondBeer = """
        {
         "productNumber": "1000501",
         "productNameBold": "Second",
         "productNameThin": "Beer",
         "categoryLevel1": "Öl",
         "categoryLevel2": "Ljus lager"
        }
    """.trimIndent()

    private val sampleWine = """
        {
         "productNumber": "7000101",
         "productNameBold": "Some Wine",
         "categoryLevel1": "Vin"
        }
    """.trimIndent()

    @Test
    fun `mapProduct maps every field exactly like the seed script`() {
        assertEquals(catalogProduct(), mapProduct(JSONObject(sampleBeer)))
    }

    @Test
    fun `mapProduct fills fallbacks for missing fields`() {
        val mapped = mapProduct(JSONObject("""{"productNumber": "42", "categoryLevel1": "Öl"}"""))
        assertEquals("42", mapped.articleNumber)
        assertNull(mapped.articleNumberShort)
        assertEquals("", mapped.name)
        assertEquals("", mapped.brewery)
        assertEquals("Öl", mapped.type)
        assertNull(mapped.alcoholPercent)
        assertNull(mapped.volumeMl)
        assertNull(mapped.price)
        assertNull(mapped.country)
        assertNull(mapped.imageUrl)
    }

    @Test
    fun `mapProduct falls back to category level 3 for the type`() {
        val json = JSONObject(sampleBeer).put("categoryLevel2", JSONObject.NULL)
        assertEquals("Pale Ale", mapProduct(json).type)
    }

    @Test
    fun `walks pages until the first empty page and keeps only beer`() = runTest {
        val requested = mutableListOf<String>()
        val pages = listOf(
            """{"products": [$sampleBeer, $sampleWine]}""",
            """{"products": [$secondBeer]}""",
            """{"products": []}""",
        )
        val fetcher = SystembolagetCatalogFetcher(
            httpGet = { url ->
                requested.add(url)
                pages[requested.size - 1]
            },
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val beers = fetcher.fetchAllBeers()

        assertEquals(3, requested.size)
        assertTrue(requested[0].endsWith("size=30&page=1&categoryLevel1=%C3%96l"))
        assertTrue(requested[2].endsWith("page=3&categoryLevel1=%C3%96l"))
        assertEquals(listOf("1000501", "1324515"), beers.map { it.articleNumber })
    }

    @Test
    fun `deduplicates by article number and sorts for stable results`() = runTest {
        val pages = listOf(
            """{"products": [$sampleBeer, $secondBeer]}""",
            """{"products": [$sampleBeer]}""",
            """{"products": []}""",
        )
        var call = 0
        val fetcher = SystembolagetCatalogFetcher(
            httpGet = { pages[call++] },
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        assertEquals(listOf("1000501", "1324515"), fetcher.fetchAllBeers().map { it.articleNumber })
    }

    @Test
    fun `http failures propagate to the caller`() = runTest {
        val fetcher = SystembolagetCatalogFetcher(
            httpGet = { throw IOException("HTTP 503") },
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        var thrown: IOException? = null
        try {
            fetcher.fetchAllBeers()
        } catch (error: IOException) {
            thrown = error
        }
        assertNotNull(thrown)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: compilation FAILS with unresolved references `SystembolagetCatalogFetcher` and `mapProduct`.

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/beertracker/data/CatalogFetcher.kt`:

```kotlin
package com.beertracker.data

import com.beertracker.domain.CatalogProduct
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The public product-search key from Systembolaget's own website JavaScript.
 * Every visitor's browser sends it in the open with each search request, so
 * shipping it inside the APK discloses nothing that is not already public.
 * It is deliberately a plain constant, not a secret.
 */
internal const val SYSTEMBOLAGET_SUBSCRIPTION_KEY = "cfc702aed3094c86b92d6d4ff7a54c84"

private const val SEARCH_URL =
    "https://api-extern.systembolaget.se/sb-api-ecommerce/v1/productsearch/search"
private const val PAGE_SIZE = 30

// categoryLevel1=Öl with the Ö percent-encoded, matching scripts/fetch_catalog.py.
private const val CATEGORY_FILTER = "categoryLevel1=%C3%96l"
private const val MAX_PAGES = 500

interface CatalogFetcher {
    /**
     * Fetches every beer in the live assortment, or throws. Politeness rules
     * match the seed script: sequential pages of 30 with a small delay, stop
     * at the first empty page.
     */
    suspend fun fetchAllBeers(): List<CatalogProduct>
}

class SystembolagetCatalogFetcher(
    private val httpGet: (String) -> String = ::defaultHttpGet,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pageDelayMillis: Long = 300L,
) : CatalogFetcher {

    override suspend fun fetchAllBeers(): List<CatalogProduct> = withContext(ioDispatcher) {
        val beers = mutableListOf<CatalogProduct>()
        for (page in 1..MAX_PAGES) {
            val products = parseProductsPage(httpGet(pageUrl(page)))
            if (products.isEmpty()) break
            products.filter(::isBeer).mapTo(beers, ::mapProduct)
            delay(pageDelayMillis)
        }
        beers
            .filter { it.articleNumber.isNotEmpty() }
            .distinctBy { it.articleNumber }
            .sortedBy { it.articleNumber }
    }
}

internal fun pageUrl(page: Int): String =
    "$SEARCH_URL?size=$PAGE_SIZE&page=$page&$CATEGORY_FILTER"

internal fun parseProductsPage(pageJson: String): List<JSONObject> {
    val products = JSONObject(pageJson).optJSONArray("products") ?: return emptyList()
    return (0 until products.length()).mapNotNull { products.optJSONObject(it) }
}

internal fun isBeer(product: JSONObject): Boolean =
    product.optString("categoryLevel1") == "Öl"

/**
 * Maps one raw API product to the catalog model. Must stay field for field
 * identical to map_product in scripts/fetch_catalog.py; both test suites use
 * the same sample product to hold the two mappers together.
 */
internal fun mapProduct(product: JSONObject): CatalogProduct {
    val nameBold = product.optStringOrNull("productNameBold")?.trim().orEmpty()
    val nameThin = product.optStringOrNull("productNameThin")?.trim().orEmpty()
    val imageUrl = product.optJSONArray("images")
        ?.optJSONObject(0)
        ?.optStringOrNull("imageUrl")
    return CatalogProduct(
        articleNumber = product.optStringOrNull("productNumber").orEmpty(),
        articleNumberShort = product.optStringOrNull("productNumberShort"),
        name = listOf(nameBold, nameThin).filter { it.isNotEmpty() }.joinToString(" "),
        brewery = product.optStringOrNull("producerName").orEmpty(),
        type = product.optStringOrNull("categoryLevel2")
            ?: product.optStringOrNull("categoryLevel3")
            ?: "Öl",
        alcoholPercent = product.optDoubleOrNull("alcoholPercentage"),
        volumeMl = product.optDoubleOrNull("volume")?.roundToInt(),
        price = product.optDoubleOrNull("price"),
        country = product.optStringOrNull("country"),
        imageUrl = imageUrl,
    )
}

private fun defaultHttpGet(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("ocp-apim-subscription-key", SYSTEMBOLAGET_SUBSCRIPTION_KEY)
        connection.setRequestProperty("Referer", "https://www.systembolaget.se/")
        connection.setRequestProperty("Accept", "application/json")
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            throw IOException("Systembolaget product search returned HTTP $code")
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass (6 new).

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/beertracker/data/CatalogFetcher.kt app/src/test/java/com/beertracker/SystembolagetCatalogFetcherTest.kt
git commit -m "[App] In-app Systembolaget catalog fetcher"
```

---

### Task 6: Catalog refresher with weekly silent auto-refresh

Refresh semantics: fetch ALL pages successfully first, then replace the catalog table in one transaction. On any failure (network, parse, or an implausibly small result) the previous catalog stays untouched: last good wins. The refresh writes `lastRefreshUtc` and the beer count into the catalog metadata and preserves the seed `snapshotVersion` so the importer keeps skipping. On every app launch, a background coroutine refreshes silently if the last refresh is at least 7 days old (or has never happened) and the network is available. Failures are logged and ignored; there is no WorkManager and no new dependency.

**Files:**
- Create: `app/src/main/java/com/beertracker/domain/CatalogRefresher.kt`
- Create: `app/src/main/java/com/beertracker/domain/CatalogPolicy.kt`
- Create: `app/src/main/java/com/beertracker/data/DefaultCatalogRefresher.kt`
- Create: `app/src/main/java/com/beertracker/data/NetworkStatus.kt`
- Modify: `app/src/main/java/com/beertracker/BeerApp.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/beertracker/CatalogPolicyTest.kt`
- Test: `app/src/test/java/com/beertracker/DefaultCatalogRefresherTest.kt`

**Interfaces:**
- Consumes: `CatalogFetcher` (Task 5), `CatalogDatabase`, `CatalogDao`, `CatalogMetadataEntity`, `toEntity()` (Task 4).
- Produces, relied on by Task 11:
  - `sealed interface RefreshResult { data class Success(val beerCount: Int, val refreshedUtc: Long) : RefreshResult; data class Failure(val reason: String) : RefreshResult }`.
  - `interface CatalogRefresher { suspend fun refresh(): RefreshResult }`.
  - `class DefaultCatalogRefresher(database: CatalogDatabase, fetcher: CatalogFetcher, clock: () -> Long = System::currentTimeMillis) : CatalogRefresher`.
  - `fun shouldAutoRefresh(lastRefreshUtc: Long?, nowUtc: Long): Boolean` and `const val CATALOG_REFRESH_INTERVAL_MS: Long`.
  - `fun isNetworkAvailable(context: Context): Boolean`.
  - `AppContainer.catalogRefresher: CatalogRefresher`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/beertracker/CatalogPolicyTest.kt`:

```kotlin
package com.beertracker

import com.beertracker.domain.CATALOG_REFRESH_INTERVAL_MS
import com.beertracker.domain.shouldAutoRefresh
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogPolicyTest {

    private val now = 1_000_000_000_000L

    @Test
    fun `never refreshed means refresh now`() {
        assertTrue(shouldAutoRefresh(lastRefreshUtc = null, nowUtc = now))
    }

    @Test
    fun `a recent refresh waits`() {
        val sixDaysAgo = now - (CATALOG_REFRESH_INTERVAL_MS - 86_400_000L)
        assertFalse(shouldAutoRefresh(lastRefreshUtc = sixDaysAgo, nowUtc = now))
    }

    @Test
    fun `exactly one week is due`() {
        assertTrue(shouldAutoRefresh(lastRefreshUtc = now - CATALOG_REFRESH_INTERVAL_MS, nowUtc = now))
    }

    @Test
    fun `older than one week is due`() {
        assertTrue(shouldAutoRefresh(lastRefreshUtc = 0L, nowUtc = now))
    }
}
```

`app/src/test/java/com/beertracker/DefaultCatalogRefresherTest.kt`:

```kotlin
package com.beertracker

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.beertracker.data.CatalogDatabase
import com.beertracker.data.CatalogFetcher
import com.beertracker.data.CatalogMetadataEntity
import com.beertracker.data.DefaultCatalogRefresher
import com.beertracker.data.toEntity
import com.beertracker.domain.CatalogProduct
import com.beertracker.domain.RefreshResult
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class DefaultCatalogRefresherTest {

    private lateinit var db: CatalogDatabase

    @Before
    fun setup() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), CatalogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.catalogDao().insertAll(listOf(catalogProduct(articleNumber = "111", name = "Stale Beer").toEntity()))
        db.catalogDao().setMetadata(
            CatalogMetadataEntity(snapshotVersion = "2026-08-01", beerCount = 1, lastRefreshUtc = null),
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun fetcherReturning(beers: List<CatalogProduct>) = object : CatalogFetcher {
        override suspend fun fetchAllBeers(): List<CatalogProduct> = beers
    }

    private fun manyBeers(count: Int): List<CatalogProduct> = List(count) { index ->
        catalogProduct(articleNumber = (1_000_000 + index).toString(), articleNumberShort = null)
    }

    @Test
    fun `a successful refresh replaces the catalog and records count and time`() = runTest {
        val refresher = DefaultCatalogRefresher(db, fetcherReturning(manyBeers(600)), clock = { 999L })

        val result = refresher.refresh()

        assertEquals(RefreshResult.Success(beerCount = 600, refreshedUtc = 999L), result)
        assertEquals(600, db.catalogDao().count())
        assertNull(db.catalogDao().findByNumber("111"))
        val metadata = db.catalogDao().getMetadata()
        assertEquals(600, metadata?.beerCount)
        assertEquals(999L, metadata?.lastRefreshUtc)
        assertEquals("2026-08-01", metadata?.snapshotVersion)
    }

    @Test
    fun `a failed fetch keeps the previous catalog untouched`() = runTest {
        val refresher = DefaultCatalogRefresher(
            db,
            object : CatalogFetcher {
                override suspend fun fetchAllBeers(): List<CatalogProduct> =
                    throw IOException("HTTP 503")
            },
        )

        val result = refresher.refresh()

        assertTrue(result is RefreshResult.Failure)
        assertEquals(1, db.catalogDao().count())
        assertNotNull(db.catalogDao().findByNumber("111"))
        assertEquals(1, db.catalogDao().getMetadata()?.beerCount)
        assertNull(db.catalogDao().getMetadata()?.lastRefreshUtc)
    }

    @Test
    fun `an implausibly small answer is treated as a failure`() = runTest {
        val refresher = DefaultCatalogRefresher(db, fetcherReturning(manyBeers(3)))

        val result = refresher.refresh()

        assertTrue(result is RefreshResult.Failure)
        assertEquals(1, db.catalogDao().count())
        assertNotNull(db.catalogDao().findByNumber("111"))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: compilation FAILS with unresolved references `shouldAutoRefresh`, `CATALOG_REFRESH_INTERVAL_MS`, `DefaultCatalogRefresher`, `RefreshResult`.

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/beertracker/domain/CatalogRefresher.kt`:

```kotlin
package com.beertracker.domain

sealed interface RefreshResult {
    data class Success(val beerCount: Int, val refreshedUtc: Long) : RefreshResult
    data class Failure(val reason: String) : RefreshResult
}

interface CatalogRefresher {
    /**
     * Replaces the catalog with the live Systembolaget assortment. The whole
     * fetch must succeed before anything is written; on any failure the
     * previous catalog stays exactly as it was (last good wins). Only the
     * catalog database is touched, never the user's beers.
     */
    suspend fun refresh(): RefreshResult
}
```

`app/src/main/java/com/beertracker/domain/CatalogPolicy.kt`:

```kotlin
package com.beertracker.domain

const val CATALOG_REFRESH_INTERVAL_MS: Long = 7L * 24 * 60 * 60 * 1000

/**
 * True when the catalog has never been refreshed from the network (a fresh
 * install running on the bundled seed) or the last refresh is a week old.
 */
fun shouldAutoRefresh(lastRefreshUtc: Long?, nowUtc: Long): Boolean =
    lastRefreshUtc == null || nowUtc - lastRefreshUtc >= CATALOG_REFRESH_INTERVAL_MS
```

`app/src/main/java/com/beertracker/data/DefaultCatalogRefresher.kt`:

```kotlin
package com.beertracker.data

import android.util.Log
import androidx.room.withTransaction
import com.beertracker.domain.CatalogRefresher
import com.beertracker.domain.RefreshResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A fetch that yields implausibly few beers (the assortment holds about
 * 1,500) is refused so a broken answer can never wipe a healthy catalog.
 */
internal const val MIN_PLAUSIBLE_BEER_COUNT = 500

class DefaultCatalogRefresher(
    private val database: CatalogDatabase,
    private val fetcher: CatalogFetcher,
    private val clock: () -> Long = System::currentTimeMillis,
) : CatalogRefresher {

    /** One refresh at a time; a manual tap during the launch refresh waits. */
    private val refreshMutex = Mutex()

    override suspend fun refresh(): RefreshResult = refreshMutex.withLock {
        val beers = try {
            fetcher.fetchAllBeers()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w(TAG, "Catalog refresh failed, keeping the previous catalog", error)
            return RefreshResult.Failure("Could not reach the Systembolaget catalog")
        }
        if (beers.size < MIN_PLAUSIBLE_BEER_COUNT) {
            Log.w(TAG, "Refusing implausible catalog of ${beers.size} beers")
            return RefreshResult.Failure("The catalog answer looked incomplete")
        }
        val now = clock()
        val dao = database.catalogDao()
        database.withTransaction {
            val previous = dao.getMetadata()
            dao.deleteAll()
            dao.insertAll(beers.map { it.toEntity() })
            dao.setMetadata(
                CatalogMetadataEntity(
                    snapshotVersion = previous?.snapshotVersion,
                    beerCount = beers.size,
                    lastRefreshUtc = now,
                ),
            )
        }
        RefreshResult.Success(beerCount = beers.size, refreshedUtc = now)
    }

    private companion object {
        const val TAG = "CatalogRefresher"
    }
}
```

`app/src/main/java/com/beertracker/data/NetworkStatus.kt`:

```kotlin
package com.beertracker.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

fun isNetworkAvailable(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
```

Replace the whole content of `app/src/main/java/com/beertracker/BeerApp.kt` with:

```kotlin
package com.beertracker

import android.app.Application
import android.content.Context
import com.beertracker.data.BeerDatabase
import com.beertracker.data.CatalogDatabase
import com.beertracker.data.CatalogImporter
import com.beertracker.data.DefaultCatalogRefresher
import com.beertracker.data.RoomBeerRepository
import com.beertracker.data.RoomCatalogRepository
import com.beertracker.data.SystembolagetCatalogFetcher
import com.beertracker.data.isNetworkAvailable
import com.beertracker.domain.BeerRepository
import com.beertracker.domain.CatalogRefresher
import com.beertracker.domain.CatalogRepository
import com.beertracker.domain.shouldAutoRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val db = BeerDatabase.build(context)
    val beerRepository: BeerRepository = RoomBeerRepository(db.beerDao())

    private val catalogDb = CatalogDatabase.build(context)
    val catalogRepository: CatalogRepository = RoomCatalogRepository(catalogDb.catalogDao())
    val catalogRefresher: CatalogRefresher =
        DefaultCatalogRefresher(catalogDb, SystembolagetCatalogFetcher())
    val catalogImporter = CatalogImporter(
        readAsset = {
            context.assets.open("catalog/beers.json").bufferedReader().use { it.readText() }
        },
        database = catalogDb,
    )
}

class BeerApp : Application() {
    lateinit var container: AppContainer
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applicationScope.launch {
            container.catalogImporter.importIfNeeded()
            autoRefreshCatalog()
        }
    }

    /**
     * Weekly, silent, launch-triggered catalog refresh. Failures are logged
     * inside the refresher and ignored here; the user is never interrupted.
     */
    private suspend fun autoRefreshCatalog() {
        val status = container.catalogRepository.observeStatus().first()
        val due = shouldAutoRefresh(status?.lastRefreshUtc, System.currentTimeMillis())
        if (due && isNetworkAvailable(this)) {
            container.catalogRefresher.refresh()
        }
    }
}
```

Replace the whole content of `app/src/main/AndroidManifest.xml` with (adds INTERNET for the fetcher and the Coil images of Task 12, plus ACCESS_NETWORK_STATE for the auto-refresh check):

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <application
        android:name=".BeerApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:theme="@style/Theme.BeerTracker">
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

- [ ] **Step 4: Run the tests to verify they pass**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass (7 new).

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/beertracker/domain/CatalogRefresher.kt app/src/main/java/com/beertracker/domain/CatalogPolicy.kt app/src/main/java/com/beertracker/data/DefaultCatalogRefresher.kt app/src/main/java/com/beertracker/data/NetworkStatus.kt app/src/main/java/com/beertracker/BeerApp.kt app/src/main/AndroidManifest.xml app/src/test/java/com/beertracker/CatalogPolicyTest.kt app/src/test/java/com/beertracker/DefaultCatalogRefresherTest.kt
git commit -m "[App] Catalog refresh with last-good-wins replacement and weekly auto update"
```

---
### Task 7: User database v2, the imageUrl column, and a proven non-destructive migration

`TriedBeer` and `BeerEntity` gain one nullable `imageUrl` field. The user's phone holds real data at version 1, so the database moves to version 2 with a plain `ALTER TABLE ADD COLUMN` migration, and a Robolectric `MigrationTestHelper` test proves a v1 row survives 1 to 2 with its data intact. The recorded v2 schema JSON is committed next to the v1 JSON from Task 1.

**Files:**
- Modify: `app/src/main/java/com/beertracker/domain/TriedBeer.kt`
- Modify: `app/src/main/java/com/beertracker/data/BeerEntity.kt`
- Modify: `app/src/main/java/com/beertracker/data/BeerDatabase.kt`
- Modify: `app/src/main/java/com/beertracker/ui/AddEditBeerViewModel.kt` (one line in `save()`)
- Modify: `app/src/main/java/com/beertracker/ui/DetailScreen.kt` (preview only)
- Modify: `app/src/main/java/com/beertracker/ui/components/BeerListItem.kt` (preview only)
- Modify: `app/src/test/java/com/beertracker/TestData.kt`
- Modify: `app/src/test/java/com/beertracker/ui/ComposeUiSmokeTest.kt` (test data builder only)
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Test: `app/src/test/java/com/beertracker/BeerDatabaseMigrationTest.kt`
- Create (generated): `app/schemas/com.beertracker.data.BeerDatabase/2.json`

**Interfaces:**
- Consumes: the committed `app/schemas/com.beertracker.data.BeerDatabase/1.json` from Task 1.
- Produces: `TriedBeer.imageUrl: String?` as the LAST constructor parameter (after `addedBy`), same for `BeerEntity`; `BeerDatabase.MIGRATION_1_2`; `BeerDatabase` at version 2; `beer(...)` test helper accepts `imageUrl: String? = null`. Tasks 8 and 12 rely on `TriedBeer.imageUrl`.

- [ ] **Step 1: Add the room-testing dependency and expose schemas to tests**

In `gradle/libs.versions.toml`, add to `[libraries]` (no version entry needed, it reuses the pinned `room` version):

```toml
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
```

In `app/build.gradle.kts`, inside the `android { ... }` block, directly after the `testOptions { ... }` block, add:

```kotlin
    sourceSets {
        getByName("test") {
            assets.srcDir("schemas")
        }
    }
```

and in the `dependencies { ... }` block, after `testImplementation(libs.androidx.test.core)`, add:

```kotlin
    testImplementation(libs.androidx.room.testing)
```

This puts the committed schema JSONs on the unit-test asset path, which is where `MigrationTestHelper` looks for them (works because `unitTests.isIncludeAndroidResources = true` is already set).

- [ ] **Step 2: Write the failing migration test**

`app/src/test/java/com/beertracker/BeerDatabaseMigrationTest.kt`:

```kotlin
package com.beertracker

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.beertracker.data.BeerDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BeerDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BeerDatabase::class.java,
    )

    @Test
    fun `migrating 1 to 2 preserves an existing beer and defaults imageUrl to null`() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL(
                "INSERT INTO tried_beers (id, name, brewery, type, alcoholPercent, volumeMl, " +
                    "price, grade, tried, note, aftertaste, goesWellWith, buyAgain, favourite, " +
                    "dateAdded, catalogArticleNumber, addedBy) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(
                    "a", "Punk IPA", "BrewDog", "IPA", 5.6, 330, 29.5,
                    9, 1, "hoppy", "citrus bitter", "Red meat\u001FDessert", 1, 1,
                    12345L, "1324515", null,
                ),
            )
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true, BeerDatabase.MIGRATION_1_2)

        db.query("SELECT id, name, grade, goesWellWith, catalogArticleNumber, imageUrl FROM tried_beers").use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("a", cursor.getString(0))
            assertEquals("Punk IPA", cursor.getString(1))
            assertEquals(9, cursor.getInt(2))
            assertEquals("Red meat\u001FDessert", cursor.getString(3))
            assertEquals("1324515", cursor.getString(4))
            assertTrue(cursor.isNull(5))
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: compilation FAILS with unresolved reference `MIGRATION_1_2`.

- [ ] **Step 4: Add the field, the migration, and update every TriedBeer construction site**

`app/src/main/java/com/beertracker/domain/TriedBeer.kt`, replace the whole file:

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
) {
    init {
        require(grade == null || grade in 5..10) { "Grade must be between 5 and 10, was $grade" }
        require(grade == null || tried) { "A graded beer must be tried, grade was $grade" }
    }
}
```

`app/src/main/java/com/beertracker/data/BeerEntity.kt`, replace the whole file:

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
)

class Converters {
    @TypeConverter
    fun listToString(value: List<String>): String = value.joinToString("\u001F")

    @TypeConverter
    fun stringToList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split("\u001F")
}

fun BeerEntity.toDomain() = TriedBeer(
    id, name, brewery, type, alcoholPercent, volumeMl, price, grade, tried,
    note, aftertaste, goesWellWith, buyAgain, favourite, dateAdded,
    catalogArticleNumber, addedBy, imageUrl,
)

fun TriedBeer.toEntity() = BeerEntity(
    id, name, brewery, type, alcoholPercent, volumeMl, price, grade, tried,
    note, aftertaste, goesWellWith, buyAgain, favourite, dateAdded,
    catalogArticleNumber, addedBy, imageUrl,
)
```

`app/src/main/java/com/beertracker/data/BeerDatabase.kt`, replace the whole file:

```kotlin
package com.beertracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BeerEntity::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class BeerDatabase : RoomDatabase() {
    abstract fun beerDao(): BeerDao

    companion object {
        /**
         * v1 to v2 adds the nullable imageUrl column. The user's phone holds
         * real beers at version 1, so this must stay a non-destructive ALTER
         * TABLE. Never attach fallbackToDestructiveMigration to this
         * database; losing user data is never acceptable here.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tried_beers ADD COLUMN imageUrl TEXT")
            }
        }

        fun build(context: Context): BeerDatabase =
            Room.databaseBuilder(context, BeerDatabase::class.java, "beertracker.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
```

In `app/src/main/java/com/beertracker/ui/AddEditBeerViewModel.kt`, inside `save()`, the `TriedBeer(...)` construction currently ends with:

```kotlin
            catalogArticleNumber = existing?.catalogArticleNumber,
            addedBy = existing?.addedBy,
        )
```

Replace those lines with:

```kotlin
            catalogArticleNumber = existing?.catalogArticleNumber,
            addedBy = existing?.addedBy,
            imageUrl = existing?.imageUrl,
        )
```

(Task 8 reworks these lines again; for now an edit simply preserves whatever the stored beer had.)

Replace the whole content of `app/src/test/java/com/beertracker/TestData.kt` with the version below. The only changes against the current file are the new `imageUrl: String? = null` parameter, the `imageUrl = imageUrl` pass-through, and (from Task 4) the `catalogProduct` helper which must be preserved exactly as Task 4 added it:

```kotlin
package com.beertracker

import com.beertracker.domain.CatalogProduct
import com.beertracker.domain.TriedBeer

fun beer(
    id: String = "id",
    name: String = "Beer $id",
    brewery: String = "Brewery",
    type: String = "Lager",
    alcoholPercent: Double? = 5.0,
    volumeMl: Int? = 330,
    price: Double? = 25.0,
    grade: Int? = 7,
    tried: Boolean = true,
    note: String = "",
    aftertaste: String = "",
    goesWellWith: List<String> = emptyList(),
    buyAgain: Boolean = false,
    favourite: Boolean = false,
    dateAdded: Long = 0L,
    imageUrl: String? = null,
) = TriedBeer(
    id = id,
    name = name,
    brewery = brewery,
    type = type,
    alcoholPercent = alcoholPercent,
    volumeMl = volumeMl,
    price = price,
    grade = grade,
    tried = tried,
    note = note,
    aftertaste = aftertaste,
    goesWellWith = goesWellWith,
    buyAgain = buyAgain,
    favourite = favourite,
    dateAdded = dateAdded,
    catalogArticleNumber = null,
    addedBy = null,
    imageUrl = imageUrl,
)

fun catalogProduct(
    articleNumber: String = "1324515",
    articleNumberShort: String? = "13245",
    name: String = "Omnipollo Prodigal Pale Ale",
    brewery: String = "Omnipollo",
    type: String = "Ale",
    alcoholPercent: Double? = 5.2,
    volumeMl: Int? = 330,
    price: Double? = 25.9,
    country: String? = "Sverige",
    imageUrl: String? = "https://product-cdn.systembolaget.se/productimages/50786609/50786609",
) = CatalogProduct(
    articleNumber = articleNumber,
    articleNumberShort = articleNumberShort,
    name = name,
    brewery = brewery,
    type = type,
    alcoholPercent = alcoholPercent,
    volumeMl = volumeMl,
    price = price,
    country = country,
    imageUrl = imageUrl,
)
```

Three literal `TriedBeer(...)` constructions in previews and test data need the new named argument. In each of these files, find the construction and add `imageUrl = null,` directly after `addedBy = null,`:

- `app/src/main/java/com/beertracker/ui/DetailScreen.kt` (the `DetailContentPreview` at the bottom)
- `app/src/main/java/com/beertracker/ui/components/BeerListItem.kt` (the `BeerListItemPreview` at the bottom)
- `app/src/test/java/com/beertracker/ui/ComposeUiSmokeTest.kt` (the private `beerWithoutSubtitle()` helper at the bottom)

- [ ] **Step 5: Run the tests to verify everything passes**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass including the new migration test. KSP also writes `app/schemas/com.beertracker.data.BeerDatabase/2.json`; confirm it exists and contains `"version": 2` and an `imageUrl` column with `"notNull": false`.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/beertracker/domain/TriedBeer.kt app/src/main/java/com/beertracker/data/BeerEntity.kt app/src/main/java/com/beertracker/data/BeerDatabase.kt app/src/main/java/com/beertracker/ui/AddEditBeerViewModel.kt app/src/main/java/com/beertracker/ui/DetailScreen.kt app/src/main/java/com/beertracker/ui/components/BeerListItem.kt app/src/test/java/com/beertracker/TestData.kt app/src/test/java/com/beertracker/ui/ComposeUiSmokeTest.kt app/src/test/java/com/beertracker/BeerDatabaseMigrationTest.kt app/schemas gradle/libs.versions.toml app/build.gradle.kts
git commit -m "[App] User database v2: nullable imageUrl with proven migration"
```

---

### Task 8: Pre-fill the add form from the catalog

`AddEditBeerViewModel` learns `prefillFromCatalog(articleNumber)`: it looks the number up in the catalog and replaces the empty form with the product's fields, copying the display image URL and the article number onto the form so they are saved onto the user's own beer record. From that moment the beer owns its copies; later catalog refreshes never change a saved beer.

**Files:**
- Modify: `app/src/main/java/com/beertracker/ui/AddEditBeerViewModel.kt`
- Modify: `app/src/test/java/com/beertracker/AddEditBeerViewModelTest.kt` (append new tests only)

**Interfaces:**
- Consumes: `CatalogRepository.findByArticleNumber` and `CatalogProduct.displayImageUrl` (Task 4), `TriedBeer.imageUrl` (Task 7), `FakeCatalogRepository` and `catalogProduct(...)` (Task 4).
- Produces, relied on by Tasks 10 and 12:
  - `AddEditBeerViewModel(repository: BeerRepository, catalogRepository: CatalogRepository? = null, clock: () -> Long = { System.currentTimeMillis() })` (the new middle parameter defaults to null so every existing test call site compiles unchanged).
  - `fun prefillFromCatalog(articleNumber: String)`.
  - `BeerFormState.catalogArticleNumber: String?` and `BeerFormState.imageUrl: String?` (both default null), populated by `load(...)` and `prefillFromCatalog(...)`, written by `save()`.

- [ ] **Step 1: Write the failing tests**

Append these tests inside the existing `AddEditBeerViewModelTest` class (before its closing brace). They reuse the existing `MainDispatcherRule` and `runTest` style of the file:

```kotlin
    @Test
    fun `prefill from catalog fills the form and marks unsaved changes`() = runTest {
        val catalog = FakeCatalogRepository().apply { add(catalogProduct()) }
        val vm = AddEditBeerViewModel(FakeBeerRepository(), catalog)

        vm.prefillFromCatalog("1324515")

        val form = vm.form.value
        assertEquals("Omnipollo Prodigal Pale Ale", form.name)
        assertEquals("Omnipollo", form.brewery)
        assertEquals("Ale", form.type)
        assertEquals("5.2", form.alcoholPercent)
        assertEquals("330", form.volumeMl)
        assertEquals("25.9", form.price)
        assertEquals("1324515", form.catalogArticleNumber)
        assertEquals(
            "https://product-cdn.systembolaget.se/productimages/50786609/50786609_400.jpg",
            form.imageUrl,
        )
        assertNull(form.grade)
        assertFalse(form.tried)
        assertTrue(form.hasUnsavedChanges)
    }

    @Test
    fun `prefill accepts the short shelf number`() = runTest {
        val catalog = FakeCatalogRepository().apply { add(catalogProduct()) }
        val vm = AddEditBeerViewModel(FakeBeerRepository(), catalog)

        vm.prefillFromCatalog("13245")

        assertEquals("1324515", vm.form.value.catalogArticleNumber)
    }

    @Test
    fun `prefill with an unknown number leaves the form untouched`() = runTest {
        val catalog = FakeCatalogRepository()
        val vm = AddEditBeerViewModel(FakeBeerRepository(), catalog)

        vm.prefillFromCatalog("999999")

        assertEquals("", vm.form.value.name)
        assertNull(vm.form.value.catalogArticleNumber)
        assertFalse(vm.form.value.hasUnsavedChanges)
    }

    @Test
    fun `saving a prefilled beer stores the catalog link and image url`() = runTest {
        val repo = FakeBeerRepository()
        val catalog = FakeCatalogRepository().apply { add(catalogProduct()) }
        val vm = AddEditBeerViewModel(repo, catalog)

        vm.prefillFromCatalog("1324515")
        vm.setGrade(8)
        vm.save()

        val saved = repo.observeBeers().first().single()
        assertEquals("Omnipollo Prodigal Pale Ale", saved.name)
        assertEquals("1324515", saved.catalogArticleNumber)
        assertEquals(
            "https://product-cdn.systembolaget.se/productimages/50786609/50786609_400.jpg",
            saved.imageUrl,
        )
        assertEquals(8, saved.grade)
        assertTrue(saved.tried)
    }

    @Test
    fun `prefill runs once per article number and never overwrites edits`() = runTest {
        val catalog = FakeCatalogRepository().apply { add(catalogProduct()) }
        val vm = AddEditBeerViewModel(FakeBeerRepository(), catalog)

        vm.prefillFromCatalog("1324515")
        vm.update { it.copy(name = "My own name") }
        vm.prefillFromCatalog("1324515")

        assertEquals("My own name", vm.form.value.name)
    }

    @Test
    fun `loading an existing beer carries its image url through an edit and save`() = runTest {
        val repo = FakeBeerRepository()
        repo.addBeer(
            beer(id = "a", imageUrl = "https://product-cdn.systembolaget.se/productimages/1/1_400.jpg"),
        )
        val vm = AddEditBeerViewModel(repo)

        vm.load("a")
        assertEquals(
            "https://product-cdn.systembolaget.se/productimages/1/1_400.jpg",
            vm.form.value.imageUrl,
        )

        vm.update { it.copy(note = "still great") }
        vm.save()

        assertEquals(
            "https://product-cdn.systembolaget.se/productimages/1/1_400.jpg",
            repo.getBeer("a")?.imageUrl,
        )
    }
```

No new imports are needed: the file already imports `assertEquals`, `assertFalse`, `assertNull`, `assertTrue`, `kotlinx.coroutines.flow.first`, and `runTest` (verified against the current file).

- [ ] **Step 2: Run the tests to verify they fail**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: compilation FAILS: `AddEditBeerViewModel` has no second value parameter of type `FakeCatalogRepository`, no `prefillFromCatalog`, and `BeerFormState` has no `catalogArticleNumber` or `imageUrl`.

- [ ] **Step 3: Implement**

All edits are in `app/src/main/java/com/beertracker/ui/AddEditBeerViewModel.kt`.

(a) Add two imports:

```kotlin
import com.beertracker.domain.CatalogRepository
```

(`CancellationException` is already imported.)

(b) In `BeerFormState`, directly before the `val nameError: Boolean = false,` line, add:

```kotlin
    val catalogArticleNumber: String? = null,
    val imageUrl: String? = null,
```

(c) Change the constructor from:

```kotlin
class AddEditBeerViewModel(
    private val repository: BeerRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
```

to:

```kotlin
class AddEditBeerViewModel(
    private val repository: BeerRepository,
    private val catalogRepository: CatalogRepository? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
```

(The parameter sits in the middle so existing `AddEditBeerViewModel(repo)` and `AddEditBeerViewModel(repo, clock = ...)` test call sites keep compiling.)

(d) Next to the existing `private var loadedBeerId: String? = null` field, add:

```kotlin
    private var prefilledArticle: String? = null
```

(e) In `load(...)`, the `loadedForm = BeerFormState(...)` construction gains two lines, directly after `pairings = loaded.goesWellWith.toSet(),`:

```kotlin
                    catalogArticleNumber = loaded.catalogArticleNumber,
                    imageUrl = loaded.imageUrl,
```

(f) Add the new function directly after `load(...)`:

```kotlin
    /**
     * Fills an empty add form from a catalog product. The product's fields,
     * article number, and display image URL are copied onto the form, so
     * saving gives the user's beer its own copies; later catalog refreshes
     * never change a saved beer. Runs at most once per article number, so a
     * configuration change cannot overwrite the user's edits. Unknown
     * numbers leave the form as it was.
     */
    fun prefillFromCatalog(articleNumber: String) {
        val catalog = catalogRepository ?: return
        if (prefilledArticle == articleNumber) return
        prefilledArticle = articleNumber
        viewModelScope.launch {
            try {
                val product = catalog.findByArticleNumber(articleNumber) ?: return@launch
                val prefilled = BeerFormState(
                    name = product.name,
                    brewery = product.brewery,
                    type = product.type,
                    alcoholPercent = product.alcoholPercent?.toString() ?: "",
                    volumeMl = product.volumeMl?.toString() ?: "",
                    price = product.price?.toString() ?: "",
                    catalogArticleNumber = product.articleNumber,
                    imageUrl = product.displayImageUrl,
                )
                _form.value = prefilled.copy(
                    hasUnsavedChanges = prefilled.formContent() != baseline.formContent(),
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // A failed lookup leaves the empty manual form usable.
            }
        }
    }
```

(g) In `save()`, the `TriedBeer(...)` construction lines from Task 7:

```kotlin
            catalogArticleNumber = existing?.catalogArticleNumber,
            addedBy = existing?.addedBy,
            imageUrl = existing?.imageUrl,
        )
```

become form-driven (load and prefill both populate the form, so the form is now the single source of truth):

```kotlin
            catalogArticleNumber = f.catalogArticleNumber,
            addedBy = existing?.addedBy,
            imageUrl = f.imageUrl,
        )
```

(h) In the `companion object`, change the factory initializer line from:

```kotlin
                AddEditBeerViewModel(app.container.beerRepository)
```

to:

```kotlin
                AddEditBeerViewModel(app.container.beerRepository, app.container.catalogRepository)
```

- [ ] **Step 4: Run the tests to verify they pass**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass (6 new). Every pre-existing AddEditBeerViewModel test stays green and unmodified.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/beertracker/ui/AddEditBeerViewModel.kt app/src/test/java/com/beertracker/AddEditBeerViewModelTest.kt
git commit -m "[App] Pre-fill the add form from a catalog article number"
```

---

### Task 9: Article-number parsing and the scan view-model

All scanner intelligence lives here, fully unit-tested with no camera: a parser that pulls plausible article numbers out of raw recognized text, and a `ScanViewModel` that checks candidates against the catalog and settles on the first confirmed hit exactly once.

**Files:**
- Create: `app/src/main/java/com/beertracker/domain/ArticleNumberParser.kt`
- Create: `app/src/main/java/com/beertracker/ui/scan/ScanViewModel.kt`
- Test: `app/src/test/java/com/beertracker/ArticleNumberParserTest.kt`
- Test: `app/src/test/java/com/beertracker/ScanViewModelTest.kt`

**Interfaces:**
- Consumes: `CatalogRepository`, `CatalogProduct`, `FakeCatalogRepository`, `catalogProduct(...)` (Task 4), `MainDispatcherRule` (existing).
- Produces, relied on by Task 10:
  - `ArticleNumberParser.extractCandidates(rawText: String): List<String>`.
  - `sealed interface ScanUiState { data object Idle; data object Searching; data class Found(val product: CatalogProduct); data class NotFound(val number: String) }` in package `com.beertracker.ui.scan`.
  - `ScanViewModel(catalogRepository: CatalogRepository)` with `uiState: StateFlow<ScanUiState>`, `onTextDetected(rawText: String)`, `onManualLookup(input: String)`, `scanAgain()`, and `ScanViewModel.Factory`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/beertracker/ArticleNumberParserTest.kt`:

```kotlin
package com.beertracker

import com.beertracker.domain.ArticleNumberParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleNumberParserTest {

    @Test
    fun `finds runs of five to seven digits`() {
        assertEquals(listOf("13245"), ArticleNumberParser.extractCandidates("Nr 13245"))
        assertEquals(listOf("132451"), ArticleNumberParser.extractCandidates("132451"))
        assertEquals(listOf("1324515"), ArticleNumberParser.extractCandidates("1324515"))
    }

    @Test
    fun `ignores shorter runs like prices volumes and percentages`() {
        assertEquals(
            emptyList<String>(),
            ArticleNumberParser.extractCandidates("5,2 % vol 330 ml 29:90"),
        )
    }

    @Test
    fun `ignores longer runs like ean barcodes`() {
        assertEquals(
            emptyList<String>(),
            ArticleNumberParser.extractCandidates("7310401012345"),
        )
    }

    @Test
    fun `a realistic shelf label yields exactly the article number`() {
        val label = "Omnipollo\nProdigal Pale Ale\n5,2 % vol 330 ml\nNr 13245\n29:90"
        assertEquals(listOf("13245"), ArticleNumberParser.extractCandidates(label))
    }

    @Test
    fun `keeps first-seen order and drops duplicates`() {
        assertEquals(
            listOf("13245", "10005"),
            ArticleNumberParser.extractCandidates("13245 10005 13245"),
        )
    }

    @Test
    fun `empty and digitless text give an empty list`() {
        assertEquals(emptyList<String>(), ArticleNumberParser.extractCandidates(""))
        assertEquals(emptyList<String>(), ArticleNumberParser.extractCandidates("IPA hoppy"))
    }
}
```

`app/src/test/java/com/beertracker/ScanViewModelTest.kt`:

```kotlin
package com.beertracker

import com.beertracker.ui.scan.ScanUiState
import com.beertracker.ui.scan.ScanViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScanViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun catalogWithSample() = FakeCatalogRepository().apply { add(catalogProduct()) }

    @Test
    fun `starts idle`() {
        assertEquals(ScanUiState.Idle, ScanViewModel(catalogWithSample()).uiState.value)
    }

    @Test
    fun `detected text with a known number settles on found`() = runTest {
        val vm = ScanViewModel(catalogWithSample())
        vm.onTextDetected("Omnipollo Nr 13245 29:90")
        val state = vm.uiState.value
        assertTrue(state is ScanUiState.Found)
        assertEquals("1324515", (state as ScanUiState.Found).product.articleNumber)
    }

    @Test
    fun `detected text with unknown numbers stays idle instead of reporting a miss`() = runTest {
        val vm = ScanViewModel(catalogWithSample())
        vm.onTextDetected("Nr 99999")
        assertEquals(ScanUiState.Idle, vm.uiState.value)
    }

    @Test
    fun `the same frame text is not looked up twice`() = runTest {
        val catalog = FakeCatalogRepository()
        val vm = ScanViewModel(catalog)
        vm.onTextDetected("Nr 99999")
        vm.onTextDetected("Nr 99999")
        assertEquals(1, catalog.lookups)
    }

    @Test
    fun `once found further camera text is ignored`() = runTest {
        val catalog = catalogWithSample().apply {
            add(catalogProduct(articleNumber = "1000501", articleNumberShort = "10005", name = "Other"))
        }
        val vm = ScanViewModel(catalog)
        vm.onTextDetected("13245")
        vm.onTextDetected("10005")
        assertEquals("1324515", (vm.uiState.value as ScanUiState.Found).product.articleNumber)
    }

    @Test
    fun `manual lookup reports a miss with the typed number`() = runTest {
        val vm = ScanViewModel(catalogWithSample())
        vm.onManualLookup(" 99999 ")
        assertEquals(ScanUiState.NotFound("99999"), vm.uiState.value)
    }

    @Test
    fun `manual lookup finds by short number`() = runTest {
        val vm = ScanViewModel(catalogWithSample())
        vm.onManualLookup("13245")
        assertTrue(vm.uiState.value is ScanUiState.Found)
    }

    @Test
    fun `blank manual input does nothing`() = runTest {
        val catalog = catalogWithSample()
        val vm = ScanViewModel(catalog)
        vm.onManualLookup("   ")
        assertEquals(ScanUiState.Idle, vm.uiState.value)
        assertEquals(0, catalog.lookups)
    }

    @Test
    fun `scan again returns to idle and allows rechecking old numbers`() = runTest {
        val catalog = catalogWithSample()
        val vm = ScanViewModel(catalog)
        vm.onManualLookup("99999")
        assertEquals(ScanUiState.NotFound("99999"), vm.uiState.value)
        vm.scanAgain()
        assertEquals(ScanUiState.Idle, vm.uiState.value)
        vm.onTextDetected("13245")
        assertTrue(vm.uiState.value is ScanUiState.Found)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: compilation FAILS with unresolved references `ArticleNumberParser`, `ScanUiState`, `ScanViewModel`.

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/beertracker/domain/ArticleNumberParser.kt`:

```kotlin
package com.beertracker.domain

/**
 * Pulls article-number candidates out of raw text recognized on a shelf
 * label. Shelf labels usually print the short number (5 or 6 digits, the
 * productNumberShort); the full article number runs up to 7 digits. Digit
 * runs embedded in longer runs, like an EAN barcode number, are not
 * candidates, which is what the lookarounds enforce.
 */
object ArticleNumberParser {

    private val candidatePattern = Regex("(?<!\\d)\\d{5,7}(?!\\d)")

    fun extractCandidates(rawText: String): List<String> =
        candidatePattern.findAll(rawText).map { it.value }.distinct().toList()
}
```

`app/src/main/java/com/beertracker/ui/scan/ScanViewModel.kt`:

```kotlin
package com.beertracker.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.beertracker.BeerApp
import com.beertracker.domain.ArticleNumberParser
import com.beertracker.domain.CatalogProduct
import com.beertracker.domain.CatalogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScanUiState {
    data object Idle : ScanUiState
    data object Searching : ScanUiState
    data class Found(val product: CatalogProduct) : ScanUiState
    data class NotFound(val number: String) : ScanUiState
}

class ScanViewModel(private val catalogRepository: CatalogRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    /** Numbers already checked against the catalog, so a stream of identical
     * camera frames costs one lookup, not one per frame. */
    private val checkedNumbers = mutableSetOf<String>()

    /**
     * Feed of raw recognized text from the camera analyzer. Safe to call on
     * every frame; the first confirmed catalog hit wins and later frames are
     * ignored. Camera misses are silent (the next frame may be sharper), so
     * this never produces NotFound.
     */
    fun onTextDetected(rawText: String) {
        if (_uiState.value is ScanUiState.Found) return
        val newCandidates = ArticleNumberParser.extractCandidates(rawText)
            .filter(checkedNumbers::add)
        if (newCandidates.isEmpty()) return
        viewModelScope.launch {
            for (number in newCandidates) {
                if (_uiState.value is ScanUiState.Found) return@launch
                val product = catalogRepository.findByArticleNumber(number)
                if (product != null) {
                    _uiState.value = ScanUiState.Found(product)
                    return@launch
                }
            }
        }
    }

    /** The typed fallback path. Unlike the camera feed, a miss is reported. */
    fun onManualLookup(input: String) {
        val number = input.trim()
        if (number.isEmpty() || _uiState.value == ScanUiState.Searching) return
        _uiState.value = ScanUiState.Searching
        viewModelScope.launch {
            val product = catalogRepository.findByArticleNumber(number)
            _uiState.value = if (product != null) {
                ScanUiState.Found(product)
            } else {
                ScanUiState.NotFound(number)
            }
        }
    }

    /** Clears a NotFound answer so scanning can continue from a clean slate. */
    fun scanAgain() {
        checkedNumbers.clear()
        _uiState.value = ScanUiState.Idle
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BeerApp
                ScanViewModel(app.container.catalogRepository)
            }
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass (15 new).

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/beertracker/domain/ArticleNumberParser.kt app/src/main/java/com/beertracker/ui/scan/ScanViewModel.kt app/src/test/java/com/beertracker/ArticleNumberParserTest.kt app/src/test/java/com/beertracker/ScanViewModelTest.kt
git commit -m "[App] Article number parser and scan view-model"
```

---
### Task 10: Scan screen, navigation, camera dependencies, and manifest

The `scan` route shows a live CameraX preview feeding ML Kit's bundled on-device text recognizer (works offline, no Play Services download) into `ScanViewModel.onTextDetected`, plus a typed article-number fallback on the same screen that also works when camera permission is denied. The first confirmed catalog hit navigates to the existing edit route with a new optional `prefillArticle` argument. The overview's top bar gains a "Scan" action next to the existing add flow (the FAB keeps meaning manual add, matching its current tests). The stateless `ScanContent` takes the camera preview as a composable slot, so Robolectric tests render every UI state without touching CameraX.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/java/com/beertracker/ui/scan/ScanScreen.kt`
- Modify: `app/src/main/java/com/beertracker/MainActivity.kt`
- Modify: `app/src/main/java/com/beertracker/ui/OverviewScreen.kt`
- Modify: `app/src/main/java/com/beertracker/ui/AddEditScreen.kt`
- Modify: `app/src/test/java/com/beertracker/ui/ComposeUiSmokeTest.kt` (one appended test)
- Test: `app/src/test/java/com/beertracker/ui/scan/ScanScreenTest.kt`

**Interfaces:**
- Consumes: `ScanViewModel`, `ScanUiState` (Task 9), `AddEditBeerViewModel.prefillFromCatalog` (Task 8), `ErrorState` and `SectionHeader` components, `BeerTrackerSpacing` tokens.
- Produces:
  - Route `scan`; the edit route becomes `edit?beerId={beerId}&prefillArticle={prefillArticle}` (both arguments optional strings).
  - `ScanScreen(viewModel: ScanViewModel, onFound: (String) -> Unit, onBack: () -> Unit)`.
  - `internal enum class CameraPermission { UNKNOWN, GRANTED, DENIED }` and `internal fun ScanContent(state: ScanUiState, permission: CameraPermission, manualInput: String, onManualInputChange: (String) -> Unit, onManualLookup: () -> Unit, onScanAgain: () -> Unit, onBack: () -> Unit, cameraPreview: @Composable () -> Unit)` (test seam).
  - `OverviewScreen` gains `onScanClick: () -> Unit = {}` as its last parameter.
  - `AddEditScreen(viewModel, beerId, prefillArticle: String? = null, onDone)`.

- [ ] **Step 1: Pin and wire the camera and ML Kit dependencies**

In `gradle/libs.versions.toml`, add to `[versions]`:

```toml
camerax = "1.4.2"
mlkitTextRecognition = "16.0.1"
```

and to `[libraries]`:

```toml
androidx-camera-core = { group = "androidx.camera", name = "camera-core", version.ref = "camerax" }
androidx-camera-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
androidx-camera-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
androidx-camera-view = { group = "androidx.camera", name = "camera-view", version.ref = "camerax" }
mlkit-text-recognition = { group = "com.google.mlkit", name = "text-recognition", version.ref = "mlkitTextRecognition" }
```

In `app/build.gradle.kts`, in the `dependencies { ... }` block after `implementation(libs.androidx.navigation.compose)`, add:

```kotlin
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.text.recognition)
```

(The bundled recognizer model adds roughly 4 MB to the APK; that is the accepted price for scanning that works inside a store with no signal.)

- [ ] **Step 2: Declare the camera in the manifest**

Replace the whole content of `app/src/main/AndroidManifest.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-feature
        android:name="android.hardware.camera.any"
        android:required="false" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.CAMERA" />
    <application
        android:name=".BeerApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:theme="@style/Theme.BeerTracker">
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

`required="false"` keeps the app installable on camera-less devices; the manual entry path covers them.

- [ ] **Step 3: Add the scan strings**

In `app/src/main/res/values/strings.xml`, add these lines directly before `</resources>`:

```xml
    <string name="scan_action">Scan</string>
    <string name="scan_title">Scan shelf label</string>
    <string name="scan_hint">Point the camera at the shelf label. The article number is picked up automatically.</string>
    <string name="camera_waiting">Waiting for camera permission.</string>
    <string name="camera_denied_title">Camera unavailable</string>
    <string name="camera_denied_message">Scanning needs the camera. Allow camera access in system settings, or type the article number below.</string>
    <string name="manual_lookup_section">Type the number</string>
    <string name="manual_lookup_help">The article number is the number printed on the shelf label.</string>
    <string name="article_number_label">Article number</string>
    <string name="look_up">Look up</string>
    <string name="searching_catalog">Searching the catalog</string>
    <string name="scan_not_found_title">No catalog match</string>
    <string name="scan_not_found_message">Number %1$s is not in the beer catalog. Check the digits or add the beer manually.</string>
    <string name="scan_again">Scan again</string>
    <string name="scan_found">Found %1$s</string>
```

- [ ] **Step 4: Write the failing UI tests**

`app/src/test/java/com/beertracker/ui/scan/ScanScreenTest.kt`:

```kotlin
package com.beertracker.ui.scan

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.beertracker.FakeCatalogRepository
import com.beertracker.MainDispatcherRule
import com.beertracker.catalogProduct
import com.beertracker.ui.theme.BeerTrackerTheme
import org.junit.Assert.assertEquals
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
class ScanScreenTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private fun renderContent(
        state: ScanUiState = ScanUiState.Idle,
        permission: CameraPermission = CameraPermission.DENIED,
        onManualLookup: () -> Unit = {},
        onScanAgain: () -> Unit = {},
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
                    onBack = {},
                    cameraPreview = { Text("Fake camera preview") },
                )
            }
        }
    }

    @Test
    fun `denied permission shows the friendly error and keeps manual entry usable`() {
        var lookedUp = false
        renderContent(permission = CameraPermission.DENIED, onManualLookup = { lookedUp = true })

        composeRule.onNodeWithText("Camera unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Article number").assertIsDisplayed()
        composeRule.onNodeWithText("Look up").performClick()
        assertTrue(lookedUp)
    }

    @Test
    fun `granted permission composes the camera preview slot`() {
        renderContent(permission = CameraPermission.GRANTED)
        composeRule.onNodeWithText("Fake camera preview").assertIsDisplayed()
    }

    @Test
    fun `not found shows the typed number and scan again resets`() {
        var reset = false
        renderContent(state = ScanUiState.NotFound("99999"), onScanAgain = { reset = true })

        composeRule
            .onNodeWithText("Number 99999 is not in the beer catalog. Check the digits or add the beer manually.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Scan again").performClick()
        assertTrue(reset)
    }

    @Test
    fun `found state names the product`() {
        renderContent(state = ScanUiState.Found(catalogProduct()))
        composeRule.onNodeWithText("Found Omnipollo Prodigal Pale Ale").assertIsDisplayed()
    }

    @Test
    fun `manual lookup through the real screen navigates with the full article number`() {
        val viewModel = ScanViewModel(FakeCatalogRepository().apply { add(catalogProduct()) })
        var foundNumber: String? = null

        composeRule.setContent {
            BeerTrackerTheme {
                ScanScreen(
                    viewModel = viewModel,
                    onFound = { foundNumber = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Article number").performTextInput("13245")
        composeRule.onNodeWithText("Look up").performClick()
        composeRule.runOnIdle {
            assertEquals("1324515", foundNumber)
        }
    }
}
```

Append this test inside `ComposeUiSmokeTest` (file `app/src/test/java/com/beertracker/ui/ComposeUiSmokeTest.kt`, before the private `beerWithoutSubtitle()` helper):

```kotlin
    @Test
    fun `overview scan action invokes the scan callback`() {
        val viewModel = OverviewViewModel(FakeBeerRepository())

        composeRule.setContent {
            BeerTrackerTheme {
                var scanInvoked by remember { mutableStateOf(false) }
                if (scanInvoked) {
                    Text("Scan action invoked")
                } else {
                    OverviewScreen(
                        viewModel = viewModel,
                        onAddClick = {},
                        onBeerClick = {},
                        onScanClick = { scanInvoked = true },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Scan").performClick()
        composeRule.onNodeWithText("Scan action invoked").assertIsDisplayed()
    }
```

- [ ] **Step 5: Run the tests to verify they fail**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: compilation FAILS with unresolved references `ScanContent`, `CameraPermission`, `ScanScreen`, and no parameter `onScanClick` on `OverviewScreen`.

- [ ] **Step 6: Write the scan screen**

`app/src/main/java/com/beertracker/ui/scan/ScanScreen.kt`:

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beertracker.R
import com.beertracker.ui.components.ErrorState
import com.beertracker.ui.components.SectionHeader
import com.beertracker.ui.theme.BeerTrackerSpacing
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

internal enum class CameraPermission { UNKNOWN, GRANTED, DENIED }

@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onFound: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
    LaunchedEffect(state) {
        val found = state as? ScanUiState.Found ?: return@LaunchedEffect
        onFound(found.product.articleNumber)
    }
    var manualInput by rememberSaveable { mutableStateOf("") }

    ScanContent(
        state = state,
        permission = permission,
        manualInput = manualInput,
        onManualInputChange = { manualInput = it },
        onManualLookup = { viewModel.onManualLookup(manualInput) },
        onScanAgain = viewModel::scanAgain,
        onBack = onBack,
        cameraPreview = {
            CameraPreviewSection(onTextDetected = viewModel::onTextDetected)
        },
    )
}

internal fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScanContent(
    state: ScanUiState,
    permission: CameraPermission,
    manualInput: String,
    onManualInputChange: (String) -> Unit,
    onManualLookup: () -> Unit,
    onScanAgain: () -> Unit,
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
            when (permission) {
                CameraPermission.GRANTED -> {
                    cameraPreview()
                    Text(
                        stringResource(R.string.scan_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CameraPermission.DENIED -> {
                    ErrorState(
                        title = stringResource(R.string.camera_denied_title),
                        message = stringResource(R.string.camera_denied_message),
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

            SectionHeader(
                title = stringResource(R.string.manual_lookup_section),
                supportingText = stringResource(R.string.manual_lookup_help),
                modifier = Modifier.padding(top = BeerTrackerSpacing.small),
            )
            OutlinedTextField(
                value = manualInput,
                onValueChange = onManualInputChange,
                label = { Text(stringResource(R.string.article_number_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onManualLookup,
                enabled = manualInput.isNotBlank() && state != ScanUiState.Searching,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.look_up))
            }

            when (state) {
                ScanUiState.Searching -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(stringResource(R.string.searching_catalog))
                    }
                }
                is ScanUiState.NotFound -> {
                    ErrorState(
                        title = stringResource(R.string.scan_not_found_title),
                        message = stringResource(R.string.scan_not_found_message, state.number),
                        actionLabel = stringResource(R.string.scan_again),
                        onAction = onScanAgain,
                    )
                }
                is ScanUiState.Found -> {
                    Text(
                        stringResource(R.string.scan_found, state.product.name),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                ScanUiState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun CameraPreviewSection(onTextDetected: (String) -> Unit) {
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
        val analyzer = LabelAnalyzer(onTextDetected)
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
 * Deduplication of repeated numbers happens in ScanViewModel.
 */
private class LabelAnalyzer(private val onText: (String) -> Unit) : ImageAnalysis.Analyzer {

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

- [ ] **Step 7: Wire the routes and the overview action**

Replace the whole content of `app/src/main/java/com/beertracker/MainActivity.kt` with:

```kotlin
package com.beertracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.beertracker.ui.AddEditBeerViewModel
import com.beertracker.ui.AddEditScreen
import com.beertracker.ui.DetailScreen
import com.beertracker.ui.DetailViewModel
import com.beertracker.ui.OverviewScreen
import com.beertracker.ui.OverviewViewModel
import com.beertracker.ui.scan.ScanScreen
import com.beertracker.ui.scan.ScanViewModel
import com.beertracker.ui.theme.BeerTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BeerTrackerTheme {
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
                onScanClick = { navController.navigate("scan") },
            )
        }
        composable(
            route = "edit?beerId={beerId}&prefillArticle={prefillArticle}",
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
            ),
        ) { backStackEntry ->
            AddEditScreen(
                viewModel = viewModel(factory = AddEditBeerViewModel.Factory),
                beerId = backStackEntry.arguments?.getString("beerId"),
                prefillArticle = backStackEntry.arguments?.getString("prefillArticle"),
                onDone = { navController.popBackStack() },
            )
        }
        composable("scan") {
            ScanScreen(
                viewModel = viewModel(factory = ScanViewModel.Factory),
                onFound = { articleNumber ->
                    navController.navigate("edit?prefillArticle=$articleNumber") {
                        popUpTo("scan") { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable("detail/{beerId}") { backStackEntry ->
            val beerId = backStackEntry.arguments?.getString("beerId") ?: return@composable
            DetailScreen(
                viewModel = viewModel(factory = DetailViewModel.factory(beerId)),
                onEdit = { id -> navController.navigate("edit?beerId=$id") },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
```

In `app/src/main/java/com/beertracker/ui/OverviewScreen.kt`, change the composable signature from:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel,
    onAddClick: () -> Unit,
    onBeerClick: (String) -> Unit,
) {
```

to:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel,
    onAddClick: () -> Unit,
    onBeerClick: (String) -> Unit,
    onScanClick: () -> Unit = {},
) {
```

and change its `TopAppBar` from:

```kotlin
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
```

to:

```kotlin
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = onScanClick) {
                        Text(stringResource(R.string.scan_action))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
```

(`TextButton` is already imported in this file.)

In `app/src/main/java/com/beertracker/ui/AddEditScreen.kt`, change the signature and load effect from:

```kotlin
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
```

to:

```kotlin
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditScreen(
    viewModel: AddEditBeerViewModel,
    beerId: String?,
    prefillArticle: String? = null,
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
```

- [ ] **Step 8: Run the tests to verify they pass**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass (6 new). Then also confirm the full app still assembles with the new dependencies:

```powershell
.\gradlew.bat assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```powershell
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml app/src/main/java/com/beertracker/ui/scan/ScanScreen.kt app/src/main/java/com/beertracker/MainActivity.kt app/src/main/java/com/beertracker/ui/OverviewScreen.kt app/src/main/java/com/beertracker/ui/AddEditScreen.kt app/src/test/java/com/beertracker/ui/scan/ScanScreenTest.kt app/src/test/java/com/beertracker/ui/ComposeUiSmokeTest.kt
git commit -m "[App] Shelf label scan screen with catalog prefill navigation"
```

---

### Task 11: Manual catalog update on the overview screen

An update action in the overview top bar opens a small dialog showing the catalog's standing ("Updated 8 Aug 2026, 1534 beers" or the bundled-seed state) with an Update now button. While refreshing, the top bar shows a small progress indicator; the result lands as a snackbar. All state transitions live in `CatalogRefreshViewModel` and are unit-tested with fakes.

**Files:**
- Create: `app/src/main/java/com/beertracker/ui/CatalogRefreshViewModel.kt`
- Modify: `app/src/main/java/com/beertracker/ui/OverviewScreen.kt`
- Modify: `app/src/main/java/com/beertracker/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/test/java/com/beertracker/FakeCatalogRefresher.kt`
- Modify: `app/src/test/java/com/beertracker/ui/ComposeUiSmokeTest.kt` (three call sites gain the new parameter)
- Test: `app/src/test/java/com/beertracker/CatalogRefreshViewModelTest.kt`
- Test: `app/src/test/java/com/beertracker/ui/OverviewCatalogRefreshTest.kt`

**Interfaces:**
- Consumes: `CatalogRefresher` and `RefreshResult` (Task 6), `CatalogRepository.observeStatus` and `CatalogStatus` (Task 4), `FakeCatalogRepository` (Task 4).
- Produces:
  - `sealed interface CatalogRefreshUiState { data object Idle; data object Refreshing; data class Done(val result: RefreshResult) }`.
  - `CatalogRefreshViewModel(catalogRepository: CatalogRepository, catalogRefresher: CatalogRefresher)` with `refreshState: StateFlow<CatalogRefreshUiState>`, `status: StateFlow<CatalogStatus?>`, `refresh()`, `acknowledgeResult()`, and `CatalogRefreshViewModel.Factory`.
  - `OverviewScreen(viewModel, catalogViewModel: CatalogRefreshViewModel, onAddClick, onBeerClick, onScanClick = {})` (new second parameter, required).
  - Test helper `FakeCatalogRefresher` with `result`, `refreshCalls`, and an optional `gate: CompletableDeferred<Unit>?`.

- [ ] **Step 1: Add the strings**

In `app/src/main/res/values/strings.xml`, add these lines directly before `</resources>`:

```xml
    <string name="update_catalog">Update beer catalog</string>
    <string name="update_now">Update now</string>
    <string name="catalog_status_unknown">The catalog is still being prepared.</string>
    <string name="catalog_status_bundled">Using the bundled catalog, %1$d beers. Not updated yet.</string>
    <string name="catalog_status_updated">Updated %1$s, %2$d beers.</string>
    <string name="catalog_updated_message">Catalog updated, %1$d beers</string>
```

- [ ] **Step 2: Write the failing tests and the fake**

`app/src/test/java/com/beertracker/FakeCatalogRefresher.kt`:

```kotlin
package com.beertracker

import com.beertracker.domain.CatalogRefresher
import com.beertracker.domain.RefreshResult
import kotlinx.coroutines.CompletableDeferred

class FakeCatalogRefresher : CatalogRefresher {

    var result: RefreshResult = RefreshResult.Success(beerCount = 1534, refreshedUtc = 0L)
    var refreshCalls = 0
        private set

    /** When set, refresh() suspends until the gate completes, so tests can
     * observe the Refreshing state and reentrancy behavior. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun refresh(): RefreshResult {
        refreshCalls += 1
        gate?.await()
        return result
    }
}
```

`app/src/test/java/com/beertracker/CatalogRefreshViewModelTest.kt`:

```kotlin
package com.beertracker

import com.beertracker.domain.CatalogStatus
import com.beertracker.domain.RefreshResult
import com.beertracker.ui.CatalogRefreshUiState
import com.beertracker.ui.CatalogRefreshViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CatalogRefreshViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        repository: FakeCatalogRepository = FakeCatalogRepository(),
        refresher: FakeCatalogRefresher = FakeCatalogRefresher(),
    ) = CatalogRefreshViewModel(repository, refresher)

    private fun TestScope.collectingStatus(vm: CatalogRefreshViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.status.collect() }
    }

    @Test
    fun `starts idle`() {
        assertEquals(CatalogRefreshUiState.Idle, viewModel().refreshState.value)
    }

    @Test
    fun `refresh lands on done with the success result`() = runTest {
        val refresher = FakeCatalogRefresher().apply {
            result = RefreshResult.Success(beerCount = 1534, refreshedUtc = 42L)
        }
        val vm = viewModel(refresher = refresher)

        vm.refresh()

        assertEquals(
            CatalogRefreshUiState.Done(RefreshResult.Success(beerCount = 1534, refreshedUtc = 42L)),
            vm.refreshState.value,
        )
        assertEquals(1, refresher.refreshCalls)
    }

    @Test
    fun `refresh lands on done with the failure result`() = runTest {
        val refresher = FakeCatalogRefresher().apply {
            result = RefreshResult.Failure("Could not reach the Systembolaget catalog")
        }
        val vm = viewModel(refresher = refresher)

        vm.refresh()

        assertEquals(
            CatalogRefreshUiState.Done(
                RefreshResult.Failure("Could not reach the Systembolaget catalog"),
            ),
            vm.refreshState.value,
        )
    }

    @Test
    fun `refresh while refreshing is ignored`() = runTest {
        val refresher = FakeCatalogRefresher().apply { gate = CompletableDeferred() }
        val vm = viewModel(refresher = refresher)

        vm.refresh()
        assertEquals(CatalogRefreshUiState.Refreshing, vm.refreshState.value)
        vm.refresh()
        refresher.gate?.complete(Unit)

        assertEquals(1, refresher.refreshCalls)
    }

    @Test
    fun `acknowledging the result returns to idle`() = runTest {
        val vm = viewModel()
        vm.refresh()
        vm.acknowledgeResult()
        assertEquals(CatalogRefreshUiState.Idle, vm.refreshState.value)
    }

    @Test
    fun `status mirrors the repository`() = runTest {
        val repository = FakeCatalogRepository()
        val vm = viewModel(repository = repository)
        collectingStatus(vm)

        repository.status.value = CatalogStatus(beerCount = 7, lastRefreshUtc = 1L)

        assertEquals(CatalogStatus(beerCount = 7, lastRefreshUtc = 1L), vm.status.value)
    }
}
```

`app/src/test/java/com/beertracker/ui/OverviewCatalogRefreshTest.kt`:

```kotlin
package com.beertracker.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.beertracker.FakeBeerRepository
import com.beertracker.FakeCatalogRefresher
import com.beertracker.FakeCatalogRepository
import com.beertracker.MainDispatcherRule
import com.beertracker.domain.CatalogStatus
import com.beertracker.domain.RefreshResult
import com.beertracker.ui.theme.BeerTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverviewCatalogRefreshTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private fun render(refresher: FakeCatalogRefresher): FakeCatalogRefresher {
        val catalogRepository = FakeCatalogRepository().apply {
            status.value = CatalogStatus(beerCount = 42, lastRefreshUtc = null)
        }
        val catalogViewModel = CatalogRefreshViewModel(catalogRepository, refresher)
        composeRule.setContent {
            BeerTrackerTheme {
                OverviewScreen(
                    viewModel = OverviewViewModel(FakeBeerRepository()),
                    catalogViewModel = catalogViewModel,
                    onAddClick = {},
                    onBeerClick = {},
                )
            }
        }
        return refresher
    }

    @Test
    fun `the update dialog shows the bundled status and a success snackbar after updating`() {
        val refresher = render(
            FakeCatalogRefresher().apply {
                result = RefreshResult.Success(beerCount = 1534, refreshedUtc = 0L)
            },
        )

        composeRule.onNodeWithContentDescription("Update beer catalog").performClick()
        composeRule.onNodeWithText("Using the bundled catalog, 42 beers. Not updated yet.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Update now").performClick()

        composeRule.onNodeWithText("Catalog updated, 1534 beers").assertIsDisplayed()
        assertEquals(1, refresher.refreshCalls)
    }

    @Test
    fun `a failed update shows the calm failure message`() {
        render(
            FakeCatalogRefresher().apply {
                result = RefreshResult.Failure("Could not reach the Systembolaget catalog")
            },
        )

        composeRule.onNodeWithContentDescription("Update beer catalog").performClick()
        composeRule.onNodeWithText("Update now").performClick()

        composeRule.onNodeWithText("Could not reach the Systembolaget catalog").assertIsDisplayed()
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: compilation FAILS with unresolved references `CatalogRefreshViewModel`, `CatalogRefreshUiState`, and no parameter `catalogViewModel` on `OverviewScreen`.

- [ ] **Step 4: Write the view-model**

`app/src/main/java/com/beertracker/ui/CatalogRefreshViewModel.kt`:

```kotlin
package com.beertracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.beertracker.BeerApp
import com.beertracker.domain.CatalogRefresher
import com.beertracker.domain.CatalogRepository
import com.beertracker.domain.CatalogStatus
import com.beertracker.domain.RefreshResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CatalogRefreshUiState {
    data object Idle : CatalogRefreshUiState
    data object Refreshing : CatalogRefreshUiState
    data class Done(val result: RefreshResult) : CatalogRefreshUiState
}

class CatalogRefreshViewModel(
    catalogRepository: CatalogRepository,
    private val catalogRefresher: CatalogRefresher,
) : ViewModel() {

    private val _refreshState = MutableStateFlow<CatalogRefreshUiState>(CatalogRefreshUiState.Idle)
    val refreshState: StateFlow<CatalogRefreshUiState> = _refreshState.asStateFlow()

    val status: StateFlow<CatalogStatus?> = catalogRepository.observeStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() {
        if (_refreshState.value == CatalogRefreshUiState.Refreshing) return
        _refreshState.value = CatalogRefreshUiState.Refreshing
        viewModelScope.launch {
            _refreshState.value = CatalogRefreshUiState.Done(catalogRefresher.refresh())
        }
    }

    /** Called after the result snackbar has been shown. */
    fun acknowledgeResult() {
        if (_refreshState.value is CatalogRefreshUiState.Done) {
            _refreshState.value = CatalogRefreshUiState.Idle
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BeerApp
                CatalogRefreshViewModel(
                    app.container.catalogRepository,
                    app.container.catalogRefresher,
                )
            }
        }
    }
}
```

- [ ] **Step 5: Rework the overview screen**

Replace the whole content of `app/src/main/java/com/beertracker/ui/OverviewScreen.kt` with (this is the Task 10 version plus the catalog action, dialog, and snackbar; the `FilterRow`, `selectedIcon`, and `sortLabel` privates are unchanged from the current file):

```kotlin
package com.beertracker.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beertracker.R
import com.beertracker.domain.BeerSort
import com.beertracker.domain.RefreshResult
import com.beertracker.ui.components.BeerListItem
import com.beertracker.ui.components.EmptyState
import com.beertracker.ui.components.ErrorState
import com.beertracker.ui.components.LoadingState
import com.beertracker.ui.theme.BeerTrackerSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel,
    catalogViewModel: CatalogRefreshViewModel,
    onAddClick: () -> Unit,
    onBeerClick: (String) -> Unit,
    onScanClick: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val refreshState by catalogViewModel.refreshState.collectAsStateWithLifecycle()
    val catalogStatus by catalogViewModel.status.collectAsStateWithLifecycle()
    var showCatalogDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val doneResult = (refreshState as? CatalogRefreshUiState.Done)?.result
    val resultMessage = when (doneResult) {
        is RefreshResult.Success ->
            stringResource(R.string.catalog_updated_message, doneResult.beerCount)
        is RefreshResult.Failure -> doneResult.reason
        null -> null
    }
    LaunchedEffect(refreshState) {
        if (resultMessage != null) {
            snackbarHostState.showSnackbar(resultMessage)
            catalogViewModel.acknowledgeResult()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = onScanClick) {
                        Text(stringResource(R.string.scan_action))
                    }
                    if (refreshState == CatalogRefreshUiState.Refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = BeerTrackerSpacing.medium)
                                .size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = { showCatalogDialog = true }) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.update_catalog),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            if (state is OverviewUiState.Content) {
                ExtendedFloatingActionButton(
                    onClick = onAddClick,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_beer)) },
                )
            }
        },
    ) { padding ->
        when (val current = state) {
            OverviewUiState.Loading -> {
                LoadingState(
                    label = stringResource(R.string.load_cellar),
                    modifier = Modifier.padding(padding),
                )
            }
            OverviewUiState.Error -> {
                ErrorState(
                    title = stringResource(R.string.overview_error_title),
                    message = stringResource(R.string.overview_error_message),
                    actionLabel = stringResource(R.string.retry),
                    onAction = viewModel::tryAgain,
                    modifier = Modifier.padding(padding).fillMaxSize(),
                )
            }
            is OverviewUiState.Content -> {
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize(),
                ) {
                    OutlinedTextField(
                        value = current.filter.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = BeerTrackerSpacing.large),
                        label = { Text(stringResource(R.string.search_label)) },
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = if (current.filter.query.isNotEmpty()) {
                            {
                                IconButton(onClick = { viewModel.setQuery("") }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.clear_search),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                    )
                    FilterRow(
                        state = current,
                        onToggleBuyAgain = viewModel::toggleBuyAgainOnly,
                        onToggleFavourite = viewModel::toggleFavouritesOnly,
                        onToggleNotTried = viewModel::toggleNotTriedOnly,
                        onToggleType = viewModel::toggleType,
                        onSort = viewModel::setSort,
                    )
                    when (current.emptyState) {
                        OverviewEmptyState.EMPTY_CELLAR -> {
                            EmptyState(
                                title = stringResource(R.string.empty_cellar_title),
                                message = stringResource(R.string.empty_cellar_message),
                                actionLabel = stringResource(R.string.add_beer),
                                onAction = onAddClick,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OverviewEmptyState.NO_RESULTS -> {
                            EmptyState(
                                title = stringResource(R.string.no_results_title),
                                message = stringResource(R.string.no_results_message),
                                actionLabel = stringResource(R.string.clear_filters),
                                onAction = viewModel::clearFilters,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        null -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = BeerTrackerSpacing.large,
                                    end = BeerTrackerSpacing.large,
                                    bottom = 96.dp,
                                ),
                            ) {
                                items(current.beers, key = { it.id }) { beer ->
                                    BeerListItem(
                                        beer = beer,
                                        onClick = { onBeerClick(beer.id) },
                                        modifier = Modifier.padding(
                                            vertical = BeerTrackerSpacing.xSmall,
                                        ),
                                    )
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCatalogDialog) {
        val status = catalogStatus
        AlertDialog(
            onDismissRequest = { showCatalogDialog = false },
            title = { Text(stringResource(R.string.update_catalog)) },
            text = {
                Text(
                    when {
                        status == null -> stringResource(R.string.catalog_status_unknown)
                        status.lastRefreshUtc == null ->
                            stringResource(R.string.catalog_status_bundled, status.beerCount)
                        else -> stringResource(
                            R.string.catalog_status_updated,
                            formatCatalogDate(status.lastRefreshUtc),
                            status.beerCount,
                        )
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCatalogDialog = false
                        catalogViewModel.refresh()
                    },
                ) {
                    Text(stringResource(R.string.update_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCatalogDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun formatCatalogDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
```

Keep everything from `@Composable private fun FilterRow(` to the end of the file exactly as it is today (the `FilterRow`, `selectedIcon`, and `sortLabel` functions are untouched; only the imports, the `OverviewScreen` function, and the new `formatCatalogDate` above them change).

- [ ] **Step 6: Wire the new view-model at the call sites**

In `app/src/main/java/com/beertracker/MainActivity.kt`, replace the `composable("overview")` block with:

```kotlin
        composable("overview") {
            OverviewScreen(
                viewModel = viewModel(factory = OverviewViewModel.Factory),
                catalogViewModel = viewModel(factory = CatalogRefreshViewModel.Factory),
                onAddClick = { navController.navigate("edit") },
                onBeerClick = { id -> navController.navigate("detail/$id") },
                onScanClick = { navController.navigate("scan") },
            )
        }
```

and add the import:

```kotlin
import com.beertracker.ui.CatalogRefreshViewModel
```

In `app/src/test/java/com/beertracker/ui/ComposeUiSmokeTest.kt`, add two imports:

```kotlin
import com.beertracker.FakeCatalogRefresher
import com.beertracker.FakeCatalogRepository
```

and update all three `OverviewScreen(...)` call sites (in the tests `overview empty cellar exposes and invokes add action`, `overview error offers retry and recovers`, and `overview scan action invokes the scan callback`) to pass the new parameter. Each call gains one line directly after `viewModel = viewModel,`:

```kotlin
                        catalogViewModel = CatalogRefreshViewModel(
                            FakeCatalogRepository(),
                            FakeCatalogRefresher(),
                        ),
```

- [ ] **Step 7: Run the tests to verify they pass**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass (8 new), including the three updated smoke tests.

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/java/com/beertracker/ui/CatalogRefreshViewModel.kt app/src/main/java/com/beertracker/ui/OverviewScreen.kt app/src/main/java/com/beertracker/MainActivity.kt app/src/main/res/values/strings.xml app/src/test/java/com/beertracker/FakeCatalogRefresher.kt app/src/test/java/com/beertracker/CatalogRefreshViewModelTest.kt app/src/test/java/com/beertracker/ui/OverviewCatalogRefreshTest.kt app/src/test/java/com/beertracker/ui/ComposeUiSmokeTest.kt
git commit -m "[App] Manual catalog update with status dialog on the overview"
```

---

### Task 12: Product image on the detail screen

Coil loads the beer's stored image URL on demand on the detail screen. The overview list rows deliberately get no thumbnails in this phase; the list stays light. Beers without an image simply show no image block.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/beertracker/ui/DetailScreen.kt`
- Test: `app/src/test/java/com/beertracker/ui/DetailScreenImageTest.kt`

**Interfaces:**
- Consumes: `TriedBeer.imageUrl` (Task 7, populated by Task 8's prefill), `DetailViewModel` and `DetailScreen` (existing).
- Produces: the detail screen renders an `AsyncImage` with content description `Product image for <name>` when `imageUrl != null`. Nothing else changes.

- [ ] **Step 1: Pin and wire Coil**

In `gradle/libs.versions.toml`, add to `[versions]`:

```toml
coil = "2.7.0"
```

and to `[libraries]`:

```toml
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
```

In `app/build.gradle.kts`, in the `dependencies { ... }` block after `implementation(libs.mlkit.text.recognition)`, add:

```kotlin
    implementation(libs.coil.compose)
```

- [ ] **Step 2: Add the string**

In `app/src/main/res/values/strings.xml`, add directly before `</resources>`:

```xml
    <string name="beer_image_description">Product image for %1$s</string>
```

- [ ] **Step 3: Write the failing test**

`app/src/test/java/com/beertracker/ui/DetailScreenImageTest.kt` (the test image URL uses the reserved `.invalid` TLD so nothing is ever fetched in tests; the node's content description exists regardless of load state):

```kotlin
package com.beertracker.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.beertracker.FakeBeerRepository
import com.beertracker.MainDispatcherRule
import com.beertracker.beer
import com.beertracker.ui.theme.BeerTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailScreenImageTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun `detail shows the product image when the beer has an image url`() {
        val repo = FakeBeerRepository()
        runBlocking {
            repo.addBeer(
                beer(
                    id = "a",
                    name = "Punk IPA",
                    imageUrl = "https://cdn.example.invalid/productimages/1/1_400.jpg",
                ),
            )
        }

        composeRule.setContent {
            BeerTrackerTheme {
                DetailScreen(
                    viewModel = DetailViewModel(repo, "a"),
                    onEdit = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Product image for Punk IPA").assertIsDisplayed()
    }

    @Test
    fun `detail shows no image block when the beer has none`() {
        val repo = FakeBeerRepository()
        runBlocking { repo.addBeer(beer(id = "a", name = "Punk IPA", imageUrl = null)) }

        composeRule.setContent {
            BeerTrackerTheme {
                DetailScreen(
                    viewModel = DetailViewModel(repo, "a"),
                    onEdit = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Product image for Punk IPA").assertDoesNotExist()
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: the new tests FAIL: the first cannot find a node with content description `Product image for Punk IPA` (the second passes trivially; that is fine, the pair documents both behaviors).

- [ ] **Step 5: Implement**

In `app/src/main/java/com/beertracker/ui/DetailScreen.kt`, add these imports (alphabetically among the existing ones):

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
```

and inside `DetailContent`, insert this block as the FIRST child of the outer `Column`, directly before the header `Row(...)`:

```kotlin
        if (beer.imageUrl != null) {
            AsyncImage(
                model = beer.imageUrl,
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

- [ ] **Step 6: Run the tests and the full build**

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Expected: BUILD SUCCESSFUL twice, all tests pass (2 new).

- [ ] **Step 7: Commit**

```powershell
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/res/values/strings.xml app/src/main/java/com/beertracker/ui/DetailScreen.kt app/src/test/java/com/beertracker/ui/DetailScreenImageTest.kt
git commit -m "[App] Product image on the detail screen via Coil"
```

---

## Done criteria

Phase 2 is done when all of the following hold:

1. `.\gradlew.bat testDebugUnitTest` is green (the 88 baseline tests plus roughly 65 new ones, all JVM), and `python -m unittest discover -s scripts -p "test_*.py"` is green (10 script tests).
2. `.\gradlew.bat assembleDebug` is green.
3. `app/src/main/assets/catalog/beers.json` is committed with 1000 to 3000 beers.
4. `app/schemas/com.beertracker.data.BeerDatabase/1.json` and `2.json` are both committed, and `BeerDatabaseMigrationTest` proves 1 to 2 preserves data.
5. Merging to main ships a release APK automatically (existing `release.yml`).
6. After merge, verified by the user on their phone (no device is available earlier):
   - updating the app keeps every existing beer (the v2 migration ran against real data),
   - Scan opens the camera, reads a shelf label in a store, and lands on a pre-filled add form,
   - denying camera permission still allows typing the number on the scan screen,
   - a beer added through the scan shows its product image on the detail screen when online,
   - the overview's update action reports something like "Catalog updated, 1534 beers", and the dialog then shows the update date.
7. No user provisioning was needed at any point.
