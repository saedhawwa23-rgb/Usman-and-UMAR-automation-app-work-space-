package com.opendroid.ai.data.repository

import java.io.File
import java.net.InetAddress
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveIntegrationTest {
    private val server = MockWebServer().also { it.start(InetAddress.getByName("127.0.0.1"), 0) }
    private val drive = GoogleDriveIntegration(OkHttpClient(), server.url("/drive/v3/files").toString())

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun listsAccessibleFilesForTheRequestedFolderWithBearerAuthorization() = runBlocking {
        server.enqueue(
            MockResponse.Builder().code(200).body(
                """{"files":[{"id":"folder_1","name":"src","mimeType":"application/vnd.google-apps.folder","parents":["root"]}]}"""
            ).build()
        )

        val files = drive.listAppAccessibleFiles("test-access-token", "parent_42")

        assertEquals(1, files.size)
        assertEquals("src", files.single().name)
        assertTrue(files.single().isFolder)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("Bearer test-access-token", request.headers["Authorization"])
        assertTrue(request.target.contains("parent_42"))
        assertTrue(request.target.contains("trashed"))
    }

    @Test
    fun rejectsMalformedFolderIdsBeforeMakingARequest() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { drive.listAppAccessibleFiles("test-access-token", "x' or trashed=true") }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun uploadsWorkspaceArchiveAsDriveResumableChunks() = runBlocking {
        val archive = File.createTempFile("drive-upload-test", ".zip")
        try {
            val size = 256 * 1024 + 5
            archive.writeBytes(ByteArray(size) { it.toByte() })
            server.enqueue(
                MockResponse.Builder().code(200)
                    .setHeader("Location", server.url("/upload-session").toString())
                    .build()
            )
            server.enqueue(MockResponse.Builder().code(308).build())
            server.enqueue(
                MockResponse.Builder().code(200).body(
                    """{"id":"uploaded_1","name":"workspace.zip","mimeType":"application/zip"}"""
                ).build()
            )

            val uploaded = drive.uploadWorkspaceArchive("test-access-token", archive)

            assertEquals("uploaded_1", uploaded.id)
            assertEquals("workspace.zip", uploaded.name)
            val createRequest = server.takeRequest()
            assertEquals("POST", createRequest.method)
            assertEquals(size.toString(), createRequest.headers["X-Upload-Content-Length"])
            val firstChunk = server.takeRequest()
            assertEquals("PUT", firstChunk.method)
            assertEquals("bytes 0-262143/$size", firstChunk.headers["Content-Range"])
            assertEquals(256L * 1024, firstChunk.body?.size)
            val finalChunk = server.takeRequest()
            assertEquals("bytes 262144-${size - 1}/$size", finalChunk.headers["Content-Range"])
            assertEquals(5L, finalChunk.body?.size)
        } finally {
            archive.delete()
        }
    }
}
