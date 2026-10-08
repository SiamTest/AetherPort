from pathlib import Path

root = Path(__file__).resolve().parents[1]
assert (root / "app/src/main/AndroidManifest.xml").is_file()
assert (root / "app/src/main/java/com/forgeport/android/MainActivity.kt").is_file()
assert not (root / "Dockerfile").exists()
assert not (root / "railway.toml").exists()
assert not (root / "render.yaml").exists()

ui = (root / "app/src/main/java/com/forgeport/android/ui/AetherPortApp.kt").read_text()
assert 'private fun ProjectsScreen' in ui
projects_block = ui.split('private fun ProjectsScreen', 1)[1].split('@Composable\nprivate fun ProjectCard', 1)[0]
assert 'StageZipCard' not in projects_block
assert 'AlertDialog(' not in projects_block
assert 'deleteTarget' not in projects_block
assert 'vm.deleteProject(project.name)' in projects_block

vm_source = (root / "app/src/main/java/com/forgeport/android/AetherPortViewModel.kt").read_text()
for success_banner in [
    'staged locally.',
    'Variable saved.',
    'statusMessage = "$name deleted."',
]:
    assert success_banner not in vm_source, success_banner
assert 'StageZipCard' not in ui and 'Choose ZIP' not in ui
assert 'Small bug fixes' in ui
assert 'Saved GitHub variable' not in ui
assert 'CenterAlignedTopAppBar' in ui
assert 'ElevatedCard' in ui
assert 'FilledTonalButton' in ui
assert 'ic_aetherport_mark' in ui
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
assert 'versionName = "3.0.4"' in build
assert 'versionCode = 3000020' in build
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
    'app/src/main/res/drawable/ic_aetherport_mark.xml',
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
    "aetherport-update.json",
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
for marker in ['GalleryCatalogMode.POPULAR', 'GalleryCatalogMode.LATEST', 'GalleryFilterDialog(', 'GridCells.Fixed(columns)', 'vm.browse(more = true)', 'No galleries found']:
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

# Preload stays bounded and uses the same validated image/cache pipeline as reading.
assert 'browse()' in catalog_vm.split('init {', 1)[1]
assert 'state.items.take(6)' in catalog_ui
assert 'repository.loadGallery(item.url)' in catalog_ui
assert 'while (links.size < parsed.total)' not in repository
assert 'parsed.links[it].orEmpty()' in repository
assert 'pageLink(gallery, index)' in repository
assert 'preloadPages(current)' in repository
assert 'vm.repository.prefetch(gallery, current)' in reader
assert 'Delete all history' in screens and 'Remove from library' in screens
assert 'else if (history) it.visitedAt > 0 else it.saved' in screens
assert 'providers.environmentVariable("GITHUB_REPOSITORY")' in build
assert 'UpdateSource.canAuthenticate(url)' in update_service
assert 'variables.resolveGitHubToken' in update_service
assert 'assetUrl(apkAsset)' in update_service

# The drawer exposes only the six product sections; secondary tools remain reachable inside them.
import re
menu = ui.split('private val destinations = listOf(', 1)[1].split('\n)', 1)[0]
assert re.findall(r'Destination\("([^"]+)", "([^"]+)"', menu) == [
    ('github', 'GitHub'), ('huggingface', 'Hugging Face'), ('google', 'Google'),
    ('ehentai', 'E-Hentai'), ('variables', 'Variables'), ('updates', 'Updates'),
]
assert 'composable("github") { RepositorySection(vm, huggingFace = false)' in ui
assert 'composable("huggingface") { RepositorySection(vm, huggingFace = true)' in ui
assert 'composable("projects")' not in ui and 'composable("download")' not in ui
section = ui.split('private fun RepositorySection', 1)[1].split('@Composable', 1)[0]
assert 'listOf("Publish", "Download", "Projects") else listOf("Publish", "Projects")' in section
assert 'rememberSaveable(huggingFace)' in section
assert 'tabState.SaveableStateProvider(tab)' in section
for target in ['ProjectsScreen(vm)', 'HfDownloadScreen(vm, manageRepositories)', 'HuggingFaceScreen(vm, manageRepositories)', 'GitHubScreen(vm, manageRepositories)']:
    assert target in section, target
forms = ui.split('private fun GitHubScreen', 1)[1].split('private fun GoogleOAuthScreen', 1)[0]
assert 'by remember { mutableStateOf(' not in forms
assert 'vm.publishProjects.none { it.id == project }' in forms
assert 'token !in vm.hfTokenNames' in forms
assert 'Version ${BuildConfig.VERSION_NAME} • Stable' in ui
assert 'android:label="@string/app_name"' in manifest
assert 'applicationId = "com.forgeport.android"' in build, 'Preserve existing installs and their private storage.'
assert '>AetherPort<' in (root / 'app/src/main/res/values/strings.xml').read_text()
assert 'AetherPort-Android-${VERSION_NAME}' in workflow
assert '--title "AetherPort Android $VERSION_NAME"' in workflow
assert (root / 'app/src/main/res/drawable/ic_aetherport_monochrome.xml').is_file()
assert 'ic_aetherport_monochrome' in (root / 'app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml').read_text()
assert 'Theme.AetherPort' in manifest
print("source verification: OK")

