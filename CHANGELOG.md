# Changelog

## 3.0.16 — Windows release version alignment

- Bumped the Windows application version to 3.0.16, including window title and sidebar label.
- Updated Windows installer version, default setup filename, and EXE File/Product Version metadata.
- GitHub Actions now obtains the version from the application and uses it consistently for release artifacts, installer and release notes.
- Added version consistency tests so a future version increment cannot leave the Windows installer behind.

## 3.0.15 — Windows desktop port

- Introduced the Windows AetherPort desktop client, using the same dark surfaces and mint-green color palette as the Android application.
- GitHub home, Hugging Face Publish/Download, Google OAuth, Variables, Updates, and Settings.
- Native centered ZIP selection dialog with scrollable archive list and real disk deletion after confirmation.
- Repository content replacement with automatic detection of unnecessary outer ZIP wrapper folders; GitHub commit identity from token owner, Hugging Face from verified account.
- Portable Android-compatible encrypted credential import and export. Windows vault uses per-user DPAPI encryption.
- Windows EXE and Inno Setup installer built by GitHub Actions and published to a separate `windows-stable` release.
- Windows update checks support SHA-256 verification of downloaded installers.
- E-Hentai is intentionally excluded, with no gallery modules in this port.
