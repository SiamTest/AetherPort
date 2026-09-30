# ForgePort Android

Native, local-first Android port of ForgePort.

**Version:** 3.0.0-alpha05  
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
7. on non-PR runs, automatically creates or updates the GitHub Release tagged from `versionName` (for example `v3.0.0-alpha05`).

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
