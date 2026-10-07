from pathlib import Path

root = Path(__file__).resolve().parents[1]
assert (root / "app/src/main/AndroidManifest.xml").is_file()
assert (root / "app/src/main/java/com/forgeport/android/MainActivity.kt").is_file()
assert not (root / "Dockerfile").exists()
assert not (root / "railway.toml").exists()
assert not (root / "render.yaml").exists()

ui = (root / "app/src/main/java/com/forgeport/android/ui/ForgePortApp.kt").read_text()
assert 'private fun ProjectsScreen' in ui
projects_block = ui.split('private fun ProjectsScreen', 1)[1].split('@Composable\nprivate fun ProjectCard', 1)[0]
assert 'StageZipCard' not in projects_block
assert 'AlertDialog(' not in projects_block
assert 'deleteTarget' not in projects_block
assert 'vm.deleteProject(project.name)' in projects_block

vm_source = (root / "app/src/main/java/com/forgeport/android/ForgePortViewModel.kt").read_text()
for success_banner in [
    'staged locally.',
    'Variable saved.',
    'statusMessage = "$name deleted."',
]:
    assert success_banner not in vm_source, success_banner
for marker in ['private fun GitHubScreen', 'private fun HuggingFaceScreen', 'private fun HfDownloadScreen']:
    block = ui.split(marker, 1)[1]
    assert 'StageZipCard(vm)' in block[:3500]
assert 'Small bug fixes' in ui
assert 'Saved GitHub variable' not in ui
assert 'CenterAlignedTopAppBar' in ui
assert 'ElevatedCard' in ui
assert 'FilledTonalButton' in ui
assert 'ic_forgeport_mark' in ui
assert 'Local-first project tools' not in ui
assert 'private fun UpdatesScreen' in ui
assert 'Check for updates' in ui
assert 'Download update' in ui
assert 'Install update' in ui
assert 'vm.checkForUpdates(silent = true)' in ui
assert 'Icons.Filled.SystemUpdate' in ui
assert 'Automatic update pop-ups' in ui
assert 'AnimatedVisibility(' in ui
assert 'UpdateProgressBanner(vm)' in ui
assert 'vm::installDownloadedUpdate' in ui
assert 'vm.resumePendingUpdateInstall()' in ui
assert 'automatic_update_popups' in vm_source
assert 'fun updateAutomaticUpdatePopups(enabled: Boolean)' in vm_source
assert 'fun setAutomaticUpdatePopups(' not in vm_source, 'This clashes with the delegated Boolean property setter on the JVM.'
assert 'onCheckedChange = vm::updateAutomaticUpdatePopups' in ui
assert 'installDownloadedUpdate()' in vm_source
assert 'resumePendingUpdateInstall' in vm_source
assert 'UpdateInstaller.requestInstallPermission' in vm_source

# Compose 1.8+ exposes weight through RowScope/ColumnScope. Importing the
# internal layout weight symbol causes compileDebugKotlin to fail.
assert 'import androidx.compose.foundation.layout.weight' not in ui
assert 'Modifier.weight(1f)' in ui

repo = (root / "app/src/main/java/com/forgeport/android/repo/RepositoryService.kt").read_text()
assert 'Small bug fixes' in repo
assert 'resolveGitHubToken' in repo

vars_src = (root / "app/src/main/java/com/forgeport/android/data/VariableStore.kt").read_text()
assert 'AndroidKeyStore' in vars_src
assert 'AES/GCM/NoPadding' in vars_src

oauth = (root / "app/src/main/java/com/forgeport/android/oauth/GoogleOAuthService.kt").read_text()
assert '127.0.0.1' in oauth
assert 'access_type' in oauth and 'offline' in oauth
assert 'prompt' in oauth and 'consent' in oauth
assert 'PythonCredentialsPickle.create' in oauth

