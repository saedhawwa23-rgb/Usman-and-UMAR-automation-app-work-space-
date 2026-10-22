package com.opendroid.ai.core.storage

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Creates a bounded archive of a workspace without following symbolic links. */
object WorkspaceArchive {
    const val MAX_ENTRIES = 25_000
    const val MAX_UNCOMPRESSED_BYTES = 512L * 1024L * 1024L

    fun create(sourceDirectory: File, destination: File): File {
        val root = sourceDirectory.canonicalFile
        require(root.isDirectory && root.canRead()) { "The app workspace is not readable." }
        require(!destination.canonicalFile.toPath().startsWith(root.toPath())) {
            "The archive destination must be outside the workspace."
        }
        val files = mutableListOf<Pair<File, String>>()
        var totalBytes = 0L

        fun collect(directory: File, relativeDirectory: String, depth: Int) {
            require(depth <= 64) { "The workspace folder depth exceeds the archive limit." }
            val children = directory.listFiles()?.sortedBy { it.name } ?: error("Could not list a workspace folder.")
            for (child in children) {
                if (java.nio.file.Files.isSymbolicLink(child.toPath())) continue
                val canonical = child.canonicalFile
                require(canonical.toPath().startsWith(root.toPath())) { "A workspace path resolves outside the app directory." }
                val relative = if (relativeDirectory.isBlank()) child.name else "$relativeDirectory/${child.name}"
                require(relative.split('/').none { it.isBlank() || it == "." || it == ".." }) {
                    "A workspace path cannot be represented safely in the archive."
                }
                require(relative.none { it == '\\' || it.isISOControl() }) {
                    "A workspace path contains characters that are unsafe in an archive."
                }
                if (canonical.isDirectory) {
                    files += canonical to "$relative/"
                    collect(canonical, relative, depth + 1)
                } else if (canonical.isFile) {
                    totalBytes = Math.addExact(totalBytes, canonical.length())
                    require(totalBytes <= MAX_UNCOMPRESSED_BYTES) {
                        "The workspace exceeds the ${MAX_UNCOMPRESSED_BYTES / (1024 * 1024)} MB export limit."
                    }
                    files += canonical to relative
                }
                require(files.size <= MAX_ENTRIES) { "The workspace exceeds the $MAX_ENTRIES-entry archive limit." }
            }
        }

        collect(root, "", 0)
        destination.parentFile?.mkdirs()
        try {
            ZipOutputStream(FileOutputStream(destination)).use { zip ->
                zip.setLevel(1)
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var streamedBytes = 0L
                for ((file, name) in files) {
                    zip.putNextEntry(ZipEntry(name))
                    if (file.isFile) FileInputStream(file).use { input ->
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            streamedBytes = Math.addExact(streamedBytes, count.toLong())
                            require(streamedBytes <= MAX_UNCOMPRESSED_BYTES) {
                                "The workspace changed while exporting and exceeded the 512 MB limit."
                            }
                            zip.write(buffer, 0, count)
                        }
                    }
                    zip.closeEntry()
                }
            }
            return destination
        } catch (failure: Exception) {
            destination.delete()
            throw failure
        }
    }
}
