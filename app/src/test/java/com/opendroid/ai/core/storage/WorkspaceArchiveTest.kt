package com.opendroid.ai.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream

class WorkspaceArchiveTest {
    @Test
    fun archiveIncludesProjectFilesAndGitHistory() {
        val base = Files.createTempDirectory("workspace-archive-test").toFile()
        try {
            val root = File(base, "workspace").apply { mkdir() }
            File(root, "project/.git").mkdirs()
            File(root, "project/src/Main.kt").apply { requireNotNull(parentFile).mkdirs(); writeText("fun main() = Unit") }
            File(root, "project/.git/HEAD").writeText("ref: refs/heads/main")
            val archive = File(base, "workspace.zip")

            WorkspaceArchive.create(root, archive)

            val names = ZipInputStream(archive.inputStream()).use { zip ->
                buildSet { while (true) add(zip.nextEntry?.name ?: break) }
            }
            assertTrue("project/src/Main.kt" in names)
            assertTrue("project/.git/HEAD" in names)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun rejectsArchiveDestinationInsideWorkspace() {
        val base = Files.createTempDirectory("workspace-archive-test").toFile()
        try {
            val root = File(base, "workspace").apply { mkdir() }
            assertThrows(IllegalArgumentException::class.java) {
                WorkspaceArchive.create(root, File(root, "workspace.zip"))
            }
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun rejectsWorkspaceLargerThanConfiguredLimitBeforeWritingArchive() {
        val base = Files.createTempDirectory("workspace-archive-test").toFile()
        try {
            val root = File(base, "workspace").apply { mkdir() }
            val oversized = File(root, "large.bin")
            oversized.createNewFile()
            java.io.RandomAccessFile(oversized, "rw").use { it.setLength(WorkspaceArchive.MAX_UNCOMPRESSED_BYTES + 1) }
            assertThrows(IllegalArgumentException::class.java) {
                WorkspaceArchive.create(root, File(base, "workspace.zip"))
            }
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun rejectsBackslashInArchivePathComponents() {
        val base = Files.createTempDirectory("workspace-archive-test").toFile()
        try {
            val root = File(base, "workspace").apply { mkdir() }
            File(root, "..\\outside.txt").writeText("unsafe")
            assertThrows(IllegalArgumentException::class.java) {
                WorkspaceArchive.create(root, File(base, "workspace.zip"))
            }
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun skipsSymbolicLinksInsteadOfFollowingThem() {
        val base = Files.createTempDirectory("workspace-archive-test").toFile()
        try {
            val root = File(base, "workspace").apply { mkdir() }
            val outside = File(base, "outside.txt").apply { writeText("outside") }
            Files.createSymbolicLink(File(root, "link.txt").toPath(), outside.toPath())
            val archive = File(base, "workspace.zip")

            WorkspaceArchive.create(root, archive)

            val names = ZipInputStream(archive.inputStream()).use { zip ->
                buildSet { while (true) add(zip.nextEntry?.name ?: break) }
            }
            assertEquals(emptySet<String>(), names)
        } finally {
            base.deleteRecursively()
        }
    }
}
