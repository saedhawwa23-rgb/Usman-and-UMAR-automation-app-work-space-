package com.opendroid.ai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opendroid.ai.data.models.DynamicAIProvider
import com.opendroid.ai.data.models.GitHubProject
import com.opendroid.ai.data.repository.DiscoveryRepository
import com.opendroid.ai.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val discoveryRepository: DiscoveryRepository
) : ViewModel() {
    private val _githubResults = MutableStateFlow<List<GitHubProject>>(emptyList())
    val githubResults: StateFlow<List<GitHubProject>> = _githubResults.asStateFlow()
    private val _aiResults = MutableStateFlow<List<GitHubProject>>(emptyList())
    val aiResults: StateFlow<List<GitHubProject>> = _aiResults.asStateFlow()
    private val _savedProjects = MutableStateFlow<List<GitHubProject>>(emptyList())
    val savedProjects: StateFlow<List<GitHubProject>> = _savedProjects.asStateFlow()
    private val _savedProviders = MutableStateFlow<List<DynamicAIProvider>>(emptyList())
    val savedProviders: StateFlow<List<DynamicAIProvider>> = _savedProviders.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init { refreshSaved() }

    fun refreshSaved() = viewModelScope.launch {
        val config = settingsRepository.llmConfig.first()
        _savedProviders.value = config.dynamicAIProviders
        _savedProjects.value = config.supportedGitHubProjects
    }

    fun searchGitHub(query: String) = viewModelScope.launch {
        _busy.value = true
        _message.value = null
        runCatching { discoveryRepository.searchGitHub(query) }
            .onSuccess { _githubResults.value = it }
            .onFailure { _message.value = it.message ?: "GitHub search failed" }
        _busy.value = false
    }

    /** Searches live GitHub metadata instead of depending on a fixed provider list. */
    fun searchAI(query: String) = viewModelScope.launch {
        _busy.value = true
        _message.value = null
        val search = query.trim().ifBlank { "AI provider API OpenAI compatible" }
        runCatching { discoveryRepository.searchGitHub(search) }
            .onSuccess { _aiResults.value = it }
            .onFailure { _message.value = it.message ?: "AI search failed" }
        _busy.value = false
    }

    fun openRepository(url: String) = viewModelScope.launch {
        _busy.value = true
        _message.value = null
        runCatching { discoveryRepository.loadGitHubRepository(url) }
            .onSuccess { addProject(it) }
            .onFailure { _message.value = it.message ?: "Repository lookup failed" }
        _busy.value = false
    }

    fun addProject(project: GitHubProject) = viewModelScope.launch {
        settingsRepository.updateConfig { config ->
            config.copy(supportedGitHubProjects = (config.supportedGitHubProjects + project).distinctBy { it.id })
        }
        refreshSaved()
    }

    fun useProject(project: GitHubProject) = viewModelScope.launch {
        settingsRepository.updateConfig { config ->
            config.copy(
                supportedGitHubProjects = (config.supportedGitHubProjects + project).distinctBy { it.id },
                activeGitHubProjectId = project.id
            )
        }
        refreshSaved()
        _message.value = "Using ${project.fullName} as the active project context."
    }

    fun addProvider(provider: DynamicAIProvider, apiKey: String) = viewModelScope.launch {
        val storedProvider = provider.copy(
            id = if (provider.id.startsWith("discovered:")) provider.id else "discovered:${provider.id}"
        )
        settingsRepository.updateConfig { config ->
            val providers = (config.dynamicAIProviders.filterNot { it.id == storedProvider.id } + storedProvider)
            val keys = config.apiKeys + (storedProvider.id to apiKey)
            val endpoints = config.customEndpoints + (storedProvider.id to storedProvider.endpoint)
            val models = (config.selectedModels.orEmpty() + (storedProvider.id to storedProvider.model))
            config.copy(dynamicAIProviders = providers, apiKeys = keys, customEndpoints = endpoints,
                selectedModels = models, activeProvider = storedProvider.id, activeModel = storedProvider.model)
        }
        refreshSaved()
        _message.value = "Added ${storedProvider.displayName}; it is now the active provider."
    }

    fun useProvider(provider: DynamicAIProvider) = viewModelScope.launch {
        settingsRepository.updateConfig { it.copy(activeProvider = provider.id, activeModel = provider.model) }
        _message.value = "Using ${provider.displayName}."
    }
}
