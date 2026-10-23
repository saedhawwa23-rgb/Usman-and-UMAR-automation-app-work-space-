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
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
    private enum class State { ACTIVE, CANCELLED, FINISHED }
    private var state = State.ACTIVE

    @Synchronized
    fun cancel(): Boolean {
        if (state != State.ACTIVE) return false
        state = State.CANCELLED
        return true
    }

    @Synchronized
    internal fun isRequested(): Boolean = state == State.CANCELLED

    @Synchronized
    internal fun <T> finishIfActive(action: () -> T): T {
        if (state == State.CANCELLED) {
            throw GitCloneCancelledException("Git clone was cancelled; temporary clone data was removed.")
        }
        check(state == State.ACTIVE) { "The clone operation has already finished." }
        val value = action()
        state = State.FINISHED
        return value
    }
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
        val rootPath = root.toPath().toAbsolutePath().normalize()
        require(!Files.isSymbolicLink(rootPath) && Files.isDirectory(rootPath, LinkOption.NOFOLLOW_LINKS)) {
            "The app workspace root is not a stable directory."
        }
        val initialTarget = resolveSafeTarget(root, rootPath, relativePath, createParents = true)
        require(!Files.exists(initialTarget.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "The workspace path already exists. Existing snapshots and repositories are preserved; rename or move it before cloning."
        }

        // Keep network writes in a direct child of the canonical workspace root; destination
        // parents are checked again immediately before and after the final move.
        val staging = File(root, ".usman-git-clone-${UUID.randomUUID()}")
        require(!Files.exists(staging.toPath(), LinkOption.NOFOLLOW_LINKS)) { "Could not reserve a unique clone staging path." }
        val lock = CLONE_LOCKS.computeIfAbsent(initialTarget.absolutePath) { ReentrantLock() }
        require(lock.tryLock()) { "A clone of this repository is already in progress." }
        var movedToTarget = false
        var cloneVerified = false
        try {
            ensureNotCancelled(cancellation)
            resolveSafeTarget(root, rootPath, relativePath, createParents = false).also {
                require(!Files.exists(it.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    "The workspace path was created while cloning; it was not overwritten."
                }
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
                throw GitCloneCancelledException("Git clone was cancelled; temporary clone data was removed.", cancelled)
            } catch (failure: Exception) {
                if (cancellation.isRequested()) {
                    throw GitCloneCancelledException("Git clone was cancelled; temporary clone data was removed.", failure)
                }
                throw GitCloneException(classifyCloneFailure(failure), failure)
            }
            try {
                ensureNotCancelled(cancellation)
                verify(downloaded.repository, root, remoteUrl, expectedBranch, mode)
            } finally {
                downloaded.close()
            }
            ensureNotCancelled(cancellation)

            return cancellation.finishIfActive {
                val target = resolveSafeTarget(root, rootPath, relativePath, createParents = false)
                require(!Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    "The workspace path was created while cloning; it was not overwritten."
                }
                Files.move(staging.toPath(), target.toPath())
                movedToTarget = true
                requireSafeExistingTarget(root, rootPath, relativePath)

                val published = Git.open(target)
                try {
                    verify(published.repository, root, remoteUrl, expectedBranch, mode).also {
                        cloneVerified = true
                    }
                } finally {
                    published.close()
                }
            }
        } catch (failure: GitCloneCancelledException) {
            throw failure
        } catch (failure: GitCloneException) {
            throw failure
        } catch (failure: Exception) {
            if (cancellation.isRequested()) {
                throw GitCloneCancelledException("Git clone was cancelled; temporary clone data was removed.", failure)
            }
            throw GitCloneException(classifyCloneFailure(failure), failure)
        } finally {
            safeDeleteTree(staging, rootPath)
            if (movedToTarget && !cloneVerified) safeDeleteTree(initialTarget, rootPath)
            lock.unlock()
        }
    }

    private fun resolveSafeTarget(root: File, rootPath: java.nio.file.Path, relativePath: String, createParents: Boolean): File {
        require(!Files.isSymbolicLink(rootPath) && Files.isDirectory(rootPath, LinkOption.NOFOLLOW_LINKS)) {
            "The app workspace root changed during the Git clone."
        }
        val segments = relativePath.split('/')
        require(segments.isNotEmpty() && segments.none { it.isBlank() || it == "." || it == ".." }) {
            "The Git destination path is invalid."
        }
        var parent = root
        for (segment in segments.dropLast(1)) {
            val next = File(parent, segment)
            require(!Files.isSymbolicLink(next.toPath())) { "The Git destination contains a symbolic-link directory." }
            if (!Files.exists(next.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                require(createParents && next.mkdir()) { "The Git destination folder is missing or could not be created." }
            }
            require(Files.isDirectory(next.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                "The Git destination parent is not a real directory."
            }
            require(next.canonicalFile.toPath().startsWith(rootPath)) { "The Git destination is outside the app workspace." }
            parent = next
        }
        val target = File(parent, segments.last())
        require(target.toPath().toAbsolutePath().normalize().startsWith(rootPath)) {
            "The Git repository path escaped the app workspace."
        }
        require(!Files.isSymbolicLink(target.toPath())) { "The Git destination is a symbolic link." }
        return target
    }

    private fun requireSafeExistingTarget(root: File, rootPath: java.nio.file.Path, relativePath: String): File {
        val target = resolveSafeTarget(root, rootPath, relativePath, createParents = false)
        require(Files.isDirectory(target.toPath(), LinkOption.NOFOLLOW_LINKS) &&
            target.canonicalFile.toPath().startsWith(rootPath)) {
            "The published Git repository is outside the app workspace or is not a real directory."
        }
        return target
    }

    private fun ensureNotCancelled(cancellation: GitCloneCancellation) {
        if (cancellation.isRequested()) {
            throw GitCloneCancelledException("Git clone was cancelled; temporary clone data was removed.")
        }
    }

    private fun verify(
        repository: org.eclipse.jgit.lib.Repository,
        root: File,
        expectedRemote: String,
        expectedBranch: String,
        mode: GitCloneMode
    ): GitWorkspaceCloneResult {
        require(!repository.isBare) { "The clone did not create a working tree." }
        val workTree = repository.workTree.canonicalFile
        val rootPath = root.toPath().toAbsolutePath().normalize()
        require(workTree.toPath().startsWith(rootPath)) { "The cloned working tree is outside the app workspace." }
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
            relativePath = rootPath.relativize(workTree.toPath()).joinToString("/"),
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

    private fun safeDeleteTree(entry: File, rootPath: java.nio.file.Path) {
        if (Files.isSymbolicLink(rootPath) || !Files.isDirectory(rootPath, LinkOption.NOFOLLOW_LINKS)) return
        val path = entry.toPath().toAbsolutePath().normalize()
        if (!path.startsWith(rootPath) || path == rootPath) return
        val relative = rootPath.relativize(path)
        var parent = rootPath
        for (segment in relative.dropLast(1)) {
            parent = parent.resolve(segment)
            if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) return
        }
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        runCatching {
            if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                Files.deleteIfExists(path)
            } else {
                val children = Files.newDirectoryStream(path).use { it.toList() }
                children.forEach { safeDeleteTree(it.toFile(), rootPath) }
                if (!Files.isSymbolicLink(path) && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.deleteIfExists(path)
                }
            }
        }
    }

    private companion object {
        val CLONE_LOCKS = ConcurrentHashMap<String, ReentrantLock>()
    }
}

class GitCloneException(message: String, cause: Throwable? = null) : Exception(message, cause)
class GitCloneCancelledException(message: String, cause: Throwable? = null) : Exception(message, cause)
