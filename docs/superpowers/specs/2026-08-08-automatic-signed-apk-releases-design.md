# Automatic Signed APK Releases

## Goal

Publish an installable, signed BeerTracker APK as the latest GitHub Release after every successful push to `main`. All published APKs must use one long-lived signing key so future builds can update earlier release builds on a device.

An app already installed with a different signature, including an Android Studio debug signature, must be uninstalled once before installing the first APK produced by this pipeline. Android does not permit one signing identity to replace another.

## Signing

Generate one release keystore outside the repository and retain a separate backup. Store these values as GitHub Actions repository secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The workflow reconstructs the keystore only in the temporary GitHub-hosted runner. Gradle receives the temporary path and credentials through environment variables. Neither the keystore nor its credentials are committed, uploaded as artifacts, or printed in logs.

The local keystore is the permanent update identity for `com.beertracker`. Losing it prevents compatible updates, so it must be backed up securely.

## Versioning

Local builds retain the version values declared in `app/build.gradle.kts`.

CI supplies a monotonically increasing `versionCode` derived from the GitHub Actions run number and attempt number. The CI `versionName` includes the same build identity. This ensures each newly published APK is accepted as newer than APKs from earlier successful workflow runs, including reruns.

## Workflow

A workflow triggered by pushes to `main` will:

1. Check out the repository.
2. Configure Java 17 and Gradle caching.
3. Run the unit test suite.
4. Decode the signing keystore from GitHub Secrets into the runner's temporary directory.
5. Build the release APK with CI-specific version and signing properties.
6. Verify that the APK is signed and report only non-secret certificate metadata.
7. Rename the asset to `BeerTracker.apk`.
8. Create a uniquely tagged, non-draft, non-prerelease GitHub Release and upload the APK.

The workflow receives only `contents: write` permission. A failed test, build, or signature check stops execution before release creation.

## Latest Download Link

The repository currently has no README. Create `README.md` with a download link to:

`https://github.com/silvijas/BeerTracker/releases/latest/download/BeerTracker.apk`

GitHub resolves this URL through the newest non-draft, non-prerelease release. Keeping the uploaded asset name stable makes the link permanent even though every release has a unique tag.

## Validation

Before handoff:

- Run the existing local unit tests.
- Validate the Gradle release-signing configuration without exposing credentials.
- Validate the workflow syntax by inspection and, where local tooling supports it, a workflow linter.
- Confirm the README URL matches the repository and stable asset name.
- Confirm all signing files and temporary secret material remain outside Git tracking.

The first real end-to-end release is validated by pushing the completed changes to `main`, because GitHub-hosted secrets and release permissions are available only in GitHub Actions.
