package com.opendroid.ai.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opendroid.ai.BuildConfig
import com.opendroid.ai.core.backend.BackendCapabilities
import com.opendroid.ai.core.backend.BackendKind
import com.opendroid.ai.core.backend.BackendStatus
import com.opendroid.ai.core.llm.ModelFetchOutcome
import com.opendroid.ai.data.models.GitHubProject
import com.opendroid.ai.ui.theme.AppTheme
import com.opendroid.ai.ui.viewmodel.DeveloperToolsViewModel
import com.opendroid.ai.ui.viewmodel.DiscoveryViewModel

@Composable
fun DiscoveryScreen(
    viewModel: DiscoveryViewModel,
    initialTab: Int = 0,
    onBack: () -> Unit,
    toolCatalogViewModel: DeveloperToolsViewModel = hiltViewModel(),
    onOpenToolAgent: () -> Unit = {},
    onOpenWorkspaceFiles: () -> Unit = {},
    onOpenTermuxControls: () -> Unit = {},
    onOpenTerminal: () -> Unit = {}
) {
    val context = LocalContext.current
    val githubResults by viewModel.githubResults.collectAsState()
    val aiResults by viewModel.aiResults.collectAsState()
    val savedProjects by viewModel.savedProjects.collectAsState()
    val openedRepository by viewModel.openedRepository.collectAsState()
    val savedProviders by viewModel.savedProviders.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val registeredTools by toolCatalogViewModel.tools.collectAsState()
    val registeredConnectors by toolCatalogViewModel.connectors.collectAsState()
    val backends by toolCatalogViewModel.backends.collectAsState()
    val codingAgents by toolCatalogViewModel.codingAgents.collectAsState()
    var tab by remember { mutableIntStateOf(initialTab.coerceIn(0, 4)) }
    var query by remember { mutableStateOf("") }
    val developerMatches = remember(query) { DeveloperCatalog.search(query) }
    val liveToolMatches = remember(query, registeredTools) { toolCatalogViewModel.searchTools(query) }
    val liveBackendMatches = remember(query, backends) { toolCatalogViewModel.searchBackends(query) }
    val liveCodingAgentMatches = remember(query, codingAgents) { toolCatalogViewModel.searchCodingAgents(query) }

    LaunchedEffect(Unit) { viewModel.refreshSaved(); toolCatalogViewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DISCOVER EXTENSIONS", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        },
        containerColor = AppTheme.colors.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("AI / MODELS") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("SANDBOX") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("DEV TOOLS") })
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("GITHUB") })
                Tab(selected = tab == 4, onClick = { tab = 4 }, text = { Text("UPDATES") })
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(when (tab) {
                            0 -> "Provider name or model ID"
                            1 -> "Search sandbox backends and health"
                            2 -> "Search tools and A–Z capabilities"
                            3 -> "Search GitHub or enter owner/repository"
                            else -> "Search update information"
                        }) },
                        leadingIcon = { Icon(Icons.Default.Search, null) }
                    )
                    Spacer(Modifier.height(8.dp))
                    if (tab == 0 || tab == 3) {
                        Button(
                            onClick = {
                                if (tab == 0) viewModel.searchAI(query)
                                else if (isDirectGitHubLookup(query)) viewModel.openRepository(query)
                                else viewModel.searchGitHub(query)
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (busy) "SEARCHING LIVE ENDPOINTS…" else "SEARCH") }
                    } else {
                        Text(
                            when (tab) {
                                1 -> "Sandbox search reads the same live backend registry as Developers. It reports actual local health and security boundaries; it does not execute a task."
                                2 -> "Developer Tools searches the A–Z capability catalog and this app process's registered connectors. Searching does not call a provider or execute a tool."
                                else -> "Update information is currently unavailable: this app has no configured release manifest, update channel, signature-verification/install flow, or automatic update check."
                            },
                            color = AppTheme.colors.textSecondary
                        )
                        if (tab == 1) {
                            OutlinedButton(onClick = { toolCatalogViewModel.refresh() }, modifier = Modifier.fillMaxWidth()) {
                                Text("REFRESH BACKEND HEALTH")
                            }
                        }
                    }
                    if (message != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(message!!, color = AppTheme.colors.textSecondary)
                    }
                }

                when (tab) {
                    0 -> {
                        item {
                            Text(
                                "Models below are returned by the provider's live catalog endpoint. This does not by itself verify chat completion or tool calling. Configure credentials/endpoints in Settings where required.",
                                color = AppTheme.colors.textSecondary
                            )
                        }
                        items(aiResults, key = { it.provider }) { providerResult ->
                            Card(colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(providerResult.provider, fontWeight = FontWeight.Bold)
                                    when (val outcome = providerResult.outcome) {
                                        is ModelFetchOutcome.Success -> {
                                            if (outcome.models.isEmpty()) Text("The provider returned no chat-capable models.", color = AppTheme.colors.textSecondary)
                                            outcome.models.take(30).forEach { liveModel ->
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(liveModel.displayName)
                                                        Text(liveModel.id, color = AppTheme.colors.textSecondary)
                                                        liveModel.contextWindow?.let { Text("Context: $it tokens", color = AppTheme.colors.textSecondary) }
                                                    }
                                                    OutlinedButton(onClick = { viewModel.selectModel(liveModel) }) { Text("USE") }
                                                }
                                            }
                                        }
                                        is ModelFetchOutcome.NeedsCredentials -> Text(outcome.message, color = AppTheme.colors.textSecondary)
                                        is ModelFetchOutcome.Failed -> Text(outcome.message, color = AppTheme.colors.accentRed)
                                    }
                                }
                            }
                        }
                        if (savedProviders.isNotEmpty()) {
                            item { Text("ADDED AI PROVIDERS", fontWeight = FontWeight.Bold) }
                            items(savedProviders, key = { it.id }) { provider ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) { Text(provider.displayName); Text(provider.model, color = AppTheme.colors.textSecondary) }
                                    OutlinedButton(onClick = { viewModel.useProvider(provider) }) { Text("USE") }
                                }
                            }
                        }
                    }
                    1 -> {
                        item {
                            Text("SANDBOX BACKENDS · ${backends.size}", fontWeight = FontWeight.Bold)
                            Text("Health checks do not run commands. Local command approval is not process isolation, and timeout does not guarantee process cancellation.", color = AppTheme.colors.textSecondary)
                        }
                        if (liveBackendMatches.isEmpty()) item { Text("No sandbox backends match ‘$query’.", color = AppTheme.colors.textSecondary) }
                        items(liveBackendMatches, key = { it.descriptor.id }) { backend ->
                            SandboxBackendCard(
                                descriptorName = backend.descriptor.name,
                                kind = backend.descriptor.kind,
                                status = backend.health.status,
                                detail = backend.health.detail,
                                installedVersion = backend.health.installedVersion,
                                securityBoundary = backend.descriptor.securityBoundary,
                                limitations = backend.descriptor.limitations,
                                capabilities = backend.descriptor.capabilities,
                                onOpenWorkspace = onOpenWorkspaceFiles,
                                onOpenTermux = onOpenTermuxControls,
                                onOpenTerminal = onOpenTerminal
                            )
                        }
                        if (backends.none { it.descriptor.kind == BackendKind.REMOTE_CLOUD }) {
                            item { Text("No remote cloud sandbox provider is configured. Provider adapters require an explicit account/API setup and verified pricing, data-transfer, runtime limits, retention, and cancellation semantics.", color = AppTheme.colors.textSecondary) }
                        }
                    }
                    2 -> {
                        item {
                            Text("LIVE REGISTERED CONNECTORS · ${registeredConnectors.size}", fontWeight = FontWeight.Bold)
                            Text("Read-only snapshot of this app process's tool registry. Searching does not call a provider or execute a tool.", color = AppTheme.colors.textSecondary)
                        }
                        if (liveToolMatches.isEmpty()) item { Text("No currently registered tools match ‘$query’.", color = AppTheme.colors.textSecondary) }
                        items(liveToolMatches, key = { it.qualifiedName }) { tool ->
                            Card(colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface)) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(tool.qualifiedName, fontWeight = FontWeight.Bold)
                                    Text("Connector: ${tool.connectorName} · Risk: ${tool.risk.name}", color = AppTheme.colors.accentCyan)
                                    Text(tool.description, color = AppTheme.colors.textSecondary)
                                    OutlinedButton(onClick = onOpenToolAgent, modifier = Modifier.fillMaxWidth()) { Text("OPEN CLOUD TOOL AGENT") }
                                }
                            }
                        }
                        item {
                            Text("REGISTERED CODING-AGENT ADAPTERS · ${codingAgents.size}", fontWeight = FontWeight.Bold)
                            Text("Only concrete adapters registered with verified metadata appear here. Candidate research and the existing cloud tool-agent are not treated as autonomous coding agents.", color = AppTheme.colors.textSecondary)
                        }
                        if (liveCodingAgentMatches.isEmpty()) {
                            item {
                                Text(
                                    if (codingAgents.isEmpty()) "No compatible coding-agent adapters currently registered; no external coding agent is installed or verified."
                                    else "No registered coding-agent adapters match ‘$query’.",
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                        }
                        items(liveCodingAgentMatches, key = { "coding-agent:${it.descriptor.id}" }) { candidate ->
                            Card(colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface)) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(candidate.descriptor.name, fontWeight = FontWeight.Bold)
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
                        item { Text("A–Z CAPABILITY GUIDE", fontWeight = FontWeight.Bold) }
                        if (developerMatches.isEmpty()) item { Text("No developer categories match ‘$query’.", color = AppTheme.colors.textSecondary) }
                        items(developerMatches, key = { it.letter }) { capability ->
                            Card(colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface)) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("${capability.letter} — ${capability.title}", fontWeight = FontWeight.Bold)
                                    Text(capability.description, color = AppTheme.colors.textSecondary)
                                    Text(DeveloperCatalog.stateLabel(capability.state), color = if (capability.state == CapabilityState.NOT_AVAILABLE) AppTheme.colors.accentRed else AppTheme.colors.accentCyan)
                                }
                            }
                        }
                    }
                    3 -> {
                        item { Text("Keyword searches and direct repository lookups use the public GitHub REST API. Private repositories require the authenticated GitHub Workspace. Clone uses a shallow public clone into Termux home; it is not full-history import into the app workspace.", color = AppTheme.colors.textSecondary) }
                        openedRepository?.let { project ->
                            item {
                                Text("LOOKUP RESULT (NOT SAVED)", fontWeight = FontWeight.Bold)
                                DiscoveryProjectCard(project, onOpen = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, project.htmlUrl.toUri()))
                                }, onAdd = { viewModel.addProject(project) }, onUse = { viewModel.useProject(project) }, onClone = { viewModel.cloneRepository(project.cloneUrl) })
                                Text("Clone runs in Termux's home directory. It is not imported into an app-managed sandbox, and this screen does not provide project editing or automatic opening.", color = AppTheme.colors.textSecondary)
                                if (message != null) Text(message!!, color = AppTheme.colors.textSecondary)
                            }
                        }
                        items(githubResults, key = { it.id }) { project ->
                            DiscoveryProjectCard(project, onOpen = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, project.htmlUrl.toUri()))
                            }, onAdd = { viewModel.addProject(project) }, onUse = { viewModel.useProject(project) }, onClone = { viewModel.cloneRepository(project.cloneUrl) })
                        }
                        item {
                            Text("SUPPORTED PROJECTS", fontWeight = FontWeight.Bold)
                            savedProjects.forEach { project ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(project.fullName, Modifier.weight(1f))
                                    IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, project.htmlUrl.toUri())) }) { Icon(Icons.Default.OpenInNew, "Open") }
                                }
                            }
                        }
                    }
                    else -> {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("CURRENT BUILD", fontWeight = FontWeight.Bold)
                                    Text("Version: ${BuildConfig.VERSION_NAME}", color = AppTheme.colors.textSecondary)
                                    Text("No update channel is configured. No background check, signed manifest verification, staged rollout, download, or in-app installer is implemented. Update only through the app's trusted distribution source.", color = AppTheme.colors.textSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SandboxBackendCard(
    descriptorName: String,
    kind: BackendKind,
    status: BackendStatus,
    detail: String,
    installedVersion: String?,
    securityBoundary: String,
    limitations: List<String>,
    capabilities: BackendCapabilities,
    onOpenWorkspace: () -> Unit,
    onOpenTermux: () -> Unit,
    onOpenTerminal: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(descriptorName, fontWeight = FontWeight.Bold)
            Text("${status.name.replace('_', ' ')} · ${kind.name.replace('_', ' ')}", color = if (status == BackendStatus.INCOMPATIBLE) AppTheme.colors.accentRed else AppTheme.colors.accentCyan)
            installedVersion?.let { Text("Installed version: $it", color = AppTheme.colors.textSecondary) }
            Text(detail, color = AppTheme.colors.textSecondary)
            Text(securityBoundary, color = AppTheme.colors.textSecondary)
            Text("Operations: ${backendOperationSummary(capabilities)}", color = AppTheme.colors.textSecondary)
            limitations.forEach { Text("• $it", color = AppTheme.colors.textSecondary) }
            when (kind) {
                BackendKind.APP_WORKSPACE -> OutlinedButton(onClick = onOpenWorkspace, modifier = Modifier.fillMaxWidth()) { Text("OPEN APP WORKSPACE") }
                BackendKind.TERMUX -> {
                    OutlinedButton(onClick = onOpenTermux, modifier = Modifier.fillMaxWidth()) { Text("OPEN TERMUX CONTROLS") }
                    OutlinedButton(onClick = onOpenTerminal, modifier = Modifier.fillMaxWidth()) { Text("OPEN TERMUX COMMAND SCREEN") }
                }
                else -> Unit
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

private fun isDirectGitHubLookup(query: String): Boolean {
    val value = query.trim()
    return value.startsWith("https://github.com/", ignoreCase = true) ||
        value.matches(Regex("[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}"))
}

@Composable
private fun DiscoveryProjectCard(
    project: GitHubProject,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    onUse: () -> Unit,
    onClone: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(project.fullName, fontWeight = FontWeight.Bold)
            Text(project.description.ifBlank { "No description provided." }, color = AppTheme.colors.textSecondary)
            Text("★ ${project.stars}${project.language?.let { " • $it" } ?: ""}", color = AppTheme.colors.textSecondary)
            if (project.defaultBranch.isNotBlank()) Text("Default branch: ${project.defaultBranch} · Forks: ${project.forks} · Open issues: ${project.openIssues}", color = AppTheme.colors.textSecondary)
            if (project.rootFiles.isNotEmpty()) Text("Root files: ${project.rootFiles.joinToString()}", color = AppTheme.colors.textSecondary)
            if (project.readme.isNotBlank()) Text("README preview:\n${project.readme.take(2_500)}", color = AppTheme.colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpen) { Text("OPEN") }
                OutlinedButton(onClick = onAdd) { Text("ADD") }
                OutlinedButton(onClick = onUse) { Text("USE") }
            }
            OutlinedButton(onClick = onClone, modifier = Modifier.fillMaxWidth()) { Text("START PUBLIC SHALLOW CLONE IN TERMUX") }
        }
    }
}
