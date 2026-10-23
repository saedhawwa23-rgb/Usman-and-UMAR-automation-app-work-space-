# Git implementation evidence

Research date: 2026-10-23. This evidence records the compatibility decision for app-workspace Git cloning. It does not claim that JGit itself certifies Android as a supported runtime.

## Android 9 compatibility decision

The first implementation used `org.eclipse.jgit:org.eclipse.jgit:7.8.0.202609011348-r`. Hosted API 28 instrumentation failed with `NoSuchMethodError` for `InputStream.readNBytes(int)` inside JGit's `IO.readFully`; the stack failed while creating a test repository, before exercising the app clone path. Android documents `InputStream.readNBytes(int)` as added at API 33, and the exact failed workflow is available in the repository's CI history.[1] [2] [8]

The selected artifact is now JGit `5.13.3.202401111512-r`. Its upstream README requires at least Java 8. The published JAR was inspected and contained no `readNBytes` reference. The JGit 5.13 `CloneCommand` source supports initial-branch selection and all-branch cloning, but exposes no `setDepth` method.[3] [4] [5] Therefore the app offers a verified full-history clone only; shallow depth-1 is explicitly unsupported in this app's minSdk 28 build. It must not be silently represented as a snapshot or full clone.

JGit is pure Java and adds no native ABI, but its README's Java 8 baseline is not a formal Android certification. App-specific API 28 and API 36 emulator tests are required. Even if those pass, they do not prove compatibility with the physical Samsung Galaxy M10/ARM32 device.[3]

## GitHub authorization boundary

The existing app requests OAuth scopes `repo read:user`. GitHub documents that `repo` grants broad read/write access to public and private repositories; that is an existing app authorization choice, not a new permission added by the clone UI. GitHub documents personal access tokens as HTTPS Git credentials, but the consulted docs do not explicitly establish that this app's OAuth App token is accepted by Git smart-HTTP transport. Private-repository cloning must remain runtime-unverified until exercised with an authorized test account. Never place a token in a remote URL, Git config, logs, or UI.[6] [7]

## Implementation guardrails

- Preserve the bounded ZIP source-snapshot importer as a distinct alternative; a source snapshot has no Git history, branches, tags, or remote.
- Clone into an app-owned workspace path, stage data before publication, and verify `.git`, `origin`, the requested default branch, and a valid commit before reporting success.
- Request full history, then reject any result containing shallow metadata. Do not expose a depth-1 action with JGit 5.13.
- Never overwrite an existing snapshot or repository at the target path.
- Do not execute cloned project code or hydrate submodules/Git LFS objects.
- Test full history, branch/tag refs, safe placement, and cancellation with local repository fixtures. Emulator results do not constitute a physical M10/ARM32 test.

## References

[1]: https://developer.android.com/reference/java/io/InputStream "Android InputStream reference"
[2]: https://raw.githubusercontent.com/eclipse-jgit/jgit/v7.8.0.202609011348-r/org.eclipse.jgit/src/org/eclipse/jgit/util/IO.java "Eclipse JGit 7.8 IO source"
[3]: https://raw.githubusercontent.com/eclipse-jgit/jgit/v5.13.3.202401111512-r/README.md "Eclipse JGit 5.13.3 README"
[4]: https://raw.githubusercontent.com/eclipse-jgit/jgit/v5.13.3.202401111512-r/org.eclipse.jgit/src/org/eclipse/jgit/api/CloneCommand.java "Eclipse JGit 5.13.3 CloneCommand source"
[5]: https://repo.maven.apache.org/maven2/org/eclipse/jgit/org.eclipse.jgit/5.13.3.202401111512-r/org.eclipse.jgit-5.13.3.202401111512-r.jar "Published JGit 5.13.3 binary inspected for API methods and readNBytes references"
[6]: https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/scopes-for-oauth-apps "GitHub OAuth App scopes"
[7]: https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens "GitHub HTTPS Git authentication with tokens"
[8]: https://github.com/saedhawwa23-rgb/Usman-and-UMAR-automation-app-work-space-/actions/runs/39971834492 "API 28 runtime failure with JGit 7.8"
