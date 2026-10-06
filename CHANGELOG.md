# Changelog

## 3.0.0-alpha13

- Added native Browse / Library / Downloads bottom navigation across the gallery section, with consistent black-and-orange screens and saved tab/scroll state. Library Continue reading opens the native reader directly; download notifications open Downloads.
- Added pull-to-refresh and automatic near-end loading in both catalogue views. Refresh keeps visible results until new data arrives, and refresh failures keep the current results. Failed pagination stops automatic requests until retried; repeated/empty continuation pages cannot loop indefinitely.
- Replaced the gallery WebView action with native Download/Pause/Offline ready controls. Website access remains a secondary Account & access menu action for login and access checks.
- Added Downloads as a separate native page with offline counts, live progress, pause/resume, and completed/failed download entries.
- Added an automatic-paging regression check to the catalogue tests. Version code is `3000013`.

## 3.0.0-alpha12

- Replaced the default E-Hentai WebView landing page with a native black-and-orange catalogue matching the supplied layout: back/search/grid/menu toolbar, Popular/Latest/Filter controls, and two-column portrait covers with titles beneath.
- Popular and Latest load distinct live lists. Search and category/language/uploader/rating/page-count filters work on both modes; Popular uses batched official metadata and retains popularity order. Latest supports the website's next-page cursor through Load more.
- Added filter validation, reset/clear, list view, refresh, loading/empty/error/retry states, and a WebView/sign-in fallback. Cancelled catalogue requests cannot overwrite newer tab/search/filter results.
- Restyled gallery details with cover/title/author, metadata, grouped tags, library/refresh/WebView actions, collapsible description, one Chapter row, download/pause controls, and an orange Start/Continue button.
- Added bounded thumbnail transfers using existing URL/image validation and atomic writes, with sampled bitmap decoding and saved-metadata compatibility. Existing offline downloads and reader modes remain available.
- Added synthetic catalogue parsing, search/filter, paging, host-validation, and metadata regression tests. Version code is `3000012`.

## 3.0.0-alpha11

- Gallery links now open native manga-style details with cover, metadata, tags, page list, Save, Continue reading, and Download gallery actions.
- Added a fullscreen reader with right-to-left and left-to-right paging, vertical scrolling, pinch/double-tap zoom, page slider, and remembered reading progress/direction.
- Added Gallery library to Overview and the drawer for saved galleries, offline page counts, and download actions.
- Gallery downloads run sequentially in an Android data-sync foreground service, with a progress notification, pause, and resume that skips completed pages after interruption/restart.
- Added atomic metadata and page persistence, separate bounded preview cache, image verification, storage-space checks, session-cookie reuse, and main-site/image-host URL restrictions.
- Website HTTP/image-limit errors are surfaced instead of marking incomplete galleries successful; quota errors pause pending downloads.
- Added synthetic gallery-parser and URL validation tests to the existing CI unit-test gate; version code is now `3000011`.

## 3.0.0-alpha10

- Added an **E-Hentai** page to the navigation drawer and Overview for browsing the live website inside ForgePort.
- Added title/artist/tag search, back/forward, home, reload/stop, page loading progress, pinch zoom, and an external-browser shortcut.
- Preserve WebView login cookies and navigation history across screen changes and activity recreation.
- Added loading-error recovery, website/account host restrictions, and safe WebView settings without a native JavaScript bridge.
- File downloads are declined; Telegram bot downloads, uploads, watchers, and credentials are not included.
- Made the navigation drawer scrollable for smaller screens and added URL-validation/search tests to the existing CI unit-test gate.
- Bumped Android version code to `3000010`.

## 3.0.0-alpha09

- Added a persistent **Automatic update pop-ups** toggle, enabled by default, matching Yutaka's update preference behavior.
- Startup update checks continue in the background even when automatic pop-ups are disabled; only the automatic prompt is suppressed.
- **Download update** now downloads, verifies, and immediately opens Android's package installer without requiring a second tap.
- On Android 8+, ForgePort automatically opens the per-app install permission screen when required and resumes installation after the user returns with permission granted.
- Added an animated Material 3 update-progress banner while downloading, waiting for install permission, or opening the installer.
- Kept a manual **Install update** fallback when a verified APK already exists or the Android installer was cancelled.
- Bumped Android version code to `3000009`.

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
