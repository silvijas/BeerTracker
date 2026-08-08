# Automatic Signed APK Releases Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish a verified, signed BeerTracker APK as the latest GitHub Release after every successful push to `main`, with a permanent latest-download link in the README.

**Architecture:** Gradle reads optional CI version and release-signing values from environment variables while preserving existing local defaults. A least-privilege GitHub Actions workflow tests the app, reconstructs the keystore in the ephemeral runner, builds and verifies the APK, and creates a uniquely tagged release whose asset always has the same filename. The long-lived key is generated outside the repository, protected locally with Windows DPAPI, and copied into GitHub repository secrets through the authenticated GitHub CLI.

**Tech Stack:** Android Gradle Plugin 8.7.3, Gradle 8.10.2, Kotlin DSL, Java 17, GitHub Actions, GitHub CLI, Java `keytool`, Android `apksigner`, PowerShell.

## Global Constraints

- Trigger releases on every push to `main`.
- Use the same long-lived signing identity for every published APK for application ID `com.beertracker`.
- A differently signed existing installation requires one uninstall before the first pipeline APK can be installed.
- Keep all keystore bytes and passwords outside Git history and workflow logs.
- Grant the workflow only `contents: write`.
- Do not publish a release unless unit tests, APK assembly, and signature verification all succeed.
- Use a monotonically increasing CI `versionCode` based on GitHub run number and attempt.
- Upload the release asset as exactly `BeerTracker.apk`.
- Link the README to `https://github.com/silvijas/BeerTracker/releases/latest/download/BeerTracker.apk`.
- Do not commit or push changes unless the user separately requests those operations.

---

### Task 1: Gradle CI versioning and release signing

**Files:**
- Modify: `app/build.gradle.kts:8-38`

**Interfaces:**
- Consumes: optional environment variables `CI_VERSION_CODE`, `CI_VERSION_NAME`, `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`.
- Produces: local builds with unchanged default version `1` / `0.1.0`; CI release builds signed by the supplied keystore and assigned the supplied version.

- [ ] **Step 1: Capture the existing local release behavior**

Run:

```powershell
.\gradlew.bat :app:assembleRelease
```

Expected: `BUILD SUCCESSFUL`. The existing APK is unsigned because no release signing configuration exists.

- [ ] **Step 2: Add validated environment-driven versioning and signing**

Add these declarations inside `android {}` before `defaultConfig`:

```kotlin
val releaseSigningValues = mapOf(
    "ANDROID_KEYSTORE_PATH" to providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull,
    "ANDROID_KEYSTORE_PASSWORD" to providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull,
    "ANDROID_KEY_ALIAS" to providers.environmentVariable("ANDROID_KEY_ALIAS").orNull,
    "ANDROID_KEY_PASSWORD" to providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull,
)
val hasReleaseSigningValue = releaseSigningValues.values.any { !it.isNullOrBlank() }
val hasCompleteReleaseSigning = releaseSigningValues.values.all { !it.isNullOrBlank() }

require(!hasReleaseSigningValue || hasCompleteReleaseSigning) {
    "Release signing requires ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, " +
        "ANDROID_KEY_ALIAS, and ANDROID_KEY_PASSWORD"
}
```

Replace the fixed versions in `defaultConfig` with:

```kotlin
versionCode = providers.environmentVariable("CI_VERSION_CODE").orNull?.toInt() ?: 1
versionName = providers.environmentVariable("CI_VERSION_NAME").orNull ?: "0.1.0"
```

Add before `buildTypes`:

```kotlin
signingConfigs {
    if (hasCompleteReleaseSigning) {
        create("release") {
            storeFile = file(requireNotNull(releaseSigningValues["ANDROID_KEYSTORE_PATH"]))
            storePassword = releaseSigningValues["ANDROID_KEYSTORE_PASSWORD"]
            keyAlias = releaseSigningValues["ANDROID_KEY_ALIAS"]
            keyPassword = releaseSigningValues["ANDROID_KEY_PASSWORD"]
        }
    }
}
```

Add inside `buildTypes.release`:

```kotlin
if (hasCompleteReleaseSigning) {
    signingConfig = signingConfigs.getByName("release")
}
```

- [ ] **Step 3: Verify local defaults still work**

Run:

