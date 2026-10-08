package com.opendroid.ai.core.needle

import com.opendroid.ai.core.llm.ToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.Locale

/** Compact model-facing names mapped back to the registry's canonical qualified names. */
data class Needle3PreparedToolSchema(
    val json: String,
    val canonicalNameByModelName: Map<String, String>
) {
    val allowedNames: Set<String> get() = canonicalNameByModelName.keys
}

object Needle3ProviderToolSchema {
    private const val LIST_TOOLS_CANONICAL = "connector_local_registry__list_available_tools"
    private val listToolsRequests = setOf(
        "list tools",
        "list available tools",
        "list the available tools",
        "show tools",
        "show available tools",
        "show the available tools",
        "what tools are available",
        "what are the available tools"
    )

    /** Strip connector/risk prose and use short aliases only when they are unambiguous. */
    fun prepare(tools: List<ToolDefinition>): Needle3PreparedToolSchema {
        val aliasCounts = tools.groupingBy { shortName(it.name).lowercase(Locale.ROOT) }.eachCount()
        val names = linkedMapOf<String, String>()
        val definitions = buildJsonArray {
            tools.forEach { tool ->
                val alias = shortName(tool.name)
                val modelName = if (aliasCounts[alias.lowercase(Locale.ROOT)] == 1) alias else tool.name
                names[modelName] = tool.name
                names[tool.name] = tool.name
                add(buildJsonObject {
                    put("name", modelName)
                    put("description", shortDescription(alias))
                    compactParameters(tool.parameters)?.let { put("parameters", it) }
                })
            }
        }
        return Needle3PreparedToolSchema(definitions.toString(), names)
    }

    /** Deterministically route the supported read-only registry query to its registered tool. */
    fun listAvailableToolsTool(userQuery: String, tools: List<ToolDefinition>): String? {
        val normalized = userQuery.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9 ]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        if (normalized !in listToolsRequests) return null
        return tools.firstOrNull { it.name == LIST_TOOLS_CANONICAL }?.name
            ?: tools.singleOrNull { shortName(it.name) == "list_available_tools" }?.name
    }

    private fun shortName(name: String): String = name.substringAfterLast("__").substringAfterLast('.')

    private fun shortDescription(name: String): String = when (name.lowercase(Locale.ROOT)) {
        "list_available_tools" -> "List tools"
        "list_connectors" -> "List connectors"
        else -> name.replace('_', ' ').lowercase(Locale.ROOT)
    }

    /** Keep only executable JSON-schema structure; remove descriptions/defaults/prose. */
    private fun compactParameters(raw: String): JsonObject? = runCatching {
        val root = Json.parseToJsonElement(raw).jsonObject
        val properties = root["properties"] as? JsonObject ?: return null
        if (properties.isEmpty()) return null
        compactSchema(root, 0).takeIf { (it["properties"] as? JsonObject)?.isNotEmpty() == true }
    }.getOrNull()

    private fun compactSchema(source: JsonObject, depth: Int): JsonObject = buildJsonObject {
        source["type"]?.let { put("type", it) }
        source["enum"]?.let { put("enum", it) }
        if (depth < 3) {
            (source["properties"] as? JsonObject)?.let { sourceProperties ->
                put("properties", buildJsonObject {
                    sourceProperties.forEach { (key, value) ->
                        val property = value as? JsonObject ?: return@forEach
                        put(key, compactSchema(property, depth + 1))
                    }
                })
            }
            (source["items"] as? JsonObject)?.let { put("items", compactSchema(it, depth + 1)) }
        }
        (source["required"] as? JsonArray)?.let { required ->
            val known = (source["properties"] as? JsonObject)?.keys.orEmpty()
            val retained = required.filter { (it as? JsonPrimitive)?.contentOrNull in known }
            if (retained.isNotEmpty()) put("required", JsonArray(retained))
        }
        (source["additionalProperties"] as? JsonPrimitive)?.let { put("additionalProperties", it) }
    }
}
