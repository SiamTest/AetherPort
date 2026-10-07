# ForgePort Android

Native, local-first Android port of ForgePort.

**Version:** 3.0.0-alpha14
**Package:** `com.forgeport.android`  
**Minimum Android:** Android 8.0 (API 26)  
**Target:** Android 16 (API 36)

## Local-first architecture

ForgePort does not require a hosted ForgePort server. Project staging, ZIP work, variables, Git operations, and Google OAuth token generation run on the Android device. Internet access is needed for GitHub, Hugging Face, Google, app update checks, and website browsing.

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
- **E-Hentai** has a black-and-orange native catalogue with Popular, Latest, search, a two-column cover grid, and a list toggle.
- **Filter** supports categories, language, uploader, minimum rating, and page-count ranges. Latest sends these to the website; Popular filters the site's ranked popular list using the official metadata API.
- Gallery details show a portrait cover, title, author, metadata, grouped tags, library/refresh/download actions, one Chapter row, and an orange Start/Continue button.
- **Browse / Library / Downloads** bottom tabs keep the gallery experience native. Library opens saved titles and reading progress; Downloads has offline counts, live progress, and pause/resume controls.
- The Overview no longer shows the old “Local-first project tools” banner.
- In-app updates check GitHub Releases on launch, support a persistent automatic-pop-up preference, show release notes, download with animated progress, verify SHA-256, and automatically continue into Android’s package installer.

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
2. runs source rules and the local stable-release script check;
3. runs `testDebugUnitTest` and `lintDebug`;
4. builds an APK;
5. creates a SHA-256 checksum;
6. uploads the APK as a workflow artifact; and
7. creates `forgeport-update.json`; and
8. on non-PR runs, automatically creates or updates the GitHub Release tagged from `versionName` (for example `v3.0.0-alpha14`) with the APK, checksum, and update manifest.

Every successful non-PR run publishes a stable GitHub release and marks it **Latest**, including when the application version still contains an alpha/beta/RC suffix. Rerunning a version uploads the new assets and promotes an existing prerelease/draft to a stable published release. The update manifest always reports `prerelease: false`.

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
3. checks automatically on app launch and shows the update prompt only when **Automatic update pop-ups** is enabled;
4. downloads the APK from the release with an animated in-app progress banner;
5. verifies the SHA-256 checksum when the matching release checksum is present;
6. automatically opens Android’s package installer as soon as verification completes; and
7. on Android 8+, opens the per-app install permission screen when needed and resumes installation automatically after the user returns with permission granted.

The **Automatic update pop-ups** preference is enabled by default and is stored locally. Disabling it does not disable update checks; it only suppresses the automatic prompt. Manual checks remain available from **Updates**.

The default update repository can be overridden at build time with:

```bash
./gradlew assembleDebug -PupdateGithubRepository=owner/repository
```

For actual in-place updates, every installed APK and future update APK must use the **same signing key**. Configure the persistent release-signing secrets below before relying on automatic updates across GitHub Actions runs. Debug APKs created by different runners are not a stable update-signing strategy.

## E-Hentai browsing

Open **E-Hentai** from the navigation drawer or Overview. The native catalogue
uses a black background, orange controls, and two portrait covers per row.
**Popular** shows the site's popularity-ranked list; **Latest** shows recent
results. Use the search icon to search titles and tags, the grid icon to toggle
between covers and a list, and the menu to refresh or open **Account & access** for sign-in/access checks.
Use the bottom tabs to move between **Browse**, **Library**, and **Downloads**;
Library can continue directly in the native reader. Your tab scroll position
is restored when returning to a saved tab.

**Filter** lets you choose categories, language, uploader, minimum rating, and
minimum/maximum page counts. Apply reloads the selected mode; Cancel keeps the
current filters; Reset clears the draft and Clear removes all applied filters
and the search. Latest sends filters to the website and offers **Load more**
as an accessible fallback when a next-page cursor exists. Scrolling near the
end loads the next results automatically. Pull down to refresh without clearing
the visible covers; failed refreshes keep the current results. Popular filters the current ranked list using
batched official gallery metadata, retaining that order. Its search supports
words, quoted phrases, and excluded words across titles/tags; use Latest for
the website's full search syntax or results outside the popular list.

Tap a cover to open native gallery details with a portrait cover, title,
author, metadata, grouped tags, category, library/refresh/download actions,
one **Chapter** row, download/pause controls, and an orange **Start/Continue**
button. Download saves all pages in that chapter. The description can collapse.
**Account & access** in the menu opens the original website for sign-in or
access checks. Refresh the native catalogue/gallery after completing an access
check. This secondary view is separate from native browsing and reading. Login cookies
are reused for gallery requests and never forwarded to image-host domains.

The website view retains search, back/forward, home, reload/stop, pinch zoom,
and **Open in browser**. Android Back traverses website history before leaving
that view.

The fullscreen reader supports **Manga (right to left)**, **Left to right**,
and **Vertical scrolling**. Pinch or double-tap to zoom, tap to show/hide
controls, and use the page slider or previous/next buttons to move around.
Reading progress and the selected reading mode are remembered locally.

Open **Library** for saved galleries and **Downloads** for offline progress
from the bottom navigation or drawer. Tapping a download notification opens
Downloads. Gallery downloads use a foreground service and a
notification, continue when the app is backgrounded, and can be paused. Resume
skips completed pages, including after an app restart. Successfully downloaded
pages and metadata live in app-private persistent storage. Browsing/reader
previews use a separate size-limited cache; these are not counted as completed
offline downloads. Fully downloaded galleries can be read without internet.

Interrupted pages remain temporary until their complete response has been
received and verified as an image. Errors and quota responses stop the affected
download; image-limit errors pause the queue. Android service time limits or
process termination leave completed pages available for manual resume. The
gallery screen can remove downloaded pages while preserving the saved gallery
and reading progress.

Loading failures provide retry and website/browser access. External web links
tapped by the user open in the system browser. The Telegram bot, its credentials,
uploads, and watcher are not bundled. Site archive links remain website actions;
the native **Download gallery** action saves pages for offline reading.
Website availability, account restrictions, and image limits remain in effect.

The implementation follows the supplied Aniyomi reference's details / continue
reading / reader / downloads flow using ForgePort's existing Compose and OkHttp
stack. jsoup handles gallery HTML parsing; the Aniyomi app itself is not embedded.

### Gallery verification

`tools/verify_source.py` checks navigation, safe WebView settings, atomic storage,
download interruption handling, service declarations, and reader wiring.
`GalleryParserTest` exercises synthetic HTML, page numbers, cover/metadata
parsing, URL validation, image-host restrictions, and quota/error responses.
`GalleryCatalogTest` checks both feed URLs, encoded filters, pagination cursors,
thumbnail/compact layouts, ranked Popular filtering, empty results, and invalid
filter values, and automatic-paging guards. Existing CI
runs these tests with `testDebugUnitTest` and Android lint before creating an APK.

On a device, check distinct Popular/Latest results, search, each filter and
reset/cancel, grid/list toggling, automatic loading, pull-to-refresh (including
failure recovery), all three bottom tabs, sign-in followed by refresh, and
small-screen/large-font layouts. Also check all three reading modes, zoom plus page
swiping, pause/resume, restart recovery, and reading a completed gallery with
airplane mode enabled. Notification permission is requested when starting a
download on Android 13+; declining it does not prevent the foreground service.

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
