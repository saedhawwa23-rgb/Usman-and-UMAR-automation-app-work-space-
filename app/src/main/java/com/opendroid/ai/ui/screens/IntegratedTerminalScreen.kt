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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.opendroid.ai.ui.theme.AppTheme
import com.opendroid.ai.ui.viewmodel.LinuxTerminalViewModel

@Composable
fun IntegratedTerminalScreen(viewModel: LinuxTerminalViewModel, onBack: () -> Unit) {
    val output by viewModel.output.collectAsState()
    val message by viewModel.message.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val termuxAvailable by viewModel.termuxAvailable.collectAsState()
    val termuxStatus by viewModel.termuxStatus.collectAsState()
    val context = LocalContext.current
    var command by remember { mutableStateOf("echo USMAN_AND_UMAR_TERMINAL_OK") }
    var pendingCommand by remember { mutableStateOf<String?>(null) }
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
                title = { Text("TERMUX SHELL COMMANDS", fontWeight = FontWeight.Bold) },
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
                Text("TERMUX COMMAND MODE", fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                Text(
                    "Commands run through Termux's background RUN_COMMAND API using Termux's shell/environment. The app-owned Alpine/PRoot path is disabled on Android 9+. Termux output includes stdout, stderr, and an exit code when returned.",
                    color = AppTheme.colors.textSecondary
                )
            }
            item {
                Text(
                    "NOT A SECURITY SANDBOX: commands are not isolated from the Termux app environment, workspace/resource limits are not enforced here, and a timeout can stop waiting without proving that a child process stopped. Do not paste secrets. Review every command before confirming.",
                    color = AppTheme.colors.accentRed,
                    fontWeight = FontWeight.SemiBold
                )
            }
            item {
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Termux shell command") },
                    singleLine = true
                )
                Button(
                    onClick = { pendingCommand = command },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = termuxAvailable && command.isNotBlank()
                ) { Text("REVIEW & RUN IN TERMUX") }
                Text(termuxStatus, color = AppTheme.colors.textSecondary)
            }
            item {
                Text(
                    "Native app-private PRoot is disabled on Android 9+ because the OS seccomp policy terminates it with SIGSYS (exit 159). This screen does not run commands inside the app-owned Alpine root filesystem.",
                    color = AppTheme.colors.textSecondary
                )
                OutlinedButton(onClick = viewModel::clearOutput, modifier = Modifier.fillMaxWidth()) { Text("CLEAR OUTPUT") }
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

    pendingCommand?.let { candidate ->
        AlertDialog(
            onDismissRequest = { pendingCommand = null },
            title = { Text("Run command in Termux?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This is user confirmation, not sandbox isolation. The command will run in Termux's environment and may change files, use the network, or continue after a timeout.")
                    Text(candidate, fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingCommand = null
                    viewModel.runViaTermux(candidate)
                }) { Text("RUN COMMAND") }
            },
            dismissButton = { TextButton(onClick = { pendingCommand = null }) { Text("CANCEL") } }
        )
    }
}
