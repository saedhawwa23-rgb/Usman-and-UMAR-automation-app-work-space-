package com.opendroid.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opendroid.ai.core.needle.Needle3Status
import com.opendroid.ai.ui.theme.AppTheme
import com.opendroid.ai.ui.viewmodel.Needle3ViewModel

@Composable
fun Needle3Screen(viewModel: Needle3ViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val diagnostic by viewModel.diagnostic.collectAsStateWithLifecycle()
    val currentStatus = status
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
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
                    Needle3Status.NotInstalled -> {
                        Text("The official Needle 3 model will be downloaded once into private app storage and verified by SHA-256.", color = AppTheme.colors.textSecondary)
                        Button(onClick = viewModel::install) { Text("INSTALL LOCALLY") }
                    }
                    Needle3Status.Loading -> Text("Loading the verified model and structured tool schemas…", color = AppTheme.colors.textSecondary)
                    Needle3Status.Ready -> {
                        Text("Model loaded and ready for local action selection.", color = AppTheme.colors.textSecondary)
                        Button(onClick = viewModel::runDiagnostic) { Text("RUN LOCAL TOOL DIAGNOSTIC") }
                    }
                    is Needle3Status.Error -> {
                        Text(currentStatus.message, color = AppTheme.colors.textSecondary)
                        Button(onClick = viewModel::install) { Text("RETRY") }
                    }
                    is Needle3Status.Unsupported -> Text(currentStatus.message, color = AppTheme.colors.textSecondary)
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
    Needle3Status.Ready -> "Ready"
    is Needle3Status.Error -> "Error"
    is Needle3Status.Unsupported -> "Unsupported"
}
