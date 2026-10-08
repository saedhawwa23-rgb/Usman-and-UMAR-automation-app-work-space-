package com.opendroid.ai.core.llm.providers

import com.opendroid.ai.core.llm.LLMProvider
import com.opendroid.ai.core.llm.LLMRequest
import com.opendroid.ai.core.llm.LLMResponse
import com.opendroid.ai.core.llm.ProviderCatalog
import com.opendroid.ai.core.llm.ResponseFormat
import com.opendroid.ai.core.llm.StreamChunk
import com.opendroid.ai.core.needle.Needle3LocalRuntime
import com.opendroid.ai.core.needle.Needle3ResponseParser
import com.opendroid.ai.core.needle.Needle3Status
import com.opendroid.ai.core.needle.Needle3PromptBudget
import com.opendroid.ai.core.needle.Needle3ProviderToolSchema
import com.opendroid.ai.data.models.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Needle 3 provider. It performs local structured action selection only; it never
 * contacts an AI API and does not replace the app's existing chat providers.
 */
@Singleton
class Needle3LocalProvider @Inject constructor(
    private val runtime: Needle3LocalRuntime
) : LLMProvider {
    override val name: String = ProviderCatalog.NEEDLE3_LOCAL
    override val availableModels: List<String> = listOf("needle3")

    /** Stages the bundled .cact file when needed, verifies it, then initializes native Needle 3. */
    suspend fun ensureReady(): Needle3Status =
        if (runtime.status.value is Needle3Status.Ready) runtime.status.value
        else runtime.installAndLoad()

    private suspend fun requireReady() {
        when (val status = ensureReady()) {
            is Needle3Status.Ready -> Unit
            is Needle3Status.Error -> throw IllegalStateException("Needle 3 is not ready: ${status.message}")
            is Needle3Status.Unsupported -> throw IllegalStateException("Needle 3 is not ready: ${status.message}")
            Needle3Status.NotInstalled -> throw IllegalStateException("Needle 3 is not ready: model installation did not complete")
            Needle3Status.Loading,
            is Needle3Status.Downloading,
            Needle3Status.Verifying,
            Needle3Status.Initializing ->
                throw IllegalStateException("Needle 3 is not ready: model installation is still in progress")
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
                    model = "needle3",
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
            Needle3ResponseParser.toNaturalText(
                nativeResponse,
                userQuery
            )
        } else {
            adaptToExistingPlan(nativeResponse, input)
        }
        LLMResponse(
            content = content,
            tokensUsed = nativeResponse.length / 4,
            model = "needle3",
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
        val input = buildString {
            append(PLAN_PROMPT)
            append('\n')
            append(messages.joinToString("\n") { it.text })
        }
        val response = runtime.completeWithTools(input, preparedTools.json, Needle3PromptBudget.MAX_TOOL_CALL_TOKENS)
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

    override suspend fun isAvailable(): Boolean = runtime.status.value is com.opendroid.ai.core.needle.Needle3Status.Ready

    private fun buildInput(request: LLMRequest): String = buildString {
        append(if (request.responseFormat == ResponseFormat.TEXT) CHAT_PROMPT else PLAN_PROMPT)
        append("\n\n")
        request.messages.takeLast(8).forEach { message ->
            append(if (message.sender == ChatMessage.Sender.USER) "User: " else "Assistant: ")
            append(message.text.take(512))
            append('\n')
        }
    }

    private companion object {
        const val CHAT_PROMPT = "You are USMAN AND UMAR, a friendly local Android assistant. For greetings and ordinary questions, answer directly in natural language only. Never output JSON, a tool call, or a plan in chat mode. Do not claim device actions were performed."
        const val PLAN_PROMPT = "For an explicit action request, return one registered tool call using its supplied name and valid arguments. If asked to list tools, output exactly {\"type\":\"call\",\"tool\":\"list_available_tools\"}. Never call tools for greetings or ordinary questions. The app validates, approves, and executes; do not claim completion."
    }

    private fun adaptToExistingPlan(raw: String, goal: String): String {
        val calls = Needle3ResponseParser.parseCalls(raw)
        if (calls.isEmpty()) return raw
        return buildJsonObject {
            put("planId", "needle3-${System.currentTimeMillis()}")
            put("goal", goal.take(500))
            put("estimatedDuration", "instant")
            put("estimatedSteps", calls.size)
            putJsonArray("steps") {
                calls.forEachIndexed { index, call ->
                    val args = Json.parseToJsonElement(call.argumentsJson).jsonObject
                    add(buildJsonObject {
                        put("stepId", "needle3-${index + 1}")
                        put("order", index + 1)
                        put("description", "Needle 3 selected ${call.name}")
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
}
