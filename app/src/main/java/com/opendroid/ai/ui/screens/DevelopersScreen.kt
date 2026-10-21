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
    LaunchedEffect(Unit) { terminalViewModel.refresh() }
    val entries = DeveloperCatalog.search(query)
    val liveTools = remember(query, registeredTools) {
        if (query.isBlank()) registeredTools else registeredTools.filter { tool ->
            tool.qualifiedName.contains(query, ignoreCase = true) || tool.connectorName.contains(query, ignoreCase = true) || tool.description.contains(query, ignoreCase = true)
        }
    }

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
            label = { Text("Search developer categories A–Z") }
        )
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
                            dynamicStatus(entry.letter, entry.state, termuxAvailable, termuxStatus, runtimeStatus.name),
                            color = if (entry.state == CapabilityState.NOT_AVAILABLE) AppTheme.colors.accentRed else AppTheme.colors.textSecondary
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
            if (entries.isEmpty()) item { Text("No developer categories match ‘$query’.", color = AppTheme.colors.textSecondary) }
        }
    }
}

private fun dynamicStatus(
    letter: Char,
    state: CapabilityState,
    termuxAvailable: Boolean,
    termuxStatus: String,
    runtimeStatus: String
): String = when (letter) {
    'D', 'E', 'I', 'P', 'T', 'W' -> "${DeveloperCatalog.stateLabel(state)} · ${if (termuxAvailable) "$termuxStatus (package/backend availability; not isolated)" else termuxStatus}"
    'R', 'S' -> "${DeveloperCatalog.stateLabel(state)} · App-owned Linux runtime: $runtimeStatus. On Android 9+ PRoot command execution is disabled; Termux is a separate backend."
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