```powershell
Remove-Item Env:CI_VERSION_CODE, Env:CI_VERSION_NAME, Env:ANDROID_KEYSTORE_PATH, Env:ANDROID_KEYSTORE_PASSWORD, Env:ANDROID_KEY_ALIAS, Env:ANDROID_KEY_PASSWORD -ErrorAction SilentlyContinue
.\gradlew.bat :app:assembleRelease
```

Expected: `BUILD SUCCESSFUL`; local builds do not require signing secrets.

- [ ] **Step 4: Verify partial signing configuration fails clearly**

Run:

```powershell
$env:ANDROID_KEY_ALIAS = 'beertracker-release'
.\gradlew.bat :app:tasks
$exitCode = $LASTEXITCODE
Remove-Item Env:ANDROID_KEY_ALIAS
if ($exitCode -eq 0) { throw 'Partial signing configuration unexpectedly succeeded' }
```

Expected: Gradle fails with `Release signing requires` and the four required variable names.

### Task 2: Automated release workflow

**Files:**
- Create: `.github/workflows/release.yml`

**Interfaces:**
- Consumes: repository secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`; Gradle environment interface from Task 1.
- Produces: a non-draft, non-prerelease GitHub Release tagged `build-<run_number>-<run_attempt>` with asset `BeerTracker.apk`.

- [ ] **Step 1: Create the release workflow**

Create `.github/workflows/release.yml` with:

```yaml
name: Release signed APK

on:
  push:
    branches:
      - main

permissions:
  contents: write

concurrency:
  group: beertracker-main-release
  cancel-in-progress: true

jobs:
  release:
    name: Test, sign, and release
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - name: Check out source
        uses: actions/checkout@v7

      - name: Set up Java 17
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v5

      - name: Calculate build version
        shell: bash
        run: |
          version_code=$((GITHUB_RUN_NUMBER * 100 + GITHUB_RUN_ATTEMPT))
          echo "CI_VERSION_CODE=$version_code" >> "$GITHUB_ENV"
          echo "CI_VERSION_NAME=0.1.0-ci.${GITHUB_RUN_NUMBER}.${GITHUB_RUN_ATTEMPT}" >> "$GITHUB_ENV"

      - name: Run unit tests
        run: ./gradlew testDebugUnitTest

      - name: Validate signing secrets
        env:
          ANDROID_KEYSTORE_BASE64: ${{ secrets.ANDROID_KEYSTORE_BASE64 }}
          ANDROID_KEYSTORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD }}
          ANDROID_KEY_ALIAS: ${{ secrets.ANDROID_KEY_ALIAS }}
          ANDROID_KEY_PASSWORD: ${{ secrets.ANDROID_KEY_PASSWORD }}
        shell: bash
        run: |
          for name in ANDROID_KEYSTORE_BASE64 ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do
            if [[ -z "${!name}" ]]; then
              echo "::error::$name is not configured"
              exit 1
            fi
          done

      - name: Restore release keystore
        env:
          ANDROID_KEYSTORE_BASE64: ${{ secrets.ANDROID_KEYSTORE_BASE64 }}
        shell: bash
        run: |
          keystore_path="$RUNNER_TEMP/beertracker-release.p12"
          printf '%s' "$ANDROID_KEYSTORE_BASE64" | base64 --decode > "$keystore_path"
          echo "ANDROID_KEYSTORE_PATH=$keystore_path" >> "$GITHUB_ENV"

      - name: Build signed release APK
        env:
          ANDROID_KEYSTORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD }}
          ANDROID_KEY_ALIAS: ${{ secrets.ANDROID_KEY_ALIAS }}
          ANDROID_KEY_PASSWORD: ${{ secrets.ANDROID_KEY_PASSWORD }}
        run: ./gradlew :app:assembleRelease

      - name: Verify APK signature
        shell: bash
        run: |
          apksigner_path="$(find "$ANDROID_HOME/build-tools" -type f -name apksigner | sort -V | tail -n 1)"
          if [[ -z "$apksigner_path" ]]; then
            echo "::error::apksigner was not found"
            exit 1
          fi
          "$apksigner_path" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk

      - name: Prepare release asset
        shell: bash
        run: |
          mkdir -p dist
          cp app/build/outputs/apk/release/app-release.apk dist/BeerTracker.apk

      - name: Publish GitHub Release
        env:
          GH_TOKEN: ${{ github.token }}
        shell: bash
        run: |
          tag="build-${GITHUB_RUN_NUMBER}-${GITHUB_RUN_ATTEMPT}"
          gh release create "$tag" dist/BeerTracker.apk \
            --target "$GITHUB_SHA" \
            --title "BeerTracker ${CI_VERSION_NAME}" \
            --notes "Automated signed APK for commit ${GITHUB_SHA}." \
            --latest
