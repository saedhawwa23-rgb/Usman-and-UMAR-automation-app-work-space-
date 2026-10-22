# Agent ecosystem research for the Android PR

**Checked:** 2026-10-22

**Target context:** existing Android application `com.usmanandumar.app`, Android 9 / API 28, Samsung Galaxy M10, ARM32.
**Scope:** identity, documented capabilities, runtime/backends, redistribution, security boundaries, and integration fit. This report distinguishes an agent from a sandbox service and distinguishes a remote option from on-device support.

> **Execution status:** No candidate was installed, health-checked, or smoke-tested on the target phone, in Termux/PRoot, or in this sandbox. Every installation result below is therefore **Not assessed**. The current PR follow-up separately adds Google Drive workspace functionality; it does not install or adapt any candidate.

## Decision summary

The reviewed official sources do **not** establish a supported native Android API 28 / ARM32 deployment for any candidate. The strongest evidence is for a thin Android client that calls a separately hosted agent or sandbox. The phone should not be treated as the agent runtime, Docker host, Linux namespace host, or Firecracker host without a new, device-specific port and security validation.

The exact first four directions are materially different:

1. **OpenHands:** the most complete documented agent/server architecture, but its local Canvas/SDK path requires Node 24+ or Python 3.12+ and commonly Docker; remote Agent Server/Cloud/Enterprise is the credible route. [1–5]
2. **OpenBuilder:** a hosted, early-beta browser app-builder with no installation, but its public source, license, release, isolation, and Android compatibility are unknown. It is not a local runtime or sandbox API. [6–10]
3. **OpenManus:** a Python agent with local Docker and an optional Daytona adapter; neither produces an Android ARM32 build, and its non-Docker path can operate with host privileges. [11–15]
4. **OpenSwarm:** VRSEN's Node/Python multi-agent TUI/FastAPI project, not the separate proprietary `openswarm-ai/openswarm`; official assets include ARM64 but not ARM32 or Android. [16–20]

## Compact comparison

