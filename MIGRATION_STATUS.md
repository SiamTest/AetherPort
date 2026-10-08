# AetherPort native migration status

## Replaced

| Hosted ForgePort component | Android implementation |
| --- | --- |
| FastAPI HTML UI | Native Kotlin + Jetpack Compose Material 3 |
| Turso variables | Android Keystore encrypted local variables |
| Server temporary workspace | Temporary app-private ZIP extraction |
| Server `git` subprocesses | JGit |
| Browser ZIP upload | Persisted folder access, newest-first ZIP list and temporary extraction |
| Browser ZIP download | Android create-document picker |
| Hosted Google OAuth callback | Local loopback callback on 127.0.0.1 |
| Python token generation | Kotlin OAuth exchange + compatible pickle writer |
| Server authentication/accounts | Removed; local device workspace |
| ForgePort hosting | Removed |

## Preserved product rules

- Projects UI and persistent staged-project selection are removed.
- Choose ZIP/upload controls are replaced by Settings → Project ZIP folder. ZIPs appear newest first in both source sections; source files stay untouched.
- Variables manages saved GitHub repositories/Hugging Face Spaces. Publish and HF Download use repository choosers instead of typed repository fields.
- GitHub token is auto-detected from the repository owner.
- Default/fallback commit message is `Small bug fixes`. New commit identities come from the publishing token’s account, with GitHub no-reply attribution and verified Hugging Face email.
- GitHub and Hugging Face publish configured-folder ZIPs through temporary private extraction.
- Google OAuth can export `token.pickle` and `token.json`.

## Android UI and identity

- Current stable version: `3.0.9` / code `3000025`.

- Rebranded to AetherPort with a mint-and-cyan portal logo, launcher/splash/drawer updates, and stable version `3.0.0` / code `3000016`.
- The drawer contains only GitHub, Hugging Face, Google, E-Hentai, Variables and Updates. GitHub opens Publish directly; Hugging Face uses Publish/Download tabs. These tools share their parent section instead of separate app-level routes, and form state is saved per tab.
- Existing application identity and private storage keys are preserved so this remains an update to existing ForgePort installs.

- Material 3 color system, shapes, centered app bar, navigation drawer, tonal actions, elevated cards, and Material icons are used throughout.
- AetherPort now includes its own adaptive launcher icon, round icon, monochrome icon, legacy icon, and Android 12+ splash treatment.
- Edge-to-edge Android rendering remains enabled.
- E-Hentai has a native black-and-orange catalogue with Popular/Latest, search, working filters, grid/list views, pull-to-refresh, automatic pagination, and Browse/Library/Downloads tabs, plus native manga-style gallery details, fullscreen reader, saved gallery library, and resumable offline page downloads. A WebView remains available for website/account access. No Telegram bot runtime is imported.
- Catalogue loading starts on launch; the first six indexes warm while Browse is visible. Details no longer block on complete thumbnail indexing, and reading caches the current page plus 20 ahead.
- Reader includes adjustable timed auto-scroll with foreground/image-ready/zoom guards. Download/resume controls confirm a persistent percentage selection before downloading its first pages.
- Library includes recent History, per-gallery history deletion, confirmed full history clearing and explicit library removal. Downloads remain independent.
- Native reader preferences and progress survive restarts; offline downloads persist separately from preview cache.
- Gallery downloads use an Android dataSync foreground service with pause/resume and notification progress.
- The old Overview “Local-first project tools” banner has been removed.
- A Material 3 Updates page and startup update prompt now provide direct GitHub Release updates, with a persistent Automatic update pop-ups preference.
- ZIP discovery refreshes every two seconds while a publishing section is visible/resumed. Newest is selected automatically, while manual older selections are respected.
- Gallery download removal clears the status entry and offline files; library/history remain independent. Pausing stays active until cancellation finishes, preventing removal racing a write.
- Cover bitmaps share an 8 MiB memory cache, gallery identity/ZIP mappings and filtered lists avoid repeated work, and frequently updating cards skip resize animations. Other spring interactions remain.

## Build and release automation

- GitHub Actions verifies, tests, lints and builds the app automatically.
- Android SDK setup uses `android-actions/setup-android@v4.0.4` and no legacy `tools` package.
- Every successful non-PR run automatically publishes or updates a GitHub Release tagged from `versionName` and uploads the APK, SHA-256 checksum, and `aetherport-update.json`.
- Successful non-PR builds are published as stable GitHub releases and marked Latest. Existing prereleases/drafts for the same tag are promoted after uploading the new assets.
- Optional release signing uses `ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. Without these secrets the workflow releases the debug APK instead.
- Compose `Modifier.weight(...)` is used only through `RowScope`/`ColumnScope`; the invalid internal import is blocked by source verification.

## Update system

- Default source: the GitHub Actions publishing repository; local builds fall back to `Chowdhury-Siam/ForgePort`. Updates supports a persistent repository override and private release access via the existing encrypted GitHub token variables.
- Startup checks are silent unless a newer release is found.
- Updates can also be checked manually from **Updates**.
- Prerelease builds receive newer prereleases; stable builds ignore prereleases.
- APK downloads expose progress through the Updates page and an animated top progress banner, and are SHA-256 verified when the release provides a checksum asset.
- **Download update** automatically continues into Android’s package installer after verification.
- Android 8+ install-app permission is requested when needed; ForgePort resumes the pending install after returning with permission granted.
- Automatic update pop-ups can be disabled without disabling the background startup check or manual checks.
- Installation uses `FileProvider` and Android’s package installer rather than silent installation.
- Persistent signing is required for Android to accept one release as an update to another.

## Production hardening still recommended

- Configure persistent release signing before treating GitHub APKs as update-compatible production builds.
- Test on API 26, 30, 34, and 36 real/emulated devices.
- Test JGit against large repositories and Git LFS repositories.
- Add operation cancellation and foreground progress for very large clones/uploads.
- Add instrumentation tests for Storage Access Framework flows.
