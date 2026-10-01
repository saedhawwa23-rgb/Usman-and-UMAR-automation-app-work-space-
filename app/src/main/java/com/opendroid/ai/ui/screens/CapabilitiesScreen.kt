package com.opendroid.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendroid.ai.ui.theme.AppTheme

/**
 * The user-facing entry point for capabilities that were previously buried in Settings.
 * Each action opens the existing, fully scoped feature screen.
 */
@Composable
fun CapabilitiesScreen(
    onSearchAI: () -> Unit,
    onSearchGitHub: () -> Unit,
    onTermux: () -> Unit,
    onNeedle3: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "TOOLS & CAPABILITIES",
            color = AppTheme.colors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
        Text(
            text = "Discover providers, add GitHub projects, and run explicitly allowlisted local Termux tasks.",
            color = AppTheme.colors.textSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(4.dp))
        CapabilityCard(
            title = "SEARCH AI PROVIDERS",
            description = "Search live provider and model projects, configure an endpoint, and add a provider dynamically.",
            icon = Icons.Default.Search,
            onClick = onSearchAI
        )
        CapabilityCard(
            title = "SEARCH GITHUB PROJECTS",
            description = "Search GitHub or analyze a repository URL, then add it to the active project context.",
            icon = Icons.Default.Code,
            onClick = onSearchGitHub
        )
        CapabilityCard(
            title = "TERMUX INTEGRATION",
            description = "Check Termux and run only the supported allowlisted tasks with visible results.",
            icon = Icons.Default.Terminal,
            onClick = onTermux
        )
        CapabilityCard(
            title = "NEEDLE 3 LOCAL",
            description = "Load Needle 3 on-device and run structured tool calls without Gemini or a cloud API.",
            icon = Icons.Default.Extension,
            onClick = onNeedle3
        )
    }
}

@Composable
private fun CapabilityCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = AppTheme.colors.accentCyan)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(description, color = AppTheme.colors.textSecondary, fontSize = 12.sp)
            }
            TextButton(onClick = onClick) { Text("OPEN") }
        }
    }
}
