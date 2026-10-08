package com.opendroid.ai.core.needle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Needle3ResponseParserTest {
    @Test
    fun `native type call envelope is extracted for the safe plan dispatcher`() {
        val calls = Needle3ResponseParser.parseCalls(
            """{"type":"call","name":"SET_TIMER","arguments":{"duration":60,"label":"Tea"}}"""
        )

        assertEquals(1, calls.size)
        assertEquals("SET_TIMER", calls.single().name)
        val args = Json.parseToJsonElement(calls.single().argumentsJson).jsonObject
        assertEquals("60", args.getValue("duration").jsonPrimitive.content)
        assertEquals("Tea", args.getValue("label").jsonPrimitive.content)
    }

    @Test
    fun `function calls envelope supports serialized argument strings`() {
        val calls = Needle3ResponseParser.parseCalls(
            """{"function_calls":[{"function":{"name":"GET_SYSTEM_INFO","arguments":"{}"}}]}"""
        )
        assertEquals("GET_SYSTEM_INFO", calls.single().name)
        assertEquals("{}", calls.single().argumentsJson)
    }

    @Test
    fun `unknown and unprovided tool names are rejected`() {
        val unknown = """{"type":"call","name":"DELETE_ALL_DATA","arguments":{}}"""
        assertTrue(Needle3ResponseParser.parseCalls(unknown).isEmpty())

        val knownButUnprovided = """{"type":"call","name":"SET_TIMER","arguments":{}}"""
        assertTrue(Needle3ResponseParser.parseCalls(knownButUnprovided, setOf("GET_SYSTEM_INFO")).isEmpty())
    }

    @Test
    fun `text response envelope displays its text instead of JSON`() {
        val output = Needle3ResponseParser.toNaturalText(
            """{"type":"text","content":"Hi there!"}""",
            "hi"
        )
        assertEquals("Hi there!", output)
        assertFalse(output.startsWith("{"))
    }

    @Test
    fun `unexpected tool call during a greeting becomes natural chat text`() {
        val raw = """{"type":"call","name":"GET_SYSTEM_INFO","arguments":{}}"""
        val output = Needle3ResponseParser.toNaturalText(raw, "hi")
        assertEquals("Hi! What can I help you with?", output)
        assertFalse(output.contains("type"))
    }

    @Test
    fun `call result envelope with internal error is not exposed for a greeting`() {
        val raw = """{"type":"call","success":true,"error":"internal native detail"}"""
        assertEquals("Hi! What can I help you with?", Needle3ResponseParser.toNaturalText(raw, "hi"))
    }

    @Test
    fun `truncated call envelope from the screenshot becomes a greeting`() {
        val raw = "{\"type\":\"call\",\"success\""
        assertEquals("Hi! What can I help you with?", Needle3ResponseParser.toNaturalText(raw, "hi"))
    }

    @Test
    fun `truncated structured output for other requests is not rendered raw`() {
        val raw = "{\"type\":\"call\",\"success\""
        val output = Needle3ResponseParser.toNaturalText(raw, "summarize the document")
        assertEquals("I couldn't format that local response. Please try again.", output)
        assertFalse(output.contains("{\"type\""))
    }

    @Test
    fun `regex fallback recovers an allowlisted tool alias from truncated JSON`() {
        val raw = "{\"type\":\"call\",\"tool\":\"list_available_tools\""
        val calls = Needle3ResponseParser.parseCalls(raw, setOf("list_available_tools"))
        assertEquals(1, calls.size)
        assertEquals("list_available_tools", calls.single().name)
        assertEquals("{}", calls.single().argumentsJson)
        assertTrue(Needle3ResponseParser.parseCalls(raw, setOf("other_tool")).isEmpty())
        assertTrue(
            Needle3ResponseParser.parseCalls(
                "Text mentioning \\\"tool\\\":\\\"list_available_tools\\\" but not a call object",
                setOf("list_available_tools")
            ).isEmpty()
        )
    }

    @Test
    fun `unparseable tool envelopes receive the specified simple-command fallback`() {
        assertEquals(
            "Tool parsing failed. Try a simpler command like 'list tools'.",
            Needle3ResponseParser.toolCallFailureOrNaturalText("{\"type\":\"call\",\"tool\":", "list tools")
        )
        assertEquals(
            "A normal response",
            Needle3ResponseParser.toolCallFailureOrNaturalText("A normal response", "hello")
        )
    }

    @Test
    fun `simple greeting detection does not swallow longer requests`() {
        assertEquals("Hi! What can I help you with?", Needle3ResponseParser.greetingReply("hi!"))
        assertEquals(null, Needle3ResponseParser.greetingReply("hi, set a timer"))
    }

    @Test
    fun `successful call result renders its human-readable summary`() {
        val raw = """{"type":"call","success":true,"result":{"message":"Timer set for one minute."}}"""
        assertEquals("Timer set for one minute.", Needle3ResponseParser.toNaturalText(raw, "set a timer"))
    }
}