build = (root / "app/build.gradle.kts").read_text()
assert 'versionName = "3.0.0-alpha14"' in build
assert 'versionCode = 3000014' in build
assert 'UPDATE_GITHUB_REPOSITORY' in build
assert 'Chowdhury-Siam/ForgePort' in build
assert 'androidx.core:core-ktx' in build
assert '<item name="android:windowLightNavigationBar">false</item>' not in (root / 'app/src/main/res/values/themes.xml').read_text(), 'API 27-only navigation-bar appearance attribute must not be in the base values theme.'
assert 'androidx.compose.material.icons.filled.ArrowForward' not in ui, 'Use the AutoMirrored ArrowForward icon.'
assert 'Icons.AutoMirrored.Filled.ArrowForward' in ui
assert 'compileSdk = 36' in build
assert 'androidx.compose.material3:material3' in build
assert 'material-icons-extended' in build
assert 'androidx.compose.animation:animation' in build

manifest = (root / "app/src/main/AndroidManifest.xml").read_text()
assert 'android:icon="@mipmap/ic_launcher"' in manifest
assert 'android:roundIcon="@mipmap/ic_launcher_round"' in manifest
assert 'android.permission.REQUEST_INSTALL_PACKAGES' in manifest
assert 'androidx.core.content.FileProvider' in manifest
assert '@xml/file_paths' in manifest
for resource in [
    'app/src/main/res/drawable/ic_forgeport_mark.xml',
    'app/src/main/res/mipmap-anydpi/ic_launcher.xml',
    'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml',
    'app/src/main/res/xml/file_paths.xml',
]:
    assert (root / resource).is_file(), resource

for update_source in [
    'app/src/main/java/com/forgeport/android/update/AppUpdate.kt',
    'app/src/main/java/com/forgeport/android/update/VersionComparator.kt',
    'app/src/main/java/com/forgeport/android/update/UpdateService.kt',
    'app/src/main/java/com/forgeport/android/update/UpdateInstaller.kt',
    'app/src/test/java/com/forgeport/android/update/VersionComparatorTest.kt',
]:
    assert (root / update_source).is_file(), update_source

update_installer = (root / 'app/src/main/java/com/forgeport/android/update/UpdateInstaller.kt').read_text()
for marker in ['canInstallPackages', 'requestInstallPermission', 'launchInstaller']:
    assert marker in update_installer

update_service = (root / 'app/src/main/java/com/forgeport/android/update/UpdateService.kt').read_text()
assert 'api.github.com/repos/' in update_service
assert 'VersionComparator.isNewer' in update_service
assert 'SHA-256' in update_service
assert 'checksumUrl' in update_service

workflow = (root / ".github/workflows/build-android.yml").read_text()
for marker in [
    "actions/checkout@v4",
    "actions/setup-java@v4",
    "android-actions/setup-android@v4.0.4",
    'packages: "platform-tools"',
    "gradle/actions/setup-gradle@v4",
    "testDebugUnitTest",
    "lintDebug",
    "assembleDebug",
    "assembleRelease",
    "actions/upload-artifact@v4",
    "gh release create",
    "contents: write",
    "forgeport-update.json",
]:
    assert marker in workflow
assert 'sdkmanager tools' not in workflow
assert '"tools"' not in workflow

assert 'Destination("ehentai", "E-Hentai", Icons.Filled.Public)' in ui
assert 'GalleryCatalogScreen(galleryVm' in ui
assert '!readerRoute && !nativeGalleryRoute' in ui
assert 'GalleryColorScheme' in ui
for marker in ['GalleryDetailsScreen(', 'GalleryReaderScreen(', 'GalleryLibraryScreen(', 'Uri.encode(url)', 'gesturesEnabled = !readerRoute']:
    assert marker in ui, marker
browser = (root / 'app/src/main/java/com/forgeport/android/ui/EhentaiScreen.kt').read_text()
for marker in [
    'AndroidView(', 'BackHandler(', 'EhentaiNavigation.searchUrl(query)',
    'allowFileAccess = false', 'allowContentAccess = false',
    'WebSettings.MIXED_CONTENT_NEVER_ALLOW', 'safeBrowsingEnabled = true',
    'setAcceptThirdPartyCookies(this, false)', 'request.isForMainFrame',
    'restoreState(it)', 'saveState(it)', 'view.destroy()', 'onRelease =',
]:
    assert marker in browser, marker
