package com.opendroid.ai.core.llm.providers

import com.opendroid.ai.core.llm.LLMProvider
import com.opendroid.ai.core.llm.LLMRequest
import com.opendroid.ai.core.llm.LLMResponse
import com.opendroid.ai.core.llm.ProviderCatalog
import com.opendroid.ai.core.llm.ResponseFormat
import com.opendroid.ai.core.llm.StreamChunk
import com.opendroid.ai.core.needle.Needle3LocalRuntime
import com.opendroid.ai.core.needle.Needle3PromptBudget
import com.opendroid.ai.core.needle.Needle3ProviderToolSchema
import com.opendroid.ai.core.needle.Needle3ResponseParser
import com.opendroid.ai.core.needle.Needle3Status
import com.opendroid.ai.core.needle.Needle3ToolSchema
import com.opendroid.ai.core.needle.Qwen3PromptFormatter
import com.opendroid.ai.data.models.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** Qwen 3 local provider; tool execution still flows through the app's existing safeguards. */
@Singleton
class Needle3LocalProvider @Inject constructor(
    private val runtime: Needle3LocalRuntime
) : LLMProvider {
    override val name: String = ProviderCatalog.QWEN3_LOCAL
    override val availableModels: List<String> = listOf(QWEN_MODEL_ID)

    suspend fun ensureReady(): Needle3Status =
        if (runtime.status.value is Needle3Status.Ready) runtime.status.value
        else runtime.loadExternalModel()

    private suspend fun requireReady() {
        when (val status = ensureReady()) {
            is Needle3Status.Ready -> Unit
            is Needle3Status.Error -> throw IllegalStateException("Qwen 3 is not ready: ${status.message}")
            is Needle3Status.Unsupported -> throw IllegalStateException("Qwen 3 is not ready: ${status.message}")
            Needle3Status.NotInstalled -> throw IllegalStateException(Needle3LocalRuntime.MODEL_MISSING_MESSAGE)
            Needle3Status.PermissionRequired -> throw IllegalStateException(
                "Allow storage access to read ${Needle3LocalRuntime.MODEL_FILE_PATH}."
            )
            Needle3Status.Loading,
            Needle3Status.Verifying,
            Needle3Status.Initializing ->
                throw IllegalStateException("Qwen 3 is still checking or loading the external model")
        }
    }

    override suspend fun complete(request: LLMRequest): LLMResponse = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val userQuery = request.messages.lastOrNull { it.sender == ChatMessage.Sender.USER }?.text.orEmpty()
        if (request.responseFormat == ResponseFormat.TEXT) {
            Needle3ResponseParser.greetingReply(userQuery)?.let { greeting ->
                return@withContext LLMResponse(
                    content = greeting,
                    tokensUsed = 0,
                    model = QWEN_MODEL_ID,
                    provider = name,
                    latencyMs = System.currentTimeMillis() - started
                )
            }
        }
        requireReady()
        val input = buildInput(request)
        val outputLimit = if (request.responseFormat == ResponseFormat.JSON) {
            Needle3PromptBudget.MAX_TOOL_CALL_TOKENS
        } else {
            request.maxTokens.coerceAtMost(Needle3PromptBudget.MAX_CHAT_TOKENS).coerceAtLeast(64)
        }
        val nativeResponse = runtime.complete(input, outputLimit)
        val content = if (request.responseFormat == ResponseFormat.TEXT) {
            Needle3ResponseParser.toNaturalText(nativeResponse, userQuery)
        } else {
            adaptToExistingPlan(nativeResponse, input)
        }
        LLMResponse(
            content = content,
            tokensUsed = nativeResponse.length / 4,
            model = QWEN_MODEL_ID,
            provider = name,
            latencyMs = System.currentTimeMillis() - started
        )
    }

    override fun streamComplete(request: LLMRequest): Flow<String> = flow {
        emit(complete(request).content)
    }

    override suspend fun generate(
        messages: List<ChatMessage>,
        tools: List<com.opendroid.ai.core.llm.ToolDefinition>
    ): Flow<StreamChunk> = flow {
        requireReady()
        val userQuery = messages.lastOrNull { it.sender == ChatMessage.Sender.USER }?.text.orEmpty()
        Needle3ProviderToolSchema.listAvailableToolsTool(userQuery, tools)?.let { canonicalName ->
            emit(StreamChunk.ToolCall(canonicalName, "{}"))
            return@flow
        }
        val preparedTools = Needle3ProviderToolSchema.prepare(tools)
        val conversation = messages.takeLast(8).joinToString("\n") { message ->
            val role = if (message.sender == ChatMessage.Sender.USER) "User" else "Assistant"
            "$role: ${message.text.take(512)}"
        }
        val response = runtime.completeWithTools(
            input = conversation,
            toolsJson = preparedTools.json,
            maxNewTokens = Needle3PromptBudget.MAX_TOOL_CALL_TOKENS
        )
        val calls = Needle3ResponseParser.parseCalls(response, preparedTools.allowedNames)
        if (calls.isEmpty()) {
            emit(StreamChunk.Content(Needle3ResponseParser.toolCallFailureOrNaturalText(response, userQuery)))
        } else {
            calls.forEach { call ->
                val canonicalName = preparedTools.canonicalNameByModelName[call.name] ?: call.name
                emit(StreamChunk.ToolCall(canonicalName, call.argumentsJson))
            }
        }
    }

    override suspend fun isAvailable(): Boolean = runtime.status.value is Needle3Status.Ready

    private fun buildInput(request: LLMRequest): String {
        if (request.responseFormat == ResponseFormat.TEXT) {
            return Qwen3PromptFormatter.chat(CHAT_PROMPT, request.messages)
        }
        val prefix = Needle3PromptBudget.fitStaticPrefix(PLAN_PROMPT, Needle3ToolSchema.json())
        val system = """${prefix.systemPrompt}
Registered actions (compact JSON schema): ${prefix.toolsJson}
Return an action only as <tool_call>{"name":"action_name","arguments":{}}</tool_call>."""
        val conversation = request.messages.takeLast(8).joinToString("\n") { message ->
            val role = if (message.sender == ChatMessage.Sender.USER) "User" else "Assistant"
            "$role: ${message.text.take(512)}"
        }
        return Qwen3PromptFormatter.oneUserMessage(
            system,
            Needle3PromptBudget.boundCompletionInput(conversation)
        )
    }

    private fun adaptToExistingPlan(raw: String, goal: String): String {
        val calls = Needle3ResponseParser.parseCalls(raw)
        if (calls.isEmpty()) return raw
        return buildJsonObject {
            put("planId", "qwen3-${System.currentTimeMillis()}")
            put("goal", goal.take(500))
            put("estimatedDuration", "instant")
            put("estimatedSteps", calls.size)
            putJsonArray("steps") {
                calls.forEachIndexed { index, call ->
                    val args = Json.parseToJsonElement(call.argumentsJson).jsonObject
                    add(buildJsonObject {
                        put("stepId", "qwen3-${index + 1}")
                        put("order", index + 1)
                        put("description", "Qwen 3 selected ${call.name}")
                        put("action", call.name)
                        putJsonObject("params") {
                            args.entries.forEach { (key, value) ->
                                put(key, when (value) {
                                    is JsonPrimitive -> value.content
                                    else -> value.toString()
                                })
                            }
                        }
                    })
                }
            }
        }.toString()
    }

    private companion object {
        const val QWEN_MODEL_ID = "qwen3-0.6b-q4_k_m"
        const val CHAT_PROMPT = "You are USMAN AND UMAR, a friendly local Android assistant. Answer ordinary chat in natural language only. Never output JSON or tool-call syntax in chat mode. Do not claim device actions were performed."
        const val PLAN_PROMPT = "You are USMAN AND UMAR, a local Android assistant. For explicit actions, select only from the registered actions below. Never call tools for greetings or ordinary questions. The app validates, approves, and executes every action; never claim completion."
    }
}
