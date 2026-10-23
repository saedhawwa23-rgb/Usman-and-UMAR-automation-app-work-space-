# USMAN AND UMAR — Product Vision

## Core vision

USMAN AND UMAR is a unified AI, tools, automation, sandbox, and developer workspace. It brings together AI models, standalone developer tools, coding agents, automation systems, sandbox environments, cloud services, local runtimes, project management, and digital productivity tools in one extensible platform.

AI is one optional component, not a prerequisite for ordinary tool and workspace operations. The application must remain useful when no AI provider or API key is configured, Termux is absent, a particular agent is unavailable, or the user chooses to work manually.

## Tool-first architecture

Tools must be discoverable and usable independently through direct user interaction, as well as usable as capabilities for AI agents when the user chooses. The ecosystem may include developer tools and runtimes; editors, file managers, terminals, build systems, debuggers and version control; HTTP/API and network utilities; document, research, conversion and data-analysis tools; workflow automation and integrations; AI models and agents; local, Termux, PRoot and remote environments; cloud storage; and supported media or content tools.

Do not restrict the product to a fixed tool list or one agent. A shared, extensible registry should discover and evaluate tools using verified upstream sources. Compatibility decisions must reflect actual dependencies, licensing, architecture, installation requirements, permissions, execution backend, security boundary and test evidence.

Manus-style autonomous task execution remains an optional capability rather than the definition of the product. Compatible agents and tools should share the same authorized project files, supported backends, and—where implemented—permissions, logs and task history.

## User control and truthful status

Users should be able to discover and search tools, configure and install compatible tools, run tools directly, connect tools to agents, manage project files, inspect results and logs, stop supported running processes, approve sensitive actions, update or remove installed tools, and use core application features without AI.

Never present a decorative catalog as an installed tool. Do not create fake installations, nonfunctional controls or unverified compatibility claims. Label implemented, available, unsupported, blocked and untested states according to evidence. A feature is not “working” merely because its metadata exists.

## Implementation constraints

Preserve the existing application and its identity: app name **USMAN AND UMAR**, application ID `com.usmanandumar.app`, minimum SDK API 28, and Samsung Galaxy M10 / ARM32 compatibility requirements. Keep unsupported Android 9/ARM32 features explicitly labelled. Make changes on the existing feature branch; do not merge PR #2 or modify `main` without explicit approval.

## Current implementation increment

The GitHub Workspace can import an authenticated repository’s default-branch ZIP source snapshot into the app-owned shared workspace. This snapshot is bounded and staged before placement. It is **not a Git clone**: Git object history, branches, tags, remotes and executable metadata are not imported. The app does not run project code or install dependencies during import. Full-history repository cloning/version control remains a separate required capability.