assert 'addJavascriptInterface' not in browser
assert 'handler.proceed' not in browser
assert 'onOpenGallery(gallery)' in browser
assert (root / 'app/src/test/java/com/forgeport/android/ui/EhentaiNavigationTest.kt').is_file()
assert (root / 'app/src/test/java/com/forgeport/android/gallery/GalleryParserTest.kt').is_file()
gallery_root = root / 'app/src/main/java/com/forgeport/android/gallery'
repository = (gallery_root / 'GalleryRepository.kt').read_text()
for marker in ['AtomicFile(', 'invokeOnCancellation', 'followRedirects(false)', 'response.code == 429 || response.code == 509', 'Cookie', 'currentCoroutineContext().ensureActive()', '.part', 'count == expected', 'validImage(temporary)', 'checkSpace', 'trimCache', 'if (validImage(pagePath(root, gallery, index))) continue']:
    assert marker in repository, marker
assert 'readNBytes' not in repository, 'InputStream.readNBytes requires newer Android versions without desugaring.'
reader = (gallery_root / 'GalleryReaderScreen.kt').read_text()
for marker in ['HorizontalPager(', 'LazyColumn(', 'reverseLayout = mode == "RTL"', 'canPan = { scale > 1f }', 'markRead(gallery, current)', 'Slider(', 'WindowInsetsCompat.Type.systemBars()']:
    assert marker in reader, marker
service = (gallery_root / 'GalleryDownloadService.kt').read_text()
for marker in ['startForeground(', 'override fun onTimeout', 'override fun onDestroy', 'START_NOT_STICKY', 'GalleryRateLimitException', 'pending.clear()', 'activeJob?.cancel()']:
    assert marker in service, marker
for marker in ['android.permission.FOREGROUND_SERVICE_DATA_SYNC', 'android.permission.POST_NOTIFICATIONS', 'android:foregroundServiceType="dataSync"', '.gallery.GalleryDownloadService']:
    assert marker in manifest, marker

assert (root / 'app/src/test/java/com/forgeport/android/gallery/GalleryCatalogTest.kt').is_file()
assert 'GalleryCatalogScreen(galleryVm' in ui
assert 'back = { nav.popBackStack() }' in ui
catalog = (gallery_root / 'GalleryCatalog.kt').read_text()
for marker in ['1023 - filters.categories.sum()', 'f_srdd', 'f_spf', 'f_spt', 'isListUrl', 'GalleryParser::isImageUrl']:
    assert marker in catalog, marker
catalog_ui = (gallery_root / 'GalleryCatalogScreen.kt').read_text()
for marker in ['GalleryCatalogMode.POPULAR', 'GalleryCatalogMode.LATEST', 'GalleryFilterDialog(', 'GridCells.Fixed(2)', 'vm.browse(more = true)', 'No galleries found']:
    assert marker in catalog_ui, marker
for marker in ['chunked(25)', 'https://api.e-hentai.org/api.php', 'Semaphore(3)', 'thumbnailFile', 'writeImage(imageUrl, target, page)']:
    assert marker in repository, marker
screens = (gallery_root / 'GalleryScreens.kt').read_text()
for marker in ['GalleryNavigationBar(', 'downloadsOnly', 'read(gallery.url, gallery.lastRead)', 'Text("Account & access")']:
    assert marker in screens, marker
assert 'DetailAction("WebView"' not in screens
assert 'composable("gallery-downloads")' in ui
assert 'read = ::readGallery' in ui
for marker in ['PullToRefreshBox(', 'vm.catalog.canLoadMore(lastVisible)', 'rememberLazyGridState()', 'rememberLazyListState()', 'GalleryNavigationBar("browse"']:
    assert marker in catalog_ui, marker
catalog_vm = (gallery_root / 'GalleryViewModel.kt').read_text()
for marker in ['refresh && sameSelection', 'refreshing = false', 'if (more) previous + page.items else page.items', 'page.next != cursor', 'error == null && next != null']:
    assert marker in catalog_vm, marker
print("source verification: OK")
