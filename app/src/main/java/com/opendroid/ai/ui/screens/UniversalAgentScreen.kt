package com.opendroid.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opendroid.ai.ui.theme.AppTheme
import com.opendroid.ai.ui.viewmodel.UniversalAgentViewModel

@Composable
fun UniversalAgentScreen(viewModel: UniversalAgentViewModel = hiltViewModel()) {
    val events by viewModel.events.collectAsState()
    val running by viewModel.running.collectAsState()
    var goal by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("UNIVERSAL LOCAL AGENT", color = AppTheme.colors.textPrimary, fontSize = 22.sp)
        Text(
            "Needle 3 plans locally. Connectors discover tools, enforce authorization, and return redacted results.",
            color = AppTheme.colors.textSecondary
        )
        OutlinedTextField(
            value = goal,
            onValueChange = { goal = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Describe a task") },
            placeholder = { Text("Deploy my project and check the logs") }
        )
        Button(onClick = { viewModel.run(goal) }, enabled = goal.isNotBlank() && !running) {
            Text(if (running) "RUNNING LOCALLY…" else "RUN WITH NEEDLE 3")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(events) { event ->
                Text("${event.type.uppercase()}: ${event.message}", color = AppTheme.colors.textSecondary, fontSize = 13.sp)
            }
        }
    }
}
