package com.opendroid.ai.core.needle

import com.opendroid.ai.core.agent.ActionSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Parsed call data; callers must still dispatch through the app's existing safeguards. */
data class Needle3ParsedCall(val name: String, val argumentsJson: String)

object Needle3ResponseParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val textKeys = listOf("summary", "text", "content", "message", "answer", "response", "output", "result", "data")
    private val argumentKeys = listOf("arguments", "params", "args", "input")
    private val nameKeys = listOf("name", "action", "tool_name", "function_name", "tool")

    /**
     * Parse native `type: call`, OpenAI-style function_calls/tool_calls, and nested
     * function envelopes. Action plans accept only registered app actions; callers
     * supplying connector tools pass their explicit allowed-name set instead.
     */
    fun parseCalls(raw: String, allowedNames: Set<String>? = null): List<Needle3ParsedCall> = runCatching {
        val root = parseObject(raw) ?: return emptyList()
        val list = (root["function_calls"] as? JsonArray)
            ?: (root["tool_calls"] as? JsonArray)
            ?: (root["calls"] as? JsonArray)
        val calls = if (list != null) {
            list.mapNotNull { parseCallElement(it, allowedNames) }
        } else {
            listOfNotNull(parseCallElement(root, allowedNames))
        }
        calls
    }.getOrDefault(emptyList())

    /** Return display-safe text; a chat response can never leak raw call JSON. */
    fun toNaturalText(raw: String, userQuery: String): String {
        val root = parseObject(raw) ?: return raw.trim()
        val type = stringValue(root["type"]).orEmpty().lowercase()
        val calls = parseCalls(raw)
        val isCallEnvelope = type == "call" || calls.isNotEmpty()

        if (isCallEnvelope) {
            val success = (root["success"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
            val summary = textKeys.asSequence()
                .mapNotNull { key -> extractDisplayText(root[key]) }
                .firstOrNull(String::isNotBlank)
            if (success == true && !summary.isNullOrBlank()) return summary
            if (isGreeting(userQuery)) {
                return "Hi! What can I help you with?"
            }
            if (success == false || !stringValue(root["error"]).isNullOrBlank()) {
                return "I couldn't complete that action. Please try again."
            }
            return if (isGreeting(userQuery)) {
                "Hi! What can I help you with?"
            } else {
                "I can help with that. Please tell me what you'd like me to do."
            }
        }

        val text = textKeys.asSequence()
            .mapNotNull { key -> extractDisplayText(root[key]) }
            .firstOrNull(String::isNotBlank)
        if (!text.isNullOrBlank()) return text

        // Do not render unrecognized structured output as raw JSON in the chat UI.
        return "I couldn't format that local response. Please try again."
    }

    private fun parseCallElement(element: JsonElement, allowedNames: Set<String>?): Needle3ParsedCall? {
        val source = element as? JsonObject ?: return null
        val function = source["function"] as? JsonObject
        val nestedCall = source["call"] as? JsonObject
        val candidates = listOfNotNull(function, nestedCall, source)
        val name = candidates.asSequence()
            .flatMap { candidate -> nameKeys.asSequence().mapNotNull { stringValue(candidate[it]) } }
            .firstOrNull { candidateName ->
                candidateName.isNotBlank() &&
                    (allowedNames?.contains(candidateName) ?: ActionSchema.isValid(candidateName))
            }
            ?.trim()
            ?: return null

        val hasOutcome = source.containsKey("success") || source.containsKey("error")
        if (hasOutcome && function == null && nestedCall == null) return null
        val argumentValue = candidates.asSequence()
            .flatMap { candidate -> argumentKeys.asSequence().mapNotNull { candidate[it] } }
            .firstOrNull()
        val argumentsJson = normalizeArguments(argumentValue)
        return Needle3ParsedCall(name, argumentsJson)
    }

    private fun normalizeArguments(value: JsonElement?): String = when (value) {
        is JsonObject -> value.toString()
        is JsonPrimitive -> {
            if (!value.isString) "{}" else runCatching {
                val parsed = json.parseToJsonElement(value.content)
                if (parsed is JsonObject) parsed.toString() else "{}"
            }.getOrDefault("{}")
        }
        else -> "{}"
    }

    private fun parseObject(raw: String): JsonObject? {
        val stripped = raw.trim()
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            .removeSuffix("```").trim()
        return runCatching { json.parseToJsonElement(stripped).jsonObject }.getOrNull()
    }

    private fun extractDisplayText(element: JsonElement?): String? = when (element) {
        is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }
        is JsonObject -> textKeys.asSequence()
            .mapNotNull { key -> extractDisplayText(element[key]) }
            .firstOrNull(String::isNotBlank)
        is JsonArray -> element.mapNotNull(::extractDisplayText).takeIf { it.isNotEmpty() }?.joinToString("\n")
        else -> null
    }

    private fun stringValue(value: JsonElement?): String? =
        (value as? JsonPrimitive)?.contentOrNull

    private fun isGreeting(query: String): Boolean = query.trim().lowercase()
        .matches(Regex("^(hi|hello|hey|hiya|good morning|good afternoon|good evening)[!.?, ]*$"))
}
