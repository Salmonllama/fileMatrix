package com.salmonllama.filematrix.core.vfs

import com.salmonllama.filematrix.core.model.GoogleDriveAccountInfo
import com.salmonllama.filematrix.core.model.VfsUri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class GoogleDriveProviderTest {

    private class FakeTokenProvider(
        private var state: GoogleDriveTokenState? = GoogleDriveTokenState(
            accessToken = "initial_token",
            refreshToken = "refresh_123",
            clientId = "client_id",
            expiresAtEpochMs = System.currentTimeMillis() + 3_600_000L
        ),
        private val refreshedToken: String = "refreshed_token"
    ) : GoogleDriveTokenProvider {
        var refreshCount = 0
        private var accountInfo: GoogleDriveAccountInfo? = null

        override fun getTokenState(): GoogleDriveTokenState? = state
        override fun saveTokenState(state: GoogleDriveTokenState) {
            this.state = state
        }

        override fun saveAccountInfo(info: GoogleDriveAccountInfo) {
            accountInfo = info
        }

        override fun getAccountInfo(): GoogleDriveAccountInfo? = accountInfo
        override fun clear() {
            state = null
            accountInfo = null
        }

        override suspend fun refreshAccessToken(): Result<String> {
            refreshCount++
            state = state?.copy(accessToken = refreshedToken)
            return Result.success(refreshedToken)
        }
    }

    private class FakeDriveHttpClient(
        private val handler: (method: String, url: String, token: String, body: String?) -> DriveHttpResponse
    ) : DriveHttpClient {
        val requests = mutableListOf<Triple<String, String, String>>()

        override fun request(
            method: String,
            url: String,
            bearerToken: String,
            contentType: String?,
            bodyBytes: ByteArray?
        ): DriveHttpResponse {
            requests.add(Triple(method, url, bearerToken))
            val bodyStr = bodyBytes?.toString(Charsets.UTF_8)
            return handler(method, url, bearerToken, bodyStr)
        }

        override fun openDownloadStream(url: String, bearerToken: String): InputStream? {
            return ByteArrayInputStream("hello drive".toByteArray())
        }
    }

    @Test
    fun testFetchAccountInfo() = runBlocking {
        val tokenProvider = FakeTokenProvider()
        val client = FakeDriveHttpClient { _, _, _, _ ->
            DriveHttpResponse(
                200,
                """
                {
                  "user": { "displayName": "Ada Lovelace", "emailAddress": "ada@example.com" },
                  "storageQuota": { "usage": "1048576", "limit": "16106127360" }
                }
                """.trimIndent()
            )
        }
        val provider = GoogleDriveProvider(tokenProvider, client)
        val info = provider.fetchAccountInfo().getOrThrow()

        assertEquals("ada@example.com", info.email)
        assertEquals("Ada Lovelace", info.displayName)
        assertEquals(1048576L, info.quotaUsedBytes)
        assertEquals(16106127360L, info.quotaTotalBytes)
    }

    @Test
    fun testListRootIncludesSharedDrivesAndFiles() = runBlocking {
        val tokenProvider = FakeTokenProvider()
        val client = FakeDriveHttpClient { _, url, _, _ ->
            when {
                url.contains("/drive/v3/drives") -> DriveHttpResponse(
                    200,
                    """{"drives":[{"id":"sd_1","name":"Engineering"}]}"""
                )
                url.contains("/drive/v3/files") -> DriveHttpResponse(
                    200,
                    """
                    {
                      "files": [
                        {"id":"f_folder","name":"Projects","mimeType":"application/vnd.google-apps.folder","modifiedTime":"2026-01-15T10:00:00Z"},
                        {"id":"f_doc","name":"spec.pdf","mimeType":"application/pdf","size":"4096","modifiedTime":"2026-01-16T12:30:00Z"}
                      ]
                    }
                    """.trimIndent()
                )
                else -> DriveHttpResponse(404, "{}")
            }
        }
        val provider = GoogleDriveProvider(tokenProvider, client)
        val children = provider.listChildren(VfsUri.fromGoogleDrive("root", "/My Drive"))

        assertEquals(3, children.size)
        assertEquals("Projects", children[0].name)
        assertTrue(children[0].isDirectory)
        assertEquals("Shared Drives", children[1].name)
        assertTrue(children[1].isDirectory)
        assertEquals("spec.pdf", children[2].name)
        assertFalse(children[2].isDirectory)
        assertEquals(4096L, children[2].sizeBytes)
        assertEquals("pdf", children[2].extension)
    }

    @Test
    fun testAutomaticTokenRefreshOn401() = runBlocking {
        val tokenProvider = FakeTokenProvider()
        val client = FakeDriveHttpClient { _, _, token, _ ->
            if (token == "initial_token") {
                DriveHttpResponse(401, """{"error":"expired"}""")
            } else {
                DriveHttpResponse(200, """{"files":[{"id":"f1","name":"notes.txt","mimeType":"text/plain","size":"12"}]}""")
            }
        }
        val provider = GoogleDriveProvider(tokenProvider, client)
        val hits = provider.search("notes")

        assertEquals(1, tokenProvider.refreshCount)
        assertEquals(1, hits.size)
        assertEquals("notes.txt", hits[0].node.name)
    }

    @Test
    fun testCreateDirectoryRenameAndTrashDelete() = runBlocking {
        val tokenProvider = FakeTokenProvider()
        val client = FakeDriveHttpClient { method, url, _, body ->
            when {
                method == "POST" && url.contains("/files") -> DriveHttpResponse(
                    200,
                    """{"id":"new_dir_1","name":"Backups","mimeType":"application/vnd.google-apps.folder"}"""
                )
                method == "PATCH" && body?.contains("\"name\"") == true -> DriveHttpResponse(
                    200,
                    """{"id":"new_dir_1","name":"Archives","mimeType":"application/vnd.google-apps.folder"}"""
                )
                method == "PATCH" && body?.contains("\"trashed\":true") == true -> DriveHttpResponse(
                    200,
                    """{"id":"new_dir_1","trashed":true}"""
                )
                else -> DriveHttpResponse(400, "{}")
            }
        }
        val provider = GoogleDriveProvider(tokenProvider, client)
        val created = provider.createDirectory(VfsUri.fromGoogleDrive("root", "/My Drive"), "Backups")
        assertEquals("Backups", created.name)
        assertTrue(created.isDirectory)

        val renamed = provider.rename(created.uri, "Archives")
        assertEquals("Archives", renamed.name)

        val deleted = provider.delete(renamed.uri)
        assertTrue(deleted)
    }

    @Test
    fun testPkceChallengeGeneration() {
        val verifier = GoogleDriveAuthStore.generateCodeVerifier()
        val challenge = GoogleDriveAuthStore.generateCodeChallenge(verifier)
        assertTrue(verifier.length >= 43)
        assertNotEquals(verifier, challenge)
        assertFalse(challenge.contains('='))
        assertFalse(challenge.contains('+'))
        assertFalse(challenge.contains('/'))
    }
}
