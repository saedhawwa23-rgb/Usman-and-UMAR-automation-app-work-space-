package com.opendroid.ai.core.storage

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.ZipInputStream

/** Result of importing repository source files. Git object history is deliberately not implied. */
data class WorkspaceProjectSnapshot(
    val relativePath: String,
    val fileCount: Int,
    val uncompressedBytes: Long
)

/** Safely unpacks a GitHub ZIP source snapshot into the app-owned shared workspace. */
object WorkspaceProjectSnapshotExtractor {
    const val MAX_ARCHIVE_BYTES = 128L * 1024L * 1024L
    const val MAX_ENTRIES = 25_000
    const val MAX_TOTAL_UNCOMPRESSED_BYTES = 512L * 1024L * 1024L
    const val MAX_SINGLE_FILE_BYTES = 128L * 1024L * 1024L

    fun extract(
        archive: File,
        workspaceRoot: File,
        destinationRelativePath: String,
        sourceUrl: String,
        ref: String
    ): WorkspaceProjectSnapshot {
        require(archive.isFile && archive.canRead()) { "The downloaded repository archive is not readable." }
        require(archive.length() <= MAX_ARCHIVE_BYTES) {
            "The repository archive exceeds the ${MAX_ARCHIVE_BYTES / (1024 * 1024)} MB download limit."
        }
        val destinationRelative = WorkspacePathPolicy.normalizeRelative(destinationRelativePath)
        val sourceUri = runCatching { java.net.URI(sourceUrl) }.getOrNull()
        require(
            sourceUri != null && sourceUri.scheme == "https" && sourceUri.host == "github.com" &&
                sourceUri.userInfo == null && sourceUri.port == -1 && sourceUri.query == null && sourceUri.fragment == null &&
                sourceUri.path.trim('/').split('/').size == 2
        ) { "Only canonical GitHub repository URLs are supported." }
        require(ref.isNotBlank() && ref.length <= 255 && ref.none(Char::isISOControl)) { "The repository branch or tag is invalid." }

        val root = workspaceRoot.canonicalFile
        require(root.mkdirs() || root.isDirectory) { "The app workspace could not be created." }
        var destinationCandidate = root
        for (component in destinationRelative.split('/')) {
            destinationCandidate = File(destinationCandidate, component)
            require(!Files.isSymbolicLink(destinationCandidate.toPath())) { "The project destination cannot pass through a symbolic link." }
        }
        val destination = destinationCandidate.canonicalFile
        require(destination.toPath().startsWith(root.toPath())) { "The project destination is outside the app workspace." }
        require(!destination.exists()) { "A workspace project already exists at $destinationRelative. Rename or remove it first." }
        val stage = File(root, ".usman-import-${UUID.randomUUID()}")
        require(stage.mkdir()) { "Could not create a temporary project-import directory." }

        var entryCount = 0
        var fileCount = 0
        var totalBytes = 0L
        var archiveRoot: String? = null
        val seenFiles = mutableSetOf<String>()
        var completed = false
        try {
            ZipInputStream(FileInputStream(archive).buffered()).use { zip ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    require(entryCount <= MAX_ENTRIES) { "The repository archive exceeds the $MAX_ENTRIES-entry limit." }
                    val rawName = entry.name
                    require(rawName.isNotEmpty() && !rawName.startsWith('/') && '\\' !in rawName && '\u0000' !in rawName) {
                        "The repository archive contains an unsafe path."
                    }
                    val trimmedName = rawName.removeSuffix("/")
                    val components = trimmedName.split('/')
                    require(components.isNotEmpty() && components.all { it.isNotEmpty() && it != "." && it != ".." && it.none(Char::isISOControl) }) {
                        "The repository archive contains an unsafe path."
                    }
                    val currentRoot = archiveRoot
                    if (currentRoot == null) archiveRoot = components.first()
                    else require(components.first() == currentRoot) { "The repository archive contains more than one root folder." }

                    val relativeParts = components.drop(1)
                    if (relativeParts.isNotEmpty()) {
                        val relative = WorkspacePathPolicy.normalizeRelative(relativeParts.joinToString("/"))
                        require(relativeParts.none { it.equals(".git", ignoreCase = true) }) {
                            "Git metadata is not accepted from a source snapshot archive."
                        }
                        val output = File(stage, relative).canonicalFile
                        require(output.toPath().startsWith(stage.canonicalFile.toPath())) {
                            "The repository archive contains a path outside its temporary directory."
                        }
                        if (entry.isDirectory || rawName.endsWith('/')) {
                            require(!seenFiles.contains(relative)) { "The repository archive has conflicting file and folder entries." }
                            require(output.mkdirs() || output.isDirectory) { "Could not create an imported project folder." }
                        } else {
                            require(seenFiles.add(relative)) { "The repository archive contains a duplicate file." }
                            require(!output.exists()) { "The repository archive has conflicting entries." }
                            require(output.parentFile?.mkdirs() != false) { "Could not create an imported project folder." }
                            var entryBytes = 0L
                            FileOutputStream(output).use { stream ->
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count < 0) break
                                    entryBytes = Math.addExact(entryBytes, count.toLong())
                                    totalBytes = Math.addExact(totalBytes, count.toLong())
                                    require(entryBytes <= MAX_SINGLE_FILE_BYTES) {
                                        "An imported file exceeds the ${MAX_SINGLE_FILE_BYTES / (1024 * 1024)} MB per-file limit."
                                    }
                                    require(totalBytes <= MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                        "The repository exceeds the ${MAX_TOTAL_UNCOMPRESSED_BYTES / (1024 * 1024)} MB expanded-size limit."
                                    }
                                    stream.write(buffer, 0, count)
                                }
                                stream.fd.sync()
                            }
                            fileCount++
                        }
                    } else {
                        require(entry.isDirectory || rawName.endsWith('/')) { "The repository archive root must be a folder." }
                    }
                    zip.closeEntry()
                }
            }
            require(archiveRoot != null && fileCount > 0) { "The repository archive contains no project files." }

            val importInfo = File(stage, ".usman-import-info.txt")
            require(importInfo.createNewFile()) { "The repository uses the reserved snapshot metadata filename." }
            importInfo.writeText(
                "USMAN AND UMAR repository source snapshot\n" +
                    "Source: $sourceUrl\n" +
                    "Ref: ${ref.replace('\\n', ' ').replace('\\r', ' ')}\n" +
                    "Git object history, branches, tags, remotes and uncommitted changes are not included.\n" +
                    "Project code was not executed and dependencies were not installed.\n",
                Charsets.UTF_8
            )
            totalBytes = Math.addExact(totalBytes, importInfo.length())
            require(totalBytes <= MAX_TOTAL_UNCOMPRESSED_BYTES) { "The repository exceeds the expanded-size limit." }

            destination.parentFile?.let { parent -> require(parent.mkdirs() || parent.isDirectory) { "Could not create the workspace Projects folder." } }
            require(!destination.exists()) { "A workspace project already exists at $destinationRelative. Rename or remove it first." }
            try {
                Files.move(stage.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(stage.toPath(), destination.toPath())
            }
            completed = true
            return WorkspaceProjectSnapshot(destinationRelative, fileCount, totalBytes)
        } finally {
            if (!completed) stage.deleteRecursively()
        }
    }
}
