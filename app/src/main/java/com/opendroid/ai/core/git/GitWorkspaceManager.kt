package com.opendroid.ai.core.git

import android.content.Context
import com.opendroid.ai.core.security.GitHubTokenStore
import com.opendroid.ai.core.storage.StorageWorkspaceProvider
import com.opendroid.ai.core.storage.WorkspacePathPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.CanceledException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.ProgressMonitor
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton

/** Full history is the default; depth-1 must be chosen explicitly. */
enum class GitCloneMode(val depth: Int?) {
    FULL_HISTORY(null),
    SHALLOW_DEPTH_1(1)
}

data class GitCloneProgress(val task: String, val completed: Int, val total: Int)

data class GitWorkspaceCloneResult(
    val relativePath: String,
    val remoteUrl: String,
    val branch: String,
    val commit: String,
    val mode: GitCloneMode,
    val shallowMetadataPresent: Boolean
)

class GitCloneCancellation internal constructor() {
    private val requested = AtomicBoolean(false)
    fun cancel() { requested.set(true) }
    internal fun isRequested(): Boolean = requested.get()
}

@Singleton
class GitWorkspaceManager @Inject constructor(
    @ApplicationContext context: Context,
    private val tokenStore: GitHubTokenStore
) {
    private val workspaceRoot = StorageWorkspaceProvider.getDefaultWorkspaceDir(context.applicationContext)

    suspend fun cloneGitHubRepository(
        fullName: String,
        defaultBranch: String,
        mode: GitCloneMode,
        cancellation: GitCloneCancellation,
        onProgress: (GitCloneProgress) -> Unit = {}
    ): GitWorkspaceCloneResult = withContext(Dispatchers.IO) {
        val (owner, repository) = parseIdentity(fullName)
        require(isSafeBranch(defaultBranch)) { "GitHub returned an invalid default branch name." }
        val remoteUrl = "https://github.com/$owner/$repository.git"
        val credentials = tokenStore.read()?.takeIf(String::isNotBlank)?.let {
            UsernamePasswordCredentialsProvider("x-access-token", it.toCharArray())
        }
        try {
            GitCloneEngine(workspaceRoot).clone(
                remoteUrl = remoteUrl,
                destinationRelativePath = "projects/$owner/$repository",
                expectedBranch = defaultBranch,
                mode = mode,
                credentials = credentials,
                cancellation = cancellation,
                onProgress = onProgress
            )
        } finally {
            credentials?.clear()
        }
    }

    private fun parseIdentity(fullName: String): Pair<String, String> {
        val parts = fullName.split('/')
        require(parts.size == 2 && OWNER.matches(parts[0]) && REPOSITORY.matches(parts[1]) && parts[1] != "." && parts[1] != "..") {
            "The GitHub repository identity is invalid."
        }
        return parts[0] to parts[1]
    }

    private fun isSafeBranch(branch: String): Boolean = runCatching {
        branch.isNotBlank() && branch.length <= 255 &&
            org.eclipse.jgit.lib.Repository.isValidRefName("refs/heads/$branch")
    }.getOrDefault(false)

    private companion object {
        val OWNER = Regex("[A-Za-z0-9-]{1,39}")
        val REPOSITORY = Regex("[A-Za-z0-9_.-]{1,100}")
    }
}

