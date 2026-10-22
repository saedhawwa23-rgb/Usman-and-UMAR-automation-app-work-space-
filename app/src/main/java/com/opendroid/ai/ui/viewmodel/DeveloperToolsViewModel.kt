package com.opendroid.ai.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.opendroid.ai.core.agent.CodingAgentDescriptor
import com.opendroid.ai.core.agent.CodingAgentHealth
import com.opendroid.ai.core.agent.DeveloperAgentRegistry
import com.opendroid.ai.core.backend.BackendDescriptor
import com.opendroid.ai.core.backend.BackendHealth
import com.opendroid.ai.core.backend.SandboxBackendRegistry
import com.opendroid.ai.core.connector.CloudToolAgent
import com.opendroid.ai.core.connector.ConnectorKind
import com.opendroid.ai.core.connector.ConnectorRisk
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RegisteredDeveloperConnector(
    val id: String,
    val displayName: String,
    val kind: ConnectorKind,
    val toolCount: Int
)

data class RegisteredDeveloperTool(
    val qualifiedName: String,
    val connectorName: String,
    val description: String,
    val risk: ConnectorRisk
)

data class RegisteredDeveloperBackend(
    val descriptor: BackendDescriptor,
    val health: BackendHealth
)

data class RegisteredDeveloperAgent(
    val descriptor: CodingAgentDescriptor,
    val health: CodingAgentHealth
)

@HiltViewModel
class DeveloperToolsViewModel @Inject constructor(
    private val agent: CloudToolAgent,
    private val backendRegistry: SandboxBackendRegistry,
    private val codingAgentRegistry: DeveloperAgentRegistry
) : ViewModel() {
    private val _connectors = MutableStateFlow<List<RegisteredDeveloperConnector>>(emptyList())
    val connectors: StateFlow<List<RegisteredDeveloperConnector>> = _connectors.asStateFlow()
    private val _tools = MutableStateFlow<List<RegisteredDeveloperTool>>(emptyList())
    val tools: StateFlow<List<RegisteredDeveloperTool>> = _tools.asStateFlow()
    private val _backends = MutableStateFlow<List<RegisteredDeveloperBackend>>(emptyList())
    val backends: StateFlow<List<RegisteredDeveloperBackend>> = _backends.asStateFlow()
    private val _codingAgents = MutableStateFlow<List<RegisteredDeveloperAgent>>(emptyList())
    val codingAgents: StateFlow<List<RegisteredDeveloperAgent>> = _codingAgents.asStateFlow()

    init { refresh() }

    /** Refresh reads the in-memory registry only; it does not contact a provider or execute a tool. */
    fun refresh() {
        val registered = agent.registeredConnectors()
        _connectors.value = registered.map { RegisteredDeveloperConnector(it.id, it.displayName, it.kind, it.tools().size) }
        _tools.value = registered.flatMap { connector ->
            connector.tools().map { tool ->
                RegisteredDeveloperTool(tool.qualifiedName, connector.displayName, tool.description, tool.risk)
            }
        }
        _backends.value = backendRegistry.healthSnapshot().map { (descriptor, health) ->
            RegisteredDeveloperBackend(descriptor, health)
        }
        _codingAgents.value = codingAgentRegistry.healthSnapshot().map { (descriptor, health) ->
            RegisteredDeveloperAgent(descriptor, health)
        }
    }

    fun searchTools(query: String): List<RegisteredDeveloperTool> {
        val term = query.trim()
        return _tools.value.filter { tool ->
            term.isEmpty() || tool.qualifiedName.contains(term, ignoreCase = true) ||
                tool.connectorName.contains(term, ignoreCase = true) ||
                tool.description.contains(term, ignoreCase = true)
        }
    }

    fun searchBackends(query: String): List<RegisteredDeveloperBackend> {
        val term = query.trim()
        return _backends.value.filter { backend ->
            term.isEmpty() || backend.descriptor.name.contains(term, ignoreCase = true) ||
                backend.health.detail.contains(term, ignoreCase = true) ||
                backend.descriptor.securityBoundary.contains(term, ignoreCase = true) ||
                backend.descriptor.limitations.any { it.contains(term, ignoreCase = true) }
        }
    }

    fun searchCodingAgents(query: String): List<RegisteredDeveloperAgent> {
        val term = query.trim()
        return _codingAgents.value.filter { candidate ->
            term.isEmpty() || candidate.descriptor.name.contains(term, ignoreCase = true) ||
                candidate.descriptor.id.contains(term, ignoreCase = true) ||
                candidate.descriptor.version.orEmpty().contains(term, ignoreCase = true) ||
                candidate.descriptor.license.orEmpty().contains(term, ignoreCase = true) ||
                candidate.descriptor.upstreamUrl.contains(term, ignoreCase = true) ||
                candidate.descriptor.documentationUrl.contains(term, ignoreCase = true) ||
                candidate.health.detail.contains(term, ignoreCase = true) ||
                candidate.descriptor.limitations.any { it.contains(term, ignoreCase = true) }
        }
    }
}
