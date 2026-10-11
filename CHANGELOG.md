# Changelog

## 3.0.15 — Windows desktop port

- Introduced the Windows AetherPort desktop client, using the same dark surfaces and mint-green color palette as the Android application.
- GitHub home, Hugging Face Publish/Download, Google OAuth, Variables, Updates, and Settings.
- Native centered ZIP selection dialog with scrollable archive list and real disk deletion after confirmation.
- Repository content replacement with automatic detection of unnecessary outer ZIP wrapper folders; GitHub commit identity from token owner, Hugging Face from verified account.
- Portable Android-compatible encrypted credential import and export. Windows vault uses per-user DPAPI encryption.
- Windows EXE and Inno Setup installer built by GitHub Actions and published to a separate `windows-stable` release.
- Windows update checks support SHA-256 verification of downloaded installers.
- E-Hentai is intentionally excluded, with no gallery modules in this port.