/** Package-local engine kept independent of Android so clone invariants can be tested directly. */
internal class GitCloneEngine(private val workspaceRoot: File) {
    fun clone(
        remoteUrl: String,
        destinationRelativePath: String,
        expectedBranch: String,
        mode: GitCloneMode,
        credentials: UsernamePasswordCredentialsProvider? = null,
        cancellation: GitCloneCancellation = GitCloneCancellation(),
        onProgress: (GitCloneProgress) -> Unit = {}
    ): GitWorkspaceCloneResult {
        require(!cancellation.isRequested()) { "Git clone was cancelled before it started." }
        require(remoteUrl.isNotBlank() && remoteUrl.length <= 2_048 && remoteUrl.none(Char::isISOControl)) {
            "The Git remote URL is invalid."
        }
        require(expectedBranch.isNotBlank() && expectedBranch.length <= 255 &&
            org.eclipse.jgit.lib.Repository.isValidRefName("refs/heads/$expectedBranch")) {
            "The repository default branch is invalid."
        }

        val relativePath = WorkspacePathPolicy.normalizeRelative(destinationRelativePath)
        val root = workspaceRoot.canonicalFile
        require(root.exists() || root.mkdirs()) { "The app workspace could not be created." }
        require(root.isDirectory && root.canRead() && root.canWrite()) { "The app workspace is not readable and writable." }
        val rootPath = root.toPath()
        val target = File(root, relativePath)
        require(target.toPath().normalize().startsWith(rootPath)) { "The Git repository path escaped the app workspace." }
        require(!target.exists() && !Files.isSymbolicLink(target.toPath())) {
            "The workspace path already exists. Existing snapshots and repositories are preserved; rename or move it before cloning."
        }

        val parentRelative = relativePath.substringBeforeLast('/', missingDelimiterValue = "")
        var parent = root
        if (parentRelative.isNotEmpty()) {
            for (segment in parentRelative.split('/')) {
                parent = File(parent, segment)
                require(!Files.isSymbolicLink(parent.toPath())) { "The Git destination contains a symbolic-link directory." }
                if (!parent.exists()) require(parent.mkdir()) { "Could not create the Git destination folder." }
                require(parent.isDirectory && parent.canonicalFile.toPath().startsWith(rootPath)) {
                    "The Git destination is outside the app workspace."
                }
            }
        }

        val staging = File(parent, ".${target.name}.clone-${UUID.randomUUID()}")
        val lock = CLONE_LOCKS.computeIfAbsent(target.absolutePath) { ReentrantLock() }
        require(lock.tryLock()) { "A clone of this repository is already in progress." }
        var movedToTarget = false
        var cloneVerified = false
        try {
            require(!target.exists()) {
                "The workspace path already exists. Existing snapshots and repositories are preserved; rename or move it before cloning."
            }

            val progressMonitor = object : ProgressMonitor {
                private var task = "Preparing Git clone"
                private var completed = 0
                private var total = 0

                override fun start(totalTasks: Int) {
                    onProgress(GitCloneProgress(task, completed, total))
                }

                override fun beginTask(title: String, totalWork: Int) {
                    task = title.take(120)
                    completed = 0
                    total = totalWork
                    onProgress(GitCloneProgress(task, completed, total))
                }

                override fun update(completed: Int) {
                    this.completed = (this.completed + completed).coerceAtLeast(0)
                    onProgress(GitCloneProgress(task, this.completed, total))
                }

                override fun endTask() {
                    onProgress(GitCloneProgress(task, completed, total))
                }

                override fun isCancelled(): Boolean = cancellation.isRequested()
            }

            var clone = Git.cloneRepository()
                .setURI(remoteUrl)
                .setDirectory(staging)
                .setBranch(expectedBranch)
                .setCloneAllBranches(true)
                .setTimeout(60)
                .setProgressMonitor(progressMonitor)
            if (credentials != null) clone = clone.setCredentialsProvider(credentials)
            mode.depth?.let { clone = clone.setDepth(it) }

            val downloaded = try {
                clone.call()
            } catch (cancelled: CanceledException) {
                throw GitCloneCancelledException("Git clone was cancelled; temporary data was removed.", cancelled)
            } catch (failure: Exception) {
                if (cancellation.isRequested()) {
                    throw GitCloneCancelledException("Git clone was cancelled; temporary data was removed.", failure)
                }
                throw GitCloneException(classifyCloneFailure(failure), failure)
            }
            try {
                verify(downloaded.repository, remoteUrl, expectedBranch, mode)
            } finally {
                downloaded.close()
            }

            require(!target.exists()) { "The workspace path was created while cloning; it was not overwritten." }
            Files.move(staging.toPath(), target.toPath())
            movedToTarget = true

            val verified = Git.open(target)
            return try {
                verify(verified.repository, remoteUrl, expectedBranch, mode).also { cloneVerified = true }
            } finally {
                verified.close()
            }
        } catch (failure: GitCloneCancelledException) {
            throw failure
        } catch (failure: GitCloneException) {
            throw failure
        } catch (failure: Exception) {
            if (cancellation.isRequested()) {
                throw GitCloneCancelledException("Git clone was cancelled; temporary data was removed.", failure)
            }
            throw GitCloneException(classifyCloneFailure(failure), failure)
        } finally {
            safeDeleteTree(staging)
            if (movedToTarget && !cloneVerified) safeDeleteTree(target)
            lock.unlock()
        }
    }

