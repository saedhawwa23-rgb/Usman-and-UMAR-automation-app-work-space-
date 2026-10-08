package com.opendroid.ai.core.needle

import com.opendroid.ai.core.llm.ToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Needle3ProviderToolSchemaTest {
    @Test
    fun `list tools schema is minimal and alias maps back to the qualified registry name`() {
        val canonical = "connector_local_registry__list_available_tools"
        val prepared = Needle3ProviderToolSchema.prepare(listOf(
            ToolDefinition(canonical, "Verbose connector and risk metadata", "{\"type\":\"object\",\"properties\":{}}")
        ))
        val tool = Json.parseToJsonElement(prepared.json).jsonArray.single().jsonObject

        assertEquals(setOf("name", "description"), tool.keys)
        assertEquals("list_available_tools", tool.getValue("name").jsonPrimitive.content)
        assertEquals("List tools", tool.getValue("description").jsonPrimitive.content)
        assertEquals(canonical, prepared.canonicalNameByModelName["list_available_tools"])
        assertEquals(canonical, Needle3ProviderToolSchema.listAvailableToolsTool("List the available tools", listOf(
            ToolDefinition(canonical, "List", "{}")
        )))
    }

    @Test
    fun `parameter schema keeps only compact types enums and required names`() {
        val prepared = Needle3ProviderToolSchema.prepare(listOf(
            ToolDefinition(
                "connector_device__set_mode",
                "Very long connector metadata that should never be sent to the model",
                """{"type":"object","properties":{"mode":{"type":"string","description":"Long prose","enum":["on","off"],"default":"on"}},"required":["mode"]}"""
            )
        ))
        val tool = Json.parseToJsonElement(prepared.json).jsonArray.single().jsonObject
        val parameters = tool.getValue("parameters").jsonObject
        val mode = parameters.getValue("properties").jsonObject.getValue("mode").jsonObject

        assertEquals("set mode", tool.getValue("description").jsonPrimitive.content)
        assertEquals("string", mode.getValue("type").jsonPrimitive.content)
        assertEquals(listOf("on", "off"), mode.getValue("enum").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("mode", parameters.getValue("required").jsonArray.single().jsonPrimitive.content)
        assertFalse(mode.containsKey("description"))
        assertFalse(mode.containsKey("default"))
    }

    @Test
    fun `list tools route is narrow and does not catch unrelated requests`() {
        val canonical = "connector_local_registry__list_available_tools"
        val tools = listOf(ToolDefinition(canonical, "List", "{}"))
        assertEquals(canonical, Needle3ProviderToolSchema.listAvailableToolsTool("list tools", tools))
        assertEquals(null, Needle3ProviderToolSchema.listAvailableToolsTool("list tools and set a timer", tools))
        assertTrue(Needle3ProviderToolSchema.prepare(emptyList()).allowedNames.isEmpty())
    }
}
