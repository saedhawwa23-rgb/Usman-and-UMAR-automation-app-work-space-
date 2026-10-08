package com.opendroid.ai.core.needle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Conservative limits for Needle 3's 8,192-token context. The native API does not
 * expose a tokenizer/count-only call, so UTF-8 bytes are used as a strict upper
 * bound for byte-fallback tokenizers. We reserve 1,000 context tokens for the
 * native chat template/special tokens, and keep actual static inputs below
 * 3,000 bytes. Dynamic input and output are bounded separately.
 */
object Needle3PromptBudget {
    const val MAX_STATIC_INPUT_BYTES = 3_000
    const val RESERVED_TEMPLATE_TOKENS = 1_000
    const val MAX_COMPLETION_INPUT_BYTES = 2_500
    const val MAX_NEW_TOKENS = 512

    private const val SYSTEM_PROMPT_BYTES = 512
    private const val TOOLS_JSON_OVERHEAD_BYTES = 2
    private const val INPUT_OMISSION_MARKER = "[Earlier context omitted]\n"

    data class StaticPrefix(
        val systemPrompt: String,
        val toolsJson: String,
        val inputBytes: Int
    )

    /** Limit both native-init inputs, retaining a valid JSON tools array. */
    fun fitStaticPrefix(systemPrompt: String, toolsJson: String): StaticPrefix {
        val boundedSystem = truncateUtf8(systemPrompt, SYSTEM_PROMPT_BYTES)
        val remaining = (MAX_STATIC_INPUT_BYTES - utf8Size(boundedSystem))
            .coerceAtLeast(TOOLS_JSON_OVERHEAD_BYTES)
        val boundedTools = compactToolsJson(toolsJson, remaining)
        val bytes = utf8Size(boundedSystem) + utf8Size(boundedTools)
        check(bytes <= MAX_STATIC_INPUT_BYTES) { "Needle static inputs exceed byte budget: $bytes" }
        return StaticPrefix(boundedSystem, boundedTools, bytes)
    }

    /**
     * Retain valid, compact tool definitions in their given order. Descriptions,
     * defaults and prose parameter descriptions are removed; names, JSON types,
     * required fields and enum constraints remain. Whole tools are omitted rather
     * than emitting partial or invalid JSON when the byte budget is reached.
     */
    fun compactToolsJson(source: String, maxBytes: Int): String {
        val budget = maxBytes.coerceAtLeast(TOOLS_JSON_OVERHEAD_BYTES)
        val sourceArray = runCatching { Json.parseToJsonElement(source) as? JsonArray }
            .getOrNull() ?: JsonArray(emptyList())
        val output = mutableListOf<JsonElement>()
        for (element in sourceArray) {
            val original = element as? JsonObject ?: continue
            val compact = compactTool(original) ?: continue
            val candidate = JsonArray(output + compact).toString()
            if (utf8Size(candidate) <= budget) output.add(compact)
        }
        val result = JsonArray(output).toString()
        check(utf8Size(result) <= budget) { "Compacted Needle tool schema exceeds byte budget" }
        return result
    }

    /** Bound completion input while preserving the most recent user/request text. */
    fun boundCompletionInput(input: String): String {
        if (utf8Size(input) <= MAX_COMPLETION_INPUT_BYTES) return input
        val markerBytes = utf8Size(INPUT_OMISSION_MARKER)
        val suffix = takeLastUtf8(input, (MAX_COMPLETION_INPUT_BYTES - markerBytes).coerceAtLeast(0))
        return INPUT_OMISSION_MARKER + suffix
    }

    fun utf8Size(value: String): Int = value.toByteArray(Charsets.UTF_8).size

    private fun compactTool(source: JsonObject): JsonObject? {
        val name = source["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (name.isEmpty()) return null

        val description = source["description"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val originalParameters = source["parameters"] as? JsonObject
        val originalProperties = originalParameters?.get("properties") as? JsonObject
        val properties = buildJsonObject {
            originalProperties?.forEach { (key, value) ->
                val sourceProperty = value as? JsonObject ?: return@forEach
                val compactProperty = buildJsonObject {
                    sourceProperty["type"]?.let { put("type", it) }
                    val enumValues = sourceProperty["enum"] as? JsonArray
                    if (enumValues != null && enumValues.isNotEmpty()) put("enum", enumValues)
                }
                if (compactProperty.isNotEmpty()) put(key, compactProperty)
            }
        }
        val propertyNames = properties.keys
        val required = (originalParameters?.get("required") as? JsonArray)
            ?.filter { value -> value.jsonPrimitive.contentOrNull in propertyNames }
            ?: emptyList()
        val parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("properties", properties)
            if (required.isNotEmpty()) put("required", JsonArray(required))
        }
        return buildJsonObject {
            put("name", JsonPrimitive(name))
            if (description.isNotEmpty()) put("description", JsonPrimitive(description.take(64)))
            put("parameters", parameters)
        }
    }

    private fun truncateUtf8(value: String, maxBytes: Int): String {
        if (utf8Size(value) <= maxBytes) return value
        var end = 0
        var used = 0
        while (end < value.length) {
            val codePoint = value.codePointAt(end)
            val width = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).size
            if (used + width > maxBytes) break
            used += width
            end += Character.charCount(codePoint)
        }
        return value.substring(0, end)
    }

    private fun takeLastUtf8(value: String, maxBytes: Int): String {
        if (utf8Size(value) <= maxBytes) return value
        var start = value.length
        var used = 0
        while (start > 0) {
            val codePoint = value.codePointBefore(start)
            val width = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).size
            if (used + width > maxBytes) break
            used += width
            start -= Character.charCount(codePoint)
        }
        return value.substring(start)
    }
}
