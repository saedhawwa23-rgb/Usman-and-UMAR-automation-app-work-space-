package com.opendroid.ai.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkspacePathPolicyTest {
    @Test
    fun acceptsAndPreservesSafeRelativePaths() {
        assertEquals("project/src/Main.kt", WorkspacePathPolicy.normalizeRelative("project/src/Main.kt"))
        assertEquals("notes.txt", WorkspacePathPolicy.validateName("notes.txt"))
    }

    @Test
    fun rejectsTraversalAbsoluteAndEmptyComponents() {
        listOf("../outside", "project/../outside", "/sdcard/private", "project//file.txt", "./file.txt", "C:\\private").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) { WorkspacePathPolicy.normalizeRelative(path) }
        }
    }

    @Test
    fun rejectsNamesThatCouldEscapeTheirParentFolder() {
        listOf("", ".", "..", "dir/file.txt", "dir\\file.txt").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) { WorkspacePathPolicy.validateName(name) }
        }
    }
}
