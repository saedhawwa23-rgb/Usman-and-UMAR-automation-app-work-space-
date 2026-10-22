package com.opendroid.ai.core.backend

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendContractTest {
    @Test
    fun capabilitiesDefaultToUnsupportedRatherThanAssumed() {
        val capabilities = BackendCapabilities()
        assertFalse(capabilities.browseFiles)
        assertFalse(capabilities.executeCommands)
        assertFalse(capabilities.cancelCommands)
        assertFalse(capabilities.resourceLimits)
        assertFalse(capabilities.taskIsolation)
        assertFalse(capabilities.persistentTaskHistory)
    }

    @Test
    fun executionRequiresZeroExitCodeAndNoReportedError() {
        assertTrue(BackendExecutionResult("task-1", "ok", "", 0, null, 123L).succeeded)
        assertFalse(BackendExecutionResult("task-2", "", "failed", 1, null, 123L).succeeded)
        assertFalse(BackendExecutionResult("task-3", "", "", null, "timed out", 123L).succeeded)
    }
}
