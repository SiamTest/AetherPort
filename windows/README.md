# AetherPort — Windows Desktop Edition

**Version 3.0.25** · A native Windows GUI inspired by AetherPort for Android. **No E-Hentai code, UI, or dependencies.**

## Features

- GitHub publishing: choose a ZIP, click Publish to choose a saved repository and start publishing directly, automatically unwrap one unnecessary outer folder, replace the repository contents, create one commit per added, modified or deleted file as the GitHub token owner, and push to the selected branch. Empty repositories are initialized automatically; missing branches are created from the remote default branch without changing that branch.
- Hugging Face Spaces: publish a ZIP with one commit per changed file, or download a Space into a ZIP. Each commit message includes the filename. Unchanged files are skipped; a rename creates separate deletion and addition commits. All commits are pushed together once.
- ZIP chooser: newest first, centered scrollable picker, permanent deletion with confirmation. Configurable source folder (defaults to Downloads).
- Variables: multiple GitHub/HF tokens and saved repositories; encrypted locally using **Windows DPAPI** under the current user account.
- **Android-compatible `.aetherbackup` import/export**: AES-256-GCM and PBKDF2-HMAC-SHA256 (210,000 rounds), exactly matching Android AetherPort 3.0.14 and later. Existing variables are preserved by default.
- WZML-X-compatible Google token generation: Desktop `credentials.json`, Drive + YouTube preset, optional Drive-only/custom permissions, browser sign-in, encrypted token reuse and automatic refresh, and `token.pickle` / `token.json` exports. Use Sign in again to switch Google accounts.
- Windows update checking/download against the `windows-stable` GitHub release tag and running its installer on user confirmation.
- Dark UI with AetherPort green and centered modal dialogs.

## Requirements

- Windows 10 or Windows 11 (x64).
- **Git for Windows** installed and discoverable on PATH; used by GitHub/Hugging Face publish and download. Install: https://git-scm.com/download/win. Restart the app after installing Git. Git credentials are passed through a temporary askpass helper and process environment, not embedded in repository remotes or command-line arguments.
- An internet connection for publishing, Google OAuth, and updates.
- A GitHub personal access token with access to the target repository; for Hugging Face, a token with appropriate permissions and a verified email address.
- Google OAuth Desktop client and authorized scopes for Google tokens.

## Build locally

```powershell
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements-build.txt
python -m pytest -q tests
python main.py
pyinstaller --noconfirm --clean --onedir --windowed --name AetherPort --icon assets/aetherport.ico --add-data "assets;assets" --collect-submodules google_auth_oauthlib --collect-submodules google.oauth2 main.py
```

For a Windows `.exe` installer, install **Inno Setup 6** and compile `installer/AetherPort.iss` after PyInstaller. The installer creates a Start menu shortcut and has an optional desktop shortcut. Installer/uninstaller leave `%LOCALAPPDATA%\AetherPort` data intact unless you remove it separately.

## GitHub Actions: automatic release

In this combined project, Windows sources live in `windows/` and the Windows workflow lives at the repository root under `.github/workflows/`. Run local build commands from the `windows/` directory; `.github/workflows/windows-release.yml` runs on every push to `main` and on manual dispatch. It runs unit tests, builds `AetherPort.exe`, the portable ZIP and Inno Setup EXE, then uploads them as Actions artifacts and to a separate `windows-stable` GitHub Release. Android releases are untouched.

Default update source is `SiamTest/AetherPort` (editable from Updates). To use another repository, set the update source within the app. For updates to work, the target repository must run the Windows release workflow and publish a `windows-stable` release. Use GitHub's release page to obtain compiled executables after Actions succeeds; this source ZIP **is not itself a Windows EXE**.

## Security and limitations

- Credential backups are **portable** and contain every variable (including secrets). Use a strong, unique backup password and store files securely.
- Local DPAPI credentials are **not portable** without exporting a `.aetherbackup` first; they are tied to the Windows user profile.
- Git publishing **replaces the target branch working tree** to mirror the ZIP. Confirm repository selection and keep backups; the app does not force-push but files absent from the ZIP are deleted from the new commit.
- Git LFS files require Git LFS to be installed where used; large Space repositories may require an LFS setup. Git must exist on PATH.
- Windows SmartScreen may warn for unsigned executables. Code signing requires your own signing certificate and release workflow integration; the app does not claim to be signed.
- This source was tested for Python syntax and logic on Linux. The Windows EXE/installer must be built on a Windows runner and tested on Windows before release.

## Files

`aetherport/core.py` — secure backup, vault, ZIP operations, Git publishing and download.
`aetherport/app.py` — desktop UI, screens and dialogs.
`aetherport/google_auth.py` — Google token flow.
`aetherport/updates.py` — Windows update checks and downloads.
`tests/` — offline test suite.

MIT License (matching the supplied Android AetherPort source). Project derived from the AetherPort Android application functionality; the Windows edition contains no E-Hentai sources.

## Google token generator

Enable Google Drive API and YouTube Data API v3 in your Google Cloud project for the WZML-X preset. Configure the consent screen and test users, create a **Desktop app** OAuth client, and download its JSON. In Google, choose the file and permissions, click **Generate token.pickle**, complete browser sign-in, then save the file. Valid saved tokens are reused and expired tokens refresh automatically. Exports are genuine Google credentials objects with access and refresh tokens and UTC expiry. Full instructions are in the root README.


### Interface check

The interface follows Auralis’s charcoal/violet styling with rounded controls and native keyboard focus. Run `python ../tools/verify_windows_ui.py` from `windows/` with a desktop display to open all six pages at the default and minimum window sizes and verify button states, shell clicks and focus. No account or network is used.