    private fun verify(
        repository: org.eclipse.jgit.lib.Repository,
        expectedRemote: String,
        expectedBranch: String,
        mode: GitCloneMode
    ): GitWorkspaceCloneResult {
        require(!repository.isBare) { "The clone did not create a working tree." }
        val workTree = repository.workTree.canonicalFile
        require(workTree.toPath().startsWith(workspaceRoot.canonicalFile.toPath())) {
            "The cloned working tree is outside the app workspace."
        }
        require(repository.config.getString("remote", "origin", "url") == expectedRemote) {
            "The clone's origin remote does not match the selected GitHub repository."
        }
        val branch = repository.branch
        require(branch == expectedBranch) { "The selected default branch was not checked out." }
        require(repository.exactRef("refs/heads/$expectedBranch") != null) {
            "The expected local branch is missing from the clone."
        }
        require(repository.exactRef("refs/remotes/origin/$expectedBranch") != null) {
            "The expected remote branch is missing from the clone."
        }
        val head = repository.resolve(Constants.HEAD)
            ?: throw IllegalStateException("Git did not create a valid HEAD commit. The repository may be empty.")
        RevWalk(repository).use { walk -> walk.parseCommit(head) }

        val shallowFile = File(repository.directory, "shallow")
        when (mode) {
            GitCloneMode.FULL_HISTORY -> require(!shallowFile.exists()) {
                "The full-history clone contains shallow metadata; no fallback was accepted."
            }
            GitCloneMode.SHALLOW_DEPTH_1 -> {
                val currentBranchCommitCount = countCurrentBranchCommits(repository, head, 2)
                require((shallowFile.isFile && shallowFile.length() > 0L) || currentBranchCommitCount <= 1) {
                    "The selected depth-1 clone could not be verified; no full-history or snapshot fallback was accepted."
                }
            }
        }

        return GitWorkspaceCloneResult(
            relativePath = relativePathFor(workTree),
            remoteUrl = expectedRemote,
            branch = branch,
            commit = head.name,
            mode = mode,
            shallowMetadataPresent = shallowFile.isFile && shallowFile.length() > 0L
        )
    }

    private fun countCurrentBranchCommits(repository: org.eclipse.jgit.lib.Repository, head: ObjectId, limit: Int): Int =
        RevWalk(repository).use { walk ->
            walk.markStart(walk.parseCommit(head))
            var count = 0
            while (count < limit && walk.next() != null) count++
            count
        }

    private fun relativePathFor(workTree: File): String = workspaceRoot.canonicalFile.toPath()
        .relativize(workTree.canonicalFile.toPath()).joinToString("/")

    private fun classifyCloneFailure(failure: Exception): String = when {
        failure.message?.contains("authentication", ignoreCase = true) == true ||
            failure.message?.contains("not authorized", ignoreCase = true) == true ->
            "GitHub rejected the repository credentials. Reauthorize with an account allowed to read this repository."
        failure is IOException && failure.message?.contains("space", ignoreCase = true) == true ->
            "The clone could not be stored. Check available workspace storage."
        failure.message?.contains("not found", ignoreCase = true) == true ->
            "GitHub could not find this repository or default branch, or the account cannot access it."
        failure is IllegalArgumentException || failure is IllegalStateException ->
            failure.message?.take(240) ?: "The cloned repository failed verification."
        else -> "Git clone failed. Check the repository, network, available storage, and GitHub authorization."
    }

    private fun safeDeleteTree(file: File) {
        val path = file.toPath()
        if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            Files.deleteIfExists(path)
            return
        }
        (file.listFiles() ?: emptyArray()).forEach(::safeDeleteTree)
        Files.deleteIfExists(path)
    }

    private companion object {
        val CLONE_LOCKS = ConcurrentHashMap<String, ReentrantLock>()
    }
}

class GitCloneException(message: String, cause: Throwable? = null) : Exception(message, cause)
class GitCloneCancelledException(message: String, cause: Throwable? = null) : Exception(message, cause)
