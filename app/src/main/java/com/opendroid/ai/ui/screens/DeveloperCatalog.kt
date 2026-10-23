package com.opendroid.ai.ui.screens

/** Category guide for current app integrations; versioned tools appear in the live backend/tool registries. */
data class DeveloperCapability(
    val letter: Char,
    val title: String,
    val description: String,
    val state: CapabilityState,
    val action: DeveloperAction? = null
)

enum class CapabilityState {
    AVAILABLE,
    COMPATIBLE,
    INSTALLABLE,
    INSTALLED,
    VERIFIED_WORKING,
    REQUIRES_CONFIGURATION,
    INCOMPATIBLE,
    NOT_TESTED,
    NOT_AVAILABLE
}

enum class DeveloperAction { AI_MODELS, GITHUB_SEARCH, GITHUB_WORKSPACE, TERMUX, TERMINAL, WORKSPACE_FILES, AGENT, PERMISSIONS, LOGS }

object DeveloperCatalog {
    val all: List<DeveloperCapability> = listOf(
        DeveloperCapability('A', "AI development and agent frameworks", "Search configured provider model catalogs and use the existing chat/cloud-tool-agent flows. External coding-agent frameworks are not installed or validated by this app.", CapabilityState.REQUIRES_CONFIGURATION, DeveloperAction.AI_MODELS),
        DeveloperCapability('B', "Build systems and build diagnostics", "No compatible in-app Gradle/Android build runner is currently available. Repository CI can run separately in GitHub Actions.", CapabilityState.NOT_AVAILABLE),
        DeveloperCapability('C', "Code editors and source editing", "Open, edit and save supported UTF-8 text/configuration files up to 100 KB in the app workspace. Syntax tooling, project-wide diffs and checkpoints are not available.", CapabilityState.AVAILABLE, DeveloperAction.WORKSPACE_FILES),
        DeveloperCapability('D', "Dependencies and package managers", "Termux package installation can be requested through existing controls when Termux, its permission and configuration are ready. Packages live in Termux, not an isolated project.", CapabilityState.REQUIRES_CONFIGURATION, DeveloperAction.TERMUX),
        DeveloperCapability('E', "Execution engines and process control", "Existing command entry executes user-approved commands in Termux and can capture a bounded result. The app cannot cancel the Termux process, enforce resource quotas or isolate it per project.", CapabilityState.REQUIRES_CONFIGURATION, DeveloperAction.TERMINAL),
        DeveloperCapability('F', "File manager and workspace operations", "Browse and search names/small text files; create, import, export, edit, rename and move workspace items; delete files or empty folders with confirmation. Operations are bounded and path-safe.", CapabilityState.AVAILABLE, DeveloperAction.WORKSPACE_FILES),
        DeveloperCapability('G', "Git, GitHub and version control", "Search public GitHub repositories, inspect metadata/README, and browse an authorized account in GitHub Workspace. The existing Termux clone is public-only, shallow and stored outside the app workspace.", CapabilityState.REQUIRES_CONFIGURATION, DeveloperAction.GITHUB_SEARCH),
        DeveloperCapability('H', "HTTP clients and API testing", "Provider and GitHub APIs are used by existing app features, but no general-purpose HTTP client/API test tool is exposed. No compatible general tool is currently available.", CapabilityState.NOT_AVAILABLE),
        DeveloperCapability('I', "Installation, updates and integrity checks", "Termux package refresh/install controls exist. No app update channel, verified release-manifest checker, artifact provenance workflow or in-app installer is configured.", CapabilityState.REQUIRES_CONFIGURATION, DeveloperAction.TERMUX),
        DeveloperCapability('J', "Java, Kotlin, Gradle and Android tools", "No compatible on-device Java/Kotlin/Gradle project compiler or Android build workflow is currently available in the app.", CapabilityState.NOT_AVAILABLE),
        DeveloperCapability('K', "Key, OAuth and credential management", "Provider API keys and GitHub OAuth tokens use Android Keystore-backed AES-GCM storage. Provider/GitHub authorization still requires the user's own configuration; no secret values are shown here.", CapabilityState.REQUIRES_CONFIGURATION, DeveloperAction.AI_MODELS),
        DeveloperCapability('L', "Logs, diagnostics and task history", "Open existing app task/conversation/diagnostic logs. Termux command results are transient and not stored as per-project task history.", CapabilityState.AVAILABLE, DeveloperAction.LOGS),
        DeveloperCapability('M', "Models and AI-provider configuration", "Search live model catalogs and select a returned model. Provider connectivity, model availability, inference success and billing status remain distinct checks.", CapabilityState.REQUIRES_CONFIGURATION, DeveloperAction.AI_MODELS),
        DeveloperCapability('N', "Network diagnostics and connectivity", "No dedicated DNS, port, proxy, or connectivity diagnostic suite is exposed. No compatible network-diagnostics tool is currently available.", CapabilityState.NOT_AVAILABLE),
        DeveloperCapability('O', "Open-source agents and orchestration", "No compatible coding-agent adapter is currently registered. The existing cloud tool-agent is not an autonomous coding-agent integration; researched external candidates are not presented as installed or verified.", CapabilityState.NOT_AVAILABLE),
        DeveloperCapability('P', "Python and compatible language runtimes", "Python can be installed in Termux through its package manager when Termux setup is complete. It is not an app-managed project runtime and is not isolated.", CapabilityState.REQUIRES_CONFIGURATION, DeveloperAction.TERMUX),
        DeveloperCapability('Q', "Quality checks, linting and test runners", "Repository CI contains test/build workflows, but no in-app source-quality runner or live workflow-status integration is available.", CapabilityState.NOT_AVAILABLE),
        DeveloperCapability('R', "Runtimes, Linux userspace and environment management", "The app-owned Alpine/PRoot path is incompatible with this app's supported Android API range for command execution. Termux is a separate optional environment, not a complete app-managed Linux distribution.", CapabilityState.INCOMPATIBLE, DeveloperAction.TERMUX),
        DeveloperCapability('S', "Sandbox backends, policies and permissions", "The app workspace provides file operations only; Termux commands run in the user's Termux environment. This is not an isolated per-project command sandbox; process cancellation, resource quotas and project task history are not currently available.", CapabilityState.NOT_AVAILABLE, DeveloperAction.WORKSPACE_FILES),
        DeveloperCapability('T', "Termux and Termux:API integration", "Reuse the existing Termux detection, version/permission checks, allowlisted tasks, and bounded command-result callbacks. Termux is optional for ordinary app functionality.", CapabilityState.REQUIRES_CONFIGURATION, DeveloperAction.TERMUX),
        DeveloperCapability('U', "User approvals and access controls", "Review Android permissions and app guidance. Destructive workspace deletes require confirmation; command approval does not create an execution sandbox.", CapabilityState.AVAILABLE, DeveloperAction.PERMISSIONS),
        DeveloperCapability('V', "Version control, diffs and rollback", "Public GitHub discovery and configured repository browsing are available. No in-app full-history clone into the shared workspace, diff, commit, checkpoint or rollback UI is implemented.", CapabilityState.REQUIRES_CONFIGURATION, DeveloperAction.GITHUB_WORKSPACE),
        DeveloperCapability('W', "Web development and local preview", "Node.js can be installed in Termux through the existing development-tool bundle. No app-managed project preview server or isolated web-development workflow is available.", CapabilityState.REQUIRES_CONFIGURATION, DeveloperAction.TERMUX),
        DeveloperCapability('X', "XML, JSON and configuration tools", "The workspace editor can open/save supported XML, JSON and other UTF-8 text files up to 100 KB. Schema validation and syntax-aware formatting are not provided.", CapabilityState.AVAILABLE, DeveloperAction.WORKSPACE_FILES),
        DeveloperCapability('Y', "YAML, workflow and automation tools", "The workspace editor can open/save supported YAML and workflow text files up to 100 KB. Workflow validation, scheduling and automation execution are not provided here.", CapabilityState.AVAILABLE, DeveloperAction.WORKSPACE_FILES),
        DeveloperCapability('Z', "Zero-to-project setup and reproducible environments", "There is no guided project scaffolder that creates, builds, tests and verifies a deliverable end-to-end. No compatible scaffolding tool is currently available.", CapabilityState.NOT_AVAILABLE)
    )

    fun search(query: String): List<DeveloperCapability> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return all
        return all.filter { capability ->
            capability.letter.toString().equals(normalized, ignoreCase = true) ||
                capability.title.contains(normalized, ignoreCase = true) ||
                capability.description.contains(normalized, ignoreCase = true)
        }
    }

    fun stateLabel(state: CapabilityState): String = when (state) {
        CapabilityState.AVAILABLE -> "AVAILABLE"
        CapabilityState.COMPATIBLE -> "COMPATIBLE"
        CapabilityState.INSTALLABLE -> "INSTALLABLE"
        CapabilityState.INSTALLED -> "INSTALLED"
        CapabilityState.VERIFIED_WORKING -> "VERIFIED WORKING"
        CapabilityState.REQUIRES_CONFIGURATION -> "REQUIRES CONFIGURATION"
        CapabilityState.INCOMPATIBLE -> "INCOMPATIBLE"
        CapabilityState.NOT_TESTED -> "NOT TESTED"
        CapabilityState.NOT_AVAILABLE -> "NO COMPATIBLE TOOLS CURRENTLY AVAILABLE"
    }
}
