package com.opendroid.ai.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.opendroid.ai.core.storage.DeveloperWorkspaceFiles
import com.opendroid.ai.core.storage.WorkspaceFileEntry
import com.opendroid.ai.core.storage.WorkspaceDiffPreview
import com.opendroid.ai.core.storage.WorkspacePathPolicy
import com.opendroid.ai.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun WorkspaceFilesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val files = remember(context) { DeveloperWorkspaceFiles(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var currentPath by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<WorkspaceFileEntry>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var searchLimited by remember { mutableStateOf(false) }
    var editorPath by remember { mutableStateOf<String?>(null) }
    var content by remember { mutableStateOf("") }
    var originalContent by remember { mutableStateOf("") }
    var saveReviewOpen by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var renameName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<WorkspaceFileEntry?>(null) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<WorkspaceFileEntry?>(null) }
    var moveTarget by remember { mutableStateOf<WorkspaceFileEntry?>(null) }
    var moveDestination by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val saveDiff = remember(originalContent, content) { WorkspaceDiffPreview.create(originalContent, content) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val imported = runCatching {
                withContext(Dispatchers.IO) {
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "imported.txt"
                    val safeName = WorkspacePathPolicy.validateName(name)
                    require(isPlainTextName(safeName)) { "Only supported text/configuration files can be imported." }
                    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 100 * 1024) { "Imported text files are limited to 100 KB." }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    } ?: error("Could not open the selected document.")
                    val text = bytes.toString(Charsets.UTF_8)
                    require('\u0000' !in text) { "The selected file does not appear to be plain text." }
                    val target = childPath(currentPath, safeName)
                    files.importText(target, text)
                    target to text
                }
            }
            imported.onSuccess { (path, text) -> editorPath = path; content = text; originalContent = text; status = "Imported ${path.substringAfterLast('/')} into the app workspace."; refresh++ }
                .onFailure { status = it.message ?: "Import failed." }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            val exported = runCatching {
                withContext(Dispatchers.IO) {
                    require(content.toByteArray(Charsets.UTF_8).size <= 100 * 1024) { "Exported text files are limited to 100 KB." }
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(content) }
                        ?: error("Could not open the selected export destination.")
                }
            }
            exported.onSuccess { status = "Exported ${editorPath?.substringAfterLast('/') ?: "text file"}." }
                .onFailure { status = it.message ?: "Export failed." }
        }
    }

    LaunchedEffect(currentPath, refresh, searchQuery) {
        loading = true
        if (searchQuery.isNotBlank()) delay(150)
        val result = runCatching {
            withContext(Dispatchers.IO) {
                if (searchQuery.isBlank()) {
                    files.list(currentPath) to false
                } else {
                    val search = files.search(searchQuery)
                    search.entries to search.limited
                }
            }
        }
        result.onSuccess { (found, limited) -> entries = found; searchLimited = limited }
            .onFailure { status = it.message ?: "Could not read the app workspace." }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("APP WORKSPACE FILES", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        },
        containerColor = AppTheme.colors.background
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("App-owned workspace · project files persist in this app's workspace folder. This is file storage, not an isolated execution sandbox.", color = AppTheme.colors.textSecondary)
                Text("/${currentPath}", color = AppTheme.colors.accentCyan, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search workspace names and small text files") }
                )
                if (searchLimited) Text("Search stopped at its safe scan/result limit; narrow the query for more results.", color = AppTheme.colors.textSecondary)
                if (currentPath.isNotBlank()) {
                    OutlinedButton(onClick = { currentPath = currentPath.substringBeforeLast('/', ""); editorPath = null }) { Text("UP ONE FOLDER") }
                }
                if (loading) Text("Loading workspace…", color = AppTheme.colors.textSecondary)
                status?.let { Text(it, color = AppTheme.colors.textSecondary) }
            }
            item {
                OutlinedTextField(newName, { newName = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("New file or folder name") })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val target = childPath(currentPath, newName)
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { files.createFile(target) } }
                                    .onSuccess { status = "Created ${it.name}."; newName = ""; refresh++ }
                                    .onFailure { status = it.message ?: "File creation failed." }
                            }
                        }, modifier = Modifier.weight(1f), enabled = newName.isNotBlank()
                    ) { Text("CREATE FILE") }
                    OutlinedButton(
                        onClick = {
                            val target = childPath(currentPath, newName)
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { files.createDirectory(target) } }
                                    .onSuccess { status = "Created folder ${it.name}."; newName = ""; refresh++ }
                                    .onFailure { status = it.message ?: "Folder creation failed." }
                            }
                        }, modifier = Modifier.weight(1f), enabled = newName.isNotBlank()
                    ) { Icon(Icons.Default.CreateNewFolder, contentDescription = null); Text(" FOLDER") }
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("text/*", "application/json", "application/xml")) }, modifier = Modifier.fillMaxWidth()) { Text("IMPORT TEXT FILE") }
            }
            items(entries, key = { it.relativePath }) { entry ->
                Card(colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, contentDescription = null, tint = AppTheme.colors.accentCyan)
                            Column(Modifier.weight(1f).clickable {
                                if (entry.isDirectory) {
                                    currentPath = entry.relativePath
                                    searchQuery = ""
                                    editorPath = null
                                } else {
                                    scope.launch {
                                        val opened = runCatching {
                                            withContext(Dispatchers.IO) {
                                                require(isPlainTextName(entry.name)) { "This viewer supports text/configuration files only." }
                                                files.read(entry.relativePath)
                                            }
                                        }
                                        opened.onSuccess { editorPath = entry.relativePath; content = it; originalContent = it; status = "Opened ${entry.name}." }
                                            .onFailure { status = it.message ?: "Could not open this file." }
                                    }
                                }
                            }.padding(vertical = 6.dp)) {
                                Text(entry.name, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (searchQuery.isNotBlank()) entry.relativePath else if (entry.isDirectory) "Folder" else "${entry.sizeBytes} bytes",
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                            IconButton(onClick = { renameTarget = entry; renameName = entry.name; renameOpen = true }) {
                                Icon(Icons.Default.Edit, "Rename ${entry.name}")
                            }
                            IconButton(onClick = { deleteTarget = entry }) { Icon(Icons.Default.Delete, "Delete ${entry.name}") }
                            TextButton(onClick = { moveTarget = entry; moveDestination = currentPath }) { Text("MOVE") }
                        }
                    }
                }
            }
            if (entries.isEmpty() && !loading) item {
                Text(
                    if (searchQuery.isBlank()) "This workspace folder is empty." else "No workspace items match ‘$searchQuery’." ,
                    color = AppTheme.colors.textSecondary
                )
            }
            editorPath?.let { path ->
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("EDIT · ${path.substringAfterLast('/')}", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        label = { Text("UTF-8 text (100 KB maximum)") }
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (content == originalContent) status = "No unsaved changes."
                            else saveReviewOpen = true
                        }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Save, contentDescription = null); Text(" REVIEW SAVE") }
                        OutlinedButton(onClick = {
                            renameTarget = WorkspaceFileEntry(path.substringAfterLast('/'), path, false, 0L)
                            renameName = path.substringAfterLast('/')
                            renameOpen = true
                        }, modifier = Modifier.weight(1f)) { Text("RENAME") }
                        OutlinedButton(onClick = { exportLauncher.launch(path.substringAfterLast('/')) }, modifier = Modifier.weight(1f)) { Text("EXPORT") }
                        IconButton(onClick = {
                            deleteTarget = WorkspaceFileEntry(path.substringAfterLast('/'), path, false, 0L)
                        }) { Icon(Icons.Default.Delete, "Delete file") }
                    }
                }
            }
        }
    }

    if (saveReviewOpen) {
        AlertDialog(
            onDismissRequest = { saveReviewOpen = false },
            title = { Text("Review changes before saving") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${saveDiff.removedLines} line(s) removed; ${saveDiff.addedLines} line(s) added. Review this bounded diff before writing to the app workspace.")
                    Text(saveDiff.text, Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val path = editorPath
                    val approvedContent = content
                    saveReviewOpen = false
                    if (path != null) scope.launch {
                        runCatching { withContext(Dispatchers.IO) { files.save(path, approvedContent) } }
                            .onSuccess { originalContent = approvedContent; status = "Saved ${path.substringAfterLast('/')}."; refresh++ }
                            .onFailure { status = it.message ?: "Save failed." }
                    }
                }) { Text("APPROVE AND SAVE") }
            },
            dismissButton = { TextButton(onClick = { saveReviewOpen = false }) { Text("CANCEL") } }
        )
    }
    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Rename workspace item") },
            text = { OutlinedTextField(renameName, { renameName = it }, label = { Text("New name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val target = renameTarget
                    if (target != null) scope.launch {
                        runCatching { withContext(Dispatchers.IO) { files.rename(target.relativePath, renameName) } }
                            .onSuccess {
                                if (editorPath == target.relativePath) editorPath = it
                                status = "Renamed to ${it.substringAfterLast('/')}."
                                refresh++
                            }
                            .onFailure { status = it.message ?: "Rename failed." }
                    }
                    renameTarget = null
                    renameOpen = false
                }) { Text("RENAME") }
            },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("CANCEL") } }
        )
    }
    moveTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("Move ${target.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a destination folder path relative to the app workspace. Leave blank for the workspace root. Existing items will never be overwritten.")
                    OutlinedTextField(
                        value = moveDestination,
                        onValueChange = { moveDestination = it },
                        singleLine = true,
                        label = { Text("Destination folder") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { files.move(target.relativePath, moveDestination) } }
                            .onSuccess { newPath ->
                                val oldPath = target.relativePath
                                fun remap(path: String): String = when {
                                    path == oldPath -> newPath
                                    path.startsWith("$oldPath/") -> newPath + path.removePrefix(oldPath)
                                    else -> path
                                }
                                currentPath = remap(currentPath)
                                editorPath = editorPath?.let(::remap)
                                status = "Moved ${target.name} to ${if (moveDestination.isBlank()) "workspace root" else moveDestination}."
                                refresh++
                            }
                            .onFailure { status = it.message ?: "Move failed." }
                    }
                    moveTarget = null
                }) { Text("MOVE") }
            },
            dismissButton = { TextButton(onClick = { moveTarget = null }) { Text("CANCEL") } }
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${target.name}?") },
            text = { Text("This permanently deletes the selected workspace file. Only empty folders can be deleted. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { files.delete(target.relativePath) } }
                            .onSuccess { status = "Deleted ${target.name}."; if (editorPath == target.relativePath) { editorPath = null; content = "" }; refresh++ }
                            .onFailure { status = it.message ?: "Delete failed." }
                    }
                    deleteTarget = null
                }) { Text("DELETE") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("CANCEL") } }
        )
    }
}

private fun childPath(parent: String, name: String): String = listOf(parent, name.trim()).filter { it.isNotEmpty() }.joinToString("/")

private fun isPlainTextName(name: String): Boolean = name in setOf(".gitignore", ".gitattributes", ".gitmodules", ".editorconfig") ||
    name.substringAfterLast('.', "").lowercase() in setOf(
        "txt", "md", "kt", "kts", "java", "gradle", "xml", "yml", "yaml", "json", "py", "sh", "properties", "js", "ts", "html", "css", "toml", "ini", "csv"
    )
