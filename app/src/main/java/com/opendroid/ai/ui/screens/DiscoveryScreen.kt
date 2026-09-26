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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opendroid.ai.data.models.DynamicAIProvider
import com.opendroid.ai.data.models.GitHubProject
import com.opendroid.ai.ui.theme.AppTheme
import com.opendroid.ai.ui.viewmodel.DiscoveryViewModel

@Composable
fun DiscoveryScreen(
    viewModel: DiscoveryViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val githubResults by viewModel.githubResults.collectAsState()
    val aiResults by viewModel.aiResults.collectAsState()
    val savedProjects by viewModel.savedProjects.collectAsState()
    val savedProviders by viewModel.savedProviders.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var directUrl by remember { mutableStateOf("") }
    var selectedAI by remember { mutableStateOf<GitHubProject?>(null) }
    var endpoint by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.refreshSaved() }

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
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("SEARCH AI") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("SEARCH GITHUB") })
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(if (tab == 0) "Search AI providers or models" else "Search GitHub projects") },
                        leadingIcon = { Icon(Icons.Default.Search, null) }
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { if (tab == 0) viewModel.searchAI(query) else viewModel.searchGitHub(query) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "SEARCHING..." else "SEARCH") }
                    if (message != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(message!!, color = AppTheme.colors.textSecondary)
                    }
                }

                if (tab == 0) {
                    item {
                        Text("Select a discovery result, then configure its endpoint, model, and API key before adding it.", color = AppTheme.colors.textSecondary)
                    }
                    items(aiResults, key = { it.id }) { project ->
                        DiscoveryProjectCard(project, onOpen = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, project.htmlUrl.toUri()))
                        }, onSelect = {
                            selectedAI = project
                            endpoint = "https://api.openai.com/v1"
                            model = project.fullName.substringAfter('/').ifBlank { "model-id" }
                        }, onAdd = { viewModel.addProject(project) })
                    }
                    selectedAI?.let { project ->
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("CONFIGURE AI PROVIDER", fontWeight = FontWeight.Bold)
                                    Text(project.fullName)
                                    OutlinedTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(), label = { Text("OpenAI-compatible endpoint") }, singleLine = true)
                                    OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("Model ID") }, singleLine = true)
                                    OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(), label = { Text("API key") }, singleLine = true)
                                    Button(onClick = {
                                        viewModel.addProvider(DynamicAIProvider("discovered:${project.fullName}", project.fullName, endpoint, model, project.description, project.htmlUrl), apiKey)
                                        selectedAI = null
                                        apiKey = ""
                                    }, modifier = Modifier.fillMaxWidth()) { Text("ADD AND USE") }
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
                } else {
                    item {
                        OutlinedTextField(directUrl, { directUrl = it }, Modifier.fillMaxWidth(), label = { Text("Direct GitHub URL or owner/repository") }, singleLine = true)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { viewModel.openRepository(directUrl) }, enabled = directUrl.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("OPEN / ANALYZE REPOSITORY") }
                    }
                    items(githubResults, key = { it.id }) { project ->
                        DiscoveryProjectCard(project, onOpen = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, project.htmlUrl.toUri()))
                        }, onSelect = {}, onAdd = { viewModel.addProject(project) }, onUse = { viewModel.useProject(project) }, showSelect = false)
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
            }
        }
    }
}

@Composable
private fun DiscoveryProjectCard(
    project: GitHubProject,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onAdd: () -> Unit,
    onUse: () -> Unit = {},
    showSelect: Boolean = true
) {
    Card(colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(project.fullName, fontWeight = FontWeight.Bold)
            Text(project.description.ifBlank { "No description provided." }, color = AppTheme.colors.textSecondary)
            Text("★ ${project.stars}${project.language?.let { " • $it" } ?: ""}", color = AppTheme.colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpen) { Text("OPEN") }
                OutlinedButton(onClick = onAdd) { Text("ADD") }
                OutlinedButton(onClick = onUse) { Text("USE") }
                if (showSelect) OutlinedButton(onClick = onSelect) { Text("SELECT") }
            }
        }
    }
}
