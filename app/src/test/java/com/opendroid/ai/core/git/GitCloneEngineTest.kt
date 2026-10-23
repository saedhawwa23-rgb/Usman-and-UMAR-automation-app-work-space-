package com.opendroid.ai.core.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.net.URI
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
            val cancellation = GitCloneCancellation()

            val result = GitCloneEngine(workspace).clone(
                remoteUrl = remote,
                destinationRelativePath = "projects/demo/sample",
                expectedBranch = "main",
                cancellation = cancellation
            )

            val clonedDir = File(workspace, "projects/demo/sample")
            Git.open(clonedDir).use { cloned ->
                assertEquals("main", cloned.repository.branch)
                val clonedRemote = cloned.repository.config.getString("remote", "origin", "url")
                    ?: error("Clone did not record its origin remote")
                assertEquals(File(URI(remote)).canonicalFile, File(URI(clonedRemote)).canonicalFile)
                assertTrue(cloned.repository.exactRef("refs/heads/main") != null)
                assertTrue(cloned.repository.exactRef("refs/remotes/origin/main") != null)
                assertTrue(cloned.repository.exactRef("refs/remotes/origin/feature/demo") != null)
                assertTrue(cloned.repository.exactRef("refs/tags/v1") != null)
                assertEquals(3, cloned.log().call().count())
                assertFalse(File(cloned.repository.directory, "shallow").exists())
            }
            assertEquals("projects/demo/sample", result.relativePath)
            assertEquals("main", result.branch)
            assertEquals(40, result.commit.length)
            assertFalse("Cancellation after successful publication must be rejected", cancellation.cancel())
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
                )
            }
            assertEquals("preserve me", File(existing, "README.txt").readText())
            assertThrows(IllegalArgumentException::class.java) {
                GitCloneEngine(workspace).clone(
                    remoteUrl = remote,
                    destinationRelativePath = "projects/../outside",
                    expectedBranch = "main",
                )
            }
        } finally {
            source?.close()
            base.deleteRecursively()
        }
    }

    @Test
    fun symbolicLinkDestinationParentIsRejectedWithoutTouchingOutsideFiles() {
        val base = Files.createTempDirectory("git-clone-symlink-test").toFile()
        var source: Git? = null
        try {
            val sourceDir = File(base, "source")
            source = createRepository(sourceDir, commitCount = 1)
            val workspace = File(base, "workspace").apply { mkdirs() }
            val outside = File(base, "outside").apply { mkdirs() }
            val marker = File(outside, "preserve.txt").apply { writeText("outside workspace") }
            Files.createSymbolicLink(File(workspace, "projects").toPath(), outside.toPath())

            assertThrows(IllegalArgumentException::class.java) {
                GitCloneEngine(workspace).clone(
                    remoteUrl = sourceDir.toURI().toString(),
                    destinationRelativePath = "projects/demo/sample",
                    expectedBranch = "main",
                )
            }
            assertEquals("outside workspace", marker.readText())
            assertFalse(File(outside, "demo").exists())
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
