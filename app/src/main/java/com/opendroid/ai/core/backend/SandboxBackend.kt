package com.opendroid.ai.core.backend

import android.content.Context
import android.os.Build
import com.opendroid.ai.core.linux.LinuxRuntimeManager
import com.opendroid.ai.core.storage.DeveloperWorkspaceFiles
import com.opendroid.ai.core.storage.WorkspaceFileEntry
import com.opendroid.ai.core.storage.WorkspaceSearchResult
import com.opendroid.ai.core.termux.TermuxIntegration
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Availability is explicit; a backend is not marked working until it has passed a real smoke test. */
enum class BackendStatus {
    AVAILABLE,
    COMPATIBLE,
    INSTALLABLE,
    INSTALLED,
    VERIFIED_WORKING,
    REQUIRES_CONFIGURATION,
    INCOMPATIBLE,
    NOT_TESTED
}

enum class BackendKind { APP_WORKSPACE, TERMUX, PROOT_LINUX, REMOTE_CLOUD, CUSTOM }

data class BackendCapabilities(
    val browseFiles: Boolean = false,
    val searchFiles: Boolean = false,
    val editFiles: Boolean = false,
    val importExportFiles: Boolean = false,
    val executeCommands: Boolean = false,
    val cancelCommands: Boolean = false,
    val boundedWait: Boolean = false,
    val outputLimitBytes: Int? = null,
    val resourceLimits: Boolean = false,
    val taskIsolation: Boolean = false,
    val networkAccess: Boolean = false,
    val persistentTaskHistory: Boolean = false
)

data class BackendDescriptor(
    val id: String,
    val name: String,
    val kind: BackendKind,
    val capabilities: BackendCapabilities,
    val securityBoundary: String,
    val limitations: List<String>
)

data class BackendHealth(
    val status: BackendStatus,
    val detail: String,
    val checkedAtMs: Long = System.currentTimeMillis(),
    val installedVersion: String? = null
)

data class BackendExecutionRequest(val command: String, val timeoutMs: Long = 30_000L)

data class BackendExecutionResult(
    val executionId: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val error: String?,
    val completedAtMs: Long
) {
    val succeeded: Boolean get() = error == null && exitCode == 0
}

data class BackendOperationResult(val supported: Boolean, val succeeded: Boolean, val detail: String)

interface SandboxBackend {
    val descriptor: BackendDescriptor
    fun healthCheck(): BackendHealth
}

/** A command adapter must report cancellation honestly; timing out the caller is not process cancellation. */
interface CommandSandboxBackend : SandboxBackend {
    suspend fun execute(request: BackendExecutionRequest): BackendExecutionResult
    fun cancel(executionId: String): BackendOperationResult
}

/** The app-owned workspace is a file service, not a shell or process-isolation backend. */
interface WorkspaceSandboxBackend : SandboxBackend {
    fun listFiles(path: String = ""): List<WorkspaceFileEntry>
    fun searchFiles(query: String): WorkspaceSearchResult
    fun readFile(path: String): String
    fun saveFile(path: String, content: String)
    fun createFile(path: String): File
    fun createDirectory(path: String): File
    fun importTextFile(path: String, content: String)
    fun rename(path: String, newName: String): String
    fun move(path: String, destinationDirectory: String): String
    fun delete(path: String)
}

/** Add a provider-specific implementation only after its auth, billing and execution semantics are verified. */
interface RemoteSandboxBackend : CommandSandboxBackend

/**
 * Shared registry consumed by the Developers/search screens. Remote adapters can be registered by
 * a configured provider module; none are registered by default because no provider credentials or
 * account configuration are present in this application build.
 */
@Singleton
class SandboxBackendRegistry @Inject constructor(
    @ApplicationContext context: Context,
    private val termux: TermuxIntegration,
    private val linuxRuntime: LinuxRuntimeManager
) {
    private val workspace = DeveloperWorkspaceFiles(context.applicationContext)
    private val proot = AppProotBackend(linuxRuntime)
    private val remoteAdapters = ConcurrentHashMap<String, RemoteSandboxBackend>()

    fun backends(): List<SandboxBackend> = buildList {
        add(workspace)
        add(termux)
        add(proot)
        addAll(remoteAdapters.values.sortedBy { it.descriptor.id })
    }

    fun healthSnapshot(): List<Pair<BackendDescriptor, BackendHealth>> = backends().map { it.descriptor to it.healthCheck() }

    fun registerRemoteBackend(backend: RemoteSandboxBackend) {
        require(backend.descriptor.kind == BackendKind.REMOTE_CLOUD) { "Remote adapters must declare REMOTE_CLOUD kind." }
        require(backend.descriptor.id.isNotBlank()) { "Remote backend ID is required." }
        check(remoteAdapters.putIfAbsent(backend.descriptor.id, backend) == null) { "A backend with this ID is already registered." }
    }

    fun unregisterRemoteBackend(id: String): Boolean = remoteAdapters.remove(id) != null

    private class AppProotBackend(private val runtime: LinuxRuntimeManager) : SandboxBackend {
        override val descriptor = BackendDescriptor(
            id = "app-proot-linux",
            name = "App-owned Alpine / PRoot",
            kind = BackendKind.PROOT_LINUX,
            capabilities = BackendCapabilities(),
            securityBoundary = "PRoot is userspace emulation, not a hardened container or VM security boundary.",
            limitations = listOf(
                "Execution is disabled for this app's supported Android range (min API 28) because Android seccomp terminates app-owned PRoot.",
                "An installed Alpine root filesystem does not mean commands can run on this device.",
                "No package installation, command execution, cancellation, task isolation, or successful smoke test is available through this backend."
            )
        )

        override fun healthCheck(): BackendHealth = BackendHealth(
            status = BackendStatus.INCOMPATIBLE,
            detail = "App-owned PRoot execution is disabled on Android API ${Build.VERSION.SDK_INT} (runtime status: ${runtime.status()}); Termux is a separate optional backend."
        )
    }
}
