package com.opendroid.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opendroid.ai.core.backend.BackendCapabilities
import com.opendroid.ai.core.backend.BackendKind
import com.opendroid.ai.core.backend.BackendStatus
import com.opendroid.ai.ui.theme.AppTheme
import com.opendroid.ai.ui.viewmodel.DeveloperToolsViewModel
import com.opendroid.ai.ui.viewmodel.LinuxTerminalViewModel

@Composable
fun DevelopersScreen(
    onOpenAI: () -> Unit,
    onOpenGitHubSearch: () -> Unit,
    onOpenGitHubWorkspace: () -> Unit,
    onOpenTermux: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenWorkspaceFiles: () -> Unit,
    onOpenAgent: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenLogs: () -> Unit,
    toolCatalogViewModel: DeveloperToolsViewModel = hiltViewModel(),
    terminalViewModel: LinuxTerminalViewModel = hiltViewModel()
) {
    var query by remember { mutableStateOf("") }
    val termuxStatus by terminalViewModel.termuxStatus.collectAsState()
    val termuxAvailable by terminalViewModel.termuxAvailable.collectAsState()
    val runtimeStatus by terminalViewModel.status.collectAsState()
    val registeredTools by toolCatalogViewModel.tools.collectAsState()
    val registeredConnectors by toolCatalogViewModel.connectors.collectAsState()
    val backends by toolCatalogViewModel.backends.collectAsState()
    val codingAgents by toolCatalogViewModel.codingAgents.collectAsState()
    val workspaceStatus = backends.firstOrNull { it.descriptor.id == "app-workspace" }?.health?.status
    LaunchedEffect(Unit) { terminalViewModel.refresh(); toolCatalogViewModel.refresh() }
    val entries = DeveloperCatalog.search(query)
    val liveTools = remember(query, registeredTools) { toolCatalogViewModel.searchTools(query) }
    val liveBackends = remember(query, backends) { toolCatalogViewModel.searchBackends(query) }
    val liveCodingAgents = remember(query, codingAgents) { toolCatalogViewModel.searchCodingAgents(query) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("DEVELOPERS", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
        Text(
            "A–Z discovery for existing app integrations. Status describes what this app can open; it does not imply that a device dependency or credential is installed.",
            color = AppTheme.colors.textSecondary
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search categories, backends, tools, and agent adapters") }
        )
        OutlinedButton(
            onClick = { terminalViewModel.refresh(); toolCatalogViewModel.refresh() },
            modifier = Modifier.fillMaxWidth()
        ) { Text("REFRESH BACKEND HEALTH AND TOOLS") }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { "category:${it.letter}" }) { entry ->
                Card(colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entry.letter.toString(), color = AppTheme.colors.accentCyan, fontWeight = FontWeight.Bold)
                            Text(entry.title, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                        }
                        Text(entry.description, color = AppTheme.colors.textSecondary)
                        Text(
                            dynamicStatus(entry.letter, entry.state, termuxAvailable, termuxStatus, runtimeStatus.name, workspaceStatus),
                            color = if (entry.state == CapabilityState.NOT_AVAILABLE || entry.state == CapabilityState.INCOMPATIBLE) AppTheme.colors.accentRed else AppTheme.colors.textSecondary
                        )
                        actionFor(
                            entry.action, onOpenAI, onOpenGitHubSearch, onOpenGitHubWorkspace,
                            onOpenTermux, onOpenTerminal, onOpenWorkspaceFiles, onOpenAgent,
                            onOpenPermissions, onOpenLogs
                        )?.let { (label, callback) ->
                            Button(onClick = callback, modifier = Modifier.fillMaxWidth()) { Text(label) }
                        }
                    }
                }
            }
            item {
                Text("SANDBOX BACKENDS · ${backends.size}", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                Text("Live local health checks; no remote provider is configured. Command approval does not create isolation.", color = AppTheme.colors.textSecondary)
            }
            if (liveBackends.isEmpty()) item { Text("No sandbox backends match ‘$query’.", color = AppTheme.colors.textSecondary) }
            items(liveBackends, key = { "backend:${it.descriptor.id}" }) { backend ->
                Card(colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(backend.descriptor.name, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            "${backend.health.status.name.replace('_', ' ')} · ${backend.descriptor.kind.name.replace('_', ' ')}",
                            color = if (backend.health.status == BackendStatus.INCOMPATIBLE) AppTheme.colors.accentRed else AppTheme.colors.accentCyan
                        )
                        backend.health.installedVersion?.let { Text("Installed version: $it", color = AppTheme.colors.textSecondary) }
                        Text(backend.health.detail, color = AppTheme.colors.textSecondary)
                        Text(backend.descriptor.securityBoundary, color = AppTheme.colors.textSecondary)
                        Text("Operations: ${backendOperationSummary(backend.descriptor.capabilities)}", color = AppTheme.colors.textSecondary)
                        backend.descriptor.limitations.forEach { limitation -> Text("• $limitation", color = AppTheme.colors.textSecondary) }
                        val action = when (backend.descriptor.kind) {
                            BackendKind.APP_WORKSPACE -> "OPEN APP WORKSPACE" to onOpenWorkspaceFiles
                            BackendKind.TERMUX -> "OPEN TERMUX CONTROLS" to onOpenTermux
                            else -> null
                        }
                        action?.let { (label, callback) -> Button(onClick = callback, modifier = Modifier.fillMaxWidth()) { Text(label) } }
                    }
                }
            }
            if (backends.none { it.descriptor.kind == BackendKind.REMOTE_CLOUD }) {
                item { Text("No compatible remote cloud sandbox is configured. Add a provider adapter only after account, API, pricing, data-transfer and cancellation behavior are verified.", color = AppTheme.colors.textSecondary) }
            }
            item {
                Text("REGISTERED CODING-AGENT ADAPTERS · ${codingAgents.size}", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                Text("Only concrete adapters registered with verified metadata appear here. Research candidates and the existing cloud tool-agent are not presented as autonomous coding agents.", color = AppTheme.colors.textSecondary)
            }
            if (liveCodingAgents.isEmpty()) {
                item {
                    Text(
                        if (codingAgents.isEmpty()) "No compatible coding-agent adapters currently registered; no external agent is installed or verified."
                        else "No registered coding-agent adapters match ‘$query’.",
                        color = AppTheme.colors.textSecondary
                    )
                }
            }
            items(liveCodingAgents, key = { "agent:${it.descriptor.id}" }) { candidate ->
                Card(colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(candidate.descriptor.name, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                        Text("${candidate.health.availability.name.replace('_', ' ')} · ${candidate.descriptor.latestRelease ?: "latest release unknown"} · ${candidate.descriptor.releaseDate ?: "release date unknown"} · ${candidate.descriptor.license ?: "license not recorded"}", color = AppTheme.colors.accentCyan)
                        Text(candidate.health.detail, color = AppTheme.colors.textSecondary)
                        Text("Installed version: ${candidate.health.installedVersion ?: "not reported"} · Health checked at: ${candidate.health.checkedAtMs}", color = AppTheme.colors.textSecondary)
                        Text("Runtime: ${candidate.descriptor.runtimeRequirements}", color = AppTheme.colors.textSecondary)
                        Text("Architecture: ${candidate.descriptor.architectureSupport} · Android API: ${candidate.descriptor.androidApiSupport}", color = AppTheme.colors.textSecondary)
                        Text("Execution backends: ${candidate.descriptor.supportedExecutionBackends}", color = AppTheme.colors.textSecondary)
                        Text("Provider requirements: ${candidate.descriptor.providerRequirements}", color = AppTheme.colors.textSecondary)
                        Text("Network/storage/permissions: ${candidate.descriptor.networkStorageAndPermissionRequirements}", color = AppTheme.colors.textSecondary)
                        Text("Security boundary: ${candidate.descriptor.securityBoundary}", color = AppTheme.colors.textSecondary)
                        Text("Metadata verified at: ${candidate.descriptor.metadataVerifiedAtMs}", color = AppTheme.colors.textSecondary)
                        Text("Upstream: ${candidate.descriptor.upstreamUrl}", color = AppTheme.colors.textSecondary)
                        Text("Documentation: ${candidate.descriptor.documentationUrl}", color = AppTheme.colors.textSecondary)
                        Text("Capabilities: ${candidate.descriptor.capabilities.features.joinToString { it.name.replace('_', ' ') }.ifBlank { "none claimed" }}", color = AppTheme.colors.textSecondary)
                        candidate.descriptor.limitations.forEach { Text("• $it", color = AppTheme.colors.textSecondary) }
                    }
                }
            }
            item {
                Text("LIVE REGISTERED TOOLS · ${registeredConnectors.size} connectors", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                Text("Read-only snapshot of this app process's tool registry. Opening the agent does not execute a tool until you submit an approved task.", color = AppTheme.colors.textSecondary)
            }
            items(liveTools, key = { "tool:${it.qualifiedName}" }) { tool ->
                Card(colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(tool.qualifiedName, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                        Text("${tool.connectorName} · ${tool.risk.name}", color = AppTheme.colors.accentCyan)
                        Text(tool.description, color = AppTheme.colors.textSecondary)
                        Button(onClick = onOpenAgent, modifier = Modifier.fillMaxWidth()) { Text("OPEN CLOUD TOOL AGENT") }
                    }
                }
            }
            if (entries.isEmpty() && liveBackends.isEmpty() && liveTools.isEmpty() && liveCodingAgents.isEmpty()) item {
                Text("No developer categories, backends, tools, or coding agents match ‘$query’.", color = AppTheme.colors.textSecondary)
            }
        }
    }
}

