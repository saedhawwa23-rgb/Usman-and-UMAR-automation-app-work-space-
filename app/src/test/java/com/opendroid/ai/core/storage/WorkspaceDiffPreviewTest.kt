package com.opendroid.ai.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceDiffPreviewTest {
    @Test
    fun reportsChangedLinesWithNearbyContext() {
        val preview = WorkspaceDiffPreview.create("one\nold\nthree", "one\nnew\nthree")
        assertEquals(1, preview.removedLines)
        assertEquals(1, preview.addedLines)
        assertTrue(preview.text.contains("- old"))
        assertTrue(preview.text.contains("+ new"))
        assertTrue(preview.text.contains("  one"))
        assertFalse(preview.truncated)
    }

    @Test
    fun noChangeReturnsEmptyDiff() {
        assertEquals("No changes.", WorkspaceDiffPreview.create("same", "same").text)
    }

    @Test
    fun capsPreviewForLargeSingleLineEdits() {
        val preview = WorkspaceDiffPreview.create("a".repeat(100_000), "b".repeat(100_000))
        assertTrue(preview.truncated)
        assertTrue(preview.text.length < 700)
    }
}
