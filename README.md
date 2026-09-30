# ForgePort Android

Native, local-first Android port of ForgePort.

**Version:** 3.0.0-alpha03  
**Package:** `com.forgeport.android`  
**Minimum Android:** Android 8.0 (API 26)  
**Target:** Android 16 (API 36)

## What changed

ForgePort no longer needs a hosted FastAPI service, Turso, Railway, Render, Koyeb, Hugging Face Spaces, or any other ForgePort server. The Android app uses the phone's private storage, CPU, RAM, Android Keystore, system document picker, browser, and network connection.

The old server login/admin model is intentionally not part of the native app. The device itself is the local workspace boundary.

## Implemented in this first native milestone

- Jetpack Compose Android UI with Overview, Projects, GitHub, Hugging Face, HF Download, Google OAuth, and Variables sections.
- `Projects` contains only **Your staged projects**; it does not contain a ZIP uploader.
- **Upload project ZIP** is available in GitHub, Hugging Face, and HF Download.
- ZIP staging uses the Android Storage Access Framework and extracts into app-private storage.
- ZIP safety limits: 500 MB compressed, 1.2 GB extracted, 25,000 entries, and canonical path traversal blocking.
- Staged projects persist locally until the user deletes them.
- Variables are encrypted with AES-GCM using a key generated and held by Android Keystore.
- GitHub token detection follows `GITHUB_TOKEN_<OWNER>` and falls back only when there is exactly one saved `GITHUB_TOKEN...` variable.
- GitHub publishing uses pure-Java JGit locally; no shell `git` binary or ForgePort backend is required.
- Hugging Face Space publishing uses JGit locally.
- Hugging Face Space download clones locally, removes `.git`, creates a clean ZIP, and saves through Android's document picker.
- GitHub and Hugging Face commit message defaults to **Small bug fixes** and also falls back to that text if the field is cleared.
- Google OAuth uses a Desktop OAuth `credentials.json`, opens the system browser, receives the callback on a local `127.0.0.1` port, exchanges the code locally, and offers both `token.pickle` and `token.json`.
- `token.pickle` reconstructs `google.oauth2.credentials.Credentials` and deliberately stores no access token so google-auth refreshes immediately from the refresh token on first use.
- No credentials are sent to a ForgePort server.

## Build

Open the project in Android Studio and let Gradle sync, or run:

```bash
./gradlew assembleDebug
```

The included lightweight `gradlew` bootstrap downloads Gradle 8.11.1 on first use. Android SDK 36 and JDK 17 are required.

### GitHub Actions

`.github/workflows/build-android.yml` builds the Android app automatically on pushes to `main`/`master`, pull requests, and manual workflow runs. The workflow installs Java 17 and Android SDK 36, verifies the source rules, runs unit tests and lint, builds a debug APK, calculates its SHA-256 hash, and uploads both files as a GitHub Actions artifact for 30 days. It uses `android-actions/setup-android@v4` with only `platform-tools`, avoiding the removed legacy Android SDK `tools` package.

To download a build, open **GitHub → Actions → Build Android App → the completed run → Artifacts**. No repository secrets are required for the debug build.

Release APK/AAB signing is intentionally not preconfigured. Add your own signing configuration before publishing to Google Play.

## Google OAuth / token.pickle

1. In Google Cloud, create an OAuth client of type **Desktop app**.
2. Download its `credentials.json`.
3. In ForgePort Android, open **Google OAuth**.
4. Choose `credentials.json` and authorize in the system browser.
5. Return to ForgePort and save `token.pickle` and/or `token.json`.

The flow requests offline access and explicit consent. For long-lived refresh access, configure the OAuth consent screen appropriately (for example, move an external app out of Testing when ready). Google still controls refresh-token validity and can revoke a token.

The exported pickle is designed for Python projects which load `google.oauth2.credentials.Credentials` with `pickle.load()`. Its access token and expiry are intentionally `None`; this causes google-auth to refresh immediately from the exported refresh token rather than embedding a one-hour access token into the file.

## GitHub variables

Example:

```text
Repository: https://github.com/chowdhury-siam/example
Variable:   GITHUB_TOKEN_CHOWDHURY_SIAM
```

There is no GitHub token selector on the GitHub publishing page. ForgePort detects the token from the repository owner.

## Local-data security

- Project ZIPs and extracted projects live in app-private storage.
- Saved variable values are encrypted using Android Keystore-backed AES-GCM.
- Google client credentials and generated Google tokens are kept in memory for the active generation flow and are exported only to the user-selected destination.
- No broad storage permission is requested; file access goes through the system picker.

## Current migration boundary

This is the first native conversion milestone. The core repository-transfer workflow is implemented locally. Before a production release, the Android build should be exercised on real devices against test GitHub/Hugging Face repositories and a real Google Desktop OAuth client, then release signing, UI polish, retry/cancellation behavior, and instrumentation tests should be added.
