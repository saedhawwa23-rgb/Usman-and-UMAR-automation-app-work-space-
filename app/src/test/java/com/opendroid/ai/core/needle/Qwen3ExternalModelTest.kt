package com.opendroid.ai.core.needle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class Qwen3ExternalModelTest {
    @Test
    fun `model path and missing-model instruction are stable`() {
        assertEquals("/sdcard/Download/qwen3.gguf", Needle3LocalRuntime.MODEL_FILE_PATH)
        assertEquals(
            "Please download the Qwen 3 model and place it in /sdcard/Download/",
            Needle3LocalRuntime.MODEL_MISSING_MESSAGE
        )
        assertEquals(484_220_320L, Needle3LocalRuntime.MODEL_SIZE)
        assertEquals("9acfc1e001311f34b4252001b626f2e466d592a42065f66571bff3790d4e1b14", Needle3LocalRuntime.MODEL_SHA256)
    }

    @Test
    fun `Qwen GGUF is not present in app assets`() {
        assertFalse(File("src/main/assets/models/qwen3.gguf").isFile)
        assertFalse(File("app/src/main/assets/models/qwen3.gguf").isFile)
    }
}
