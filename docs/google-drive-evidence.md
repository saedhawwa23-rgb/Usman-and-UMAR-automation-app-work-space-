# Google Drive integration evidence

Research checked 2026-10-22 against official Google developer documentation. This is evidence about requirements, not proof that the app is already connected.

- The current feature branch now includes a `play-services-auth` dependency, an action-scoped `AuthorizationClient` request for `drive.file`, a Drive v3 file/folder listing client, and bounded ZIP workspace export using 256 KiB resumable-upload chunks. Access tokens are passed to a single operation and are not persisted by the app. There is still no configured Google Cloud project, verified Android signing fingerprint, or successful Drive API/device test. The app package is `com.usmanandumar.app`. The `Google Workspace` Manus connector is separate and does not configure OAuth for the Android APK.
- Google says Drive scopes must be declared both in the Cloud Console consent configuration and in the app. Google recommends least-privilege scopes; `drive.file` allows the app to create files and access files opened/shared with it, while broad Drive access scopes are restricted. [1]
- Google documents that services relying on Google Play sign-in may require the Android package name and signing certificate SHA-1; it recommends obtaining debug and release fingerprints from the signing certificate / Gradle report. The repository uses `com.usmanandumar.app`, but no release signing key exists in this workspace, so the release SHA-1 cannot be verified here. [2]
- Drive API v3 upload uses `files.create`; small files can be sent as media or multipart, while resumable uploads support recovery after interrupted transfer. Requests require a valid OAuth access token; long-term access needs secure refresh-token handling. [1] [3]
- Before a real Drive operation can be tested, the owner must configure the actual Google Cloud project, enable Drive API, configure OAuth consent and allowed scopes, register the correct Android client for package + signing SHA-1 (debug and/or release), provide any required Web client ID only if the selected auth flow requires it, and authorize a test Google account. No secret should be embedded in the APK.

## Android implementation constraints

- Google recommends separating sign-in from data authorization and requesting Drive access only when the user invokes a Drive action. The Android `AuthorizationClient` flow uses the `play-services-auth` dependency, requests a Drive scope, and returns a resolution intent when user consent is needed. Access tokens are not to be treated as permanent credentials; request authorization through the client for an operation rather than persisting a long-lived bearer token in app preferences. [7]
- The narrow `drive.file` scope is non-sensitive and supports files the app creates or files the user explicitly opens/shares with it. It does **not** authorize unrestricted browsing of the user's entire Drive. Full-drive listing/download would require broader restricted scopes and additional verification; do not silently request them. [1]
- For mobile uploads, Google recommends resumable uploads when network interruption is likely. The app packages the app workspace, including any `.git` directory, into a ZIP with symlinks skipped, a 25,000-entry cap, and a 512 MiB uncompressed-size cap, then uploads in 256 KiB chunks using Drive v3 resumable `files.create`. In-process session recovery after connection loss, progress reporting, and user cancellation are not implemented; the Android/Drive runtime flow remains untested. [3] [7]
- The app's Android OAuth client must be registered for package `com.usmanandumar.app` and the actual signing certificate SHA-1. The debug and release fingerprints may differ; the release signing key is not available in this workspace, so a release OAuth client cannot yet be verified. No client secret belongs in the APK. [2] [7]

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
[7]: https://developer.android.com/identity/authorization "Google Identity authorization for Android"
