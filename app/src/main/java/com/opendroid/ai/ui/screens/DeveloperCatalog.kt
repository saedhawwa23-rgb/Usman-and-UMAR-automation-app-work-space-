package com.opendroid.ai.ui.screens

/**
 * Honest capability inventory for the Developers hub and unified discovery search.
 * `existing` means an existing app screen/integration is available; it does not
 * imply that a device dependency or credential is installed/configured.
 */
data class DeveloperCapability(
    val letter: Char,
    val title: String,
    val description: String,
    val state: CapabilityState,
    val action: DeveloperAction? = null
)

enum class CapabilityState { EXISTING_INTEGRATION, DEVICE_OR_CREDENTIAL_REQUIRED, NOT_AVAILABLE }

enum class DeveloperAction { AI_MODELS, GITHUB_SEARCH, GITHUB_WORKSPACE, TERMUX, TERMINAL, WORKSPACE_FILES, AGENT, PERMISSIONS, LOGS }

object DeveloperCatalog {
    val all: List<DeveloperCapability> = listOf(
        DeveloperCapability('A', "AI development", "Discover configured provider catalogs, select a model, and use the existing chat/agent flows.", CapabilityState.EXISTING_INTEGRATION, DeveloperAction.AI_MODELS),
        DeveloperCapability('B', "Build systems", "No in-app Gradle/Android build runner is provided. Builds run in the repository's GitHub Actions workflow or an independently configured environment.", CapabilityState.NOT_AVAILABLE),
        DeveloperCapability('C', "Code editor", "The app does not currently include a project file browser/editor or save/diff workflow.", CapabilityState.NOT_AVAILABLE),
        DeveloperCapability('D', "Dependencies", "Termux package installation is available when Termux and command permission are ready; packages are installed in Termux, not in an isolated project.", CapabilityState.DEVICE_OR_CREDENTIAL_REQUIRED, DeveloperAction.TERMUX),
        DeveloperCapability('E', "Execution", "The existing command screen accepts a user-entered Termux command and captures output/exit status when Termux, callback support, permission, and its external-command setting are ready. It is not isolated.", CapabilityState.DEVICE_OR_CREDENTIAL_REQUIRED, DeveloperAction.TERMINAL),
        DeveloperCapability('F', "File manager", "Browse the app-owned workspace, create folders/files, edit supported UTF-8 text files up to 100 KB, rename items, and delete files or empty folders with confirmation.", CapabilityState.EXISTING_INTEGRATION, DeveloperAction.WORKSPACE_FILES),
        DeveloperCapability('G', "GitHub", "Search public repositories, inspect metadata/README, and use the authenticated GitHub workspace. Public clone runs in Termux; it does not open a project in an app sandbox.", CapabilityState.DEVICE_OR_CREDENTIAL_REQUIRED, DeveloperAction.GITHUB_SEARCH),
        DeveloperCapability('H', "HTTP / API tools", "Provider and GitHub APIs are used by existing features; there is no general-purpose HTTP request composer in the app.", CapabilityState.NOT_AVAILABLE),
        DeveloperCapability('I', "Installation", "The existing Termux screen can refresh packages and install its supported development-tool bundle after permission checks.", CapabilityState.DEVICE_OR_CREDENTIAL_REQUIRED, DeveloperAction.TERMUX),
        DeveloperCapability('J', "Java / Kotlin", "No on-device Java/Kotlin project compiler or Android Gradle build workflow is currently available in-app.", CapabilityState.NOT_AVAILABLE),
        DeveloperCapability('K', "Keys and credentials", "Existing provider and GitHub credentials use app credential-storage integrations; configure them through the provider discovery and GitHub authentication flows.", CapabilityState.EXISTING_INTEGRATION, DeveloperAction.AI_MODELS),
        DeveloperCapability('L', "Logs", "Open existing app task, conversation, and diagnostic logs. Termux command results remain in the Termux/terminal UI during the current process.", CapabilityState.EXISTING_INTEGRATION, DeveloperAction.LOGS),
        DeveloperCapability('M', "Models", "Search live model catalogs for supported providers and select a returned model. A catalog result is not proof of inference availability.", CapabilityState.DEVICE_OR_CREDENTIAL_REQUIRED, DeveloperAction.AI_MODELS),
        DeveloperCapability('N', "Network diagnostics", "No dedicated connectivity/port diagnostic suite is exposed in the app.", CapabilityState.NOT_AVAILABLE),
        DeveloperCapability('O', "Open-source agents", "The cloud tool-agent screen exists; OpenHands, OpenBuilder, OpenManus, and OpenSwarm are not claimed as installed or validated integrations.", CapabilityState.DEVICE_OR_CREDENTIAL_REQUIRED, DeveloperAction.AGENT),
        DeveloperCapability('P', "Python", "Python can be installed in Termux through its package manager when the existing Termux requirements are met; no Python project manager is included.", CapabilityState.DEVICE_OR_CREDENTIAL_REQUIRED, DeveloperAction.TERMUX),
        DeveloperCapability('Q', "Quality checks", "The repository contains CI workflows, but there is no in-app workflow status or local code-quality runner.", CapabilityState.NOT_AVAILABLE),
        DeveloperCapability('R', "Runtimes", "An Alpine/PRoot runtime path exists, but app-owned PRoot is disabled on Android 9+; Termux availability is checked separately.", CapabilityState.DEVICE_OR_CREDENTIAL_REQUIRED, DeveloperAction.TERMUX),
        DeveloperCapability('S', "Sandbox", "The current free-form Termux command screen is not a security-isolated project sandbox and does not provide workspace quotas, a file manager, task cancellation, or project-scoped task history.", CapabilityState.DEVICE_OR_CREDENTIAL_REQUIRED, DeveloperAction.TERMINAL),
        DeveloperCapability('T', "Termux", "Reuse the existing Termux detection, permission checks, allowlisted tasks, and command-result callbacks. Termux absence does not block normal app features.", CapabilityState.DEVICE_OR_CREDENTIAL_REQUIRED, DeveloperAction.TERMUX),
        DeveloperCapability('U', "User permissions", "Review Android permissions and the app's permission guidance. Sensitive execution must still use the relevant Android/Termux authorization.", CapabilityState.EXISTING_INTEGRATION, DeveloperAction.PERMISSIONS),
        DeveloperCapability('V', "Version control", "GitHub repository discovery and authenticated repository browsing are available; an in-app Git diff/commit/rollback UI is not.", CapabilityState.DEVICE_OR_CREDENTIAL_REQUIRED, DeveloperAction.GITHUB_WORKSPACE),
        DeveloperCapability('W', "Web development", "Node.js can be installed in Termux through the supported development-tool bundle; no in-app project editor or preview server UI is provided.", CapabilityState.DEVICE_OR_CREDENTIAL_REQUIRED, DeveloperAction.TERMUX),
        DeveloperCapability('X', "XML / configuration", "No in-app XML, manifest, Gradle, or workflow editor/validator is currently provided.", CapabilityState.NOT_AVAILABLE),
        DeveloperCapability('Y', "YAML / automation", "No in-app GitHub Actions YAML or automation validator is currently provided.", CapabilityState.NOT_AVAILABLE),
        DeveloperCapability('Z', "Zero-to-project setup", "There is no guided project scaffolder that creates, builds, tests, and verifies a deliverable end-to-end.", CapabilityState.NOT_AVAILABLE)
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
        CapabilityState.EXISTING_INTEGRATION -> "EXISTING APP INTEGRATION"
        CapabilityState.DEVICE_OR_CREDENTIAL_REQUIRED -> "DEVICE / SETUP REQUIRED"
        CapabilityState.NOT_AVAILABLE -> "NOT AVAILABLE IN APP"
    }
}
