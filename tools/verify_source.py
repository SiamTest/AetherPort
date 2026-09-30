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
    assert 'StageZipCard(vm)' in block[:2500]
assert 'Small bug fixes' in ui
assert 'Saved GitHub variable' not in ui

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
assert 'versionName = "3.0.0-alpha04"' in build
assert 'compileSdk = 36' in build
print("source verification: OK")

workflow = (root / ".github/workflows/build-android.yml").read_text()
for marker in [
    "actions/checkout@v4",
    "actions/setup-java@v4",
    "android-actions/setup-android@v4",
    'packages: "platform-tools"',
    "gradle/actions/setup-gradle@v4",
    "testDebugUnitTest",
    "lintDebug",
    "assembleDebug",
    "actions/upload-artifact@v4",
]:
    assert marker in workflow

assert 'sdkmanager tools' not in workflow
assert '"tools"' not in workflow
