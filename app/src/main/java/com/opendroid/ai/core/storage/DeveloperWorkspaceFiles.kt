package com.opendroid.ai.core.storage

import android.content.Context
import com.opendroid.ai.core.backend.BackendCapabilities
import com.opendroid.ai.core.backend.BackendDescriptor
import com.opendroid.ai.core.backend.BackendHealth
import com.opendroid.ai.core.backend.BackendKind
import com.opendroid.ai.core.backend.BackendStatus
import com.opendroid.ai.core.backend.WorkspaceSandboxBackend
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

internal object WorkspaceAtomicWriter {
    fun write(file: File, bytes: ByteArray) {
        val parent = file.parentFile ?: error("The workspace file has no parent directory.")
        val temporary = File(parent, ".usman-${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }
}

data class WorkspaceFileEntry(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long
)

data class WorkspaceSearchResult(
    val entries: List<WorkspaceFileEntry>,
    val limited: Boolean
)

object WorkspacePathPolicy {
    fun normalizeRelative(path: String): String {
        require(path.isNotBlank()) { "A relative workspace path is required." }
        require(!path.startsWith('/') && !path.startsWith('\\')) { "Absolute paths are not allowed in the workspace." }
        require('\u0000' !in path && '\\' !in path) { "The workspace path contains an invalid separator." }
        val parts = path.split('/')
        require(parts.none { it.isBlank() || it == "." || it == ".." }) { "The workspace path is invalid or traverses outside its directory." }
        require(parts.all { it.length <= 128 && it.none(Char::isISOControl) }) { "A workspace path component is invalid or too long." }
        return parts.joinToString("/")
    }

    fun validateName(name: String): String {
        require(name.isNotBlank() && name != "." && name != "..") { "Enter a valid file or folder name." }
        require(name.length <= 128 && name.none { it.isISOControl() || it == '/' || it == '\\' }) {
            "Names must be a single workspace path component (maximum 128 characters)."
        }
        return name
    }

    fun moveTarget(sourceRelativePath: String, destinationDirectory: String): String {
        val source = normalizeRelative(sourceRelativePath)
        val destination = if (destinationDirectory.isBlank()) "" else normalizeRelative(destinationDirectory)
        val target = listOf(destination, source.substringAfterLast('/')).filter { it.isNotBlank() }.joinToString("/")
        require(target != source && !target.startsWith("$source/")) {
            "An item cannot be moved into itself or one of its child folders."
        }
        return target
    }

    fun renameTarget(sourceRelativePath: String, newName: String): String {
        val source = normalizeRelative(sourceRelativePath)
        val cleanName = validateName(newName)
        val parent = source.substringBeforeLast('/', missingDelimiterValue = "")
        return listOf(parent, cleanName).filter { it.isNotBlank() }.joinToString("/")
    }
}

/** Small app-private workspace API for the Developers file browser. */
class DeveloperWorkspaceFiles(private val context: Context) : WorkspaceSandboxBackend {
    override val descriptor = BackendDescriptor(
        id = "app-workspace",
        name = "App-owned project workspace",
        kind = BackendKind.APP_WORKSPACE,
        capabilities = BackendCapabilities(
            browseFiles = true,
            searchFiles = true,
            editFiles = true,
            importExportFiles = true,
            resourceLimits = true
        ),
        securityBoundary = "Files are stored in this app's app-specific external files directory, falling back to its internal files directory; all app code shares this app boundary.",
        limitations = listOf(
            "This backend provides file operations only; it does not execute commands or AI tasks.",
            "Text editing/search is limited to supported UTF-8 files up to 100 KB; search scans at most 5,000 entries and returns at most 100 matches.",
            "It has no Git metadata, project-scoped task history, diff/checkpoint/rollback, process cancellation, or per-project isolation."
        )
    )

    fun root(): File = StorageWorkspaceProvider.getDefaultWorkspaceDir(context).canonicalFile

    override fun healthCheck(): BackendHealth = runCatching {
        val directory = root()
        require(directory.isDirectory && directory.canRead() && directory.canWrite())
        BackendHealth(BackendStatus.AVAILABLE, "App workspace directory exists and current process has read/write access; no command-execution smoke test applies.")
    }.getOrElse { BackendHealth(BackendStatus.REQUIRES_CONFIGURATION, "App workspace is not currently readable/writable: ${it.message ?: "storage unavailable"}") }

    override fun listFiles(path: String): List<WorkspaceFileEntry> = list(path)
    override fun searchFiles(query: String): WorkspaceSearchResult = search(query)
    override fun readFile(path: String): String = read(path)
    override fun saveFile(path: String, content: String) = save(path, content)
    override fun importTextFile(path: String, content: String) = importText(path, content)

    fun list(relativeDirectory: String = ""): List<WorkspaceFileEntry> {
        val directory = resolve(relativeDirectory, allowRoot = true)
        require(directory.exists() && directory.isDirectory) { "Workspace directory does not exist." }
        return (directory.listFiles() ?: emptyArray())
            .filter { !Files.isSymbolicLink(it.toPath()) && it.canonicalFile.toPath().startsWith(root().toPath()) }
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .map(::entryFor)
    }

    /**
     * Searches names and small known-text files without following symlinks. The scan is bounded
     * to 5,000 filesystem entries, depth 12, 100 results, and 100 KB per text file.
     */
    fun search(query: String, maxResults: Int = MAX_SEARCH_RESULTS): WorkspaceSearchResult {
        val needle = query.trim()
        require(needle.isNotEmpty()) { "Enter a search term." }
        require(maxResults in 1..MAX_SEARCH_RESULTS) { "Search result limit must be between 1 and $MAX_SEARCH_RESULTS." }
        val workspaceRoot = root()
        val rootPath = workspaceRoot.toPath()
        val matches = mutableListOf<WorkspaceFileEntry>()
        var visited = 0
        var limited = false

        fun walk(directory: File, depth: Int) {
            if (limited) return
            if (depth > MAX_SEARCH_DEPTH || visited >= MAX_SEARCH_ENTRIES || matches.size >= maxResults) {
                limited = true
                return
            }
            val children = directory.listFiles() ?: return
            for (child in children) {
                if (Files.isSymbolicLink(child.toPath())) continue
                val canonical = runCatching { child.canonicalFile }.getOrNull() ?: continue
                if (!canonical.toPath().startsWith(rootPath)) continue
                visited++
                if (canonical.name.contains(needle, ignoreCase = true)) {
                    matches += entryFor(canonical)
                } else if (canonical.isFile && isSearchableText(canonical) && canonical.length() <= MAX_TEXT_BYTES) {
                    val text = runCatching { canonical.readText(Charsets.UTF_8) }.getOrNull()
                    if (text != null && '\u0000' !in text && text.contains(needle, ignoreCase = true)) {
                        matches += entryFor(canonical)
                    }
                }
                if (matches.size >= maxResults || visited >= MAX_SEARCH_ENTRIES) {
                    limited = true
                    return
                }
                if (canonical.isDirectory) walk(canonical, depth + 1)
                if (limited) return
            }
        }

        walk(workspaceRoot, 0)
        return WorkspaceSearchResult(matches, limited)
    }

    fun read(relativeFile: String): String {
        val file = resolve(relativeFile)
        require(file.isFile) { "Select a workspace text file to open." }
        require(file.length() <= MAX_TEXT_BYTES) { "Text files larger than ${MAX_TEXT_BYTES / 1024} KB cannot be opened in the editor." }
        return file.readText(Charsets.UTF_8)
    }

    override fun createFile(relativePath: String): File {
        val file = resolve(relativePath)
        require(!file.exists()) { "A file or folder with that name already exists." }
        require(file.parentFile?.isDirectory == true) { "The selected parent folder does not exist." }
        require(file.createNewFile()) { "Could not create the file." }
        return file
    }

    override fun createDirectory(relativePath: String): File {
        val directory = resolve(relativePath)
        require(!directory.exists()) { "A file or folder with that name already exists." }
        require(directory.parentFile?.isDirectory == true) { "The selected parent folder does not exist." }
        require(directory.mkdir()) { "Could not create the folder." }
        return directory
    }

    fun save(relativePath: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_TEXT_BYTES) { "Text files are limited to ${MAX_TEXT_BYTES / 1024} KB." }
        val file = resolve(relativePath)
        require(file.isFile) { "The selected workspace file no longer exists." }
        WorkspaceAtomicWriter.write(file, bytes)
    }

    fun importText(relativePath: String, content: String) {
        val file = createFile(relativePath)
        try {
            save(relativePath, content)
        } catch (failure: Exception) {
            file.delete()
            throw failure
        }
    }

    override fun rename(relativePath: String, newName: String): String {
        val source = resolve(relativePath)
        require(source.exists()) { "The selected workspace item no longer exists." }
        val destination = resolve(WorkspacePathPolicy.renameTarget(relativePath, newName))
        require(!destination.exists()) { "A file or folder with that name already exists." }
        require(source.renameTo(destination)) { "Rename failed. Check available storage and permissions." }
        return relativePathOf(destination)
    }

    /** Moves a file or folder inside the app workspace. Existing targets are never overwritten. */
    override fun move(relativePath: String, destinationDirectory: String): String {
        val source = resolve(relativePath)
        require(source.exists()) { "The selected workspace item no longer exists." }
        val destinationDir = resolve(destinationDirectory, allowRoot = true)
        require(destinationDir.isDirectory) { "The destination workspace folder does not exist." }
        val targetPath = WorkspacePathPolicy.moveTarget(relativePath, destinationDirectory)
        val destination = resolve(targetPath)
        require(!destination.exists()) { "A file or folder with that name already exists in the destination." }
        require(source.renameTo(destination)) { "Move failed. Check available storage and permissions." }
        return relativePathOf(destination)
    }

    /** Only files and empty folders can be deleted; recursive deletion is intentionally disabled. */
    override fun delete(relativePath: String) {
        val target = resolve(relativePath)
        require(target.exists()) { "The selected workspace item no longer exists." }
        require(!target.isDirectory || target.listFiles().isNullOrEmpty()) { "Folder is not empty. Move or delete its contents first." }
        require(target.delete()) { "Delete failed. Check available storage and permissions." }
    }

    private fun resolve(path: String, allowRoot: Boolean = false): File {
        val workspaceRoot = root()
        if (path.isBlank() && allowRoot) return workspaceRoot
        val relative = WorkspacePathPolicy.normalizeRelative(path)
        var candidate = workspaceRoot
        for (component in relative.split('/')) {
            candidate = File(candidate, component)
            require(!Files.isSymbolicLink(candidate.toPath())) { "Symbolic links are not supported in the app workspace." }
        }
        val canonical = candidate.canonicalFile
        require(canonical.toPath().startsWith(workspaceRoot.toPath())) { "The requested path resolves outside the app workspace." }
        return canonical
    }

    private fun entryFor(file: File): WorkspaceFileEntry = WorkspaceFileEntry(
        name = file.name,
        relativePath = relativePathOf(file),
        isDirectory = file.isDirectory,
        sizeBytes = if (file.isFile) file.length() else 0L
    )

    private fun relativePathOf(file: File): String = root().toPath().relativize(file.canonicalFile.toPath())
        .toString().replace(File.separatorChar, '/')

    private fun isSearchableText(file: File): Boolean = file.name in SEARCHABLE_DOTFILES ||
        file.extension.lowercase() in SEARCHABLE_EXTENSIONS

    private companion object {
        const val MAX_TEXT_BYTES = 100 * 1024L
        const val MAX_SEARCH_RESULTS = 100
        const val MAX_SEARCH_ENTRIES = 5_000
        const val MAX_SEARCH_DEPTH = 12
        val SEARCHABLE_DOTFILES = setOf(".gitignore", ".gitattributes", ".gitmodules", ".editorconfig")
        val SEARCHABLE_EXTENSIONS = setOf("txt", "md", "kt", "kts", "java", "gradle", "xml", "yml", "yaml", "json", "py", "sh", "properties", "js", "ts", "html", "css", "toml", "ini", "csv")
    }
}
