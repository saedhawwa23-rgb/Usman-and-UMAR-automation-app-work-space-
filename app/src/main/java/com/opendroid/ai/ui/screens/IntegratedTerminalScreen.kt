package com.opendroid.ai.ui.screens

import android.content.ClipData
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.opendroid.ai.core.linux.RuntimeStatus
import com.opendroid.ai.ui.theme.AppTheme
import com.opendroid.ai.ui.viewmodel.LinuxTerminalViewModel

@Composable
fun IntegratedTerminalScreen(viewModel: LinuxTerminalViewModel, onBack: () -> Unit) {
    val status by viewModel.status.collectAsState()
    val output by viewModel.output.collectAsState()
    val message by viewModel.message.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val termuxAvailable by viewModel.termuxAvailable.collectAsState()
    val termuxStatus by viewModel.termuxStatus.collectAsState()
    val context = LocalContext.current
    var command by remember { mutableStateOf("echo USMAN_AND_UMAR_TERMINAL_OK") }
    var showDiagnostics by remember { mutableStateOf(false) }
    var copyAfterRefresh by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
        viewModel.diagnostics()
    }
    LaunchedEffect(diagnostics, copyAfterRefresh) {
        if (copyAfterRefresh && diagnostics.isNotBlank()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("USMAN AND UMAR diagnostics", diagnostics))
            copyAfterRefresh = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("INTEGRATED LINUX TERMINAL", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        },
        containerColor = AppTheme.colors.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text("Backend: Alpine ARMv7 under Termux-maintained PRoot", color = AppTheme.colors.textSecondary) }
            item {
                Text(
                    when (status) {
                        RuntimeStatus.READY -> "READY: verified native-library PRoot and Alpine rootfs available"
                        RuntimeStatus.PROOT_MISSING -> "Alpine is installed, but native-library PRoot verification failed."
                        RuntimeStatus.NOT_INSTALLED -> "NOT INSTALLED: download Alpine on first use."
                    },
                    color = if (status == RuntimeStatus.READY) AppTheme.colors.textPrimary else AppTheme.colors.accentRed
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.install() }, modifier = Modifier.weight(1f), enabled = status != RuntimeStatus.READY) { Text("INSTALL / REPAIR") }
                    OutlinedButton(onClick = { viewModel.start() }, modifier = Modifier.weight(1f), enabled = status == RuntimeStatus.READY) { Text("START SHELL") }
                }
            }
            item {
                OutlinedTextField(command, { command = it }, Modifier.fillMaxWidth(), label = { Text("Command") }, singleLine = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.send(command) }, modifier = Modifier.weight(1f), enabled = status == RuntimeStatus.READY) { Text("RUN") }
                    OutlinedButton(onClick = viewModel::clearOutput, modifier = Modifier.weight(1f)) { Text("CLEAR") }
                }
            }
            item {
                Text("TERMUX COMMAND MODE", fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                Text(
                    "Fallback for Android seccomp failures such as PRoot exit 159. This runs one bounded command through Termux RUN_COMMAND and returns output/exit code; it is not an interactive PTY.",
                    color = AppTheme.colors.textSecondary
                )
                OutlinedButton(
                    onClick = { viewModel.runViaTermux(command) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = termuxAvailable
                ) { Text("RUN COMMAND VIA TERMUX") }
                Text(termuxStatus, color = AppTheme.colors.textSecondary)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDiagnostics = !showDiagnostics; viewModel.diagnostics() }, modifier = Modifier.weight(1f)) {
                        Text(if (showDiagnostics) "HIDE DIAGNOSTICS" else "DIAGNOSTICS")
                    }
                    OutlinedButton(onClick = { copyAfterRefresh = true; viewModel.diagnostics() }, modifier = Modifier.weight(1f)) {
                        Text("COPY DIAGNOSTICS")
                    }
                }
            }
            if (!message.isNullOrBlank()) item { Text(message!!, color = AppTheme.colors.textSecondary) }
            if (showDiagnostics && diagnostics.isNotBlank()) {
                item {
                    Text("On-device diagnostics", fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                    Text(diagnostics, color = AppTheme.colors.textSecondary)
                }
            }
            item { Text(output.ifBlank { "No command output yet." }, color = AppTheme.colors.textPrimary) }
        }
    }
}
