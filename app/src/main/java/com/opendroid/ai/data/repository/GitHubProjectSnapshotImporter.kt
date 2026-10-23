package com.opendroid.ai.data.repository

import android.content.Context
import com.opendroid.ai.core.storage.DeveloperWorkspaceFiles
import com.opendroid.ai.core.storage.WorkspaceProjectSnapshot
import com.opendroid.ai.core.storage.WorkspaceProjectSnapshotExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Imports repository source files into the shared app workspace; this is not a Git clone. */
class GitHubProjectSnapshotImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val github: GitHubIntegration
) {
    suspend fun importDefaultBranch(repository: GitHubRepoSummary): WorkspaceProjectSnapshot {
        val identity = repository.fullName.split('/')
        require(identity.size == 2) { "GitHub returned an invalid repository identity." }
        val (owner, name) = identity
        require(OWNER_PATTERN.matches(owner) && REPOSITORY_PATTERN.matches(name) && name != "." && name != "..") {
            "GitHub returned an invalid repository identity."
        }
        val destination = "projects/$owner/$name"
        val workspace = DeveloperWorkspaceFiles(context).root()
        require(!File(workspace, destination).exists()) {
            "This repository already exists in the shared workspace. Remove or rename it before importing again."
        }
        val archive = File(context.cacheDir, "github-source-${UUID.randomUUID()}.zip")
        try {
            github.downloadRepositoryArchive(owner, name, repository.defaultBranch, archive)
            return withContext(Dispatchers.IO) {
                WorkspaceProjectSnapshotExtractor.extract(
                    archive = archive,
                    workspaceRoot = workspace,
                    destinationRelativePath = destination,
                    sourceUrl = "https://github.com/${repository.fullName}",
                    ref = repository.defaultBranch
                )
            }
        } finally {
            archive.delete()
        }
    }

    private companion object {
        val OWNER_PATTERN = Regex("[A-Za-z0-9-]{1,39}")
        val REPOSITORY_PATTERN = Regex("[A-Za-z0-9_.-]{1,100}")
    }
}
