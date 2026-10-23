package com.opendroid.ai.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opendroid.ai.core.storage.DeveloperWorkspaceFiles
import com.opendroid.ai.core.storage.WorkspaceArchive
import com.opendroid.ai.data.repository.GoogleDriveFile
import com.opendroid.ai.data.repository.GoogleDriveIntegration
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class GoogleDriveBreadcrumb(val id: String, val name: String)

@HiltViewModel
class GoogleDriveViewModel @Inject constructor(
    private val drive: GoogleDriveIntegration,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val files = MutableStateFlow<List<GoogleDriveFile>>(emptyList())
    val breadcrumbs = MutableStateFlow(listOf(GoogleDriveBreadcrumb("root", "My Drive")))
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun browseRoot(accessToken: String) {
        breadcrumbs.value = listOf(GoogleDriveBreadcrumb("root", "My Drive"))
        loadFolder(accessToken, "root")
    }

    fun openFolder(accessToken: String, folder: GoogleDriveFile) {
        require(folder.isFolder) { "Select a Google Drive folder." }
        breadcrumbs.value = breadcrumbs.value + GoogleDriveBreadcrumb(folder.id, folder.name)
        loadFolder(accessToken, folder.id)
    }

    fun goUp(accessToken: String) {
        val current = breadcrumbs.value
        if (current.size > 1) breadcrumbs.value = current.dropLast(1)
        loadFolder(accessToken, breadcrumbs.value.last().id)
    }

    private fun loadFolder(accessToken: String, folderId: String) = viewModelScope.launch {
        busy.value = true
        message.value = null
        try {
            val found = drive.listAppAccessibleFiles(accessToken, folderId)
            files.value = found
            message.value = if (found.isEmpty()) {
                "This folder has no files currently accessible to this app. The drive.file scope does not expose all of Drive."
            } else {
                "Showing ${found.size} app-accessible item(s), up to 100 in this folder."
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            message.value = failure.message ?: "Could not list Google Drive items."
        } finally {
            busy.value = false
        }
    }

    fun exportWorkspace(accessToken: String) = viewModelScope.launch {
        busy.value = true
        message.value = "Preparing a ZIP of the full app workspace, including any .git history…"
        var archive: File? = null
        try {
            archive = File.createTempFile("usman-workspace-", ".zip", context.cacheDir)
            withContext(Dispatchers.IO) {
                WorkspaceArchive.create(DeveloperWorkspaceFiles(context).root(), archive!!)
            }
            val uploaded = drive.uploadWorkspaceArchive(accessToken, archive!!)
            message.value = "Exported ${uploaded.name} to Drive (${uploaded.id})."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            message.value = failure.message ?: "Google Drive export failed."
        } finally {
            archive?.delete()
            busy.value = false
        }
    }
}
