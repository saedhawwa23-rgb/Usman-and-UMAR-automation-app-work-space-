package com.opendroid.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opendroid.ai.core.needle.Needle3LocalRuntime
import com.opendroid.ai.core.needle.Needle3Status
import com.opendroid.ai.ui.theme.AppTheme
import com.opendroid.ai.ui.viewmodel.Needle3ViewModel

@Composable
fun Needle3Screen(viewModel: Needle3ViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val diagnostic by viewModel.diagnostic.collectAsStateWithLifecycle()
    val currentStatus = status
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("NEEDLE 3 LOCAL", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(
            "On-device structured tool calling. No Gemini, cloud model, or API key is used.",
            color = AppTheme.colors.textSecondary,
            fontSize = 13.sp
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Status: ${currentStatus.label()}", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                when (currentStatus) {
                    Needle3Status.NotInstalled -> Text(
                        "The verified Needle 3 model is bundled with this app and will be installed into private app storage.",
                        color = AppTheme.colors.textSecondary
                    )
                    Needle3Status.Loading -> Text("Preparing the bundled Needle 3 model…", color = AppTheme.colors.textSecondary)
                    is Needle3Status.Downloading -> {
                        val progress = (currentStatus.bytesCopied.toFloat() / Needle3LocalRuntime.MODEL_SIZE).coerceIn(0f, 1f)
                        Text(
                            "Installing bundled model: ${currentStatus.bytesCopied / 1_000_000} / 35.3 MB",
                            color = AppTheme.colors.textSecondary
                        )
                        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                    }
                    Needle3Status.Verifying -> Text("Verifying model size and SHA-256…", color = AppTheme.colors.textSecondary)
                    Needle3Status.Initializing -> Text("Initializing ARM native runtime and tool schema…", color = AppTheme.colors.textSecondary)
                    Needle3Status.Ready -> {
                        Text("Model loaded and ready for local action selection.", color = AppTheme.colors.textSecondary)
                        Button(onClick = viewModel::runDiagnostic) { Text("RUN LOCAL TOOL DIAGNOSTIC") }
                    }
                    is Needle3Status.Error -> Text(currentStatus.message, color = AppTheme.colors.textSecondary)
                    is Needle3Status.Unsupported -> Text(currentStatus.message, color = AppTheme.colors.textSecondary)
                }

                // Keep an actionable recovery control visible for every non-ready state,
                // including unsupported/runtime-error states that hid the old install button.
                if (currentStatus !is Needle3Status.Ready && currentStatus !is Needle3Status.Loading &&
                    currentStatus !is Needle3Status.Downloading && currentStatus !is Needle3Status.Verifying &&
                    currentStatus !is Needle3Status.Initializing
                ) {
                    Button(onClick = viewModel::install, modifier = Modifier.fillMaxWidth()) {
                        Text("INSTALL LOCALLY")
                    }
                }
            }
        }
        diagnostic?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = AppTheme.colors.accentCyan, fontSize = 13.sp)
        }
        Text(
            "Safety: Needle 3 only selects schemas already registered by USMAN AND UMAR. The existing approval policy and ActionDispatcher remain authoritative before device actions run.",
            color = AppTheme.colors.textSecondary,
            fontSize = 12.sp
        )
    }
}

private fun Needle3Status.label(): String = when (this) {
    Needle3Status.NotInstalled -> "Not Installed"
    Needle3Status.Loading -> "Loading"
    is Needle3Status.Downloading -> "Installing bundled model"
    Needle3Status.Verifying -> "Verifying"
    Needle3Status.Initializing -> "Initializing"
    Needle3Status.Ready -> "Ready"
    is Needle3Status.Error -> "Error"
    is Needle3Status.Unsupported -> "Unsupported"
}
