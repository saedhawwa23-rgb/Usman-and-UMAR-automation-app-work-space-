package com.opendroid.ai.core.needle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Needle3PromptBudgetTest {
    @Test
    fun `all action schema is compacted below the native static prefix ceiling`() {
        val prefix = Needle3PromptBudget.fitStaticPrefix(
            "You are USMAN AND UMAR's local Android tool planner. Select only evidenced tool calls.",
            Needle3ToolSchema.json()
        )
        val tools = Json.parseToJsonElement(prefix.toolsJson).jsonArray

        assertTrue(prefix.inputBytes <= Needle3PromptBudget.MAX_STATIC_INPUT_BYTES)
        assertTrue(Needle3PromptBudget.utf8Size(prefix.systemPrompt) +
            Needle3PromptBudget.utf8Size(prefix.toolsJson) <= Needle3PromptBudget.MAX_STATIC_INPUT_BYTES)
        assertTrue("Expected a useful, non-empty subset of registered actions", tools.isNotEmpty())
        assertTrue("Oversized catalog should be compacted", tools.size < Needle3ToolSchema.definitions().size)
    }

    @Test
    fun `schema compaction preserves types required fields and enums but removes prose`() {
        val original = buildJsonArray {
            add(buildJsonObject {
                put("name", "TOGGLE_FLASHLIGHT")
                put("description", "A very verbose tool description that should not survive compaction")
                put("parameters", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("state", buildJsonObject {
                            put("type", "string")
                            put("description", "A verbose parameter explanation")
                            put("default", "toggle")
                            put("enum", buildJsonArray {
                                add(kotlinx.serialization.json.JsonPrimitive("on"))
                                add(kotlinx.serialization.json.JsonPrimitive("off"))
                                add(kotlinx.serialization.json.JsonPrimitive("toggle"))
                            })
                        })
                    })
                    put("required", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("state")) })
                })
            })
        }.toString()

        val compact = Json.parseToJsonElement(Needle3PromptBudget.compactToolsJson(original, 600)).jsonArray
        val tool = compact.first().jsonObject
        val parameters = tool.getValue("parameters").jsonObject
        val property = parameters.getValue("properties").jsonObject.getValue("state").jsonObject

        assertEquals("TOGGLE_FLASHLIGHT", tool.getValue("name").jsonPrimitive.content)
        assertEquals("string", property.getValue("type").jsonPrimitive.content)
        assertEquals(listOf("on", "off", "toggle"),
            property.getValue("enum").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("state", parameters.getValue("required").jsonArray.single().jsonPrimitive.content)
        assertFalse(property.containsKey("description"))
        assertFalse(property.containsKey("default"))
        assertTrue(Needle3PromptBudget.utf8Size(compact.toString()) <= 600)
    }

    @Test
    fun `completion input is byte bounded and retains its newest suffix`() {
        val input = "system prompt and old history ".repeat(300) + "LATEST USER REQUEST"
        val bounded = Needle3PromptBudget.boundCompletionInput(input)

        assertTrue(Needle3PromptBudget.utf8Size(bounded) <= Needle3PromptBudget.MAX_COMPLETION_INPUT_BYTES)
        assertTrue(bounded.endsWith("LATEST USER REQUEST"))
        assertTrue(bounded.startsWith("[Earlier context omitted]"))
    }
}
