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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opendroid.ai.core.termux.TermuxTask
import com.opendroid.ai.ui.theme.AppTheme
import com.opendroid.ai.ui.viewmodel.TermuxViewModel

@Composable
fun TermuxScreen(viewModel: TermuxViewModel, onBack: () -> Unit) {
    val installed by viewModel.installed.collectAsState()
    val status by viewModel.status.collectAsState()
    val result by viewModel.result.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TERMUX INTEGRATION", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        },
        containerColor = AppTheme.colors.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    if (installed) "Termux detected on this device." else "Termux is not installed. Normal app operation does not require it.",
                    color = if (installed) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "These are explicit, supported local tasks. The app does not accept or execute arbitrary command text silently. Cloud provider execution remains separate from this local Termux connection.",
                    color = AppTheme.colors.textSecondary
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.refresh() }, modifier = Modifier.weight(1f)) { Text("CHECK") }
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("DONE") }
                }
            }
            if (installed) {
                item { Text("LOCAL TASKS", fontWeight = FontWeight.Bold) }
                items(TermuxTask.entries.size) { index ->
                    val task = TermuxTask.entries[index]
                    OutlinedButton(onClick = { viewModel.run(task) }, modifier = Modifier.fillMaxWidth()) { Text(task.label) }
                }
            }
            if (status != null) item { Text(status!!, color = AppTheme.colors.textSecondary) }
            result?.let { output ->
                item {
                    Text("RESULT: ${output.task.label} (exit ${output.exitCode})", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(output.stdout.ifBlank { output.stderr.ifBlank { "No output." } }, color = AppTheme.colors.textSecondary)
                    if (output.stderr.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Error: ${output.stderr}", color = AppTheme.colors.accentRed)
                    }
                }
            }
        }
    }
}
