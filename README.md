# ForgePort Android

Native, local-first Android port of ForgePort.

**Version:** 3.0.0-alpha07  
**Package:** `com.forgeport.android`  
**Minimum Android:** Android 8.0 (API 26)  
**Target:** Android 16 (API 36)

## Local-first architecture

ForgePort does not require a hosted ForgePort server. Project staging, ZIP work, variables, Git operations, and Google OAuth token generation run on the Android device. Internet access is only needed when a selected feature talks to GitHub, Hugging Face, or Google.

- Jetpack Compose **Material 3** interface.
- `Projects` contains only **Your staged projects**.
- **Upload project ZIP** is available in GitHub, Hugging Face, and HF Download.
- ZIPs are extracted into app-private storage with traversal and size checks.
- Variables are encrypted with Android Keystore-backed AES-GCM.
- GitHub publishing automatically resolves `GITHUB_TOKEN_<OWNER>` from the repository owner.
- GitHub and Hugging Face repository operations use JGit locally.
- Commit message defaults/falls back to **Small bug fixes**.
- Google OAuth uses a Desktop OAuth `credentials.json`, local `127.0.0.1` callback, offline access, and exports `token.pickle` / `token.json`.
- ForgePort has its own adaptive, round, monochrome, legacy, and Android 12+ splash logo resources.
- The Overview no longer shows the old “Local-first project tools” banner.
- In-app updates check GitHub Releases on launch, support manual checks, show release notes, download the APK with progress, verify the release SHA-256 asset, and launch Android’s package installer.

## Build locally

Open the project in Android Studio or run:

```bash
./gradlew assembleDebug
```

The lightweight `gradlew` bootstrap downloads Gradle 8.11.1 on first use. Android SDK 36 and JDK 17 are required.

## GitHub Actions: build + automatic release

`.github/workflows/build-android.yml` runs on pushes to `main` / `master`, pull requests, and manual workflow runs.

It:

1. sets up Java 17 and Android SDK 36;
2. runs `tools/verify_source.py`;
3. runs `testDebugUnitTest` and `lintDebug`;
4. builds an APK;
5. creates a SHA-256 checksum;
6. uploads the APK as a workflow artifact; and
7. creates `forgeport-update.json`; and
8. on non-PR runs, automatically creates or updates the GitHub Release tagged from `versionName` (for example `v3.0.0-alpha07`) with the APK, checksum, and update manifest.

Alpha, beta and RC versions are marked as prereleases automatically.

### Optional persistent release signing

For an update-compatible signed release APK, add these GitHub repository secrets:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_STORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

`ANDROID_KEYSTORE_BASE64` is the Base64 form of your `.jks` / keystore file. When all four values are present the workflow runs `assembleRelease` and publishes the signed release APK. When they are absent, CI still succeeds and publishes the debug APK instead.

Do not use a disposable signing key for production installs. Android requires future updates to be signed with the same key.

## In-app updates

ForgePort checks `Chowdhury-Siam/ForgePort` GitHub Releases when the app opens. The navigation drawer and Overview also expose an **Updates** page for manual checks.

The update flow:

1. compares semantic versions against the installed `BuildConfig.VERSION_NAME`;
2. allows prerelease updates when the installed build is alpha/beta/RC, while stable builds ignore prereleases;
3. shows a Material 3 update prompt when a newer release exists;
4. downloads the APK from the release with progress;
5. verifies the SHA-256 checksum when the matching release checksum is present; and
6. opens Android’s package installer. Android 8+ may first ask the user to allow ForgePort to install unknown apps.

The default update repository can be overridden at build time with:

```bash
./gradlew assembleDebug -PupdateGithubRepository=owner/repository
```

For actual in-place updates, every installed APK and future update APK must use the **same signing key**. Configure the persistent release-signing secrets below before relying on automatic updates across GitHub Actions runs. Debug APKs created by different runners are not a stable update-signing strategy.

## Google OAuth / token.pickle

1. In Google Cloud, create an OAuth client of type **Desktop app**.
2. Download its `credentials.json`.
3. Open **Google OAuth** in ForgePort Android.
4. Choose `credentials.json` and authorize in the system browser.
5. Save `token.pickle` and/or `token.json`.

The flow requests offline access and explicit consent. `token.pickle` is generated for Python projects that load `google.oauth2.credentials.Credentials` using `pickle.load()` and is designed to refresh through the saved refresh token on first use.

Google controls refresh-token validity and can revoke tokens. Configure the OAuth consent screen appropriately for long-lived use.

## GitHub variables

Example:

```text
Repository: https://github.com/chowdhury-siam/example
Variable:   GITHUB_TOKEN_CHOWDHURY_SIAM
```

There is no GitHub token selector on the GitHub publishing page. ForgePort matches the token automatically from the repository owner.

## Local-data security

- Staged projects live in app-private storage.
- Saved variable values are encrypted using Android Keystore-backed AES-GCM.
- Google client credentials and generated tokens are not sent to a ForgePort server.
- File access uses Android's document picker rather than broad storage permissions.
