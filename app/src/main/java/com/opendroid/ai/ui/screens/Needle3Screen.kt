package com.opendroid.ai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opendroid.ai.core.needle.Needle3LocalRuntime
import com.opendroid.ai.core.needle.Needle3Status
import com.opendroid.ai.ui.theme.AppTheme
import com.opendroid.ai.ui.viewmodel.Needle3ViewModel

@Composable
fun Needle3Screen(viewModel: Needle3ViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val diagnostic by viewModel.diagnostic.collectAsStateWithLifecycle()
    val currentStatus = status
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.loadModel()
    }

    LaunchedEffect(currentStatus) {
        if (currentStatus is Needle3Status.PermissionRequired &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("QWEN 3 LOCAL", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(
            "On-device Qwen 3 tool calling via llama.cpp. No Gemini, cloud model, or API key is used.",
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
                        Text(Needle3LocalRuntime.MODEL_MISSING_MESSAGE, color = AppTheme.colors.textSecondary)
                        Text(
                            "Expected file: ${Needle3LocalRuntime.MODEL_FILE_PATH} (484 MB)",
                            color = AppTheme.colors.textSecondary
                        )
                    }
                    Needle3Status.PermissionRequired -> Text(
                        "Allow file access so Qwen 3 can read ${Needle3LocalRuntime.MODEL_FILE_PATH}.",
                        color = AppTheme.colors.textSecondary
                    )
                    Needle3Status.Loading -> Text(
                        "Checking ${Needle3LocalRuntime.MODEL_FILE_PATH}…",
                        color = AppTheme.colors.textSecondary
                    )
                    Needle3Status.Verifying -> Text("Verifying model size and SHA-256…", color = AppTheme.colors.textSecondary)
                    Needle3Status.Initializing -> Text("Initializing llama.cpp ARM runtime…", color = AppTheme.colors.textSecondary)
                    Needle3Status.Ready -> {
                        Text("Qwen 3 model loaded and ready for local action selection.", color = AppTheme.colors.textSecondary)
                        Button(onClick = viewModel::runDiagnostic) { Text("RUN LOCAL TOOL DIAGNOSTIC") }
                    }
                    is Needle3Status.Error -> Text(currentStatus.message, color = AppTheme.colors.textSecondary)
                    is Needle3Status.Unsupported -> Text(currentStatus.message, color = AppTheme.colors.textSecondary)
                }

                if (currentStatus !is Needle3Status.Ready &&
                    currentStatus !is Needle3Status.Loading &&
                    currentStatus !is Needle3Status.Verifying &&
                    currentStatus !is Needle3Status.Initializing &&
                    currentStatus !is Needle3Status.Unsupported
                ) {
                    Button(
                        onClick = {
                            if (currentStatus is Needle3Status.PermissionRequired &&
                                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                            ) {
                                storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                            } else {
                                viewModel.loadModel()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            when (currentStatus) {
                                Needle3Status.NotInstalled -> "CHECK MODEL"
                                Needle3Status.PermissionRequired -> "ALLOW FILE ACCESS"
                                else -> "RETRY MODEL CHECK"
                            }
                        )
                    }
                }
            }
        }
        diagnostic?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = AppTheme.colors.accentCyan, fontSize = 13.sp)
        }
        Text(
            "Safety: Qwen 3 only selects schemas already registered by USMAN AND UMAR. The existing approval policy and ActionDispatcher remain authoritative before device actions run.",
            color = AppTheme.colors.textSecondary,
            fontSize = 12.sp
        )
    }
}

private fun Needle3Status.label(): String = when (this) {
    Needle3Status.NotInstalled -> "Model Not Found"
    Needle3Status.PermissionRequired -> "File Access Required"
    Needle3Status.Loading -> "Checking Model"
    Needle3Status.Verifying -> "Verifying"
    Needle3Status.Initializing -> "Initializing"
    Needle3Status.Ready -> "Ready"
    is Needle3Status.Error -> "Error"
    is Needle3Status.Unsupported -> "Unsupported"
}
