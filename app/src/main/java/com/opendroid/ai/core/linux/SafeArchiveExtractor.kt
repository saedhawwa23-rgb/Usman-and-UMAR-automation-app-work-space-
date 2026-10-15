package com.opendroid.ai.core.linux

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.GZIPInputStream

/**
 * Extracts the small Alpine minirootfs tarball without delegating writes to a
 * host tar implementation. Every archive path and symlink is checked before
 * anything is written, and all writes happen in a staging directory.
 */
internal object SafeArchiveExtractor {
    fun extract(archive: File, destination: File) {
        require(archive.isFile && archive.length() > 0) { "Downloaded rootfs archive is missing or empty" }
        val staging = File(destination.parentFile, "${destination.name}.part")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            GZIPInputStream(BufferedInputStream(FileInputStream(archive))).use { input ->
                TarReader(input).forEachEntry { entry -> extractEntry(entry, staging.toPath()) }
            }
            if (destination.exists() && !Files.isSymbolicLink(destination.toPath())) {
                destination.deleteRecursively()
            } else if (destination.exists()) {
                throw IOException("Rootfs destination is a symbolic link")
            }
            check(staging.renameTo(destination)) { "Could not activate extracted Alpine rootfs" }
        } catch (failure: Throwable) {
            staging.deleteRecursively()
            throw failure
        }
    }

    private fun extractEntry(entry: TarEntry, root: Path) {
        val relative = SafeArchivePath.normalizeEntry(entry.name)
        val output = root.resolve(relative).normalize()
        SafeArchivePath.requireContained(root, output, "archive entry")
        if (relative.toString() == "." && entry.type == '5') return
        requireNoSymlinkParents(root, output.parent)
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("Conflicting archive entry: ${entry.name}")
        }

        when (entry.type) {
            '5' -> Files.createDirectories(output)
            '2' -> {
                val target = SafeArchivePath.resolveLinkTarget(relative, entry.linkName)
                val targetPath = root.resolve(target).normalize()
                SafeArchivePath.requireContained(root, targetPath, "symbolic-link target")
                Files.createSymbolicLink(output, entry.linkName.toPathString())
            }
            '0', '\u0000' -> {
                output.parent?.let { Files.createDirectories(it) }
                FileOutputStream(output.toFile()).use { out -> entry.copyDataTo(out) }
                output.toFile().setExecutable(entry.mode and 0x49 != 0, false)
            }
            else -> throw IOException("Unsupported Alpine archive entry type: ${entry.type}")
        }
    }

    private fun requireNoSymlinkParents(root: Path, parent: Path?) {
        var current = parent ?: return
        val normalizedRoot = root.normalize()
        val missing = java.util.ArrayDeque<Path>()
        while (current != normalizedRoot) {
            SafeArchivePath.requireContained(normalizedRoot, current, "archive parent")
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current)) {
                    throw IOException("Archive write would follow symbolic link: $current")
                }
                if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw IOException("Archive parent is not a directory: $current")
                }
                break
            }
            missing.addFirst(current)
            current = current.parent ?: throw IOException("Archive parent escaped rootfs")
        }
        missing.forEach { Files.createDirectory(it) }
    }

    private data class TarEntry(
        val name: String,
        val type: Char,
        val linkName: String,
        val size: Long,
        val mode: Int,
        private val input: TarReader
    ) {
        fun copyDataTo(output: FileOutputStream) = input.copy(size, output)
    }

    private class TarReader(private val input: InputStream) {
        fun forEachEntry(consumer: (TarEntry) -> Unit) {
            while (true) {
                val header = ByteArray(BLOCK_SIZE)
                val count = readFully(header)
                if (count == 0 || header.all { it == 0.toByte() }) return
                check(count == BLOCK_SIZE) { "Truncated Alpine tar header" }
                val name = readString(header, 0, 100)
                val prefix = readString(header, 345, 155)
                val fullName = if (prefix.isEmpty()) name else "$prefix/$name"
                val size = readOctal(header, 124, 12)
                val mode = readOctal(header, 100, 8).toInt()
                val type = header[156].toInt().toChar().let { if (it == '\u0000') '\u0000' else it }
                val linkName = readString(header, 157, 100)
                val entry = TarEntry(fullName, type, linkName, size, mode, this)
                consumer(entry)
                if (type != '0' && type != '\u0000') skip(size)
                skip((BLOCK_SIZE - (size % BLOCK_SIZE)) % BLOCK_SIZE)
            }
        }

        fun copy(size: Long, output: FileOutputStream) {
            var remaining = size
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (remaining > 0) {
                val wanted = minOf(remaining, buffer.size.toLong()).toInt()
                val read = input.read(buffer, 0, wanted)
                check(read > 0) { "Truncated Alpine tar entry" }
                output.write(buffer, 0, read)
                remaining -= read
            }
        }

        private fun skip(count: Long) {
            var remaining = count
            while (remaining > 0) {
                val skipped = input.skip(remaining)
                if (skipped > 0) remaining -= skipped
                else check(input.read() >= 0) { "Truncated Alpine tar archive" }.also { remaining-- }
            }
        }

        private fun readFully(buffer: ByteArray): Int {
            var offset = 0
            while (offset < buffer.size) {
                val read = input.read(buffer, offset, buffer.size - offset)
                if (read < 0) break
                offset += read
            }
            return offset
        }

        private fun readString(bytes: ByteArray, offset: Int, length: Int): String =
            bytes.copyOfRange(offset, offset + length)
                .takeWhile { it != 0.toByte() }
                .toByteArray()
                .toString(Charsets.US_ASCII)
                .trim()

        private fun readOctal(bytes: ByteArray, offset: Int, length: Int): Long =
            readString(bytes, offset, length).trimStart('0').ifEmpty { "0" }.toLong(8)

        companion object { private const val BLOCK_SIZE = 512 }
    }
}

internal object SafeArchivePath {
    fun isSafe(name: String): Boolean = runCatching { normalizeEntry(name); true }.getOrDefault(false)

    fun normalizeEntry(name: String): Path {
        require(name.isNotEmpty() && !name.contains('\u0000')) { "Archive path is empty or contains NUL" }
        val normalized = name.replace('\\', '/')
        require(!normalized.startsWith('/')) { "Absolute archive path: $name" }
        val withoutPrefix = normalized.removePrefix("./")
        val path = withoutPrefix.toPathString().normalize()
        require(!path.isAbsolute && path.nameCount > 0 && path.first().toString() != "..") {
            "Archive path escapes rootfs: $name"
        }
        return path
    }

    fun resolveLinkTarget(link: Path, target: String): Path {
        require(target.isNotEmpty() && !target.contains('\u0000')) { "Invalid symbolic-link target" }
        val targetPath = target.replace('\\', '/').toPathString()
        require(!targetPath.isAbsolute) { "Absolute symbolic-link target: $target" }
        val parent = link.parent ?: Paths.get("")
        val resolved = parent.resolve(targetPath).normalize()
        require(!resolved.isAbsolute && resolved.nameCount > 0 && resolved.first().toString() != "..") {
            "Symbolic-link target escapes rootfs: $target"
        }
        return resolved
    }

    fun requireContained(root: Path, candidate: Path, what: String) {
        val rootPath = root.toAbsolutePath().normalize()
        val candidatePath = candidate.toAbsolutePath().normalize()
        require(candidatePath == rootPath || candidatePath.startsWith(rootPath)) {
            "$what escapes rootfs: $candidate"
        }
    }
}

private fun String.toPathString(): Path = Paths.get(this)
