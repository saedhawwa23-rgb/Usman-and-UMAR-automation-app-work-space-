package com.opendroid.ai.core.storage

import android.content.Context
import java.io.File

/** Small app-private workspace API for the Developers file browser. */
data class WorkspaceFileEntry(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long
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
}

class DeveloperWorkspaceFiles(private val context: Context) {
    fun root(): File = StorageWorkspaceProvider.getDefaultWorkspaceDir(context).canonicalFile

    fun list(relativeDirectory: String = ""): List<WorkspaceFileEntry> {
        val directory = resolve(relativeDirectory, allowRoot = true)
        require(directory.exists() && directory.isDirectory) { "Workspace directory does not exist." }
        return (directory.listFiles() ?: emptyArray())
            .filter { it.canonicalFile.toPath().startsWith(root().toPath()) }
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .map { file ->
                val relative = root().toPath().relativize(file.canonicalFile.toPath()).toString().replace(File.separatorChar, '/')
                WorkspaceFileEntry(file.name, relative, file.isDirectory, if (file.isFile) file.length() else 0L)
            }
    }

    fun read(relativeFile: String): String {
        val file = resolve(relativeFile)
        require(file.isFile) { "Select a workspace text file to open." }
        require(file.length() <= MAX_TEXT_BYTES) { "Text files larger than ${MAX_TEXT_BYTES / 1024} KB cannot be opened in the editor." }
        return file.readText(Charsets.UTF_8)
    }

    fun createFile(relativePath: String): File {
        val file = resolve(relativePath)
        require(!file.exists()) { "A file or folder with that name already exists." }
        require(file.parentFile?.isDirectory == true) { "The selected parent folder does not exist." }
        require(file.createNewFile()) { "Could not create the file." }
        return file
    }

    fun createDirectory(relativePath: String): File {
        val directory = resolve(relativePath)
        require(!directory.exists()) { "A file or folder with that name already exists." }
        require(directory.parentFile?.isDirectory == true) { "The selected parent folder does not exist." }
        require(directory.mkdir()) { "Could not create the folder." }
        return directory
    }

    fun save(relativePath: String, content: String) {
        require(content.toByteArray(Charsets.UTF_8).size <= MAX_TEXT_BYTES) { "Text files are limited to ${MAX_TEXT_BYTES / 1024} KB." }
        val file = resolve(relativePath)
        require(file.isFile) { "The selected workspace file no longer exists." }
        file.writeText(content, Charsets.UTF_8)
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

    fun rename(relativePath: String, newName: String): String {
        val source = resolve(relativePath)
        require(source.exists()) { "The selected workspace item no longer exists." }
        val cleanName = WorkspacePathPolicy.validateName(newName)
        val destination = resolve(source.parentFile?.let { parent ->
            val parentPath = root().toPath().relativize(parent.canonicalFile.toPath()).toString().replace(File.separatorChar, '/')
            listOf(parentPath, cleanName).filter { it.isNotBlank() }.joinToString("/")
        } ?: cleanName)
        require(!destination.exists()) { "A file or folder with that name already exists." }
        require(source.renameTo(destination)) { "Rename failed. Check available storage and permissions." }
        return root().toPath().relativize(destination.canonicalFile.toPath()).toString().replace(File.separatorChar, '/')
    }

    /** Only files and empty folders can be deleted; recursive deletion is intentionally disabled. */
    fun delete(relativePath: String) {
        val target = resolve(relativePath)
        require(target.exists()) { "The selected workspace item no longer exists." }
        require(!target.isDirectory || target.listFiles().isNullOrEmpty()) { "Folder is not empty. Move or delete its contents first." }
        require(target.delete()) { "Delete failed. Check available storage and permissions." }
    }

    private fun resolve(path: String, allowRoot: Boolean = false): File {
        val root = root()
        val candidate = if (path.isBlank() && allowRoot) root else File(root, WorkspacePathPolicy.normalizeRelative(path))
        val canonical = candidate.canonicalFile
        require(canonical.toPath().startsWith(root.toPath())) { "The requested path resolves outside the app workspace." }
        return canonical
    }

    private companion object { const val MAX_TEXT_BYTES = 100 * 1024 }
}
