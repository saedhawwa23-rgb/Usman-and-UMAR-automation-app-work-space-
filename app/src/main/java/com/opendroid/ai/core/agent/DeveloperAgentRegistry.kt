package com.opendroid.ai.core.agent

import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

enum class AgentAvailability {
    AVAILABLE,
    COMPATIBLE,
    INSTALLABLE,
    INSTALLED,
    VERIFIED_WORKING,
    REQUIRES_CONFIGURATION,
    INCOMPATIBLE,
    NOT_TESTED
}

enum class AgentFeature {
    TASK_PLANNING,
    DURABLE_TASK_STATE,
    READ_PROJECT_FILES,
    WRITE_PROJECT_FILES,
    EXECUTE_APPROVED_COMMANDS,
    GIT_AND_GITHUB,
    RUN_TESTS,
    CONFIGURED_MODEL_PROVIDER,
    DOCUMENTED_PLUGINS,
    PAUSE_AND_RESUME,
    CANCELLATION,
    RECOVERY
}

data class AgentCapabilities(val features: Set<AgentFeature> = emptySet())

data class CodingAgentDescriptor(
    val id: String,
    val name: String,
    val version: String?,
    val license: String?,
    val upstreamUrl: String,
    val documentationUrl: String,
    val capabilities: AgentCapabilities = AgentCapabilities(),
    val limitations: List<String> = emptyList()
)

data class CodingAgentHealth(
    val availability: AgentAvailability,
    val detail: String,
    val checkedAtMs: Long = System.currentTimeMillis(),
    val installedVersion: String? = null
)

data class AgentTaskRequest(
    val prompt: String,
    val workspaceReference: String? = null,
    val backendId: String? = null
)

enum class AgentTaskStatus { QUEUED, RUNNING, NEEDS_APPROVAL, COMPLETED, FAILED, CANCELLED, UNKNOWN }

data class AgentTaskHandle(val id: String, val status: AgentTaskStatus)

data class AgentTaskSnapshot(
    val id: String,
    val status: AgentTaskStatus,
    val userVisibleSummary: String? = null,
    val error: String? = null
)

data class AgentOperationResult(val supported: Boolean, val succeeded: Boolean, val detail: String)

/**
 * An agent adapter must only advertise implemented capabilities and report lifecycle support honestly.
 * Installation/configuration is intentionally not implied by registering an adapter.
 */
interface CodingAgentAdapter {
    val descriptor: CodingAgentDescriptor
    fun healthCheck(): CodingAgentHealth
    suspend fun start(request: AgentTaskRequest): AgentTaskHandle
    suspend fun inspect(taskId: String): AgentTaskSnapshot
    suspend fun cancel(taskId: String): AgentOperationResult
    suspend fun resume(taskId: String): AgentOperationResult
}

/** Shared extension point. It starts empty: candidates need a concrete, tested adapter before registration. */
@Singleton
class DeveloperAgentRegistry @Inject constructor() {
    private val adapters = ConcurrentHashMap<String, CodingAgentAdapter>()

    fun all(): List<CodingAgentAdapter> = adapters.values.sortedBy { it.descriptor.id }

    fun get(id: String): CodingAgentAdapter? = adapters[id]

    fun healthSnapshot(): List<Pair<CodingAgentDescriptor, CodingAgentHealth>> =
        all().map { it.descriptor to it.healthCheck() }

    fun register(adapter: CodingAgentAdapter) {
        require(adapter.descriptor.id.isNotBlank()) { "Agent ID is required." }
        require(adapter.descriptor.name.isNotBlank()) { "Agent name is required." }
        require(adapter.descriptor.upstreamUrl.startsWith("https://")) { "Agent upstream must use HTTPS." }
        require(adapter.descriptor.documentationUrl.startsWith("https://")) { "Agent documentation must use HTTPS." }
        check(adapters.putIfAbsent(adapter.descriptor.id, adapter) == null) {
            "An agent with ID ${adapter.descriptor.id} is already registered."
        }
    }

    fun unregister(id: String): Boolean = adapters.remove(id) != null
}
