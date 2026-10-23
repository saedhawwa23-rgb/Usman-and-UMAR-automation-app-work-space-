package com.opendroid.ai.core.agent

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

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
    val latestRelease: String?,
    val releaseDate: String?,
    val license: String?,
    val upstreamUrl: String,
    val documentationUrl: String,
    val runtimeRequirements: String,
    val architectureSupport: String,
    val androidApiSupport: String,
    val supportedExecutionBackends: String,
    val providerRequirements: String,
    val networkStorageAndPermissionRequirements: String,
    val securityBoundary: String,
    val metadataVerifiedAtMs: Long,
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
 * Registration does not imply installation or support for any Android ABI/API level.
 */
interface CodingAgentAdapter {
    val descriptor: CodingAgentDescriptor
    fun healthCheck(): CodingAgentHealth
    suspend fun start(request: AgentTaskRequest): AgentTaskHandle
    suspend fun inspect(taskId: String): AgentTaskSnapshot
    suspend fun cancel(taskId: String): AgentOperationResult
    suspend fun resume(taskId: String): AgentOperationResult
}

/** Optional lifecycle surface; unsupported actions must not be exposed as working buttons. */
interface CodingAgentManagement {
    suspend fun install(): AgentOperationResult
    suspend fun configure(): AgentOperationResult
    suspend fun update(): AgentOperationResult
    suspend fun uninstall(): AgentOperationResult
    suspend fun troubleshoot(): AgentOperationResult
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
        val descriptor = adapter.descriptor
        require(descriptor.id.isNotBlank()) { "Agent ID is required." }
        require(descriptor.name.isNotBlank()) { "Agent name is required." }
        require(descriptor.upstreamUrl.startsWith("https://")) { "Agent upstream must use HTTPS." }
        require(descriptor.documentationUrl.startsWith("https://")) { "Agent documentation must use HTTPS." }
        require(descriptor.runtimeRequirements.isNotBlank()) { "Agent runtime requirements must be recorded, or explicitly marked unknown." }
        require(descriptor.architectureSupport.isNotBlank()) { "Agent architecture support must be recorded, or explicitly marked unknown." }
        require(descriptor.androidApiSupport.isNotBlank()) { "Agent Android API support must be recorded, or explicitly marked unknown." }
        require(descriptor.supportedExecutionBackends.isNotBlank()) { "Agent execution backends must be recorded." }
        require(descriptor.providerRequirements.isNotBlank()) { "Agent provider requirements must be recorded." }
        require(descriptor.networkStorageAndPermissionRequirements.isNotBlank()) { "Agent network, storage and permission requirements must be recorded." }
        require(descriptor.securityBoundary.isNotBlank()) { "Agent security boundary must be recorded." }
        require(descriptor.metadataVerifiedAtMs > 0L) { "Agent metadata verification timestamp is required." }
        check(adapters.putIfAbsent(descriptor.id, adapter) == null) {
            "An agent with ID ${descriptor.id} is already registered."
        }
    }

    fun unregister(id: String): Boolean = adapters.remove(id) != null
}
