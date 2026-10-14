# Changelog

## 3.0.17 — One commit per changed file

- ZIP publishing to GitHub and Hugging Face now creates one commit per added, modified or deleted file, with the filename in the message, and pushes the batch once.
- Skip unchanged files and stop before pushing if any commit fails. Keep token-owner attribution and non-force pushes.
- Support literal filenames with spaces, brackets and Unicode; process deletions first for file/directory replacements.

## 3.0.16 — Repository chooser on publish

- GitHub and Hugging Face Publish buttons now open the saved repository chooser before confirmation and upload, matching the Android flow.
- The chooser shows the selected branch and closes before the publish confirmation opens.

## 3.0.15 — Windows desktop port

- Introduced the Windows AetherPort desktop client, using the same dark surfaces and mint-green color palette as the Android application.
- GitHub home, Hugging Face Publish/Download, Google OAuth, Variables, Updates, and Settings.
- Native centered ZIP selection dialog with scrollable archive list and real disk deletion after confirmation.
- Repository content replacement with automatic detection of unnecessary outer ZIP wrapper folders; GitHub commit identity from token owner, Hugging Face from verified account.
- Portable Android-compatible encrypted credential import and export. Windows vault uses per-user DPAPI encryption.
- Windows EXE and Inno Setup installer built by GitHub Actions and published to a separate `windows-stable` release.
- Windows update checks support SHA-256 verification of downloaded installers.
- E-Hentai is intentionally excluded, with no gallery modules in this port.
