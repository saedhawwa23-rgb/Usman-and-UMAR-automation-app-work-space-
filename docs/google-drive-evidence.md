# Google Drive integration evidence

Research checked 2026-10-22 against official Google developer documentation. This is evidence about requirements, not proof that the app is already connected.

- The repository contains no Google Drive/OAuth source file, no Drive API dependency/reference, no `google-services.json`, and no configured Android/Web Google OAuth client IDs. Its existing configured OAuth field is `GITHUB_CLIENT_ID`; the app package is `com.usmanandumar.app`. The `Google Workspace` Manus connector is present but disabled; it does not configure OAuth for the Android APK.
- Google says Drive scopes must be declared both in the Cloud Console consent configuration and in the app. Google recommends least-privilege scopes; `drive.file` allows the app to create files and access files opened/shared with it, while broad Drive access scopes are restricted. [1]
- Google documents that services relying on Google Play sign-in may require the Android package name and signing certificate SHA-1; it recommends obtaining debug and release fingerprints from the signing certificate / Gradle report. The repository uses `com.usmanandumar.app`, but no release signing key exists in this workspace, so the release SHA-1 cannot be verified here. [2]
- Drive API v3 upload uses `files.create`; small files can be sent as media or multipart, while resumable uploads support recovery after interrupted transfer. Requests require a valid OAuth access token; long-term access needs secure refresh-token handling. [1] [3]
- Before a real Drive operation can be tested, the owner must configure the actual Google Cloud project, enable Drive API, configure OAuth consent and allowed scopes, register the correct Android client for package + signing SHA-1 (debug and/or release), provide any required Web client ID only if the selected auth flow requires it, and authorize a test Google account. No secret should be embedded in the APK.

## References

[1]: https://developers.google.com/workspace/drive/api/guides/api-specific-auth "Google Drive API authorization scopes"
[2]: https://developers.google.com/android/guides/client-auth "Google Play services client authentication"
[3]: https://developers.google.com/workspace/drive/api/guides/manage-uploads "Google Drive API upload management"

## Repository clone feasibility evidence

- Eclipse describes JGit as a pure-Java Git implementation with clone/fetch, repository, refs and working-tree features. Its current 7.8.0 release is dated 2026-09-09 and the project page lists it as a minor release. JGit's README identifies Eclipse Distribution License 1.0 / BSD-3-Clause and says JGit 6+ requires at least Java 11. [4] [5]
- Android's Java compatibility guide documents bytecode desugaring and a limited set of library APIs. It does not certify JGit 7.8.0 on Android API 28 / ARM32. The repository has no JGit dependency or Android device smoke-test evidence. Adding current JGit to the APK would therefore be unverified, and selecting an older Java-8-compatible version solely for compatibility would require separate maintenance/security review and API 28/ARM32 tests. No embedded Git library was added in this pass. [6]
- Existing repository clone UI calls the Termux API to run a shallow (`--depth=1`) public clone into Termux home. It is not a full-history import to the app workspace, and the current app has no verified private-repository import implementation. That operation remains explicitly limited rather than being presented as meeting the requested default full-history import.

[4]: https://projects.eclipse.org/projects/technology.jgit/releases/7.8.0 "Eclipse JGit 7.8.0 release"
[5]: https://github.com/eclipse-jgit/jgit "Eclipse JGit project README"
[6]: https://developer.android.com/studio/write/java8-support "Android Java API desugaring and compatibility"
