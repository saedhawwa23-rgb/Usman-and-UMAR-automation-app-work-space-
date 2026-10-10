package com.opendroid.ai.core.needle

import android.content.Context
import android.util.Log
import com.opendroid.ai.core.agent.ActionSchema
import com.opendroid.ai.core.agent.ParamType
import com.opendroid.llama.Qwen3Native
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Local-only Qwen 3 engine backed by the bundled GGUF model and llama.cpp. */
sealed class Needle3Status {
    data object NotInstalled : Needle3Status()
    data object Loading : Needle3Status()
    data class Downloading(val bytesCopied: Long) : Needle3Status()
    data object Verifying : Needle3Status()
    data object Initializing : Needle3Status()
    data object Ready : Needle3Status()
    data class Error(val message: String) : Needle3Status()
    data class Unsupported(val message: String) : Needle3Status()
}

@Singleton
class Needle3LocalRuntime @Inject constructor(private val context: Context) {
    private val state = MutableStateFlow<Needle3Status>(initialStatus())
    val status: StateFlow<Needle3Status> = state.asStateFlow()
    private val mutex = Mutex()
    private var loadedPath: String? = null
    private var loadedModifiedAt: Long? = null
    private var nativeContextReady = false

    suspend fun installAndLoad(): Needle3Status = withContext(Dispatchers.IO) {
        mutex.withLock {
            var stage = "MODEL_INSTALL_FAILED"
            try {
                state.value = Needle3Status.Loading
                val model = modelFile()
                if (!model.isFile || model.length() != MODEL_SIZE || sha256(model) != MODEL_SHA256) {
                    state.value = Needle3Status.Downloading(0L)
                    installBundledModel(model)
                }
                stage = "MODEL_CHECKSUM_FAILED"
                state.value = Needle3Status.Verifying
                verifyModel(model)
                stage = "NATIVE_RUNTIME_FAILED"
                ensureNativeSupported()
                stage = "MODEL_INITIALIZATION_FAILED"
                state.value = Needle3Status.Initializing
                loadNativeIfNeeded(model)
                state.value = Needle3Status.Ready
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.e(TAG, "$stage: Qwen 3 local install/load failed", error)
                state.value = Needle3Status.Error("$stage: ${error.message ?: "Qwen 3 could not be loaded"}")
            }
            state.value
        }
    }

