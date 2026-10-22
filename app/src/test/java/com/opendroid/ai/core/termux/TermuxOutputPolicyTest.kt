package com.opendroid.ai.core.termux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxOutputPolicyTest {
    @Test
    fun leavesSmallOutputUnchanged() {
        assertEquals("hello\n", TermuxOutputPolicy.capture("hello\n"))
    }

    @Test
    fun boundsLargeOutputAndShowsThatItWasTruncated() {
        val captured = TermuxOutputPolicy.capture("x".repeat(TermuxOutputPolicy.MAX_CAPTURED_CHARS_PER_STREAM + 100))
        assertTrue(captured.length <= TermuxOutputPolicy.MAX_CAPTURED_CHARS_PER_STREAM)
        assertTrue(captured.endsWith("[output truncated by app limit]"))
    }
}
