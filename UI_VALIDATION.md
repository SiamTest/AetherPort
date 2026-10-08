# AetherPort 3.0.8 validation

## Passed locally

- 17 actual Kotlin/JUnit tests compiled from production logic: newest-first ZIP ordering and selection, explicit older choices and missing ZIP fallback, repository/account attribution, gallery percentage selection, responsive layout boundaries, direct Download enumeration/boundaries, Downloads visibility, actual offline/partial file deletion, retained metadata and deletion failure handling.
- Source regression checks for existing gallery, publishing, update, storage permission and expressive/responsive bindings, plus Projects removal and lifecycle-bound ZIP refresh.
- Kotlin grammar checks across 50 production/test files, 15 Android XML files and workflow YAML parsing. These are syntax checks, not Android compilation.
- Stable-release scripts with a mock GitHub CLI and APK fixture, covering new and existing releases. Nothing was published.

## Blocked locally

`./gradlew --no-daemon testDebugUnitTest lintDebug` stopped before Android compilation because Gradle could not resolve the Kotlin Android plugin `2.1.20` from its configured repositories. Android compilation, the complete Android unit suite and lint have not completed. No APK was generated.

- Static regression checks cover text-only Home and hidden repository variable keys while preserving selection/editing identities.

## Device verification before distribution

- Confirm the Home screen contains only centered welcome text and the GitHub/Hugging Face picker and saved-repositories editor display only repository paths, not variable names.
- Compare scrolling/navigation on the same device/build type before and after this version; no frame-time benchmark or measured FPS claim has been made locally. Use a signed release APK to assess production performance, retaining the same signing key for upgrades.
- Verify ZIP discovery on both providers after entering/resuming, adding/modifying/deleting ZIPs while the screen stays open, manually pinning an older ZIP, selecting automatic newest again, and changing/revoking the configured folder. Verify the chooser publishes the captured archive when a newer ZIP arrives while it is open.
- Test Downloads removal after successful, partial, failed and paused transfers, with a restart; check that library/history remain and no active write can recreate removed offline pages.
- Retain phone/tablet, landscape, split-screen, 200% font scale, keyboard-visible forms and disabled-animation checks. Verify preview caches and 20-page reader preload remain independent of permanent Downloads.
- Retain direct Download permission tests on API 26–29 and 30+, including grant/deny/revoke, app restart and switching back to a picker-selected folder.

## 3.0.8 ZIP publishing

- GitHub and Hugging Face publish forms show ZIP selection, branch, commit message, token selection where applicable, and publish action; no wrapper checkbox or target-path text field.
- ZIP with a single `project-main/` root publishes the contents of that folder to the repository root. ZIP with direct root files publishes the existing root unchanged.
- ZIP with multiple top-level directories retains all directories. ZIP with only `src/` or `.github/` retains the directory name and contents; macOS `__MACOSX` artifacts do not prevent wrapper detection.
- Verify that projects containing nothing except empty directories or ignored archive artifacts cannot be pushed. The original ZIP must never be modified.
- Confirm account identity, GitHub/Hugging Face repository selection, branch handling, default commit text and successful push behavior on a real Android device. Local Kotlin tests do not test Android runtime or remote publishing.