# Account identity is shared by both publishers; never silently fall back to the app.
assert '.setAuthor(identity.name, identity.email)' in repo
assert '.setCommitter(identity.name, identity.email)' in repo
assert '.setAuthor("AetherPort"' not in repo and '.setAuthor("ForgePort"' not in repo
for marker in ['identity = githubIdentity(tokenPair.second)', 'identity = huggingFaceIdentity(token)', 'https://api.github.com/user', 'https://huggingface.co/api/whoami-v2', 'followRedirects(false)', 'followSslRedirects(false)']:
    assert marker in repo, marker
for marker in ['galleryDownloadPageCount(gallery.pages.size, percent)', 'for (index in 0 until target)', 'download_percent_', 'setDownloadPercent']:
    assert marker in repository, marker
for marker in ['Text("Download gallery")', 'valueRange = 1f..100f', 'confirmedPercent', 'showSelection = true']:
    assert marker in screens, marker
assert 'vm.download(gallery)' not in screens
assert 'repository.downloadPercent(gallery)' in service
assert 'repository.download(gallery, percent)' in service
for marker in ['Text("Auto-scroll")', 'valueRange = 1f..60f', 'Lifecycle.State.RESUMED', 'readyPages[current] != true', 'animateScrollBy(viewport * 0.85f, animationSpec = ExpressiveMotion.spatial())', 'pager.animateScrollToPage(current + 1, animationSpec = ExpressiveMotion.spatial())', 'Pause auto-scroll', 'zoomed || settings || autoScrollDialog']:
    assert marker in reader, marker
print("account attribution and gallery controls: OK")

# A single persisted folder powers both publishers; original archives are never deleted.
pickers = (root / 'app/src/main/java/com/forgeport/android/ui/RepositoryPickers.kt').read_text()
project_store = (root / 'app/src/main/java/com/forgeport/android/data/ProjectStore.kt').read_text()
assert 'OutlinedTextField(repo,' not in forms and 'var repo by' not in forms
assert forms.count('if (chooseRepository) RepositoryChooser(') == 3
assert forms.count('ProjectSourcePicker(vm, project)') == 2
assert 'SavedRepositoriesEditor(vm)' in ui and 'Icons.Filled.Settings' in ui
for marker in ['ActivityResultContracts.OpenDocumentTree()', 'Choose folder', 'Manage in Variables', 'vm.refreshArchives()', 'vm.saveRepository(label, repository, huggingFace, editingName)']:
    assert marker in pickers, marker
for marker in ['takePersistableUriPermission', 'Intent.FLAG_GRANT_READ_URI_PERMISSION', 'buildChildDocumentsUriUsingTree', 'COLUMN_LAST_MODIFIED', 'newestArchives(archives)', 'listArchives().any { it.uri == uri }', 'MAX_UPLOAD_BYTES', 'MAX_EXTRACTED_BYTES', 'safeTarget(dir, entry.name)']:
    assert marker in project_store, marker
assert 'DocumentsContract.deleteDocument' not in project_store
assert 'finally { withContext(NonCancellable) { projectStore.delete(temporary.name) } }' in vm_source
assert 'variableStore.savedRepositories()' in vm_source
assert 'RepoParsing.repositoryKind(name)' in vars_src
print('saved repositories and ZIP-folder publishing: OK')

# Every native screen shares stable Material 3 styling and responsive layout decisions.
components = (root / 'app/src/main/java/com/forgeport/android/ui/theme/ExpressiveComponents.kt').read_text()
theme = (root / 'app/src/main/java/com/forgeport/android/ui/theme/Theme.kt').read_text()
assert 'typography = AetherPortTypography' in theme
assert 'bottomStart = 12.dp' in theme
for marker in ['collectIsPressedAsState()', 'RoundedCornerShape(corner.roundToInt()', 'ExpressiveMotion.spatial()', 'verticalScroll(rememberScrollState())', 'heightIn(min = 56.dp)']:
    assert marker in components, marker
for marker in ['popEnterTransition', 'AnimatedContent(', 'contentColumns(maxWidth.value', 'imePadding()', 'AdaptiveContent {']:
    assert marker in ui, marker
assert 'contentColumns(maxWidth.value - 24f' in catalog_ui
assert 'sideBySideGalleryHeader(maxWidth.value' in screens
assert 'Modifier.width(92.dp)' not in screens
assert 'FlowRow(' in screens and 'FlowRow(' in catalog_ui
for path in ['ui/AetherPortApp.kt', 'ui/RepositoryPickers.kt', 'ui/EhentaiScreen.kt', 'gallery/GalleryCatalogScreen.kt', 'gallery/GalleryScreens.kt', 'gallery/GalleryReaderScreen.kt']:
    source = (root / 'app/src/main/java/com/forgeport/android' / path).read_text()
    assert 'ExpressiveIconButton as IconButton' in source, path
assert 'MaterialExpressiveTheme' not in theme, 'Keep compatible stable Material 3 APIs.'
print('expressive components and responsive bindings: OK')
