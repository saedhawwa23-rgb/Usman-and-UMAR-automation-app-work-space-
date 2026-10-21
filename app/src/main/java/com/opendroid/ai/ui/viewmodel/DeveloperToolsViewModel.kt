package com.opendroid.ai.ui.viewmodel

import androidx.lifecycle.ViewModel
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

@HiltViewModel
class DeveloperToolsViewModel @Inject constructor(
    private val agent: CloudToolAgent
) : ViewModel() {
    private val _connectors = MutableStateFlow<List<RegisteredDeveloperConnector>>(emptyList())
    val connectors: StateFlow<List<RegisteredDeveloperConnector>> = _connectors.asStateFlow()
    private val _tools = MutableStateFlow<List<RegisteredDeveloperTool>>(emptyList())
    val tools: StateFlow<List<RegisteredDeveloperTool>> = _tools.asStateFlow()

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
    }
}
