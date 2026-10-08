# Changelog

## 3.0.9 — Failure-only publish notifications

- Publishing to GitHub or Hugging Face no longer shows a success notification or commit-status card after a successful push or a no-changes result.
- Failed publishes still display their error details and diagnostic log in an error-styled status card; unexpected exceptions remain visible.
- Successful Hugging Face ZIP preparation and Google credential generation now surface their follow-up save actions without an extra success message.
- Added Kotlin regression coverage for successful, unchanged and failed operation feedback. Android app version `3.0.9` (code `3000025`).

## 3.0.8 — Automatic ZIP wrapper detection and simpler publishing

- Automatically detects and removes one outer ZIP wrapper directory during GitHub and Hugging Face publishing, ignoring common archive metadata such as `__MACOSX` and `.DS_Store`. ZIPs with files directly at the root keep their original layout.
- Preserves conventional project directories such as `src/`, `app/` and `.github/` if they are the sole top-level entry, avoiding accidental flattening of meaningful paths. Never unwraps more than one level. Invalid or content-empty ZIPs fail before pushing.
- Removed the **Remove one outer wrapper folder** toggle and **Target path (optional)** field from both publish screens. Publishing always targets the repository root; existing branch, message, credentials and ZIP selection remain unchanged.
- Added JVM regression tests for wrapped/flat/mixed ZIPs, ignored artifacts, legitimate single-directory projects and empty sources. Android app version `3.0.8` (code `3000024`).

## 3.0.7 — Simplified Home and repository names

- Replaced the Home dashboard, statistics and feature cards with a centered welcome message. App sections remain accessible through the navigation drawer; no functionality or saved data is removed.
- Hidden `GITHUB_REPOSITORY_*` and Hugging Face variable keys in the repository chooser and saved-repositories editor. Repository paths are still shown, and internal keys are retained for correct selection, editing and deletion.
- Advanced Android version to `3.0.7` (code `3000023`) and updated source/release verification rules.

## 3.0.6 — Smoother lists and automatic ZIP selection

- Removed Projects from GitHub/Hugging Face and removed persistent staged-project selection. Publishing still uses safe temporary extraction; all other features and existing account/storage identity are retained.
- ZIP discovery refreshes in the background every two seconds while a publishing section is visible/resumed. Default selection follows the newest ZIP; manually choosing an older file is respected, with a return-to-newest action. Publishing captures the source before opening the repository chooser.
- Removing gallery downloads deletes offline/partial files and removes their status entry so the Downloads card disappears. Empty paused/error entries can also be removed; metadata, library and history remain. Counts recover after partial deletion failures, and pausing must complete before removal.
- Added an 8 MiB decoded-cover cache and cached gallery identity/ZIP mappings and filtered lists. Avoided repeated user-agent initialization and layout animations on frequently updating cards; existing spring navigation, presses, dialogs and expanding controls remain.
- Added executable tests for automatic/manual ZIP selection, Downloads visibility and actual file removal/failure handling. Stable version code is `3000022`. Device performance verification remains outstanding.

## 3.0.5 — Direct Download folder access

- Added **Settings → Use Download** to read ZIPs directly in the primary Download folder for GitHub and Hugging Face. Android 11+ opens the native All files access grant; Android 8–10 uses runtime storage permission. No root or subfolder is required.
- Persisted the source choice, retained picker-selected folders, and refreshed lists on return/open/manual refresh. Denied permissions preserve the prior selection; revoked access has a recovery message. Settings explains the broader Android permission before requesting it.
- Only readable, top-level ZIPs are included and sorted newest first. Files outside Download, symlink escapes, nested ZIPs and other file types are excluded; source archives stay untouched and the existing private extraction/size/path checks are reused.
- Added a filesystem regression covering enumeration, ordering, unchanged originals, symlink boundaries, vanished files and missing storage. Stable version code is `3000021`.

## 3.0.4

- Applied Material 3 Expressive principles using stable Compose components: mint/cyan and gallery-orange color roles, stronger typography, varied rounded/asymmetric shapes, and consistent spacing.
- Added spring-based navigation and tab transitions, interruptible press/selection feedback, button shape changes, dialog entry, and expanding gallery/search/reader controls. Compose animations honor Android's animator duration scale, including disabled animations.
- Bounded forms/details/library on wide windows; overview cards gain columns when space allows. Gallery columns follow available width and font scale. Header content stacks and action/filter chips wrap on narrow windows.
- Long dialog content scrolls, forms accommodate the keyboard, fixed-width gallery actions are removed, and reader status stays compact. Existing publishing, account, gallery preload/download, and update behavior is retained.
- Added phone/tablet/landscape/large-font component previews and responsive-layout boundary/sweep tests. Version code is `3000020`.

## 3.0.3 — Saved repositories and ZIP folders

