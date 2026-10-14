package com.opendroid.ai.core.service

import com.opendroid.ai.core.linux.LinuxRuntimeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class TerminalSessionInfo(
    val id: String,
    val backend: CommandBackend
)

@Singleton
class PersistentTerminalManager @Inject constructor(
    private val linuxRuntime: LinuxRuntimeManager
) {
    private val sessions = ConcurrentHashMap<String, Process>()

    suspend fun create(): TerminalSessionInfo = withContext(Dispatchers.IO) {
        val process = linuxRuntime.startShell()
        val id = UUID.randomUUID().toString()
        sessions[id] = process
        TerminalSessionInfo(id, CommandBackend.INTEGRATED_LINUX)
    }

    suspend fun write(id: String, command: String) = withContext(Dispatchers.IO) {
        require(command.length <= MAX_COMMAND_LENGTH) { "Command is too long" }
        val process = requireSession(id)
        process.outputStream.write((command + "\n").toByteArray(Charsets.UTF_8))
        process.outputStream.flush()
    }

    suspend fun read(id: String): String = withContext(Dispatchers.IO) {
        val process = requireSession(id)
        val output = StringBuilder()
        readAvailable(listOf(process.inputStream, process.errorStream), output)
        output.toString()
    }

    fun list(): List<TerminalSessionInfo> = sessions.keys().asSequence()
        .map { TerminalSessionInfo(it, CommandBackend.INTEGRATED_LINUX) }
        .toList()

    fun close(id: String) {
        sessions.remove(id)?.let { process ->
            process.destroy()
            if (process.isAlive) process.destroyForcibly()
        }
    }

    fun closeAll() = sessions.keys().toList().forEach(::close)

    private fun requireSession(id: String): Process = sessions[id]
        ?: throw IllegalArgumentException("Unknown terminal session: $id")

    private fun readAvailable(streams: List<InputStream>, output: StringBuilder) {
        val deadline = System.nanoTime() + READ_TIMEOUT_MS * NANOS_PER_MILLISECOND
        val buffer = ByteArray(8192)
        while (output.length < MAX_OUTPUT_BYTES && System.nanoTime() < deadline) {
            var readAny = false
            streams.forEach { stream ->
                val available = stream.available().coerceAtMost(MAX_OUTPUT_BYTES - output.length)
                if (available > 0) {
                    val count = stream.read(buffer, 0, minOf(buffer.size, available))
                    if (count > 0) {
                        output.append(String(buffer, 0, count, Charsets.UTF_8))
                        readAny = true
                    }
                }
            }
            if (!readAny) Thread.sleep(READ_POLL_INTERVAL_MS)
        }
    }

    private companion object {
        const val MAX_COMMAND_LENGTH = 4096
        const val MAX_OUTPUT_BYTES = 64 * 1024
        const val READ_TIMEOUT_MS = 1000L
        const val READ_POLL_INTERVAL_MS = 10L
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
