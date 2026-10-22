# Developer workspace and agent ecosystem audit

Audit date: 2026-10-22. Repository: [saedhawwa23-rgb/Usman-and-UMAR-automation-app-work-space-](https://github.com/saedhawwa23-rgb/Usman-and-UMAR-automation-app-work-space-). Work is on existing PR [#2](https://github.com/saedhawwa23-rgb/Usman-and-UMAR-automation-app-work-space-/pull/2), branch `feature/developer-workspace-sandbox-search` to `main`; the latest source commit validated by hosted CI is `23086ada93860b2b0bd374e029054a9919831e26`. PR #2 remains OPEN and UNMERGED; `main` remains `2715e497ef372c91b31cf57170166820fce4bc42`. This work is confined to the feature branch; no merge or change to `main` is authorized.

Android identity remains `applicationId com.usmanandumar.app`, `minSdk 28`, and ARM32 `armeabi-v7a` default. No physical Samsung Galaxy M10 test is claimed.

## Scope of this revision

- Added a shared backend contract with explicit capability flags, health/compatibility status, bounded command request/result types, cancellation semantics and an extensible in-memory registry. The registered adapters are the existing app-owned workspace, existing Termux integration and app PRoot status; no remote provider is registered by default. Remote adapters require provider-specific implementation and verified credentials/configuration.
- Extended the existing app-workspace file manager with bounded search (5,000 entries, depth 12, 100 results), safe relative-path checks, symlink rejection, create/read/edit/save, import/export of supported UTF-8 text, rename, in-workspace move and confirmation-gated deletion (empty folders only). Approved saves display a bounded before/after diff and use staged/atomic file replacement when supported. Text operations are limited to 100 KiB. This is app file storage, not a command sandbox or per-project workspace.
- Reused the existing Termux RUN_COMMAND bridge as the command backend. It requires explicit user confirmation in the terminal UI, uses bounded output and a 1–30 second wait, and reports its real boundary: Termux shell execution is not isolated and cannot be cancelled by this app. A timeout stops waiting and cleans the app-side callback state but may leave the Termux process running. Termux is optional.
- Added a generic coding-agent lifecycle contract and empty-by-default registry. A registered adapter must record release/date/license/source, runtime/architecture/API/backend/provider/network/storage/permission/security metadata, health, capability flags and task lifecycle; a separate optional management interface defines install/configure/update/uninstall/troubleshoot only when implemented. It is visible through the same `DeveloperToolsViewModel` search as live tools/backends in Developers and Discovery. No coding-agent adapters are currently registered; the existing CloudToolAgent is explicitly kept separate because it is not an autonomous coding-agent integration. Centralized filtering reads only the current process registries. The A–Z entries remain a static category guide, not a downloadable or auto-updating package catalog; release-feed refresh/caching and concrete agent adapters remain unimplemented.
- Discovery offers one text field with AI/model, sandbox/backend, developer-tools, GitHub-project and updates filters. Existing AI and GitHub behavior is retained; a missing update feed is shown as unavailable, not as an installed tool.
- The A–Z guide describes existing functions and marks unsupported categories explicitly. It does not install OpenHands, OpenBuilder, OpenManus, OpenSwarm or another candidate.

## Backend status and boundaries

| Backend | Current status | Verified capability/boundary | Not implemented or tested |
|---|---|---|---|
| App-owned workspace | Available when health check can read/write its app-specific directory | Bounded text-file operations and safe-path checks; this is storage only | No shell execution, per-project isolation, Git metadata, repository import, task queue/history, checkpoint/rollback, or device UI test |
| Termux | Requires Termux 0.109+ result callback support, Android RUN_COMMAND permission, and `allow-external-apps=true`; actual execution availability is not verified here | Existing command API; 30-second maximum wait and bounded output; command submission/results are not isolated from Termux files/processes/credentials | No child-process cancellation, hard CPU/RAM/disk/network quotas, durable per-project task log, or real-device permission/command test |
| App-owned Alpine/PRoot | Incompatible for command execution on the supported app target; UI reports runtime status | PRoot is userspace emulation and not a security boundary; app source documents its command path disabled for this Android range | No claim that a root filesystem being installed means it runs; no execution, install or smoke test |
| Android-local containers | Not available in this app | No Android Docker daemon/kernel isolation is claimed | No Docker/Podman backend or container image run |
| Remote Daytona/E2B/custom | Interface and programmatic registry hook only; no adapter configured | Registry rejects incorrect backend kind/duplicate IDs | No provider API, authentication, billing/terms UI, file sync, resource enforcement, remote cancellation, cleanup or smoke test |

No backend is labelled “securely isolated.” Command approval is authorization, not isolation. The app workspace is visible to this app's components; Termux commands execute in the user's Termux environment. Do not send credentials to an agent or put them in command output.

## Agent-candidate research

Official-source review is complete for 14 agent products and two remote sandbox services. [agent-compatibility-research.md](agent-compatibility-research.md) contains the candidate profiles; the consolidated [ecosystem-research.md](ecosystem-research.md) provides the 16-record comparison, integration/security analysis and full references. None is installed or smoke-tested by this revision. The required OpenHands/OpenBuilder/OpenManus/OpenSwarm identities are kept distinct; native API 28 ARM32 compatibility is not claimed.

## Google Drive and credentials

The feature branch now has a `play-services-auth` dependency and requests Google's Android `AuthorizationClient` only when the user selects a Drive action. The app asks for the narrow `drive.file` scope, browses accessible Drive folders/files, and exports the app workspace to ZIP using Drive v3 resumable 256 KiB chunks. Workspace export skips symlinks and is capped at 25,000 entries and 512 MiB uncompressed. Access tokens are passed only to the immediate request and not saved by the app. The code and mock-server listing/upload tests pass hosted CI; no Google Cloud project, Drive API enablement, Android signing fingerprints, actual consent, or real Drive operation has been configured or tested. `drive.file` does not permit unrestricted listing of all Drive content; full-drive access is not requested. Setup, scope and test prerequisites are in [google-drive-evidence.md](google-drive-evidence.md). The owner must enable Drive API, configure consent, register package `com.usmanandumar.app` with the relevant debug/release signing SHA-1, and authorize a test account before runtime verification. No Web client secret belongs in the APK.

Existing GitHub OAuth and provider API-key stores use Android Keystore-backed AES-GCM storage in the inspected source. This is not evidence that OAuth clients/providers are configured or live. No credential values were added to this revision.

## A–Z functional status

- **A (AI/agents):** Existing configured model/chat/cloud-tool-agent paths; external coding-agent candidates remain uninstalled/unverified.
- **B/J/Q (build, Android compiler, quality runner):** No in-app Android project build or quality runner; existing remote GitHub CI is separate.
- **C/F/X/Y (editor, files, XML/config, YAML):** Supported UTF-8 files up to 100 KiB in the app workspace, with create/import/browse/search/edit/diff-preview/approve-save/rename/move/export/delete. No syntax engine, project-wide diff, Git history, checkpoint or rollback.
- **D/I/P/W (packages, installation, Python, web tools):** Existing optional Termux package controls only. No app-managed installation/version/health catalog for external agents.
- **E/T (execution, Termux):** Existing Termux RUN_COMMAND only, with explicit confirmation, bounded output and bounded wait; not isolated and not cancellable by this app.
- **G/V (GitHub/version control):** Existing repository discovery and authorized GitHub browsing retained; existing clone remains public-only, shallow and in Termux home. No full-history clone into app workspace, Git status/diff/branch/commit/rollback workflow.
- **H/N (HTTP/network diagnostics):** No general API client or network-diagnostic suite.
- **K (credentials):** Provider and GitHub secrets use Keystore-backed encrypted storage; live provider/GitHub authentication was not exercised. Google Drive uses action-scoped authorization; OAuth configuration and API access remain untested.
- **L (logs/task state):** Existing app conversation/task logs remain; shell results are transient. No separate project task queue/history or artifact ledger.
- **M (models):** Existing live model search/selection retained; no provider/network/inference test in this revision.
- **O (open-source agents):** Candidate research only; none is installed or adapted for this Android app.
- **R/S (runtime/sandbox):** App PRoot execution incompatible; Termux optional and non-isolated; no local container or remote cloud adapter.
- **U (approval):** Confirmations gate the new workspace deletes, reviewed saves, and free-form Termux commands; confirmations do not provide isolation.
- **Z (zero-to-project):** No end-to-end project scaffolder/reproducible build workflow.

## Verification status

- Static checks in the sandbox: passed for `git diff --check`, no conflict markers, app ID, `minSdk 28`, ARM32 ABI declaration and exactly one A–Z entry in order.
- Local Gradle `:app:testDebugUnitTest`: BLOCKED before compilation because no Android SDK is installed and `ANDROID_HOME`/`sdk.dir` is absent. This is an environment limitation; it is not a test pass or source failure.
- GitHub Actions run [39822459710](https://github.com/saedhawwa23-rgb/Usman-and-UMAR-automation-app-work-space-/actions/runs/39822459710) on exact PR source commit `23086ada93860b2b0bd374e029054a9919831e26`: **PASS**. Unit tests and debug APK build, Android Lint, API 28 and API 36 instrumented tests, PRoot/Alpine provenance checks, ARM32 PRoot packaging, unsigned R8/ProGuard release build and artifact uploads all completed successfully. This includes the Drive mock-server listing and chunked upload tests.
- Physical Galaxy M10 test: **NOT RUN**.
- Drive OAuth/API runtime test: **BLOCKED** pending owner-provided Google Cloud configuration, signing SHA-1 registration and account consent. Mock-server tests and compilation pass, but no real Google account, Drive API call, or physical-device flow has been tested.
- External-agent installs/smoke tests: **NOT RUN**; official-source research only.

## Current changed and added files

Modified application files:
- `app/src/main/java/com/opendroid/ai/core/storage/DeveloperWorkspaceFiles.kt`
- `app/src/main/java/com/opendroid/ai/core/termux/TermuxIntegration.kt`
- `app/src/main/java/com/opendroid/ai/ui/Navigation.kt`
- `app/src/main/java/com/opendroid/ai/ui/screens/DeveloperCatalog.kt`
- `app/src/main/java/com/opendroid/ai/ui/screens/DevelopersScreen.kt`
- `app/src/main/java/com/opendroid/ai/ui/screens/DiscoveryScreen.kt`
- `app/src/main/java/com/opendroid/ai/ui/screens/WorkspaceFilesScreen.kt`
- `app/build.gradle`
- `app/src/main/java/com/opendroid/ai/ui/viewmodel/DeveloperToolsViewModel.kt`

Modified tests:
- `app/src/test/java/com/opendroid/ai/core/storage/WorkspacePathPolicyTest.kt`
- `app/src/test/java/com/opendroid/ai/ui/DeveloperCatalogTest.kt`

Added files:
- `app/src/main/java/com/opendroid/ai/core/agent/DeveloperAgentRegistry.kt`
- `app/src/main/java/com/opendroid/ai/core/backend/SandboxBackend.kt`
- `app/src/main/java/com/opendroid/ai/core/storage/WorkspaceDiffPreview.kt`
- `app/src/main/java/com/opendroid/ai/core/storage/WorkspaceArchive.kt`
- `app/src/main/java/com/opendroid/ai/data/repository/GoogleDriveIntegration.kt`
- `app/src/main/java/com/opendroid/ai/ui/viewmodel/GoogleDriveViewModel.kt`
- `app/src/test/java/com/opendroid/ai/core/agent/DeveloperAgentRegistryTest.kt`
- `app/src/test/java/com/opendroid/ai/core/backend/BackendContractTest.kt`
- `app/src/test/java/com/opendroid/ai/core/storage/WorkspaceAtomicWriterTest.kt`
- `app/src/test/java/com/opendroid/ai/core/storage/WorkspaceDiffPreviewTest.kt`
- `app/src/test/java/com/opendroid/ai/core/termux/TermuxOutputPolicyTest.kt`
- `app/src/test/java/com/opendroid/ai/core/storage/WorkspaceArchiveTest.kt`
- `app/src/test/java/com/opendroid/ai/data/repository/GoogleDriveIntegrationTest.kt`
- `docs/agent-compatibility-research.md`
- `docs/ecosystem-research.md`
- `docs/google-drive-evidence.md`
- `docs/developer-workspace-audit.md`

No `main` change or PR merge is part of this work.
