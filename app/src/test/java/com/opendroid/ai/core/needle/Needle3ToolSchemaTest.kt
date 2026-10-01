package com.opendroid.ai.core.needle

import com.opendroid.ai.core.agent.ActionSchema
import com.opendroid.ai.core.agent.ParamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Needle3ToolSchemaTest {
    @Test
    fun `schema has one structured definition for every registered action`() {
        val definitions = Needle3ToolSchema.definitions()
        assertEquals(ActionSchema.ALL_ACTIONS.size, definitions.size)
        assertTrue(ActionSchema.isValid("GET_SYSTEM_INFO"))
    }

    @Test
    fun `schema preserves enum constraints for existing action safety`() {
        val action = ActionSchema.getAction("TOGGLE_FLASHLIGHT")!!
        val state = action.params.single { it.name == "state" }
        assertEquals(ParamType.ENUM, state.type)
        assertEquals(listOf("on", "off", "toggle"), state.enumValues)
        assertTrue(Needle3ToolSchema.definitions().isNotEmpty())
    }
}
