package com.opendroid.ai.core.needle

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class Qwen3BundledModelTest {
    private fun asset(relativePath: String): File {
        val candidates = listOf(
            File("src/main/assets/$relativePath"),
            File("app/src/main/assets/$relativePath")
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("Bundled asset not found: $relativePath (checked ${candidates.joinToString()})")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `bundled Qwen GGUF matches pinned artifact`() {
        val model = asset("models/qwen3.gguf")
        assertEquals(Needle3LocalRuntime.MODEL_SIZE, model.length())
        assertEquals(Needle3LocalRuntime.MODEL_SHA256, sha256(model))
    }
}