private fun backendOperationSummary(capabilities: BackendCapabilities): String = listOfNotNull(
    "browse files".takeIf { capabilities.browseFiles },
    "search files".takeIf { capabilities.searchFiles },
    "edit/import/export".takeIf { capabilities.editFiles || capabilities.importExportFiles },
    "execute commands".takeIf { capabilities.executeCommands },
    "cancel commands".takeIf { capabilities.cancelCommands },
    "bounded wait".takeIf { capabilities.boundedWait },
    "resource limits".takeIf { capabilities.resourceLimits },
    "per-task isolation".takeIf { capabilities.taskIsolation },
    "persistent task history".takeIf { capabilities.persistentTaskHistory }
).joinToString().ifBlank { "no enabled operations" }

private fun dynamicStatus(
    letter: Char,
    state: CapabilityState,
    termuxAvailable: Boolean,
    termuxStatus: String,
    runtimeStatus: String,
    workspaceStatus: BackendStatus?
): String = when (letter) {
    'D', 'E', 'I', 'P', 'T', 'W' -> "${DeveloperCatalog.stateLabel(state)} · ${if (termuxAvailable) "$termuxStatus (package/backend availability; not isolated)" else termuxStatus}"
    'R', 'S' -> "${DeveloperCatalog.stateLabel(state)} · App-owned Linux runtime: $runtimeStatus. On Android 9+ PRoot command execution is disabled; Termux is a separate backend."
    'C', 'F', 'X', 'Y' -> "${DeveloperCatalog.stateLabel(state)} · App workspace: ${workspaceStatus?.name?.replace('_', ' ') ?: "health not checked"}"
    else -> DeveloperCatalog.stateLabel(state)
}