| Candidate | Exact upstream / role | Native API28 ARM32 evidence | Documented remote or host path | Release / license | Catalog posture |
|---|---|---|---|---|---|
| **OpenHands** | `OpenHands/OpenHands`; agent control center plus SDK/server | Not supported/verified; no Android/ARM32 statement | Agent Server, Docker/Kubernetes/VM, Cloud/Enterprise, remote workspaces | v1.30.1; MIT (ecosystem components separate) [1–5] | Remote-first; do not mark Android-compatible |
| **OpenBuilder** | `TheOpenBuilder`; hosted browser AI app-builder | No APK or local runtime; Android compatibility unknown | Hosted browser service; Supabase/API integrations | Release and license unknown; early beta [6–10] | Hosted-web category only; reject installable-Android claim |
| **OpenManus** | `FoundationAgents/OpenManus`; Python general agent | Unsupported/low feasibility; Python 3.12+, Docker assumed for isolation | Local Docker; optional Daytona adapter | v0.3.0; MIT [11–15] | Research/remote only; not the Android app |
| **OpenSwarm** | `VRSEN/OpenSwarm`; local multi-agent TUI/FastAPI | Unsupported/unverified; x64/ARM64 assets, no ARM32 | Local host, optional Docker Compose/FastAPI | v1.1.3; MIT [16–20] | Separate upstream; identity mismatch unresolved |
| **OpenAI Codex CLI** | `openai/codex`; Rust terminal coding agent | No Android/ARM32 artifact; supported desktop/server OSes | Local supported host; Codex Cloud/mobile surface is separate | 0.163.0 stable; Apache-2.0 [21–25] | Optional remote/host candidate |
| **Gemini CLI** | `google-gemini/gemini-cli`; Node terminal agent | No Android/ARM32; Docker/Podman sandbox is host-side | Google login/API/Vertex; local Docker/Podman sandbox | v0.65.0 stable; Apache-2.0 [26–30] | Optional host/remote candidate |
| **Hermes Agent** | `NousResearch/hermes-agent`; self-hosted agent/orchestrator | Explicit Android support is aarch64 only; Termux package currently broken | Local, Docker, SSH, Singularity, Modal, Daytona, Vercel Sandbox | v0.22.4; MIT [31–35] | Reject ARM32 local; remote conditional |
| **Goose** | `aaif-goose/goose`; Rust agent | Termux is documented, but only x86_64/ARM64; no ARM32 | Local CLI/API, MCP, remote `goose serve`, roaming agents | v1.55.0; Apache-2.0 [36–40] | ARM32 reject; remote/host only |
| **Cline** | `cline/cline`; CLI/SDK/IDE agent | No Android/ARM32; SSH helper supports x64/ARM64/macOS | Cloud Agents from mobile browser; desktop SSH | CLI v3.0.73 / SDK v0.0.95 / Desktop v0.0.48 / IDE v4.1.25; Apache-2.0 [41–45] | Strong remote-client option; not local Android |
| **Kilo Code** | `Kilo-Org/kilocode`; CLI/IDE agent plus Cloud Agent | No Android/ARM32 artifact; native deps x64/ARM64 | Kilo Cloud isolated Linux containers; local CLI/IDE | CLI/VS Code v7.8.15; MIT client (backend distinctions) [46–50] | Remote Cloud Agent conditional |
| **Aider** | `Aider-AI/aider`; Python terminal pair programmer | No Android path; PyPI wheel is not Android evidence | Local Python/Git host; cloud or local model endpoint | PyPI 0.86.2 vs GitHub v0.86.0 discrepancy; Apache-2.0 [51–55] | Host/remote-Linux experiment only |
| **Qwen Code** | `QwenLM/qwen-code`; Node/TypeScript agent and daemon | No Android/ARM32; Node 22+, native packages x64/ARM64 | CLI/ACP and local HTTP/SSE daemon; provider endpoints | 0.26.0; Apache-2.0 [56–60] | Optional host candidate |
| **Open Interpreter** | `openinterpreter/openinterpreter`; Rust/Codex terminal agent | Release binaries x64/ARM64 only; no Android/ARM32 | Local supported host; model provider may be remote | 0.0.56; Apache-2.0 [61–65] | Unsupported/unverified on target |
| **OpenCode** *(optional)* | `anomalyco/opencode`; local coding agent/server | No Android/ARM32; v1/v2 feeds disagree | Local TUI + HTTP server; remote MCP/GitHub Actions are integrations | npm 2.1.3 vs GitHub v1.19.4; MIT [66–70] | Keep optional; never substitute for OpenBuilder |
| **Daytona** *(adapter)* | `daytonaio/daytona`; remote sandbox platform, not an agent | Android is client-only; runner/namespace/VM infrastructure is remote | Managed API, BYOC, SDK/CLI/MCP; remote Linux containers/VMs | OSS core v0.190.0 archived; SDK feeds 0.226.0; core AGPLv3, SDK Apache-2.0 [71–75] | Remote-only adapter |
| **E2B** *(adapter)* | `e2b-dev/e2b` + `e2b-dev/runtime`; remote sandbox platform, not an agent | Cloud API may be called remotely; Embed requires Linux + KVM | Cloud, BYOC, Embed Firecracker microVMs | SDK 2.59.0; component licenses differ (MIT/Apache-2.0) [76–80] | Remote-only adapter; no Android-local claim |

## Four required directions

### 1. OpenHands — the most complete agent/server direction

**Identity and architecture.** Use only `OpenHands/OpenHands`; do not substitute its SDK, `sandbox-server`, OpenBuilder, OpenManus, OpenSwarm, OpenCode, Daytona, or E2B. Agent Canvas is a browser control center, not the execution runtime. The Agent Server executes conversations and tools against a selected workspace/sandbox; HTTP and WebSocket APIs include health/readiness and authenticated API routes. [1–5]

**Documented capabilities.** The SDK documents reasoning/planning, terminal/bash, file editing, task tracking, optional web/MCP tools, conversation goals, pause/resume, persistence and restore. GitHub Cloud integration can read/write selected repositories, issues, pull requests, statuses, workflows and webhooks. SDLC guidance covers feature work, test generation, test/coverage and regression workflows; these are documented workflows, not guarantees of successful tests. Plugins can bundle skills, hooks, MCP servers, agents and slash commands. [2–5]

**Fit.** Canvas requires Node 24+ and `uv`/Python 3.12+; SDK/Agent Server are Python packages and local container execution expects Docker. Official host guidance names macOS, Linux and Windows/WSL2, not Android API 28 or ARM32. Termux/PRoot feasibility is **unknown/not assessed**, not compatibility evidence. Remote Agent Server, Cloud and Enterprise are documented, but the Android browser/client path is not officially verified. [1,3,4]

