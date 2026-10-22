# Developer workspace and agent ecosystem audit

Audit date: 2026-10-22. Repository: [saedhawwa23-rgb/Usman-and-UMAR-automation-app-work-space-](https://github.com/saedhawwa23-rgb/Usman-and-UMAR-automation-app-work-space-). Work is on existing PR [#2](https://github.com/saedhawwa23-rgb/Usman-and-UMAR-automation-app-work-space-/pull/2), branch `feature/developer-workspace-sandbox-search` to `main`. Before this revision, PR #2 was OPEN and UNMERGED; `main` was `2715e497ef372c91b31cf57170166820fce4bc42`. This change is being prepared only on the feature branch; no merge or change to `main` is authorized.

Android identity remains `applicationId com.usmanandumar.app`, `minSdk 28`, and ARM32 `armeabi-v7a` default. No physical Samsung Galaxy M10 test is claimed.

## Scope of this revision

- Added a shared backend contract with explicit capability flags, health/compatibility status, bounded command request/result types, cancellation semantics and an extensible in-memory registry. The registered adapters are the existing app-owned workspace, existing Termux integration and app PRoot status; no remote provider is registered by default. Remote adapters require provider-specific implementation and verified credentials/configuration.
- Extended the existing app-workspace file manager with bounded search (5,000 entries, depth 12, 100 results), safe relative-path checks, symlink rejection, create/read/edit/save, import/export of supported UTF-8 text, rename, in-workspace move and confirmation-gated deletion (empty folders only). Approved saves display a bounded before/after diff and use staged/atomic file replacement when supported. Text operations are limited to 100 KiB. This is app file storage, not a command sandbox or per-project workspace.
- Reused the existing Termux RUN_COMMAND bridge as the command backend. It requires explicit user confirmation in the terminal UI, uses bounded output and a 1–30 second wait, and reports its real boundary: Termux shell execution is not isolated and cannot be cancelled by this app. A timeout stops waiting and cleans the app-side callback state but may leave the Termux process running. Termux is optional.
- Added a generic coding-agent lifecycle contract and empty-by-default registry with explicit metadata, capability flags, health, start/inspect/cancel/resume results and duplicate-ID rejection. It is visible through the same `DeveloperToolsViewModel` search as live tools/backends in Developers and Discovery. No coding-agent adapters are currently registered; the existing CloudToolAgent is explicitly kept separate because it is not an autonomous coding-agent integration. Centralized filtering reads only the current process registries. The A–Z entries remain a static category guide, not a downloadable or auto-updating package catalog. Tool version/license/update/install metadata and remote release feeds are not yet implemented.
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

Official-source review is underway. The six completed candidates (OpenHands, OpenBuilder, OpenManus, OpenSwarm, OpenAI Codex CLI and Gemini CLI) are summarized with official repository/docs, observed release/license, runtime requirements, Android/ARM32 fit and security caveats in [agent-compatibility-research.md](agent-compatibility-research.md). Hermes Agent review is also completed and appended there. None is installed, exposed as available, or locally tested by this revision. In particular, the verified OpenHands/OpenBuilder/OpenManus/OpenSwarm identities have not been substituted for each other; native API 28 ARM32 compatibility is not claimed. Remaining candidate and Daytona/E2B research is still pending in the official-source workflow and will be added before finalizing this audit.

## Google Drive and credentials

The Android repository has no Google Drive API integration, Drive scopes, Drive OAuth implementation or Google Android OAuth client ID/resource. No `google-services.json` or Drive API operation is configured in the inspected source. The configured `Google Workspace` Manus connector exists but is disabled; it does not configure Android APK OAuth. Therefore there is no Drive browse/search/import/export/upload/save operation to test, and no Drive-connected claim. Official setup/least-privilege scope/signing/upload evidence and the exact Cloud project, OAuth, consent and test-account prerequisites are recorded in [google-drive-evidence.md](google-drive-evidence.md). To proceed, the owner must configure a Google Cloud project, enable Drive API, register an Android OAuth client for this package and the relevant signing-certificate SHA-1, set consent/scopes and authorize a test account; do not embed a Web client secret in the APK.

Existing GitHub OAuth and provider API-key stores use Android Keystore-backed AES-GCM storage in the inspected source. This is not evidence that OAuth clients/providers are configured or live. No credential values were added to this revision.

## A–Z functional status

- **A (AI/agents):** Existing configured model/chat/cloud-tool-agent paths; external coding-agent candidates remain uninstalled/unverified.
- **B/J/Q (build, Android compiler, quality runner):** No in-app Android project build or quality runner; existing remote GitHub CI is separate.
- **C/F/X/Y (editor, files, XML/config, YAML):** Supported UTF-8 files up to 100 KiB in the app workspace, with create/import/browse/search/edit/diff-preview/approve-save/rename/move/export/delete. No syntax engine, project-wide diff, Git history, checkpoint or rollback.
- **D/I/P/W (packages, installation, Python, web tools):** Existing optional Termux package controls only. No app-managed installation/version/health catalog for external agents.
- **E/T (execution, Termux):** Existing Termux RUN_COMMAND only, with explicit confirmation, bounded output and bounded wait; not isolated and not cancellable by this app.
- **G/V (GitHub/version control):** Existing repository discovery and authorized GitHub browsing retained; existing clone remains public-only, shallow and in Termux home. No full-history clone into app workspace, Git status/diff/branch/commit/rollback workflow.
- **H/N (HTTP/network diagnostics):** No general API client or network-diagnostic suite.
- **K (credentials):** Provider and GitHub secrets use Keystore-backed encrypted storage; live provider/GitHub authentication was not exercised. Google Drive OAuth is absent.
- **L (logs/task state):** Existing app conversation/task logs remain; shell results are transient. No separate project task queue/history or artifact ledger.
- **M (models):** Existing live model search/selection retained; no provider/network/inference test in this revision.
- **O (open-source agents):** Candidate research only; none is installed or adapted for this Android app.
- **R/S (runtime/sandbox):** App PRoot execution incompatible; Termux optional and non-isolated; no local container or remote cloud adapter.
- **U (approval):** Confirmations gate the new workspace deletes, reviewed saves, and free-form Termux commands; confirmations do not provide isolation.
- **Z (zero-to-project):** No end-to-end project scaffolder/reproducible build workflow.

## Verification status

- Static checks in the sandbox: passed for `git diff --check`, no conflict markers, app ID, `minSdk 28`, ARM32 ABI declaration and exactly one A–Z entry in order.
- Local Gradle `testDebugUnitTest assembleDebug`: BLOCKED before compilation because no Android SDK is installed and `ANDROID_HOME`/`sdk.dir` is absent. This is an environment limitation; it is not a test pass or source failure.
- GitHub Actions on this revised source commit: **PENDING**. The exact test count, lint, debug/release build, API 28/API 36 emulator tests and APK metadata will be added only after the final PR commit's CI completes.
- Physical Galaxy M10 test: **NOT RUN**.
- Drive OAuth/API test: **BLOCKED**; no Drive integration or OAuth setup exists.
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
- `app/src/main/java/com/opendroid/ai/ui/viewmodel/DeveloperToolsViewModel.kt`
- `app/src/main/java/com/opendroid/ai/core/agent/DeveloperAgentRegistry.kt`

Modified tests:
- `app/src/test/java/com/opendroid/ai/core/storage/WorkspacePathPolicyTest.kt`
- `app/src/test/java/com/opendroid/ai/ui/DeveloperCatalogTest.kt`
- `app/src/test/java/com/opendroid/ai/core/agent/DeveloperAgentRegistryTest.kt`

Added files:
- `app/src/main/java/com/opendroid/ai/core/backend/SandboxBackend.kt`
- `app/src/main/java/com/opendroid/ai/core/storage/WorkspaceDiffPreview.kt`
- `app/src/test/java/com/opendroid/ai/core/backend/BackendContractTest.kt`
- `app/src/test/java/com/opendroid/ai/core/storage/WorkspaceAtomicWriterTest.kt`
- `app/src/test/java/com/opendroid/ai/core/storage/WorkspaceDiffPreviewTest.kt`
- `app/src/test/java/com/opendroid/ai/core/termux/TermuxOutputPolicyTest.kt`
- `docs/agent-compatibility-research.md`
- `docs/google-drive-evidence.md`
- `docs/developer-workspace-audit.md`

No `main` change or PR merge is part of this work.
