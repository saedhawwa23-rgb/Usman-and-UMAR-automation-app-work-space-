package com.opendroid.ai.core.needle

import android.content.Context
import android.util.Log
import com.opendroid.ai.core.agent.ActionSchema
import com.opendroid.ai.core.agent.ParamType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

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

/** Local-only Needle 3 engine. No cloud inference or API key is used. */
@Singleton
class Needle3LocalRuntime @Inject constructor(private val context: Context) {
    private val state = MutableStateFlow<Needle3Status>(initialStatus())
    val status: StateFlow<Needle3Status> = state.asStateFlow()
    private val mutex = Mutex()
    private var initializedToolsHash: String? = null

    suspend fun installAndLoad(): Needle3Status = withContext(Dispatchers.IO) {
        mutex.withLock {
                var stage = "MODEL_INSTALL_FAILED"
                try {
                    state.value = Needle3Status.Loading
                    val model = modelFile()
                    if (!model.exists() || model.length() != MODEL_SIZE || sha256(model) != MODEL_SHA256) {
                        stage = "MODEL_INSTALL_FAILED"
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
                    loadForCurrentTools(model)
                    state.value = Needle3Status.Ready
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    Log.e(TAG, "$stage: Needle 3 local install/load failed", t)
                    state.value = Needle3Status.Error(
                        "$stage: ${t.message ?: "Needle 3 could not be loaded"}"
                )
            }
            state.value
        }
    }

    suspend fun complete(input: String, maxNewTokens: Int = 256): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureNativeSupported()
            val model = modelFile()
            if (!model.isFile || sha256(model) != MODEL_SHA256) {
                throw IllegalStateException("Needle 3 is not installed. Tap Install locally first.")
            }
            loadForCurrentTools(model)
            Needle3Native.complete(
                Needle3PromptBudget.boundCompletionInput(input),
                maxNewTokens.coerceIn(64, Needle3PromptBudget.MAX_NEW_TOKENS)
            )
                ?: throw IllegalStateException(Needle3Native.lastError().ifBlank { "Needle 3 completion failed" })
        }
    }

    suspend fun completeWithTools(input: String, toolsJson: String, maxNewTokens: Int = 256): String =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureNativeSupported()
                val model = modelFile()
                if (!model.isFile || sha256(model) != MODEL_SHA256) {
                    throw IllegalStateException("Needle 3 is not installed. Tap Install locally first.")
                }
                loadForTools(model, toolsJson)
                Needle3Native.complete(
                    Needle3PromptBudget.boundCompletionInput(input),
                    maxNewTokens.coerceIn(64, Needle3PromptBudget.MAX_NEW_TOKENS)
                )
                    ?: throw IllegalStateException(Needle3Native.lastError().ifBlank { "Needle 3 completion failed" })
            }
        }

    suspend fun diagnosticToolCall(): String {
        val response = complete("Get the current system information.")
        val root = JSONObject(response)
        val calls = root.optJSONArray("function_calls") ?: JSONArray()
        if (calls.length() == 0) {
            throw IllegalStateException("Needle 3 returned no diagnostic tool call: $response")
        }
        return response
    }

    fun modelFile(): File = File(context.filesDir, "needle3/needle3.cact")

    fun hasBundledModelAsset(): Boolean = try {
        context.assets.open(BUNDLED_MODEL_ASSET).use { }
        true
    } catch (_: Exception) {
        false
    }

    private fun initialStatus(): Needle3Status = try {
        ensureNativeSupported()
        val file = modelFile()
        if (file.isFile && file.length() == MODEL_SIZE) Needle3Status.NotInstalled else Needle3Status.NotInstalled
    } catch (t: Throwable) {
        Needle3Status.Unsupported(t.message ?: "This device ABI is not supported by Needle 3")
    }

    private fun ensureNativeSupported() {
        if (!Needle3Native.isSupported) {
            throw UnsupportedOperationException(Needle3Native.lastError().ifBlank {
                "Needle 3 is supported on ARMv7 and ARM64 Android devices only"
            })
        }
    }

    private fun loadForCurrentTools(model: File) {
        loadForTools(model, Needle3ToolSchema.json())
    }

    private fun loadForTools(model: File, tools: String) {
        val prefix = Needle3PromptBudget.fitStaticPrefix(NEEDLE_SYSTEM_PROMPT, tools)
        val hash = sha256Bytes("${prefix.systemPrompt}\u0000${prefix.toolsJson}".toByteArray(Charsets.UTF_8))
        if (initializedToolsHash == hash) return
        if (!Needle3Native.load(model.readBytes())) {
            throw IllegalStateException(Needle3Native.lastError().ifBlank { "Needle 3 model load failed" })
        }
        val index = File(model.parentFile, "tools.idx")
        if (!Needle3Native.init(
                prefix.systemPrompt,
                prefix.toolsJson,
                index.absolutePath
            )) {
            throw IllegalStateException(Needle3Native.lastError().ifBlank { "Needle 3 initialization failed" })
        }
        initializedToolsHash = hash
    }

    private fun installBundledModel(destination: File) {
        destination.parentFile?.mkdirs()
        val partial = File(destination.parentFile, "needle3.cact.installing")
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
                throw IllegalStateException("Bundled Needle 3 model failed its size/SHA-256 check")
            }
            if (destination.exists() && !destination.delete()) {
                throw IllegalStateException("Could not replace the existing Needle 3 model")
            }
            if (!partial.renameTo(destination)) throw IllegalStateException("Could not install Needle 3 model")
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

    private fun sha256Bytes(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "Needle3Runtime"
        private const val NEEDLE_SYSTEM_PROMPT = "You are USMAN AND UMAR's local Android tool planner. Select only evidenced tool calls."
        private const val BUNDLED_MODEL_ASSET = "needle3/needle3.cact"
        private const val COPY_BUFFER_BYTES = 64 * 1024
        private const val PROGRESS_INTERVAL_BYTES = 512 * 1024L
        const val MODEL_SIZE = 35_335_380L
        const val MODEL_SHA256 = "c9d915eca282ed42d1a09b143b592adb4cc6744ffe2d294adf5cfc5548170c38"
    }
}

