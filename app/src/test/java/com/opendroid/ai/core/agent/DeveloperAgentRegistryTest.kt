package com.opendroid.ai.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperAgentRegistryTest {
    @Test
    fun registryStartsEmptyUntilAnAdapterIsExplicitlyRegistered() {
        assertEquals(0, DeveloperAgentRegistry().all().size)
    }

    @Test
    fun registrationPreservesExplicitNotTestedHealthAndCapabilities() {
        val registry = DeveloperAgentRegistry()
        val adapter = StubAdapter()
        registry.register(adapter)

        assertEquals(listOf(adapter), registry.all())
        assertEquals(AgentAvailability.NOT_TESTED, registry.healthSnapshot().single().second.availability)
        assertNotNull(registry.get("example-agent"))
        assertTrue(registry.all().single().descriptor.capabilities.features.isEmpty())
        assertEquals("Not assessed", registry.all().single().descriptor.architectureSupport)
    }

    @Test
    fun duplicateAgentIdsAreRejectedAndCanBeUnregistered() {
        val registry = DeveloperAgentRegistry()
        registry.register(StubAdapter())
        assertThrows(IllegalStateException::class.java) { registry.register(StubAdapter()) }
        assertTrue(registry.unregister("example-agent"))
        assertFalse(registry.unregister("example-agent"))
    }

    private class StubAdapter : CodingAgentAdapter {
        override val descriptor = CodingAgentDescriptor(
            id = "example-agent",
            name = "Example agent",
            latestRelease = null,
            releaseDate = null,
            license = null,
            upstreamUrl = "https://example.org/source",
            documentationUrl = "https://example.org/docs",
            runtimeRequirements = "Not assessed",
            architectureSupport = "Not assessed",
            androidApiSupport = "Not assessed",
            supportedExecutionBackends = "Not assessed",
            providerRequirements = "Not assessed",
            networkStorageAndPermissionRequirements = "Not assessed",
            securityBoundary = "Not assessed",
            metadataVerifiedAtMs = System.currentTimeMillis()
        )

        override fun healthCheck() = CodingAgentHealth(AgentAvailability.NOT_TESTED, "No installation or smoke test has been run.")

        override suspend fun start(request: AgentTaskRequest) = AgentTaskHandle("task-1", AgentTaskStatus.QUEUED)

        override suspend fun inspect(taskId: String) = AgentTaskSnapshot(taskId, AgentTaskStatus.UNKNOWN)

        override suspend fun cancel(taskId: String) = AgentOperationResult(false, false, "Not supported.")

        override suspend fun resume(taskId: String) = AgentOperationResult(false, false, "Not supported.")
    }
}
