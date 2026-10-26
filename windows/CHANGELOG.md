# Changelog

## Android 3.0.25 / Windows 3.0.26 — Hugging Face hidden-email fallback

- Handle the observed fine-grained whoami response that identifies the token owner while omitting both email and emailVerified.
- Use the authenticated username’s Hub-supported no-reply address when personal email is absent, preserving profile attribution without asking for another token or manually entered email.
- Keep an API-supplied valid email unchanged, reject explicitly unverified accounts and malformed supplied email, and retain authentication and repository permission checks.
- Update Check token to show Hub no-reply fallback while keeping missing verification status marked Not returned.
- Test the hidden-metadata profile and actual local Git commits against a harmless bare repository; no live account credentials or remote publishing are used.
- Reference: https://github.com/huggingface/hub-docs/issues/259 (Hub maintainer confirmation of profile-linked no-reply addresses). Historical attribution can change if the account username is renamed.

## Android 3.0.24 / Windows 3.0.25 — Optional Hugging Face verification metadata

- Fix the reproduced publishing rejection when whoami-v2 omits emailVerified: missing/null now means unknown, rather than an unverified account.
- Retain the authenticated personal account and valid account-email requirements, reject explicit false or malformed verification flags, and preserve real account commit attribution.
- Keep Windows Check token diagnostics honest: an omitted status remains Not returned, never Yes.
- Add regression coverage for omitted/null verification, explicit false and missing/invalid account email. No tokens, private email addresses or live publishing credentials are included in tests.

## Windows 3.0.24 — Hugging Face token diagnostics

- Add Check token beside the publishing token selector, available without selecting a ZIP or publishing.
- Show the authenticated username, token role, email verification status and commit-email availability without displaying the secret or personal email.
- Replace the ambiguous verify-email error with separate messages for rejected tokens, invalid identity, unverified email and missing profile fields.
- Keep verified-email commit attribution and repository write checks; do not bypass authentication or fabricate account email addresses.
- Cover a verified fine-grained profile, incomplete profiles, rejected tokens and the UI callback with offline tests. Android remains at 3.0.23.

## Windows 3.0.23 — Auralis-inspired interface

- Match SiamTest/Auralis's charcoal surfaces, soft violet accents, muted text, rounded shapes and compact type hierarchy.
- Restyle native fields, action buttons, cards, navigation and dialogs across publishing, Google credentials, Variables, Updates and the Android gallery.
- Use a consistent dark appearance and readable system-bar icons on Android. Keep menu-button-only drawer opening, instant gallery auto-advance and stable reader image sizing.
- Give Windows rounded panels, buttons and filled fields while retaining native keyboard controls and existing callbacks.
- Preserve saved credentials, OAuth generation, publishing, gallery downloads and update behavior.

## 3.0.22 — Portable token.pickle exports

- Export a whitelist of credential data instead of serializing Google-auth's private runtime managers. Fix token.pickle loading on older bots without google.auth._regional_access_boundary_utils.
- Keep access token, expiry, refresh credentials and scopes; preserve the existing encrypted cache and sign-in flow.
- Add a regression check that allows only the public Google credentials and datetime classes in exported pickle references.
- Verify Windows and Android exports can unpickle and refresh with Google-auth 1.35.0, 2.23.4, 2.38.0 and 2.62.0.


## 3.0.21 — WZML-X Google token generator

- Add WZML-X Drive + YouTube, Drive-only and custom permissions with Desktop credentials selection and token.pickle generation.
- Reuse and refresh encrypted saved credentials. Sign in again switches accounts, and failed authorization preserves the previous saved token.
- Export real Google credentials objects and token.json; require trusted Google OAuth endpoints and all selected permissions.


## 3.0.20 — Empty repositories and missing branches

- Initialize empty GitHub and Hugging Face repositories on the selected branch.
- Create missing branches from the remote default branch, preserving its history and leaving that branch unchanged.
- Keep one commit per changed file, token-owner attribution, and a single non-force push after all commits succeed.

## 3.0.19 — Hidden Git operations

- Launch Git commands without console windows on Windows, eliminating terminal flashes during publishing and Space downloads.
- Preserve captured output, error reporting, token redaction, per-file commits and direct publishing.

## 3.0.18 — Direct publish after repository selection

- Remove the additional Yes/No publish confirmation for GitHub and Hugging Face. Selecting a saved repository starts publishing directly after the existing setup checks.
- Keep the file-replacement notice on the publish form.

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
