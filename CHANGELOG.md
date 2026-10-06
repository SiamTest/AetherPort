# Changelog

## 3.0.0-alpha08

- Staged projects now delete immediately when the trash button is tapped; the confirmation dialog has been removed.
- Removed success banners after staging a project, deleting a staged project, saving a variable, or deleting a variable.
- Failure messages remain visible so unsuccessful operations still provide feedback.
- Added source-verification checks for the direct-delete and no-success-banner behavior.
- Bumped Android version code to `3000008`.

## 3.0.0-alpha07

- Removed the large **Local-first project tools** banner from Overview.
- Added a Yutaka-style in-app updater backed by GitHub Releases.
- Added automatic update checks when ForgePort opens plus an **Updates** page for manual checks.
- Added semantic version comparison with prerelease-channel handling, release notes, APK download progress, and direct Android package-installer handoff.
- Added SHA-256 verification when a release checksum asset is available.
- Added `FileProvider`-based APK sharing and Android 8+ unknown-app installation permission handling.
- GitHub Actions now publishes `forgeport-update.json` alongside the APK and checksum.
- Default update source is `Chowdhury-Siam/ForgePort`, overrideable with `-PupdateGithubRepository=owner/repository`.
- Bumped Android version code to `3000007`.

## 3.0.0-alpha06

- Fixed Android lint failure on API 26 by removing the API 27-only `android:windowLightNavigationBar` attribute from the base theme. Android 12+ splash/theme resources remain in their API-qualified resource directory.
- Replaced the deprecated `Icons.Filled.ArrowForward` with the auto-mirrored Material icon.
- Added source-verification regression checks for both fixes.
- Bumped Android version code to `3000006`.

## 3.0.0-alpha05

- Reworked the Android interface around Material 3 components, spacing, typography, tonal surfaces, icons, cards, navigation drawer, and centered top app bar.
- Added a new ForgePort launcher identity with adaptive, round, monochrome, legacy, and Android 12+ splash-screen resources.
- Added automatic GitHub Releases from the Android build workflow. Pushes to `main`/`master` and manual runs publish the built APK plus SHA-256 checksum under the app version tag.
- Added optional persistent release signing through GitHub repository secrets. When signing credentials are present the workflow publishes a signed release APK; otherwise it publishes the debug APK so CI remains usable without setup.
- Preserved local-first project processing, automatic GitHub-token matching, Google OAuth/token export, and `Small bug fixes` as the default commit message.

## 3.0.0-alpha04

- Fixed `compileDebugKotlin` failure in `ForgePortApp.kt` caused by importing Compose's internal `androidx.compose.foundation.layout.weight` symbol.
- `Modifier.weight(...)` now resolves from the surrounding `RowScope`/`ColumnScope`, which is the supported Compose API usage.
- Added a source-verification regression check so the invalid import cannot be reintroduced.

## 3.0.0-alpha03

- Fixed GitHub Actions Android SDK setup after Google removed the legacy `tools` SDK package.
- Upgraded `android-actions/setup-android` from v3 to v4.
- Explicitly installs only `platform-tools` during SDK setup, then installs Android 36 platform and build-tools with `sdkmanager`.
- Removed the redundant second license-acceptance command from the workflow.

## 3.0.0-alpha02

- Added GitHub Actions Android CI for pushes, pull requests, and manual runs.
- GitHub Actions installs Java 17 and Android SDK 36, verifies source rules, runs unit tests and lint, and builds the debug APK.
- Build artifacts include the installable APK plus its SHA-256 checksum.

## 3.0.0-alpha01

- Started native Kotlin/Jetpack Compose conversion from ForgePort 2.1.6.
- Removed the requirement for ForgePort hosting and Turso.
- Added local staged-project management.
- Added encrypted local variables with Android Keystore.
- Added local GitHub publish flow with automatic GitHub token matching.
- Added local Hugging Face publish and ZIP download flows.
- Preserved `Small bug fixes` as the commit-message default and fallback.
- Added local Google Desktop OAuth flow and `token.pickle` / `token.json` export.
