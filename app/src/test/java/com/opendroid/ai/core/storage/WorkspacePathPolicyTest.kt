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

    @Test
    fun buildsSafeMoveTargetWithinWorkspace() {
        assertEquals("notes.txt", WorkspacePathPolicy.moveTarget("project/notes.txt", ""))
        assertEquals("archive/notes.txt", WorkspacePathPolicy.moveTarget("project/notes.txt", "archive"))
    }

    @Test
    fun buildsSafeRenameTargetsAtRootAndInsideFolders() {
        assertEquals("new.txt", WorkspacePathPolicy.renameTarget("old.txt", "new.txt"))
        assertEquals("src/main/New.kt", WorkspacePathPolicy.renameTarget("src/main/Old.kt", "New.kt"))
        assertThrows(IllegalArgumentException::class.java) {
            WorkspacePathPolicy.renameTarget("src/Old.kt", "../outside")
        }
    }

    @Test
    fun rejectsMovingFolderIntoItselfOrDescendant() {
        listOf("../outside", "/absolute", "project/child", "project/child/deeper").forEach { destination ->
            assertThrows(IllegalArgumentException::class.java) {
                WorkspacePathPolicy.moveTarget("project", destination)
            }
        }
    }
}