**Integration decision.** Treat OpenHands as a **remote-backend candidate**, not an Android-local dependency. A future adapter would need a reachable Agent Server/runtime URL, session authentication, LLM/provider credentials, persistent workspace handling, reconnect/resume semantics and an Android-compatible client UI. Local exposure is unsafe by default: Agent Server local start has no API authentication; use session API keys, stable `OH_SECRET_KEY`, private networking/TLS/reverse proxy, firewalling and restrictive CORS before exposure. [3–5]

### 2. OpenBuilder — hosted browser app-builder direction

**Identity and provenance.** The exact product is the hosted service at [theopenbuilder.com](https://theopenbuilder.com/) and GitHub organization `TheOpenBuilder`. The organization exposes no verifiable public source repository or release entry. Official documentation calls it early beta; do not replace it with another similarly named repository. [6–10]

**Documented capabilities.** The service supports project/feature planning, plan review/accept/reject, context approval, code edits, lint/build checks, quick fixes, preview/deployment, runtime logs, Flow Diagram and screenshot/element-selector debugging, interactive logpoints, checkpoints/revert and API/Supabase integrations. The reviewed sources do **not** document generic shell/file APIs, a test framework, GitHub operations, plugins, pause/cancel controls or a portable sandbox. [7–9]

**Fit and unknowns.** The quick start says no installation is required and the service runs in a browser. It documents current desktop browser versions, not Android/API 28/Galaxy M10/ARM32. License, source availability, release/version, storage location, model selection, quotas and isolation are unknown. The FAQ/API wording about API-key storage is internally ambiguous (locally/not shared versus stored server-side); this is not a security guarantee. [6,8,10]

**Integration decision.** Keep OpenBuilder only in a **hosted-web-service** category. It may be tested later from a browser if product policy allows, but it is not evidence for an installable Android agent, local Termux runtime, or sandbox adapter. Its documented backend integration is Supabase; existing Supabase projects are currently unsupported. [8–10]

### 3. OpenManus — Python agent plus optional remote sandbox

**Identity and architecture.** The maintained upstream is `FoundationAgents/OpenManus`; the older `mannaandpoem/OpenManus` repository says the project moved. It is an agent framework, not a hosted sandbox service. [11–15]

**Documented capabilities.** It provides planning steps and bounded execution, local file view/create/replace/insert/undo, Python execution, ask-human/terminate, browser automation via Browser Use MCP, generic MCP over stdio/SSE, search/data-analysis flows and test files. Daytona is an optional adapter; no E2B integration was found in the exact repository. There is no documented durable paused/cancelled task state or guaranteed recovery; interruption is via keyboard/bash process controls. [11–15]

**Fit.** Setup requires Python 3.12+ and a substantial dependency graph. The Docker sandbox needs a host Docker daemon, bind mounts and resource/network controls; default configuration uses local filesystem operations when sandboxing is disabled. A Termux/PRoot install would not reproduce the documented Docker kernel boundary. The remote Daytona path does not make the client itself Android ARM32-compatible. [12–15]

**Integration and security decision.** Reject as an installable Android candidate. If used on a remote supported host, isolate it before granting repository credentials: `PythonExecute` uses `exec` in a child process with timeout, not a security boundary; MCP stdio/SSE and shell tools are powerful. Daytona is separate infrastructure with its own account, API key, billing and terms. [13–15,71–75]

### 4. OpenSwarm — multi-agent TUI/FastAPI direction

**Identity and mismatch.** The verifiable project is `VRSEN/OpenSwarm`, package `@vrsen/openswarm`, not the distinct proprietary `openswarm-ai/openswarm`. No official source associates `VRSEN/OpenSwarm` or the supplied app ID with this Android project; the public Google Play URL and GitHub-user lookup did not verify an app match. [16–20]

**Documented capabilities.** An orchestrator routes to specialist agents for virtual assistance, deep research, data analysis, slides, docs, images and video. Tools include web search, persistent shell, IPython, file copy/read/write, Composio discovery/execution and handoff. Planning is mentioned in release notes; thread loading exists through Agency callbacks, but a stable task-state schema, pause/cancel/resume guarantee or general plugin SDK is not documented. [16–19]

**Fit.** Official metadata requires Node 20+ and Python 3.12+; bootstrap may install browsers, LibreOffice and Poppler. v1.1.3 assets include Linux ARM64, x64, macOS and Windows variants, but no Android or Linux ARM32. Docker Compose mounts host directories and exposes a local FastAPI service; no Daytona/E2B integration or server authentication contract was verified. [17–20]

**Integration decision.** Record the exact identity as **unresolved for `com.usmanandumar.app`** and keep local testing **Not assessed**. If separately cataloged, classify it as a host-side multi-agent service, not a sandbox. Use a hardened remote host/container, least-privilege credentials and an authenticated reverse proxy before exposing its API. [18–20]

## Optional candidates

These are useful comparison points but do not change the Android boundary:

- **Codex CLI:** strong documented repository editing, tests, JSONL automation, GitHub integration and resumable saved threads. Local execution uses OS sandboxing and approval policy on supported hosts; Cloud is a separate OpenAI-hosted workspace. No Linux ARM32 or Android package is published. [21–25]
- **Gemini CLI:** Node 20+ terminal agent with Plan Mode, shell/files/tests, web, GitHub workflows, headless JSON/JSONL, MCP/extensions and session restore/rewind. Docker/Podman sandboxing is optional and host-side. No Android/ARM32 support is documented. [26–30]
- **Hermes Agent:** the only reviewed candidate with an explicit Android path, but it is **aarch64/arm64-v8a only** and the current Termux guide says the package is broken. It has rich tools, SQLite sessions, skills/plugins, provider switching, checkpoints and documented Daytona—but none establishes this ARM32 target. [31–35]
- **Goose:** Rust agent with Termux documentation, sessions, MCP extensions, file/shell/Git/test tools and remote `goose serve`; official install/assets are x64/ARM64 only. Its macOS-only sandbox is not an Android/Linux boundary. [36–40]
- **Cline:** CLI/SDK/IDE agent with Plan/Act, tests, GitHub workflows, checkpoints, plugins/MCP and Cloud Agents. Cloud Agents are the clearest phone-friendly route: a browser can control a per-session hosted sandbox. Desktop SSH still requires a supported desktop client and remote x64/ARM64/macOS host. [41–45]
- **Kilo Code:** local CLI/IDE agent with modes, file/shell/web/MCP tools, checkpoints, GitHub/PR flows and 30+ providers. Kilo Cloud offers isolated Linux containers and repository worktrees, but has separate account, integration and compute billing. No Android/ARM32 artifact. [46–50]
- **Aider:** Python/Git pair programmer with `/run`, `/git`, `/test`, `/web`, chat-history save/load, Git commits and `/undo`. The PyPI wheel is `py3-none-any`, but that is not Android compatibility; the dependency graph and Python/Git host still need validation. GitHub release and PyPI version feeds disagree. [51–55]
- **Qwen Code:** Node 22+ TypeScript agent with file/shell/web/MCP tools, planning, goals, background tasks, resume/restore/rewind, subagents and an optional authenticated local HTTP/SSE daemon. Native packages list x64/ARM64 only; no Android/ARM32 evidence. [56–60]
- **Open Interpreter:** current Rust/Codex-based terminal agent, not the legacy Python package. It supports plan/review, shell/files, tests/QA, MCP/skills/hooks and session resume/fork, but release assets are x64/ARM64 and sandbox enforcement relies on host mechanisms such as Bubblewrap/seccomp/Seatbelt. [61–65]
- **OpenCode (optional):** local TUI plus HTTP server with Build/Plan/subagents, shell/files/LSP/web/MCP/GitHub integrations, snapshots and session export/import. The v1/v2/npm/GitHub version feeds disagree; neither feed contains Android/ARM32 support. Keep it optional and never substitute it for OpenBuilder. [66–70]

Across these optional agents, the common integration pattern is **model endpoint + local host execution**. A remote model provider is not a remote execution backend: files, shell commands, credentials and test processes still run wherever the agent is installed.

## Remote sandbox adapters

### Daytona

Daytona is infrastructure, not an autonomous agent. It exposes managed sandbox lifecycle, files, processes/code execution, Git, previews, MCP and SDK/REST/CLI integrations. The documented API is remote; Android can be a client but is not the compute plane. Its public OSS core (`v0.190.0`) is archived/no longer maintained, while Python/TypeScript SDK feeds separately report `0.226.0`; do not collapse those versions. Core licensing is AGPLv3, SDK feeds identify Apache-2.0, and the hosted service is governed by separate terms. [71–75]

The remote path requires a Daytona account/API key or JWT, outbound HTTPS and commonly WebSocket/polling connectivity. Official sandboxes provide container or VM isolation with filesystem/network/resource boundaries, but valid organization keys can access sandbox tooling broadly and secrets may be exposed if misconfigured. The practical Android design is an app-side HTTPS/WebSocket client to an already hosted Daytona environment, not a Termux/PRoot Daytona host. [72–75]

### E2B

E2B is likewise a sandbox substrate, not an agent/planner. Its Cloud, BYOC and Embed choices expose sandbox lifecycle, files, commands, persistence, Git-like workflows and the ability to run an integrator-supplied agent/LLM. Cloud uses Firecracker microVMs; Embed requires a Linux host with hardware virtualization. SDK 2.59.0 feeds and repository/package licenses are component-specific (MIT/Apache-2.0), while hosted-service terms are separate. [76–80]

An Android app could conditionally call the E2B API over HTTPS if it can protect an API key and the orchestration runs in a suitable place. Android/API28/ARM32 SDK compatibility remains undocumented. No official Termux/PRoot or handset-local Firecracker path was found. Egress is enabled by default in Cloud and domain filtering has documented limits; microVM isolation does not remove credential, network or data-exposure risk. [77–80]

**Adapter conclusion:** Daytona and E2B are plausible **remote execution backends**, not evidence of native Android support. OpenManus and Hermes document Daytona integrations; OpenHands documents its own Agent Server/Cloud/Enterprise paths; no verified native Daytona/E2B adapter was found for OpenBuilder, OpenSwarm, Codex, Gemini, Goose, Cline, Kilo, Aider, Qwen Code, Open Interpreter or OpenCode. Do not infer support from a generic SDK, MCP server or OpenAI-compatible endpoint.

## Android / ARM32 boundary

1. **ABI:** the target is 32-bit ARM. The reviewed native/package matrices generally stop at Linux/Android ARM64 (`aarch64`/`arm64-v8a`) and x64. Hermes explicitly excludes non-aarch64 Android; Goose, OpenSwarm, Codex, Gemini, Cline, Kilo, Open Interpreter and OpenCode publish no ARM32 target. [16–20,21–30,31–40,41–50,61–70]
2. **API level:** Android API 28 is not a documented support target for these projects. An API24 ARM64 wheel or generic `py3-none-any` wheel is not proof of API28/ARM32 compatibility.
3. **Userland is not the kernel boundary:** Termux/PRoot may provide a shell or Linux userland, but does not provide Docker/Podman daemons, Linux namespaces/cgroups/seccomp, macOS Seatbelt, or Firecracker/KVM. Therefore a PRoot experiment cannot be labeled equivalent to an upstream sandbox.
4. **Remote is a different product shape:** a mobile browser/client can sometimes reach OpenHands Canvas, OpenBuilder, Cline Cloud Agents, Kilo Cloud, Codex Cloud or a custom Daytona/E2B API, but the sources do not certify this specific Android browser/device. Network, authentication, reconnect, storage and UI behavior require a separate Android smoke test.
5. **No app-ID evidence:** `com.usmanandumar.app` is not evidence that any upstream candidate has an Android build. OpenSwarm's exact app association is unresolved; the other records likewise provide no APK/Gradle/manifest mapping.

**Boundary decision:** mark native local support as **not supported / not established** rather than “works.” Keep Termux/PRoot as an explicitly unverified experiment only. Prefer a thin, least-privilege remote client if the PR requires agent functionality on this phone.

## Security and trust boundaries

- **Agents are powerful operators, not sandboxes.** OpenHands Canvas, OpenManus local mode, Goose, Cline, Kilo, Aider, Qwen Code and OpenCode can read/write files or execute commands in their runtime environment. Approval prompts, checkpoints and Git history are control/recovery aids, not isolation.
- **Use an external boundary for untrusted work.** Prefer a hardened VM/container or managed Daytona/E2B/Cline/Kilo/OpenHands backend, with minimal mounts, no host Docker socket, no privileged mode, restricted egress, short-lived credentials and an authenticated proxy.
- **Secrets are a first-class risk.** Provider keys, GitHub tokens, Daytona/E2B keys and stored auth files must not be placed in the Android app bundle or logs. OpenHands requires `OH_SECRET_KEY`/session API keys for exposure; Codex documents sensitive local auth state; OpenBuilder's key-storage wording is ambiguous; Daytona organization keys can grant broad sandbox access; E2B credentials must be held by the server-side integration where possible. [3–5,8,10,21–25,71–80]
- **Network controls are incomplete by default.** E2B permits outbound internet by default; agent MCP servers and browser tools can exfiltrate data; cloud agents can reach repositories and provider endpoints. Domain filters, CORS, TLS and reverse proxies reduce exposure but do not replace authorization and data minimization.
- **Prompt injection remains in scope.** Web search, repository content, issue text, MCP responses and uploaded files can influence an agent. Review diffs, commands and generated tests; do not use unattended/yolo/danger-full-access modes on sensitive workspaces.
- **Hosted-service terms matter.** Daytona/E2B/Kilo/OpenBuilder account, billing, retention and acceptable-use terms are separate from the open-source license of a client or SDK. License compatibility does not grant rights to redistribute hosted services or provider credentials.

## Project integration priorities

1. **Keep the Android app as client/control plane.** Define a narrow HTTPS/WebSocket protocol for task submission, status/events, approval prompts, cancellation, reconnect/resume, artifact transfer and logs. Do not embed a Python/Node/Rust agent or Docker daemon in the API28 ARM32 build.
2. **Choose one remote execution contract before choosing an agent.** OpenHands Agent Server is the most fully documented agent/server contract; Daytona and E2B are stronger sandbox substrates but require an agent/orchestrator. Cline Cloud/Kilo Cloud/Codex Cloud are product-specific hosted routes with their own account and repository constraints.
3. **Separate provider and execution credentials.** Store provider keys, GitHub tokens and sandbox keys server-side; issue short-lived, scoped Android session tokens. Never send long-lived secrets to an untrusted agent workspace.
4. **Make the backend replaceable.** Implement an adapter interface for `create_task`, `stream_events`, `approve/reject`, `cancel`, `resume`, `list_artifacts`, `download_artifact` and `delete_task`; avoid coupling Android UI to a candidate-specific event schema.
5. **Require an evidence gate.** Before any catalog status changes from research-only, record exact upstream, supported host/ABI, license, credential model, isolation mode, release feed, Android client behavior and a reproducible smoke test. Preserve “Unknown,” “Not assessed,” and feed discrepancies.
6. **Test in layers.** First test the remote service from a supported host; then test Android API28 browser/network/TLS/reconnect behavior; only then consider any Termux/PRoot experiment. A successful remote call must not be recorded as native Android support.
7. **Use the existing project security posture.** Apply least privilege, TLS, server-side audit logs, explicit user approval for write/command actions, bounded task timeouts, artifact size limits, redaction and deletion policies. Treat checkpoints/Git commits as rollback aids, not security controls.
8. **Prioritize candidates by integration evidence, not feature count.** Recommended first investigation: OpenHands Agent Server or a minimal custom backend; evaluate Daytona/E2B only when a sandbox boundary is required. Keep OpenBuilder as hosted-web research and reject all listed candidates as native ARM32 installs until first-party evidence and a device test exist.

## Installation and smoke-test status

The upstream records describe possible procedures—examples include `agent-canvas`/Agent Server `/health`, OpenBuilder browser workflow, `python main.py`, `gemini -p ... --output-format json`, `cline doctor`, `kilo roll-call`, `qwen serve` `/health`, OpenCode `/global/health`, and Daytona/E2B create-execute-delete flows. These are **procedures only**. None was run for this PR, on the target phone, or as a claim of Android support.

## References

1. [OpenHands repository](https://github.com/OpenHands/OpenHands) — exact upstream.
2. [OpenHands introduction](https://docs.openhands.dev/overview/introduction) — ecosystem map.
3. [Agent Canvas setup](https://docs.openhands.dev/openhands/usage/agent-canvas/setup) — Node/uv/Docker setup.
4. [Agent Canvas backends and architecture](https://docs.openhands.dev/openhands/usage/agent-canvas/backends) — local and remote execution boundaries.
5. [OpenHands SDK / Agent Server guides](https://docs.openhands.dev/sdk/getting-started) — SDK, persistence, goals, server and integrations.
6. [OpenBuilder product](https://theopenbuilder.com/) — hosted product identity.
7. [OpenBuilder official documentation](https://docs.theopenbuilder.com/) — product capabilities and early-beta docs.
8. [OpenBuilder quick start and FAQ](https://docs.theopenbuilder.com/get-started/quick-start.md) — browser/no-installation path and browser requirements.
9. [OpenBuilder planning/debugging/API guides](https://docs.theopenbuilder.com/guides/project-planning.md) — documented planning, edits and integrations.
10. [OpenBuilder security/support and GitHub organization](https://docs.theopenbuilder.com/best-practices/security.md) — limited security evidence; organization/release listings.
11. [OpenManus maintained upstream](https://github.com/FoundationAgents/OpenManus) — exact repository and README.
12. [OpenManus setup metadata](https://raw.githubusercontent.com/FoundationAgents/OpenManus/main/setup.py) — Python requirements.
13. [OpenManus sandbox implementation](https://raw.githubusercontent.com/FoundationAgents/OpenManus/main/app/sandbox/core/sandbox.py) — Docker/local boundary.
14. [OpenManus agent and tool implementations](https://raw.githubusercontent.com/FoundationAgents/OpenManus/main/app/agent/manus.py) — tools and execution behavior.
15. [OpenManus Daytona adapter](https://github.com/FoundationAgents/OpenManus/blob/main/app/daytona/README.md) — optional remote backend.
16. [VRSEN OpenSwarm upstream](https://github.com/VRSEN/OpenSwarm) — exact maintained project.
17. [OpenSwarm package metadata](https://raw.githubusercontent.com/VRSEN/OpenSwarm/main/package.json) — Node/package targets.
18. [OpenSwarm Python metadata](https://raw.githubusercontent.com/VRSEN/OpenSwarm/main/pyproject.toml) — Python dependencies.
19. [OpenSwarm Docker/server](https://raw.githubusercontent.com/VRSEN/OpenSwarm/main/docker-compose.yml) — local service and mounts.
20. [OpenSwarm latest release](https://api.github.com/repos/VRSEN/OpenSwarm/releases/latest) — v1.1.3 release and assets.
21. [OpenAI Codex upstream](https://github.com/openai/codex) — exact agent project.
22. [Codex installation requirements](https://github.com/openai/codex/blob/main/docs/install.md) — supported OS/toolchain.
23. [Codex npm metadata](https://registry.npmjs.org/@openai%2fcodex/0.163.0) — native package matrix.
24. [Codex CLI and non-interactive docs](https://developers.openai.com/codex/cli) — capabilities and automation.
25. [Codex security/cloud docs](https://developers.openai.com/codex/agent-approvals-security) — sandbox, approvals and cloud boundary.
26. [Gemini CLI upstream](https://github.com/google-gemini/gemini-cli) — exact agent project.
27. [Gemini CLI installation](https://geminicli.com/docs/get-started/installation/) — Node/OS requirements.
28. [Gemini CLI npm metadata](https://registry.npmjs.org/@google%2fgemini-cli/0.65.0) — native package matrix.
29. [Gemini tools/headless docs](https://geminicli.com/docs/reference/tools/) — documented tools and automation.
30. [Gemini sandbox/authentication docs](https://geminicli.com/docs/cli/sandbox/) — Docker/Podman and provider boundary.
31. [Hermes Agent upstream](https://github.com/NousResearch/hermes-agent) — exact project.
32. [Hermes platform support](https://hermes-agent.nousresearch.com/docs/getting-started/platform-support) — aarch64-only Android statement.
33. [Hermes Termux guide](https://hermes-agent.nousresearch.com/docs/getting-started/termux) — package status and Android limits.
34. [Hermes tools/backends](https://hermes-agent.nousresearch.com/docs/user-guide/features/tools) — execution backends.
35. [Hermes security/plugins](https://hermes-agent.nousresearch.com/docs/user-guide/security) — host, container and plugin boundaries.
36. [Goose upstream](https://github.com/aaif-goose/goose) — exact current repository.
37. [Goose installer architecture](https://raw.githubusercontent.com/aaif-goose/goose/main/download_cli.sh) — x64/ARM64 targets.
38. [Goose Android build guidance](https://github.com/aaif-goose/goose/blob/main/BUILDING_LINUX.md) — Termux build limitations.
39. [Goose CLI/extensions docs](https://goose-docs.ai/docs/getting-started/using-extensions/) — tools and MCP.
40. [Goose remote server/sandbox docs](https://goose-docs.ai/docs/guides/remote-goose-server/) — remote execution and auth.
41. [Cline upstream](https://github.com/cline/cline) — exact project.
42. [Cline CLI installation](https://docs.cline.bot/getting-started/installing-cline) — supported CLI path.
43. [Cline Cloud Agents](https://docs.cline.bot/usage/cloud-agents) — mobile-browser remote route.
44. [Cline SSH support](https://docs.cline.bot/usage/cline-desktop-ssh) — remote host/architecture limits.
45. [Cline CLI/SDK releases and license](https://github.com/cline/cline/releases) — separate version tracks.
46. [Kilo Code upstream](https://github.com/Kilo-Org/kilocode) — exact project.
47. [Kilo CLI docs](https://kilo.ai/docs/code-with-ai/platforms/cli) — local CLI.
48. [Kilo Cloud Agent](https://kilo.ai/docs/code-with-ai/platforms/cloud-agent) — hosted containers and billing.
49. [Kilo package metadata](https://raw.githubusercontent.com/Kilo-Org/kilocode/main/packages/opencode/package.json) — native target matrix.
50. [Kilo security/open-source notes](https://kilo.ai/open) — client/backend licensing and limits.
51. [Aider upstream/docs](https://github.com/Aider-AI/aider) — exact project and usage.
52. [Aider installation](https://aider.chat/docs/install.html) — Python/host assumptions.
53. [Aider command/testing docs](https://aider.chat/docs/usage/commands.html) — files, shell, Git, tests.
54. [Aider PyPI metadata](https://pypi.org/project/aider-chat/) — package version and wheel.
55. [Aider security/model docs](https://aider.chat/docs/repomap.html) — provider data and local execution.
56. [Qwen Code upstream](https://github.com/QwenLM/qwen-code) — exact project.
57. [Qwen Code quick start](https://qwenlm.github.io/qwen-code-docs/en/users/quickstart/) — Node/auth flow.
58. [Qwen Code package metadata](https://raw.githubusercontent.com/QwenLM/qwen-code/main/package.json) — Node/native targets.
59. [Qwen tools/architecture](https://qwenlm.github.io/qwen-code-docs/en/developers/tools/introduction/) — capabilities.
60. [Qwen sandbox/daemon docs](https://qwenlm.github.io/qwen-code-docs/en/users/features/sandbox/) — optional isolation and trust boundaries.
61. [Open Interpreter upstream](https://github.com/openinterpreter/openinterpreter) — current Rust project.
62. [Open Interpreter terminal install](https://www.openinterpreter.com/docs/terminal/install) — host requirements.
63. [Open Interpreter release assets](https://api.github.com/repos/OpenInterpreter/openinterpreter/releases/405347506/assets?per_page=100) — x64/ARM64 matrix.
64. [Open Interpreter interactive/sandbox docs](https://www.openinterpreter.com/docs/terminal/interactive) — controls and execution.
65. [Open Interpreter sandbox/approvals](https://www.openinterpreter.com/docs/terminal/sandbox) — host isolation limits.
66. [OpenCode upstream](https://github.com/anomalyco/opencode) — exact optional project.
67. [OpenCode v1/v2 docs](https://opencode.ai/docs/) — agents, tools and server.
68. [OpenCode package metadata](https://registry.npmjs.org/@opencode/cli/2.1.3) — package targets/version.
69. [OpenCode security](https://raw.githubusercontent.com/anomalyco/opencode/dev/SECURITY.md) — no built-in sandbox statement.
70. [OpenCode release feeds](https://api.github.com/repos/anomalyco/opencode/releases) — v1/v2 version discrepancy.
71. [Daytona upstream](https://github.com/daytonaio/daytona) — archived OSS core and identity.
72. [Daytona architecture/isolation](https://www.daytona.io/docs/en/architecture) — remote runner boundary.
73. [Daytona sandbox/process docs](https://www.daytona.io/docs/en/sandboxes) — lifecycle and execution.
74. [Daytona authentication/API keys](https://www.daytona.io/docs/en/api-keys) — organization credentials.
75. [Daytona license/pricing/SDK feeds](https://www.daytona.io/pricing) — hosted terms and current service context.
76. [E2B SDK upstream](https://github.com/e2b-dev/e2b) — official SDK repository.
77. [E2B runtime upstream](https://github.com/e2b-dev/runtime) — Firecracker runtime.
78. [E2B quickstart/lifecycle](https://docs.e2b.dev/quickstart) — Cloud API smoke procedure.
79. [E2B security/network](https://e2b.dev/security) — microVM and egress boundaries.
80. [E2B pricing/terms](https://e2b.dev/terms) — hosted-service constraints.
