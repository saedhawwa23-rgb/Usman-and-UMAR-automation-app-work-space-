package com.opendroid.ai.core.termux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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

    @Test
    fun waitTimeoutAcceptsOnlyOneToThirtySeconds() {
        TermuxExecutionPolicy.validateWait(TermuxExecutionPolicy.MIN_WAIT_MS)
        TermuxExecutionPolicy.validateWait(TermuxExecutionPolicy.MAX_WAIT_MS)
        listOf(0L, 999L, 30_001L, Long.MAX_VALUE).forEach { timeout ->
            assertThrows(IllegalArgumentException::class.java) { TermuxExecutionPolicy.validateWait(timeout) }
        }
    }

    @Test
    fun cancellationIsReportedAsUnsupportedAndMayLeaveProcessRunning() {
        val result = TermuxExecutionPolicy.cancellationUnsupported()
        assertFalse(result.supported)
        assertFalse(result.succeeded)
        assertTrue(result.detail.contains("may still be running"))
    }
}
