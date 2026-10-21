package com.opendroid.ai.ui

import com.opendroid.ai.ui.screens.CapabilityState
import com.opendroid.ai.ui.screens.DeveloperCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperCatalogTest {
    @Test
    fun catalogContainsExactlyOneEntryForEachLetterInOrder() {
        assertEquals(('A'..'Z').toList(), DeveloperCatalog.all.map { it.letter })
        assertEquals(26, DeveloperCatalog.all.distinctBy { it.letter }.size)
    }

    @Test
    fun searchMatchesLetterTitleAndDescriptionCaseInsensitively() {
        assertTrue(DeveloperCatalog.search("p").any { it.letter == 'P' })
        assertTrue(DeveloperCatalog.search("github").any { it.letter == 'G' })
        assertTrue(DeveloperCatalog.search("file manager").any { it.letter == 'F' })
        assertTrue(DeveloperCatalog.search("definitely absent").isEmpty())
    }

    @Test
    fun blankSearchReturnsWholeCatalogAndUnsupportedToolsAreMarkedUnavailable() {
        assertEquals(26, DeveloperCatalog.search("  ").size)
        assertTrue(DeveloperCatalog.all.filter { it.action == null }.all { it.state == CapabilityState.NOT_AVAILABLE })
        assertEquals("NOT AVAILABLE IN APP", DeveloperCatalog.stateLabel(CapabilityState.NOT_AVAILABLE))
    }
}
