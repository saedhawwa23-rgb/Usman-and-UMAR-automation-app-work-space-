package com.opendroid.ai.core.storage

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WorkspaceAtomicWriterTest {
    @Test
    fun replacesExistingFileAndCleansTemporaryFile() {
        val directory = Files.createTempDirectory("workspace-atomic-").toFile()
        try {
            val target = File(directory, "Main.kt")
            target.writeText("old source", Charsets.UTF_8)

            WorkspaceAtomicWriter.write(target, "updated source".toByteArray(Charsets.UTF_8))

            assertEquals("updated source", target.readText(Charsets.UTF_8))
            assertFalse(directory.listFiles().orEmpty().any { it.name.startsWith(".usman-") })
        } finally {
            directory.deleteRecursively()
        }
    }
}
