# ForgePort native migration status

## Replaced

| Hosted ForgePort component | Android implementation |
| --- | --- |
| FastAPI HTML UI | Native Kotlin + Jetpack Compose Material 3 |
| Turso variables | Android Keystore encrypted local variables |
| Server temporary workspace | App-private staged-project storage |
| Server `git` subprocesses | JGit |
| Browser ZIP upload | Android document picker |
| Browser ZIP download | Android create-document picker |
| Hosted Google OAuth callback | Local loopback callback on 127.0.0.1 |
| Python token generation | Kotlin OAuth exchange + compatible pickle writer |
| Server authentication/accounts | Removed; local device workspace |
| ForgePort hosting | Removed |

## Preserved product rules

- Projects has no upload control.
- Upload project ZIP exists in GitHub, Hugging Face, and HF Download.
- GitHub token is auto-detected from the repository owner.
- Default/fallback commit message is `Small bug fixes`.
- GitHub and Hugging Face operations use staged projects.
- Google OAuth can export `token.pickle` and `token.json`.

## Android UI and identity

- Material 3 color system, shapes, centered app bar, navigation drawer, tonal actions, elevated cards, and Material icons are used throughout.
- ForgePort now includes its own adaptive launcher icon, round icon, monochrome icon, legacy icon, and Android 12+ splash treatment.
- Edge-to-edge Android rendering remains enabled.
- The old Overview “Local-first project tools” banner has been removed.
- A Material 3 Updates page and startup update prompt now provide direct GitHub Release updates.

## Build and release automation

- GitHub Actions verifies, tests, lints and builds the app automatically.
- Android SDK setup uses `android-actions/setup-android@v4.0.4` and no legacy `tools` package.
- Every successful non-PR run automatically publishes or updates a GitHub Release tagged from `versionName` and uploads the APK, SHA-256 checksum, and `forgeport-update.json`.
- Alpha/beta/RC versions are marked as GitHub prereleases.
- Optional release signing uses `ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. Without these secrets the workflow releases the debug APK instead.
- Compose `Modifier.weight(...)` is used only through `RowScope`/`ColumnScope`; the invalid internal import is blocked by source verification.

## Update system

- Default source: `Chowdhury-Siam/ForgePort` GitHub Releases.
- Startup checks are silent unless a newer release is found.
- Updates can also be checked manually from **Updates**.
- Prerelease builds receive newer prereleases; stable builds ignore prereleases.
- APK downloads expose progress and are SHA-256 verified when the release provides a checksum asset.
- Installation uses `FileProvider` and Android’s package installer rather than silent installation.
- Persistent signing is required for Android to accept one release as an update to another.

## Production hardening still recommended

- Configure persistent release signing before treating GitHub APKs as update-compatible production builds.
- Test on API 26, 30, 34, and 36 real/emulated devices.
- Test JGit against large repositories and Git LFS repositories.
- Add operation cancellation and foreground progress for very large clones/uploads.
- Add instrumentation tests for Storage Access Framework flows.
