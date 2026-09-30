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
for marker in ['private fun GitHubScreen', 'private fun HuggingFaceScreen', 'private fun HfDownloadScreen']:
    block = ui.split(marker, 1)[1]
    assert 'StageZipCard(vm)' in block[:3500]
assert 'Small bug fixes' in ui
assert 'Saved GitHub variable' not in ui
assert 'CenterAlignedTopAppBar' in ui
assert 'ElevatedCard' in ui
assert 'FilledTonalButton' in ui
assert 'ic_forgeport_mark' in ui

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
assert 'versionName = "3.0.0-alpha06"' in build
assert 'versionCode = 3000006' in build
assert '<item name="android:windowLightNavigationBar">false</item>' not in (root / 'app/src/main/res/values/themes.xml').read_text(), 'API 27-only navigation-bar appearance attribute must not be in the base values theme.'
app_ui = (root / 'app/src/main/java/com/forgeport/android/ui/ForgePortApp.kt').read_text()
assert 'androidx.compose.material.icons.filled.ArrowForward' not in app_ui, 'Use the AutoMirrored ArrowForward icon.'
assert 'Icons.AutoMirrored.Filled.ArrowForward' in app_ui
assert 'compileSdk = 36' in build
assert 'androidx.compose.material3:material3' in build
assert 'material-icons-extended' in build

manifest = (root / "app/src/main/AndroidManifest.xml").read_text()
assert 'android:icon="@mipmap/ic_launcher"' in manifest
assert 'android:roundIcon="@mipmap/ic_launcher_round"' in manifest
for resource in [
    'app/src/main/res/drawable/ic_forgeport_mark.xml',
    'app/src/main/res/mipmap-anydpi/ic_launcher.xml',
    'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml',
]:
    assert (root / resource).is_file(), resource

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
]:
    assert marker in workflow
assert 'sdkmanager tools' not in workflow
assert '"tools"' not in workflow

print("source verification: OK")
