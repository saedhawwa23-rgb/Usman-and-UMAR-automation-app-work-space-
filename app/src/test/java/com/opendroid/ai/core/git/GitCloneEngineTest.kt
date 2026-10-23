package com.opendroid.ai.core.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

class GitCloneEngineTest {
    @Test
    fun fullHistoryCloneKeepsOriginBranchesTagsAndAllDefaultBranchCommits() {
        val base = Files.createTempDirectory("git-clone-full-test").toFile()
        var source: Git? = null
        try {
            val sourceDir = File(base, "source")
            source = createRepository(sourceDir, commitCount = 3)
            val workspace = File(base, "workspace")
            val remote = sourceDir.toURI().toString()

            val result = GitCloneEngine(workspace).clone(
                remoteUrl = remote,
                destinationRelativePath = "projects/demo/sample",
                expectedBranch = "main",
                mode = GitCloneMode.FULL_HISTORY
            )

            val clonedDir = File(workspace, "projects/demo/sample")
            Git.open(clonedDir).use { cloned ->
                assertEquals("main", cloned.repository.branch)
                assertEquals(remote, cloned.repository.config.getString("remote", "origin", "url"))
                assertTrue(cloned.repository.exactRef("refs/heads/main") != null)
                assertTrue(cloned.repository.exactRef("refs/remotes/origin/main") != null)
                assertTrue(cloned.repository.exactRef("refs/remotes/origin/feature/demo") != null)
                assertTrue(cloned.repository.exactRef("refs/tags/v1") != null)
                assertEquals(3, cloned.log().call().count())
                assertFalse(File(cloned.repository.directory, "shallow").exists())
            }
            assertEquals("projects/demo/sample", result.relativePath)
            assertEquals("main", result.branch)
            assertEquals(GitCloneMode.FULL_HISTORY, result.mode)
            assertEquals(40, result.commit.length)
            assertFalse(result.shallowMetadataPresent)
        } finally {
            source?.close()
            base.deleteRecursively()
        }
    }

    @Test
    fun depthOneCloneIsExplicitAndVerifiesShallowMetadata() {
        val base = Files.createTempDirectory("git-clone-shallow-test").toFile()
        var source: Git? = null
        try {
            val sourceDir = File(base, "source")
            source = createRepository(sourceDir, commitCount = 3)
            val workspace = File(base, "workspace")
            val remote = sourceDir.toURI().toString()

            val result = GitCloneEngine(workspace).clone(
                remoteUrl = remote,
                destinationRelativePath = "projects/demo/sample",
                expectedBranch = "main",
                mode = GitCloneMode.SHALLOW_DEPTH_1
            )

            val clonedDir = File(workspace, "projects/demo/sample")
            Git.open(clonedDir).use { cloned ->
                assertEquals("main", cloned.repository.branch)
                assertTrue(File(cloned.repository.directory, "shallow").isFile)
                assertEquals(1, cloned.log().call().count())
            }
            assertEquals(GitCloneMode.SHALLOW_DEPTH_1, result.mode)
            assertTrue(result.shallowMetadataPresent)
        } finally {
            source?.close()
            base.deleteRecursively()
        }
    }

    @Test
    fun existingSnapshotIsNeverOverwrittenAndTraversalIsRejected() {
        val base = Files.createTempDirectory("git-clone-safety-test").toFile()
        var source: Git? = null
        try {
            val sourceDir = File(base, "source")
            source = createRepository(sourceDir, commitCount = 1)
            val workspace = File(base, "workspace")
            val existing = File(workspace, "projects/demo/sample").apply { mkdirs() }
            File(existing, "README.txt").writeText("preserve me")
            val remote = sourceDir.toURI().toString()

            assertThrows(IllegalArgumentException::class.java) {
                GitCloneEngine(workspace).clone(
                    remoteUrl = remote,
                    destinationRelativePath = "projects/demo/sample",
                    expectedBranch = "main",
                    mode = GitCloneMode.FULL_HISTORY
                )
            }
            assertEquals("preserve me", File(existing, "README.txt").readText())
            assertThrows(IllegalArgumentException::class.java) {
                GitCloneEngine(workspace).clone(
                    remoteUrl = remote,
                    destinationRelativePath = "projects/../outside",
                    expectedBranch = "main",
                    mode = GitCloneMode.FULL_HISTORY
                )
            }
        } finally {
            source?.close()
            base.deleteRecursively()
        }
    }

    private fun createRepository(directory: File, commitCount: Int): Git {
        val git = Git.init().setDirectory(directory).setInitialBranch("main").call()
        val author = PersonIdent("Clone Test", "clone-test@example.invalid")
        repeat(commitCount) { index ->
            File(directory, "README.txt").writeText("commit-${index + 1}\n")
            git.add().addFilepattern("README.txt").call()
            git.commit().setMessage("commit ${index + 1}").setAuthor(author).setCommitter(author).call()
        }
        git.branchCreate().setName("feature/demo").call()
        git.tag().setName("v1").call()
        return git
    }
}
