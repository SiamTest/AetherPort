# ForgePort native migration status

## Replaced

| Hosted ForgePort component | Android implementation |
| --- | --- |
| FastAPI HTML UI | Jetpack Compose |
| Turso variables | Android Keystore encrypted local variables |
| Server temporary workspace | App-private staged-project storage |
| Server `git` subprocesses | JGit |
| Browser ZIP upload | Android document picker |
| Browser ZIP download | Android create-document picker |
| Hosted Google OAuth callback | Local loopback callback on 127.0.0.1 |
| Python token generation | Kotlin OAuth exchange + compatible pickle writer |
| Server authentication/accounts | Removed; local device workspace |
| ForgePort hosting | Removed |

## Preserved product rules

- Projects has no upload control.
- Upload project ZIP exists in GitHub, Hugging Face, and HF Download.
- GitHub token is auto-detected from the repository owner.
- Default/fallback commit message is `Small bug fixes`.
- GitHub and Hugging Face operations use staged projects.
- Google OAuth can export `token.pickle` and `token.json`.

## Build automation

- GitHub Actions now verifies and builds the native Android app automatically.
- Successful runs publish a debug APK and SHA-256 checksum as workflow artifacts.
- No hosting service is involved in the app build.

## Production hardening still recommended

- Run Android Gradle/SDK build matrix on API 26, 30, 34, and 36.
- Test JGit against large repositories and Git LFS repositories.
- Add operation cancellation and foreground progress for very large clones/uploads.
- Add WorkManager only where background continuation is actually needed.
- Add release signing and Play integrity/release configuration.
- Add instrumentation tests for Storage Access Framework flows.
