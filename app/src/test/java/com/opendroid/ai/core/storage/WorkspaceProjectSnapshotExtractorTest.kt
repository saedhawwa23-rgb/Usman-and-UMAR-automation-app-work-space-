package com.opendroid.ai.core.storage

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkspaceProjectSnapshotExtractorTest {
    @Test
    fun importsSnapshotUnderProjectsAndRecordsThatHistoryIsNotPresent() {
        val base = Files.createTempDirectory("project-snapshot-test").toFile()
        try {
            val archive = File(base, "repo.zip")
            zip(archive, listOf("owner-repo-sha/" to "", "owner-repo-sha/README.md" to "sample project", "owner-repo-sha/src/main.kt" to "fun main() = Unit"))
            val workspace = File(base, "workspace")

            val result = WorkspaceProjectSnapshotExtractor.extract(
                archive = archive,
                workspaceRoot = workspace,
                destinationRelativePath = "projects/owner/repo",
                sourceUrl = "https://github.com/owner/repo",
                ref = "main"
            )

            val imported = File(workspace, "projects/owner/repo")
            assertEquals("projects/owner/repo", result.relativePath)
            assertEquals(2, result.fileCount)
            assertEquals("sample project", File(imported, "README.md").readText())
            assertEquals("fun main() = Unit", File(imported, "src/main.kt").readText())
            val note = File(imported, ".usman-import-info.txt").readText()
            assertTrue(note.contains("Source: https://github.com/owner/repo"))
            assertTrue(note.contains("Git object history, branches, tags"))
            assertFalse(File(imported, ".git").exists())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun rejectsZipTraversalAndRemovesStagingDirectory() {
        val base = Files.createTempDirectory("project-snapshot-test").toFile()
        try {
            val archive = File(base, "repo.zip")
            zip(archive, listOf("owner-repo-sha/" to "", "owner-repo-sha/../../escape.txt" to "not allowed"))
            val workspace = File(base, "workspace")

            assertThrows(IllegalArgumentException::class.java) {
                WorkspaceProjectSnapshotExtractor.extract(
                    archive, workspace, "projects/owner/repo", "https://github.com/owner/repo", "main"
                )
            }

            assertFalse(File(base, "escape.txt").exists())
            assertFalse(File(workspace, "projects/owner/repo").exists())
            assertTrue(workspace.listFiles().orEmpty().none { it.name.startsWith(".usman-import-") })
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun refusesGitMetadataAndReservedProvenanceFilename() {
        val base = Files.createTempDirectory("project-snapshot-test").toFile()
        try {
            val gitArchive = File(base, "git.zip")
            zip(gitArchive, listOf("owner-repo-sha/" to "", "owner-repo-sha/.git/HEAD" to "ref: refs/heads/main"))
            assertThrows(IllegalArgumentException::class.java) {
                WorkspaceProjectSnapshotExtractor.extract(
                    gitArchive, File(base, "workspace-git"), "projects/owner/repo", "https://github.com/owner/repo", "main"
                )
            }

            val reservedArchive = File(base, "reserved.zip")
            zip(reservedArchive, listOf("owner-repo-sha/" to "", "owner-repo-sha/.usman-import-info.txt" to "fake history"))
            assertThrows(IllegalArgumentException::class.java) {
                WorkspaceProjectSnapshotExtractor.extract(
                    reservedArchive, File(base, "workspace-reserved"), "projects/owner/repo", "https://github.com/owner/repo", "main"
                )
            }
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun refusesToReplaceAnExistingWorkspaceProject() {
        val base = Files.createTempDirectory("project-snapshot-test").toFile()
        try {
            val archive = File(base, "repo.zip")
            zip(archive, listOf("owner-repo-sha/" to "", "owner-repo-sha/README.md" to "new"))
            val workspace = File(base, "workspace")
            val existing = File(workspace, "projects/owner/repo").apply { mkdirs() }
            File(existing, "README.md").writeText("keep")

            assertThrows(IllegalArgumentException::class.java) {
                WorkspaceProjectSnapshotExtractor.extract(
                    archive, workspace, "projects/owner/repo", "https://github.com/owner/repo", "main"
                )
            }
            assertEquals("keep", File(existing, "README.md").readText())
        } finally {
            base.deleteRecursively()
        }
    }

    private fun zip(destination: File, entries: List<Pair<String, String>>) {
        ZipOutputStream(FileOutputStream(destination)).use { output ->
            entries.forEach { (name, content) ->
                output.putNextEntry(ZipEntry(name))
                if (content.isNotEmpty()) output.write(content.toByteArray())
                output.closeEntry()
            }
        }
    }
}
