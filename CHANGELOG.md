# Changelog

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
- Build artifacts now include the installable debug APK plus its SHA-256 checksum.

## 3.0.0-alpha01

- Started native Kotlin/Jetpack Compose conversion from ForgePort 2.1.6.
- Removed the requirement for ForgePort hosting and Turso.
- Added local staged-project management.
- Added encrypted local variables with Android Keystore.
- Added local GitHub publish flow with automatic GitHub token matching.
- Added local Hugging Face publish and ZIP download flows.
- Preserved `Small bug fixes` as the commit-message default and fallback.
- Added local Google Desktop OAuth flow and `token.pickle` / `token.json` export.