object Needle3ToolSchema {
    fun definitions(): List<JSONObject> = buildList {
        ActionSchema.ALL_ACTIONS.forEach { action ->
            val parameters = JSONObject()
            parameters.put("type", "object")
            val properties = JSONObject()
            val required = JSONArray()
            action.params.forEach { param ->
                val property = JSONObject()
                property.put("description", param.description)
                when (param.type) {
                    ParamType.INT -> property.put("type", "integer")
                    ParamType.BOOLEAN -> property.put("type", "boolean")
                    ParamType.ENUM -> {
                        property.put("type", "string")
                        val enumValues = JSONArray()
                        param.enumValues.forEach(enumValues::put)
                        property.put("enum", enumValues)
                    }
                    ParamType.STRING -> property.put("type", "string")
                }
                param.defaultValue?.let { property.put("default", it) }
                properties.put(param.name, property)
                if (param.required) required.put(param.name)
            }
            parameters.put("properties", properties)
            if (required.length() > 0) parameters.put("required", required)
            val tool = JSONObject()
            tool.put("name", action.name)
            tool.put("description", action.description)
            tool.put("parameters", parameters)
            add(tool)
        }
    }

    fun json(): String {
        return buildJsonArray {
            ActionSchema.ALL_ACTIONS.forEach { action ->
                add(buildJsonObject {
                    put("name", action.name)
                    put("description", action.description)
                    put("parameters", buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            action.params.forEach { param ->
                                put(param.name, buildJsonObject {
                                    put("type", when (param.type) {
                                        ParamType.INT -> "integer"
                                        ParamType.BOOLEAN -> "boolean"
                                        ParamType.ENUM, ParamType.STRING -> "string"
                                    })
                                    if (param.enumValues.isNotEmpty()) {
                                        put("enum", buildJsonArray {
                                            param.enumValues.forEach { add(JsonPrimitive(it)) }
                                        })
                                    }
                                })
                            }
                        })
                        val required = action.params.filter { it.required }
                        if (required.isNotEmpty()) {
                            put("required", buildJsonArray {
                                required.forEach { add(JsonPrimitive(it.name)) }
                            })
                        }
                    })
                })
            }
        }.toString()
    }
}

object Needle3Native {
    val isSupported: Boolean
    private var loadError: String = ""
    init {
        isSupported = try {
            System.loadLibrary("needlebridge")
            true
        } catch (t: Throwable) {
            loadError = "Needle 3 native runtime unavailable: ${t.javaClass.simpleName}"
            false
        }
    }
    fun lastError(): String = if (isSupported) nativeLastError() else loadError
    fun load(model: ByteArray): Boolean = isSupported && nativeLoad(model)
    fun init(systemPrompt: String, toolsJson: String, toolIndexPath: String): Boolean =
        isSupported && nativeInit(systemPrompt, toolsJson, toolIndexPath)
    fun complete(input: String, maxNewTokens: Int): String? =
        if (isSupported) nativeComplete(input, maxNewTokens) else null
    private external fun nativeLoad(model: ByteArray): Boolean
    private external fun nativeInit(systemPrompt: String, toolsJson: String, toolIndexPath: String): Boolean
    private external fun nativeComplete(input: String, maxNewTokens: Int): String?
    private external fun nativeLastError(): String
}
