# AetherPort 3.0.5 UI validation

Implemented Material 3 Expressive principles with stable Compose APIs. The source contains custom spring feedback and expressive shapes rather than prerelease MaterialExpressiveTheme/MaterialShapes dependencies.

## Passed locally

- 12 actual Kotlin/JUnit tests: direct Download ZIP enumeration, canonical path boundaries, original-file preservation, responsive phone/tablet/font-scale boundaries and width sweeps, repository normalization, archive order, account attribution and gallery download selection.
- Source regression checks for existing behavior and new shared component/layout bindings.
- Kotlin grammar checks across 46 source/test files and workflow YAML parsing. These are syntax checks, not Android compilation.
- Stable-release scripts with a mock GitHub CLI and APK fixture for both new and existing releases. Nothing was published.

## Blocked locally

`./gradlew --no-daemon testDebugUnitTest lintDebug` stopped before Android compilation: Gradle could not resolve the Kotlin Android plugin `2.1.20` from its configured repositories. Android unit tests, lint and device/emulator UI verification have not completed. No APK was generated.

## Device verification before distribution

Use the included Android Studio component previews for 320dp phone width, 840dp tablet width, landscape and 200% font size. Run the full Gradle checks and verify the six sections on a phone/tablet or emulator, including keyboard-visible forms, long dialog text, quick tab changes, disabled system animations, gallery filters/details and reader controls. Keep the same signing key for upgrades.

Direct Download device checks: grant/deny All files access, return from Android Settings, restart the app, revoke permission, switch to a picker-selected folder, and publish a ZIP directly from Download through both providers. Check API 26–29 runtime permission and legacy storage on older devices. These Android permission flows have not been verified on a device locally.