- Variables now has Credentials/Repositories tabs. Save, edit or remove GitHub repositories and Hugging Face Spaces once; repository values reuse encrypted variable storage and are validated/normalized. Existing generic repository variables are supported.
- Removed repository text fields from GitHub Publish, Hugging Face Publish and Hugging Face Download. The action button opens a centered saved-repository chooser; selecting a target performs the requested action, with an empty-state link directly to repository management.
- Added Settings via the app-bar gear for a persisted, read-only Project ZIP folder. Direct-child ZIPs appear newest first in both publishers, with deterministic date ties and automatic/manual refresh. Old staged projects remain available.
- Removed Choose ZIP/upload cards. Publishing validates the source is still in the selected tree, safely extracts it to temporary private staging and cleans that copy after the operation. Original ZIPs are never deleted. Lost folder access has a recovery message.
- Stable version `3.0.3`, code `3000019`; account commit attribution, gallery features, existing storage and the stable/Latest release workflow are retained.

## 3.0.2 — Account commits and gallery controls

- New GitHub/Hugging Face commits use the account behind the publishing token as author and committer, including when publishing to someone else’s or an organization’s repository. GitHub uses an account-linked no-reply email; Hugging Face uses the verified account email. Failed identity lookup stops publishing instead of falling back to the app name. Existing commits are unchanged.
- Added reader auto-scroll with a saved 1–60 second interval and immediate pause. Paged modes advance one image; vertical mode scrolls most of a screen to preserve tall-image reading. It waits for image loading, pauses during zoom/settings/background use, and stops at the end.
- All gallery download/resume entry points open a centered 1–100% slider with the exact page count before starting. The selection downloads the first pages in reading order, rounds up, persists for resume, skips existing pages and never removes previously downloaded pages. Progress and completion messages reflect the selected count. Cancelling starts no download.
- Stable version `3.0.2`, code `3000018`; existing app data, six sections and stable/Latest release automation are retained.

## 3.0.1 — Complete service sections

- Grouped GitHub publishing, ZIP importing and staged-project management within GitHub's Publish/Projects tabs.
- Grouped Hugging Face publishing, ZIP importing, repository downloads and staged-project management within Hugging Face's Publish/Download/Projects tabs. Removed separate app-level Projects and Download routes.
- Saved each tab's form fields and scroll state when switching tabs or sections. Removed projects/tokens are replaced with an available selection when returning to a form.
- Kept Google authorization/token exports and E-Hentai browse/reader/library/history/downloads in their existing sections. The drawer still has exactly six entries and the AetherPort logo/theme remains unchanged.
- Bumped stable version to `3.0.1`, code `3000017`; stable/Latest publishing is retained.

## 3.0.0 — AetherPort stable

- Rebranded ForgePort to AetherPort across the launcher, app screens, OAuth completion page, update/download messaging, commit identity and release assets.
- Introduced a mint-and-cyan portal logo with adaptive, round, monochrome and legacy launcher variants; refreshed splash and drawer branding and the Material 3 palette.
- Reduced the drawer to GitHub, Hugging Face, Google, E-Hentai, Variables and Updates in that order. The workspace uses the same six sections. Staged projects remain accessible from GitHub/Hugging Face; repository ZIP downloads are available within Hugging Face.
- Changed the version from `3.0.0-alpha15` to stable `3.0.0` with monotonically increased code `3000016`. Stable/Latest release automation now emits AetherPort APKs and `aetherport-update.json`.
- Preserved applicationId, encrypted storage keys, gallery data and updater repository behavior for existing installations. Added menu/rebrand checks and a stable-after-alpha version regression.

## 3.0.0-alpha15

- Fixed update-source selection: Actions builds use their publishing repository, with a persistent repository override in Updates. Private releases use encrypted saved GitHub variables and authenticated API asset downloads; missing source, token and publication errors explain the next step. Only the selected APK’s matching checksum is used.
- Start catalogue loading on launch and warm the first six gallery indexes while Browse is visible. Gallery details appear after one response; later thumbnail index pages load on demand and remain cached.
- Automatically cache the resume/current page plus the next 20 on gallery entry and as reading advances. Leaving cancels preloading; page locks permit foreground reads without waiting behind another background image.
- Added recent History, per-gallery Delete history, confirmed Delete all history, and explicit Remove from library controls. Reading/visit state, saved membership and permanent downloads are independent.
- Added targeted Kotlin tests and source checks. Version code is `3000015`; the existing stable/Latest GitHub release workflow remains enabled.

## Stable GitHub release workflow

- Successful non-PR builds now create stable GitHub releases and mark them Latest, independent of the application version suffix.
- Rebuilding an existing tag uploads the assets and clears prerelease/draft flags before marking it Latest.
- The update manifest now declares `prerelease: false`. Added a local mock check of new/existing release branches and manifest generation, also run by CI.
- Application version remains `3.0.0-alpha14` / `3000014` because this update changes release automation only.

## 3.0.0-alpha14

- Fixed the Kotlin/JVM compilation failure caused by `setAutomaticUpdatePopups(Boolean)` colliding with the generated setter for the delegated `automaticUpdatePopups` property. Renamed the action to `updateAutomaticUpdatePopups` and updated the Switch callback.
- Preserved preference persistence and the behavior that disabling automatic pop-ups dismisses the current update prompt.
- Added source regression checks for the non-conflicting action name and UI callback; version code is `3000014`.

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
