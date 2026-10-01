package com.opendroid.ai.core.connector

import com.opendroid.ai.core.security.CredentialStoreResult
import com.opendroid.ai.core.security.ProviderCredentialId
import com.opendroid.ai.core.security.ProviderCredentialStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ServiceStatus { NOT_INSTALLED, AVAILABLE, NOT_CONNECTED, CONNECTED, ERROR }

data class CloudflareServiceState(
    val status: ServiceStatus,
    val message: String
)

/** Cloudflare is deliberately a manifest until the user supplies credentials. */
@Singleton
class CloudflareConnectorManager @Inject constructor(
    private val registry: UniversalToolRegistry,
    private val credentials: ProviderCredentialStore
) {
    private val mutableState = MutableStateFlow(initialState())
    val state: StateFlow<CloudflareServiceState> = mutableState.asStateFlow()

    init { registerManifestIfConnected() }

    fun refresh() {
        mutableState.value = initialState()
        if (mutableState.value.status == ServiceStatus.CONNECTED) registerManifestIfConnected()
    }

    /** Stores only in the existing Android Keystore-backed credential store. No API call is made. */
    fun connect(apiToken: String): Boolean {
        if (apiToken.isBlank()) {
            mutableState.value = CloudflareServiceState(ServiceStatus.ERROR, "Cloudflare API token is required")
            return false
        }
        return when (credentials.write(ProviderCredentialId.ApiKey(CREDENTIAL_ID), apiToken.trim())) {
            is CredentialStoreResult.Success -> {
                registerManifestIfConnected()
                mutableState.value = CloudflareServiceState(
                    ServiceStatus.CONNECTED,
                    "Credential stored securely. Cloudflare API calls remain user-authorized."
                )
                true
            }
            CredentialStoreResult.CredentialsMustBeReentered,
            CredentialStoreResult.StorageUnavailable -> {
                mutableState.value = CloudflareServiceState(ServiceStatus.ERROR, "Secure credential storage is unavailable")
                false
            }
        }
    }

    fun disconnect() {
        credentials.remove(ProviderCredentialId.ApiKey(CREDENTIAL_ID))
        registry.unregister(CONNECTOR_ID)
        mutableState.value = CloudflareServiceState(ServiceStatus.NOT_CONNECTED, "Not connected")
    }

    private fun initialState(): CloudflareServiceState = when (val result = credentials.read(ProviderCredentialId.ApiKey(CREDENTIAL_ID))) {
        is CredentialStoreResult.Success -> if (!result.value.isNullOrBlank()) {
            CloudflareServiceState(ServiceStatus.CONNECTED, "Connected credential; no API call has been made")
        } else CloudflareServiceState(ServiceStatus.NOT_CONNECTED, "Not connected")
        CredentialStoreResult.CredentialsMustBeReentered -> CloudflareServiceState(ServiceStatus.ERROR, "Cloudflare credential must be re-entered")
        CredentialStoreResult.StorageUnavailable -> CloudflareServiceState(ServiceStatus.ERROR, "Secure credential storage is unavailable")
    }

    private fun registerManifestIfConnected() {
        if (initialState().status != ServiceStatus.CONNECTED) return
        registry.registerHttpManifest(
            id = CONNECTOR_ID,
            displayName = "Cloudflare",
            kind = ConnectorKind.CLOUD,
            baseUrl = "https://api.cloudflare.com/client/v4",
            tools = CLOUDFLARE_TOOLS,
            methodByTool = mapOf("list_deployments" to "GET", "deploy_project" to "POST"),
            pathByTool = mapOf("list_deployments" to "accounts/{accountId}/pages/projects/{projectName}/deployments", "deploy_project" to "accounts/{accountId}/pages/projects/{projectName}/deployments")
        )
    }

    companion object {
        const val CONNECTOR_ID = "cloudflare"
        const val CREDENTIAL_ID = "cloudflare-api-token"
        private val CLOUDFLARE_TOOLS = listOf(
            ConnectorTool(
                CONNECTOR_ID, "list_deployments", "List Cloudflare Pages deployments for a project. No mutation.",
                "{\"type\":\"object\",\"required\":[\"accountId\",\"projectName\"],\"properties\":{\"accountId\":{\"type\":\"string\"},\"projectName\":{\"type\":\"string\"}}}",
                ConnectorRisk.READ_ONLY, ConnectorCredentialRef(CREDENTIAL_ID, "api_token")
            ),
            ConnectorTool(
                CONNECTOR_ID, "deploy_project", "Start a Cloudflare Pages deployment. Requires explicit user approval.",
                "{\"type\":\"object\",\"required\":[\"accountId\",\"projectName\"],\"properties\":{\"accountId\":{\"type\":\"string\"},\"projectName\":{\"type\":\"string\"},\"branch\":{\"type\":\"string\"}}}",
                ConnectorRisk.SENSITIVE, ConnectorCredentialRef(CREDENTIAL_ID, "api_token")
            )
        )
    }
}
