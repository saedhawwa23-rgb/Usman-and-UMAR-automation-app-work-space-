package com.opendroid.ai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opendroid.ai.core.git.GitCloneCancellation
import com.opendroid.ai.core.git.GitCloneMode
import com.opendroid.ai.core.git.GitWorkspaceCloneResult
import com.opendroid.ai.core.git.GitWorkspaceManager
import com.opendroid.ai.data.repository.GitHubDeviceCode
import com.opendroid.ai.data.repository.GitHubDevicePoll
import com.opendroid.ai.data.repository.GitHubFile
import com.opendroid.ai.data.repository.GitHubIntegration
import com.opendroid.ai.data.repository.GitHubIssue
import com.opendroid.ai.data.repository.GitHubProjectSnapshotImporter
import com.opendroid.ai.data.repository.GitHubRepoSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GitHubWorkspaceViewModel @Inject constructor(
    private val github: GitHubIntegration,
    private val projectImporter: GitHubProjectSnapshotImporter,
    private val gitWorkspace: GitWorkspaceManager
) : ViewModel() {
    val authenticated = MutableStateFlow(github.isAuthenticated())
    val account = MutableStateFlow<com.opendroid.ai.data.repository.GitHubAccount?>(null)
    val repositories = MutableStateFlow<List<GitHubRepoSummary>>(emptyList())
    val files = MutableStateFlow<List<GitHubFile>>(emptyList())
    val selectedFile = MutableStateFlow<GitHubFile?>(null)
    val issues = MutableStateFlow<List<GitHubIssue>>(emptyList())
    val busy = MutableStateFlow(false)
    val cloning = MutableStateFlow(false)
    val cloneResult = MutableStateFlow<GitWorkspaceCloneResult?>(null)
    val message = MutableStateFlow<String?>(null)
    val deviceCode = MutableStateFlow<GitHubDeviceCode?>(null)
    val configured get() = github.isConfigured()
    private var activeCloneCancellation: GitCloneCancellation? = null

    init { if (authenticated.value) refresh() }

    fun beginLogin() = viewModelScope.launch {
        busy.value = true; message.value = null
        runCatching { github.requestDeviceCode() }.onSuccess {
            deviceCode.value = it
            message.value = "Open ${it.verificationUri} and enter code ${it.userCode}."
        }.onFailure { message.value = it.message ?: "Could not start GitHub authorization." }
        busy.value = false
    }

    fun pollLogin() = viewModelScope.launch {
        val code = deviceCode.value ?: return@launch
        busy.value = true
        try {
            while (true) {
                when (val result = github.pollDeviceCode(code)) {
                    is GitHubDevicePoll.Pending -> { message.value = "Waiting for GitHub authorization…"; delay(result.seconds * 1_000L) }
                    is GitHubDevicePoll.Authorized -> {
                        authenticated.value = true; deviceCode.value = null; message.value = "Authenticated as ${result.login}."; refresh(); break
                    }
                }
            }
        } catch (failure: Throwable) { message.value = failure.message ?: "GitHub authorization failed." }
        finally { busy.value = false }
    }

    fun refresh() = viewModelScope.launch {
        busy.value = true; message.value = null
        runCatching { github.currentUser() }.onSuccess { account.value = it }.onFailure { message.value = it.message }
        runCatching { github.repositories() }.onSuccess { repositories.value = it }.onFailure { message.value = it.message }
        busy.value = false
    }

    fun openRepository(repo: GitHubRepoSummary, path: String = "") = viewModelScope.launch {
        busy.value = true; message.value = null
        runCatching { github.files(repo.fullName.substringBefore('/'), repo.fullName.substringAfter('/'), path) }
            .onSuccess { files.value = it }
            .onFailure { message.value = it.message ?: "Could not load repository files." }
        busy.value = false
    }

    fun inspect(repo: GitHubRepoSummary, file: GitHubFile) = viewModelScope.launch {
        if (file.type != "file") return@launch
        busy.value = true
        runCatching { github.readFile(repo.fullName.substringBefore('/'), repo.fullName.substringAfter('/'), file.path, repo.defaultBranch) }
            .onSuccess { selectedFile.value = it }.onFailure { message.value = it.message }
        busy.value = false
    }

    fun importSourceSnapshot(repo: GitHubRepoSummary) = viewModelScope.launch {
        if (busy.value) return@launch
        busy.value = true
        message.value = "Downloading ${repo.fullName} source snapshot…"
        try {
            val imported = projectImporter.importDefaultBranch(repo)
            message.value = "Imported ${imported.fileCount} files to ${imported.relativePath}. Git history, branches and tags are not included."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            message.value = failure.message ?: "Could not import the repository source snapshot."
        } finally {
            busy.value = false
        }
    }

    fun cloneRepository(repo: GitHubRepoSummary, mode: GitCloneMode) = viewModelScope.launch {
        if (busy.value) return@launch
        val cancellation = GitCloneCancellation()
        activeCloneCancellation = cancellation
        cloneResult.value = null
        cloning.value = true
        busy.value = true
        message.value = "Starting ${if (mode == GitCloneMode.FULL_HISTORY) "full-history" else "depth-1"} clone of ${repo.fullName}…"
        try {
            val result = gitWorkspace.cloneGitHubRepository(
                fullName = repo.fullName,
                defaultBranch = repo.defaultBranch,
                mode = mode,
                cancellation = cancellation
            ) { progress ->
                message.value = if (progress.total > 0) {
                    "${progress.task}: ${progress.completed}/${progress.total}"
                } else {
                    progress.task
                }
            }
            cloneResult.value = result
            val history = when {
                result.mode == GitCloneMode.FULL_HISTORY -> "full history"
                result.shallowMetadataPresent -> "shallow depth-1"
                else -> "depth-1 request (repository history has no older commit to truncate)"
            }
            message.value = "Verified $history clone at ${result.relativePath} · ${result.branch} · ${result.commit.take(12)}. Project code was not run."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            message.value = if (cancellation.isRequested()) {
                "Clone cancelled; temporary clone data was removed."
            } else {
                failure.message ?: "Could not clone the GitHub repository."
            }
        } finally {
            activeCloneCancellation = null
            cloning.value = false
            busy.value = false
        }
    }

    fun cancelClone() {
        val accepted = activeCloneCancellation?.cancel() == true
        if (cloning.value) {
            message.value = if (accepted) {
                "Cancellation requested; waiting for the current Git transfer to stop…"
            } else {
                "Clone is finishing or cancellation was already requested."
            }
        }
    }

    override fun onCleared() {
        activeCloneCancellation?.cancel()
        super.onCleared()
    }

    fun loadIssues(repo: GitHubRepoSummary) = viewModelScope.launch {
        busy.value = true
        runCatching { github.issues(repo.fullName.substringBefore('/'), repo.fullName.substringAfter('/')) }
            .onSuccess { issues.value = it }.onFailure { message.value = it.message }
        busy.value = false
    }

    fun createIssue(repo: GitHubRepoSummary, title: String, body: String) = viewModelScope.launch {
        busy.value = true
        runCatching { github.createIssue(repo.fullName.substringBefore('/'), repo.fullName.substringAfter('/'), title, body) }
            .onSuccess { message.value = "Created issue #${it.number}."; loadIssues(repo) }
            .onFailure { message.value = it.message ?: "Could not create issue." }
        busy.value = false
    }

    fun signOut() { github.signOut(); authenticated.value = false; account.value = null; repositories.value = emptyList(); message.value = "GitHub token removed from this device." }
}
