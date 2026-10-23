# Git implementation evidence

Research date: 2026-10-23. This evidence informs the app-workspace repository-clone increment; it does not claim a physical Android device test.

## Library and clone modes

Eclipse JGit is a pure-Java Git implementation, licensed under the Eclipse Distribution License 1.0 (BSD-3-Clause). Its upstream README says JGit 6 and newer requires Java 11 or newer and lists shallow/partial cloning among missing features. The Maven Central metadata currently publishes `org.eclipse.jgit:org.eclipse.jgit:7.8.0.202609011348-r`.[1] [2]

The source for that exact release nevertheless contains `CloneCommand.setDepth(int)` and documents it as making a shallow clone. It also supports branch selection, all-branch cloning, and a progress callback.[3] Because the release README and API source disagree about shallow support, this implementation must validate both full and depth-1 clones against that exact artifact in repository tests before exposing the modes as working. If the tests fail, shallow mode must be reported unavailable rather than silently changed to a snapshot or full clone.

The upstream project does not certify Android as a supported runtime. The app uses Java 21 bytecode/tooling and minSdk 28; successful API 28/36 emulator instrumentation can provide app-specific runtime evidence but will not prove Samsung Galaxy M10/ARM32 hardware compatibility. JGit is pure Java and introduces no native ABI of its own.[1]

## GitHub authorization boundary

The existing app requests OAuth scopes `repo read:user`. GitHub documents that `repo` grants full read/write access to public and private repositories, broader than clone-only access; this is an existing app authorization choice, not a new permission added by the clone UI.[4] GitHub documents personal access tokens as HTTPS Git credentials and separately documents OAuth access tokens and OAuth scopes. This source set does not explicitly establish that the app's OAuth App token is accepted by Git smart-HTTP transport. Private clone must therefore remain runtime-unverified until exercised with an authorized test account; never put a token in the remote URL, Git config, logs, or UI.[4] [5]

## Implementation guardrails

- Preserve the existing bounded ZIP source-snapshot import; it is not a Git clone.
- Place Git clones under the app-owned workspace, stage them before placement, and verify `.git`, `origin`, selected/default branch, and a valid commit before reporting success.
- Full history is the default. A depth-1 clone is explicit and is accepted only when JGit records shallow metadata; no fallback between clone modes is allowed.
- Never overwrite an existing snapshot or repository at the target path.
- Do not execute cloned project code.
- Test repository metadata and clone modes with a local test repository. Emulator tests, if green, do not constitute a physical M10/ARM32 test.

## References

[1]: https://github.com/eclipse-jgit/jgit "Eclipse JGit upstream repository and README"
[2]: https://repo.maven.apache.org/maven2/org/eclipse/jgit/org.eclipse.jgit/maven-metadata.xml "Maven Central metadata for Eclipse JGit"
[3]: https://raw.githubusercontent.com/eclipse-jgit/jgit/v7.8.0.202609011348-r/org.eclipse.jgit/src/org/eclipse/jgit/api/CloneCommand.java "Eclipse JGit 7.8 CloneCommand source"
[4]: https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/scopes-for-oauth-apps "GitHub OAuth App scopes"
[5]: https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens "GitHub HTTPS Git authentication with tokens"