```

- [ ] **Step 2: Inspect the workflow for release-safety invariants**

Confirm all of the following directly in `.github/workflows/release.yml`:

```text
trigger branch: main
permission: contents: write
concurrency cancellation: enabled
tests occur before keystore restoration and release creation
signature verification occurs before release creation
asset path: dist/BeerTracker.apk
release is neither draft nor prerelease
```

- [ ] **Step 3: Confirm Gradle wrapper compatibility on Linux**

Run:

```powershell
git ls-files --stage gradlew
```

Expected: the mode is `100755`. If it is not, run `git update-index --chmod=+x gradlew` and confirm the mode becomes `100755`.

### Task 3: Permanent latest-release README link

**Files:**
- Create: `README.md`

**Interfaces:**
- Consumes: stable workflow asset name `BeerTracker.apk`.
- Produces: a permanent download link that resolves through GitHub's latest-release redirect.

- [ ] **Step 1: Create the project README**

Create `README.md` with:

```markdown
# BeerTracker

Track beers you have tried and keep your personal ratings in one place.

## Download

[Download the latest signed BeerTracker APK](https://github.com/silvijas/BeerTracker/releases/latest/download/BeerTracker.apk)

Android may ask you to allow installs from your browser or file manager. If an older copy was signed with a different key, uninstall it once before installing this release. Future APKs from this link will update normally.
```

- [ ] **Step 2: Verify the repository and asset names match**

Run:

```powershell
Select-String -Path README.md -SimpleMatch 'https://github.com/silvijas/BeerTracker/releases/latest/download/BeerTracker.apk'
Select-String -Path .github/workflows/release.yml -SimpleMatch 'dist/BeerTracker.apk'
```

Expected: each command prints exactly one matching line.

### Task 4: Generate and configure the permanent signing identity

**Files:**
- Create outside repository: `%USERPROFILE%\.android\beertracker-release.p12`
- Create outside repository: `%USERPROFILE%\.android\beertracker-release-credential.xml`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: local Java `keytool`, authenticated `gh` CLI with repository administration access.
- Produces: one RSA 4096-bit PKCS12 signing identity, a Windows DPAPI-protected local credential, and the four GitHub repository secrets consumed by Task 2.

- [ ] **Step 1: Add defensive signing-file ignores**

Append to `.gitignore`:

```gitignore

# Android signing keys
*.jks
*.keystore
*.p12
```

- [ ] **Step 2: Confirm the permanent keystore path is unused**

Run:

```powershell
$keystorePath = Join-Path $HOME '.android\beertracker-release.p12'
$credentialPath = Join-Path $HOME '.android\beertracker-release-credential.xml'
if (Test-Path $keystorePath) { throw "Refusing to overwrite $keystorePath" }
if (Test-Path $credentialPath) { throw "Refusing to overwrite $credentialPath" }
```

Expected: no output and a zero exit status.

- [ ] **Step 3: Generate a cryptographically random password and key**

Run in one PowerShell session:

```powershell
$keystorePath = Join-Path $HOME '.android\beertracker-release.p12'
$credentialPath = Join-Path $HOME '.android\beertracker-release-credential.xml'
$alias = 'beertracker-release'
$randomBytes = [byte[]]::new(32)
[System.Security.Cryptography.RandomNumberGenerator]::Fill($randomBytes)
$password = [Convert]::ToBase64String($randomBytes)
$securePassword = ConvertTo-SecureString $password -AsPlainText -Force
$credential = [PSCredential]::new($alias, $securePassword)
$credential | Export-Clixml -Path $credentialPath

keytool -genkeypair `
  -keystore $keystorePath `
  -storetype PKCS12 `
  -storepass $password `
  -keypass $password `
  -alias $alias `
  -keyalg RSA `
  -keysize 4096 `
  -validity 10000 `
  -dname 'CN=BeerTracker, OU=Mobile, O=BeerTracker'
if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
```

Expected: `keytool` creates one certificate valid for 10,000 days. The credential XML is encrypted by Windows DPAPI for the current Windows user.

- [ ] **Step 4: Upload the signing material to GitHub without printing it**

Continue in the same PowerShell session:

```powershell
$keystoreBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystorePath))
$keystoreBase64 | gh secret set ANDROID_KEYSTORE_BASE64 --repo silvijas/BeerTracker
$password | gh secret set ANDROID_KEYSTORE_PASSWORD --repo silvijas/BeerTracker
$alias | gh secret set ANDROID_KEY_ALIAS --repo silvijas/BeerTracker
$password | gh secret set ANDROID_KEY_PASSWORD --repo silvijas/BeerTracker
Remove-Variable keystoreBase64, password, securePassword, randomBytes
```

Expected: all four `gh secret set` commands succeed and do not echo secret values.

- [ ] **Step 5: Confirm secret names and local key files**

Run:

```powershell
gh secret list --repo silvijas/BeerTracker
Get-Item (Join-Path $HOME '.android\beertracker-release.p12'), (Join-Path $HOME '.android\beertracker-release-credential.xml') |
    Select-Object FullName, Length
git status --short
```

Expected: GitHub lists all four required secret names; both local files exist; neither local file appears in `git status`.

### Task 5: Verify the signed build and repository changes

**Files:**
- Verify: `app/build.gradle.kts`
- Verify: `.github/workflows/release.yml`
- Verify: `README.md`
- Verify: `.gitignore`

**Interfaces:**
- Consumes: the DPAPI-protected local credential and permanent local keystore from Task 4.
- Produces: evidence that tests pass, the release APK is signed by the permanent identity, and no secret material is tracked.

- [ ] **Step 1: Run the full local unit test suite**

Run:

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Build with the permanent signing identity without exposing its password**

Run:

```powershell
$keystorePath = Join-Path $HOME '.android\beertracker-release.p12'
$credential = Import-Clixml (Join-Path $HOME '.android\beertracker-release-credential.xml')
$env:ANDROID_KEYSTORE_PATH = $keystorePath
$env:ANDROID_KEYSTORE_PASSWORD = $credential.GetNetworkCredential().Password
$env:ANDROID_KEY_ALIAS = $credential.UserName
$env:ANDROID_KEY_PASSWORD = $credential.GetNetworkCredential().Password
$env:CI_VERSION_CODE = '101'
$env:CI_VERSION_NAME = '0.1.0-ci.local'

.\gradlew.bat :app:assembleRelease
$buildExitCode = $LASTEXITCODE

Remove-Item Env:ANDROID_KEYSTORE_PATH, Env:ANDROID_KEYSTORE_PASSWORD, Env:ANDROID_KEY_ALIAS, Env:ANDROID_KEY_PASSWORD, Env:CI_VERSION_CODE, Env:CI_VERSION_NAME
Remove-Variable credential
if ($buildExitCode -ne 0) { throw 'Signed release build failed' }
```

Expected: `BUILD SUCCESSFUL` and `app/build/outputs/apk/release/app-release.apk` exists.

- [ ] **Step 3: Verify the local APK signature**

Run:

```powershell
$apksigner = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools\*\apksigner.bat" |
    Sort-Object FullName -Descending |
    Select-Object -First 1
if (-not $apksigner) { throw 'apksigner.bat was not found' }
& $apksigner.FullName verify --verbose --print-certs app\build\outputs\apk\release\app-release.apk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
```

Expected: verification succeeds and prints the BeerTracker certificate subject and digest without private key material.

- [ ] **Step 4: Check diagnostics and ensure secrets are untracked**

Run:

```powershell
git status --short
git diff --check
git ls-files '*.jks' '*.keystore' '*.p12' '*credential.xml'
```

Expected: no signing key or credential file is listed by `git ls-files`; `git diff --check` reports no whitespace errors.

- [ ] **Step 5: Review the final diff without committing or pushing**

Run:

```powershell
git diff -- app/build.gradle.kts .gitignore README.md .github/workflows/release.yml docs/superpowers/specs/2026-08-08-automatic-signed-apk-releases-design.md docs/superpowers/plans/2026-08-08-automatic-signed-apk-releases.md
```

Expected: the diff contains only the approved Gradle, workflow, README, ignore, design, and plan changes. End-to-end GitHub Release creation remains pending until these files are committed and pushed to `main`.
