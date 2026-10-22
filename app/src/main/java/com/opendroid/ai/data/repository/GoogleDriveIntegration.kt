package com.opendroid.ai.data.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val GOOGLE_DRIVE_FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"

data class GoogleDriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val modifiedTime: String?,
    val webViewLink: String?,
    val parentIds: List<String>
) {
    val isFolder: Boolean get() = mimeType == GOOGLE_DRIVE_FOLDER_MIME_TYPE
}

/** Drive REST client. Tokens are short-lived inputs from AuthorizationClient and are never stored. */
@Singleton
class GoogleDriveIntegration private constructor(
    private val client: OkHttpClient,
    private val filesUrl: okhttp3.HttpUrl
) {
    @Inject constructor(client: OkHttpClient) : this(client, FILES_URL.toHttpUrl())

    internal constructor(client: OkHttpClient, filesEndpoint: String) : this(client, filesEndpoint.toHttpUrl())

    suspend fun listAppAccessibleFiles(
        accessToken: String,
        parentId: String = "root"
    ): List<GoogleDriveFile> = withContext(Dispatchers.IO) {
        validateToken(accessToken)
        require(parentId == "root" || DRIVE_ID_PATTERN.matches(parentId)) { "The selected Google Drive folder ID is invalid." }
        val url = filesUrl.newBuilder()
            .addQueryParameter("q", "'$parentId' in parents and trashed = false")
            .addQueryParameter("pageSize", MAX_LIST_RESULTS.toString())
            .addQueryParameter("orderBy", "modifiedTime desc")
            .addQueryParameter("fields", "nextPageToken,files(id,name,mimeType,modifiedTime,webViewLink,parents)")
            .build()
        val request = Request.Builder().url(url).get().header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            val json = response.body.string().asJsonObject()
            if (!response.isSuccessful) throw apiFailure("list Drive files", response.code, json)
            json.getAsJsonArray("files")?.map { item ->
                val file = item.asJsonObject
                GoogleDriveFile(
                    id = file.string("id"),
                    name = file.string("name"),
                    mimeType = file.string("mimeType"),
                    modifiedTime = file.stringOrNull("modifiedTime"),
                    webViewLink = file.stringOrNull("webViewLink"),
                    parentIds = file.getAsJsonArray("parents")?.map { it.asString }.orEmpty()
                )
            }.orEmpty()
        }
    }

    /** Uploads a workspace ZIP using Drive's resumable-create protocol in bounded 256 KiB chunks. */
    suspend fun uploadWorkspaceArchive(accessToken: String, archive: File): GoogleDriveFile = withContext(Dispatchers.IO) {
        validateToken(accessToken)
        require(archive.isFile && archive.canRead()) { "The workspace archive is not readable." }
        require(archive.length() in 1L..MAX_UPLOAD_BYTES) { "The workspace archive is empty or exceeds the 512 MB upload limit." }

        val metadata = JsonObject().apply {
            addProperty("name", archive.name)
            addProperty("mimeType", ZIP_MIME_TYPE)
        }
        val startUrl = filesUrl.newBuilder()
            .addQueryParameter("uploadType", "resumable")
            .addQueryParameter("fields", "id,name,mimeType,modifiedTime,webViewLink")
            .build()
        val startRequest = Request.Builder().url(startUrl).post(metadata.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json; charset=UTF-8")
            .header("X-Upload-Content-Type", ZIP_MIME_TYPE)
            .header("X-Upload-Content-Length", archive.length().toString())
            .build()
        val sessionUrl = client.newCall(startRequest).execute().use { response ->
            if (!response.isSuccessful) throw apiFailure("start Drive upload", response.code, response.body.string().asJsonObject())
            response.header("Location") ?: throw IOException("Drive did not return a resumable upload session.")
        }

        FileInputStream(archive).use { input ->
            var offset = 0L
            val total = archive.length()
            while (offset < total) {
                val chunkLength = minOf(CHUNK_BYTES.toLong(), total - offset).toInt()
                val chunk = ByteArray(chunkLength)
                var filled = 0
                while (filled < chunkLength) {
                    val count = input.read(chunk, filled, chunkLength - filled)
                    if (count < 0) throw IOException("The archive changed during upload.")
                    filled += count
                }
                val end = offset + chunkLength - 1
                val request = Request.Builder().url(sessionUrl).put(chunk.toRequestBody(ZIP_MEDIA_TYPE))
                    .header("Authorization", "Bearer $accessToken")
                    .header("Content-Range", "bytes $offset-$end/$total")
                    .build()
                client.newCall(request).execute().use { response ->
                    val finalChunk = end + 1 == total
                    if (finalChunk) {
                        if (!response.isSuccessful) throw apiFailure("finish Drive upload", response.code, response.body.string().asJsonObject())
                        val json = response.body.string().asJsonObject()
                        return@withContext json.toDriveFile()
                    }
                    if (response.code != RESUMABLE_INCOMPLETE) {
                        throw apiFailure("continue Drive upload", response.code, response.body.string().asJsonObject())
                    }
                }
                offset += chunkLength
            }
        }
        throw IOException("Drive upload ended without a file result.")
    }

    private fun validateToken(token: String) {
        require(token.isNotBlank() && token.length <= MAX_TOKEN_CHARS && token.none(Char::isWhitespace)) {
            "Google Drive authorization is missing or invalid. Authorize the Drive action again."
        }
    }

    private fun apiFailure(action: String, status: Int, json: JsonObject): IOException {
        val message = json.getAsJsonObject("error")?.get("message")?.asString
        return IOException("Could not $action (HTTP $status)${message?.let { ": $it" } ?: "."}")
    }

    private fun String.asJsonObject(): JsonObject = runCatching { Gson().fromJson(this, JsonObject::class.java) }
        .getOrNull() ?: JsonObject()

    private fun JsonObject.string(key: String): String = get(key)?.takeUnless { it.isJsonNull }?.asString
        ?: throw IOException("Drive response omitted $key.")

    private fun JsonObject.stringOrNull(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.asString

    private fun JsonObject.toDriveFile() = GoogleDriveFile(
        id = string("id"),
        name = string("name"),
        mimeType = string("mimeType"),
        modifiedTime = stringOrNull("modifiedTime"),
        webViewLink = stringOrNull("webViewLink"),
        parentIds = getAsJsonArray("parents")?.map { it.asString }.orEmpty()
    )

    private companion object {
        const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
        const val MAX_LIST_RESULTS = 100
        const val MAX_TOKEN_CHARS = 8_192
        const val MAX_UPLOAD_BYTES = 512L * 1024L * 1024L
        const val CHUNK_BYTES = 256 * 1024
        const val RESUMABLE_INCOMPLETE = 308
        const val ZIP_MIME_TYPE = "application/zip"
        val DRIVE_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,256}")
        val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()
        val ZIP_MEDIA_TYPE = ZIP_MIME_TYPE.toMediaType()
    }
}
