package com.opendroid.ai.core.llm.providers

import com.opendroid.ai.core.llm.LLMProvider
import com.opendroid.ai.core.llm.LLMRequest
import com.opendroid.ai.core.llm.LLMResponse
import com.opendroid.ai.core.llm.ProviderCatalog
import com.opendroid.ai.core.llm.StreamChunk
import com.opendroid.ai.core.needle.Needle3LocalRuntime
import com.opendroid.ai.data.models.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
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

    override suspend fun complete(request: LLMRequest): LLMResponse = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val input = buildInput(request)
        val nativeResponse = runtime.complete(input, request.maxTokens.coerceIn(64, 1024))
        LLMResponse(
            content = adaptToExistingPlan(nativeResponse, input),
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
        val response = runtime.complete(messages.joinToString("\n") { it.text }, 512)
        emit(StreamChunk.Content(response))
        parseCalls(response).forEach { call ->
            emit(StreamChunk.ToolCall(call.first, call.second))
        }
    }

    override suspend fun isAvailable(): Boolean = runtime.status.value is com.opendroid.ai.core.needle.Needle3Status.Ready

    private fun buildInput(request: LLMRequest): String = buildString {
        append(request.systemPrompt.take(4_000))
        append("\n\n")
        request.messages.takeLast(8).forEach { message ->
            append(if (message.sender == ChatMessage.Sender.USER) "User: " else "Assistant: ")
            append(message.text.take(4_000))
            append('\n')
        }
    }

    private fun adaptToExistingPlan(raw: String, goal: String): String {
        val calls = parseCalls(raw)
        if (calls.isEmpty()) return raw
        return buildJsonObject {
            put("planId", "needle3-${System.currentTimeMillis()}")
            put("goal", goal.take(500))
            put("estimatedDuration", "instant")
            put("estimatedSteps", calls.size)
            putJsonArray("steps") {
                calls.forEachIndexed { index, call ->
                    val args = Json.parseToJsonElement(call.second).jsonObject
                    add(buildJsonObject {
                        put("stepId", "needle3-${index + 1}")
                        put("order", index + 1)
                        put("description", "Needle 3 selected ${call.first}")
                        put("action", call.first)
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

    private fun parseCalls(raw: String): List<Pair<String, String>> = runCatching {
        val root = Json.parseToJsonElement(raw).jsonObject
        val calls = root["function_calls"]?.jsonArray ?: return emptyList()
        calls.mapNotNull { element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val arguments = obj["arguments"]?.let { if (it is JsonObject) it.toString() else it.jsonPrimitive.content }
                ?: "{}"
            name to arguments
        }
    }.getOrDefault(emptyList())

    private fun JsonObject.toStringMap(): Map<String, String> = entries.associate { (key, value) ->
        key to when (value) {
            is JsonPrimitive -> value.content
            else -> value.toString()
        }
    }
}