    suspend fun complete(input: String, maxNewTokens: Int = MAX_CHAT_TOKENS): String =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureNativeSupported()
                val model = modelFile()
                if (!model.isFile || model.length() != MODEL_SIZE) {
                    throw IllegalStateException("Qwen 3 is not installed. Install the bundled Qwen 3 model first.")
                }
                loadNativeIfNeeded(model)
                val prompt = if (input.contains("<|im_start|>system")) {
                    input
                } else {
                    Qwen3PromptFormatter.oneUserMessage(CHAT_SYSTEM_PROMPT, input)
                }
                Qwen3Native.generate(prompt, maxNewTokens.coerceIn(64, MAX_TOOL_CALL_TOKENS))
                    ?: throw IllegalStateException(Qwen3Native.lastError().ifBlank { "Qwen 3 completion failed" })
            }
        }

    suspend fun completeWithTools(
        input: String,
        toolsJson: String,
        maxNewTokens: Int = MAX_TOOL_CALL_TOKENS
    ): String {
        val bounded = Needle3PromptBudget.fitStaticPrefix(TOOL_SYSTEM_PROMPT, toolsJson)
        val systemPrompt = """${bounded.systemPrompt}
Registered tools (compact JSON schema): ${bounded.toolsJson}
For a tool call, output exactly <tool_call>{"name":"tool_name","arguments":{}}</tool_call>. Never invent a tool name."""
        return complete(
            Qwen3PromptFormatter.oneUserMessage(systemPrompt, Needle3PromptBudget.boundCompletionInput(input)),
            maxNewTokens
        )
    }

    suspend fun diagnosticToolCall(): String {
        val response = completeWithTools(
            "Get current system information.",
            """[{"name":"GET_SYSTEM_INFO","description":"Get system information"}]""",
            256
        )
        if (Needle3ResponseParser.parseCalls(response, setOf("GET_SYSTEM_INFO")).isEmpty()) {
            throw IllegalStateException("Qwen 3 returned no diagnostic tool call")
        }
        return response
    }

    fun modelFile(): File = File(context.filesDir, "models/qwen3.gguf")

    fun hasBundledModelAsset(): Boolean = try {
        context.assets.open(BUNDLED_MODEL_ASSET).use { }
        true
    } catch (_: Exception) {
        false
    }

    private fun initialStatus(): Needle3Status = Needle3Status.NotInstalled

    private fun ensureNativeSupported() {
        if (!Qwen3Native.isSupported) {
            throw UnsupportedOperationException(Qwen3Native.lastError().ifBlank {
                "Qwen 3 requires a supported ARM Android ABI (armeabi-v7a or arm64-v8a)"
            })
        }
    }

    private fun loadNativeIfNeeded(model: File) {
        val path = model.absolutePath
        if (nativeContextReady && loadedPath == path && loadedModifiedAt == model.lastModified()) return
        nativeContextReady = false
        if (!Qwen3Native.loadModel(path)) {
            throw IllegalStateException(Qwen3Native.lastError().ifBlank { "llama.cpp could not load Qwen 3" })
        }
        if (!Qwen3Native.createContext(CONTEXT_TOKENS)) {
            throw IllegalStateException(Qwen3Native.lastError().ifBlank { "llama.cpp could not create a Qwen context" })
        }
        loadedPath = path
        loadedModifiedAt = model.lastModified()
        nativeContextReady = true
    }

    private fun installBundledModel(destination: File) {
        destination.parentFile?.mkdirs()
        val partial = File(destination.parentFile, "qwen3.gguf.installing")
        if (partial.exists()) partial.delete()
        try {
            context.assets.open(BUNDLED_MODEL_ASSET).buffered().use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    var copied = 0L
                    var lastReported = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        if (copied - lastReported >= PROGRESS_INTERVAL_BYTES || copied == MODEL_SIZE) {
                            state.value = Needle3Status.Downloading(copied)
                            lastReported = copied
                        }
                    }
                    output.flush()
                }
            }
            if (partial.length() != MODEL_SIZE || sha256(partial) != MODEL_SHA256) {
                throw IllegalStateException("Bundled Qwen 3 model failed its size/SHA-256 check")
            }
            if (destination.exists() && !destination.delete()) {
                throw IllegalStateException("Could not replace the existing Qwen 3 model")
            }
            if (!partial.renameTo(destination)) throw IllegalStateException("Could not install Qwen 3 model")
        } finally {
            if (partial.exists()) partial.delete()
        }
    }

    private fun verifyModel(model: File) {
        if (!model.isFile || model.length() != MODEL_SIZE) {
            throw IllegalStateException("expected $MODEL_SIZE bytes, found ${model.length()}")
        }
        val actual = sha256(model)
        if (actual != MODEL_SHA256) throw IllegalStateException("SHA-256 mismatch: $actual")
        if (!model.canRead()) throw IllegalStateException("model file is not readable")
    }

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "Qwen3Runtime"
        private const val BUNDLED_MODEL_ASSET = "models/qwen3.gguf"
        private const val COPY_BUFFER_BYTES = 1024 * 1024
        private const val PROGRESS_INTERVAL_BYTES = 4 * 1024 * 1024L
        private const val CONTEXT_TOKENS = 4096
        private const val MAX_CHAT_TOKENS = 512
        private const val MAX_TOOL_CALL_TOKENS = 1536
        private const val CHAT_SYSTEM_PROMPT =
            "You are USMAN AND UMAR, a friendly local Android assistant. Answer ordinary chat in natural language. Never output tool-call syntax in chat mode. Do not claim device actions were performed."
        private const val TOOL_SYSTEM_PROMPT =
            "You are USMAN AND UMAR, a local Android assistant. Call only a registered tool for an explicit action. Never claim an action was completed."
        const val MODEL_SIZE = 484_220_320L
        const val MODEL_SHA256 = "9acfc1e001311f34b4252001b626f2e466d592a42065f66571bff3790d4e1b14"
    }
}

/** Compact list of app actions supplied to the older local-planner surface. */
object Needle3ToolSchema {
    fun definitions(): List<JsonObject> = ActionSchema.ALL_ACTIONS.map { action ->
        val properties = buildJsonObject {
            action.params.forEach { param ->
                put(param.name, buildJsonObject {
                    val type = when (param.type) {
                        ParamType.INT -> "integer"
                        ParamType.BOOLEAN -> "boolean"
                        ParamType.ENUM, ParamType.STRING -> "string"
                    }
                    put("type", JsonPrimitive(type))
                    if (param.type == ParamType.ENUM && param.enumValues.isNotEmpty()) {
                        put("enum", JsonArray(param.enumValues.map { JsonPrimitive(it) }))
                    }
                })
            }
        }
        val parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("properties", properties)
            val required = action.params.filter { it.required }.map { JsonPrimitive(it.name) }
            if (required.isNotEmpty()) put("required", JsonArray(required))
        }
        buildJsonObject {
            put("name", JsonPrimitive(action.name))
            put("description", JsonPrimitive(action.name.lowercase().replace('_', ' ')))
            put("parameters", parameters)
        }
    }

    fun json(): String = JsonArray(definitions()).toString()
}