private fun actionFor(
    action: DeveloperAction?,
    onOpenAI: () -> Unit,
    onOpenGitHubSearch: () -> Unit,
    onOpenGitHubWorkspace: () -> Unit,
    onOpenTermux: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenWorkspaceFiles: () -> Unit,
    onOpenAgent: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenLogs: () -> Unit
): Pair<String, () -> Unit>? = when (action) {
    DeveloperAction.AI_MODELS -> "OPEN AI & MODELS" to onOpenAI
    DeveloperAction.GITHUB_SEARCH -> "SEARCH GITHUB" to onOpenGitHubSearch
    DeveloperAction.GITHUB_WORKSPACE -> "OPEN GITHUB WORKSPACE" to onOpenGitHubWorkspace
    DeveloperAction.TERMUX -> "OPEN TERMUX CONTROLS" to onOpenTermux
    DeveloperAction.TERMINAL -> "OPEN TERMUX COMMAND SCREEN" to onOpenTerminal
    DeveloperAction.WORKSPACE_FILES -> "OPEN APP WORKSPACE FILES" to onOpenWorkspaceFiles
    DeveloperAction.AGENT -> "OPEN CLOUD TOOL AGENT" to onOpenAgent
    DeveloperAction.PERMISSIONS -> "REVIEW PERMISSIONS" to onOpenPermissions
    DeveloperAction.LOGS -> "OPEN LOGS" to onOpenLogs
    null -> null
}
