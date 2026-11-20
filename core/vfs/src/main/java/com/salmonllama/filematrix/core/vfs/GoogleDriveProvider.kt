package com.salmonllama.filematrix.core.vfs

import com.salmonllama.filematrix.core.model.GoogleDriveAccountInfo
import com.salmonllama.filematrix.core.model.SearchHit
import com.salmonllama.filematrix.core.model.VfsNode
import com.salmonllama.filematrix.core.model.VfsUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

data class DriveHttpResponse(
    val statusCode: Int,
    val body: String
)

interface DriveHttpClient {
    fun request(
        method: String,
        url: String,
        bearerToken: String,
        contentType: String? = null,
        bodyBytes: ByteArray? = null
    ): DriveHttpResponse

    fun openDownloadStream(url: String, bearerToken: String): InputStream?
}

class HttpURLConnectionDriveClient : DriveHttpClient {
    override fun request(
        method: String,
        url: String,
        bearerToken: String,
        contentType: String?,
        bodyBytes: ByteArray?
    ): DriveHttpResponse {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = if (method == "PATCH") "POST" else method
            if (method == "PATCH") {
                setRequestProperty("X-HTTP-Method-Override", "PATCH")
            }
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $bearerToken")
            setRequestProperty("Accept", "application/json")
            if (contentType != null) {
                setRequestProperty("Content-Type", contentType)
            }
            if (bodyBytes != null) {
                doOutput = true
            }
        }

        return try {
            if (bodyBytes != null) {
                conn.outputStream.use { it.write(bodyBytes) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            DriveHttpResponse(statusCode = code, body = responseText)
        } finally {
            conn.disconnect()
        }
    }

    override fun openDownloadStream(url: String, bearerToken: String): InputStream? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $bearerToken")
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            return null
        }
        return object : java.io.FilterInputStream(conn.inputStream) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    conn.disconnect()
                }
            }
        }
    }
}

class GoogleDriveProvider(
    private val tokenProvider: GoogleDriveTokenProvider,
    private val httpClient: DriveHttpClient = HttpURLConnectionDriveClient()
) {

    fun isConnected(): Boolean = tokenProvider.getTokenState() != null

    fun getCachedAccountInfo(): GoogleDriveAccountInfo? = tokenProvider.getAccountInfo()

    suspend fun fetchAccountInfo(): Result<GoogleDriveAccountInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$API_BASE/about?fields=user(displayName,emailAddress),storageQuota(usage,limit)"
            val response = executeAuthorized("GET", url)
            if (response.statusCode !in 200..299) {
                throw IllegalStateException("Google Drive API error (HTTP ${response.statusCode}): ${response.body}")
            }
            val json = JSONObject(response.body)
            val user = json.optJSONObject("user")
            val quota = json.optJSONObject("storageQuota")

            val email = user?.optString("emailAddress", "").orEmpty().ifBlank { "Google Drive Account" }
            val displayName = user?.optString("displayName", "").orEmpty().ifBlank { email }
            val used = quota?.optString("usage", "")?.toLongOrNull()
            val total = quota?.optString("limit", "")?.toLongOrNull()

            val info = GoogleDriveAccountInfo(
                email = email,
                displayName = displayName,
                quotaUsedBytes = used,
                quotaTotalBytes = total
            )
            tokenProvider.saveAccountInfo(info)
            info
        }
    }

    suspend fun listChildren(uri: VfsUri): List<VfsNode> = withContext(Dispatchers.IO) {
        if (!isConnected()) return@withContext emptyList()
        val folderId = uri.authority?.ifBlank { ROOT_FOLDER_ID } ?: ROOT_FOLDER_ID

        if (folderId == SHARED_DRIVES_VIRTUAL_ID) {
            return@withContext listSharedDrives()
        }

        val query = "'${escapeDriveQuery(folderId)}' in parents and trashed = false"
        val url = "$API_BASE/files" +
            "?q=${urlEncode(query)}" +
            "&fields=${urlEncode("files(id,name,mimeType,size,modifiedTime,parents)")}" +
            "&pageSize=200" +
            "&supportsAllDrives=true" +
            "&includeItemsFromAllDrives=true" +
            "&orderBy=folder,name_natural"

        val response = runCatching { executeAuthorized("GET", url) }.getOrNull()
            ?: return@withContext emptyList()
        if (response.statusCode !in 200..299) return@withContext emptyList()

        val nodes = mutableListOf<VfsNode>()
        val parentPath = uri.path.trimEnd('/').ifEmpty { "/My Drive" }

        // Surface Shared Drives entry at root when user has access to any Shared Drives
        if (folderId == ROOT_FOLDER_ID) {
            val sharedDrives = runCatching { listSharedDrives() }.getOrDefault(emptyList())
            if (sharedDrives.isNotEmpty()) {
                nodes.add(
                    VfsNode(
                        id = "gdrive://$SHARED_DRIVES_VIRTUAL_ID",
                        uri = VfsUri.fromGoogleDrive(SHARED_DRIVES_VIRTUAL_ID, "/Shared Drives"),
                        name = "Shared Drives",
                        isDirectory = true,
                        sizeBytes = 0L,
                        lastModified = 0L,
                        mimeType = MIME_FOLDER,
                        extension = "",
                        childCount = sharedDrives.size
                    )
                )
            }
        }

        val json = JSONObject(response.body)
        val files = json.optJSONArray("files") ?: JSONArray()
        for (i in 0 until files.length()) {
            val obj = files.getJSONObject(i)
            nodes.add(parseFileJson(obj, parentPath))
        }

        nodes.sortedWith(
            compareBy<VfsNode> { !it.isDirectory }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
    }

    private suspend fun listSharedDrives(): List<VfsNode> {
        val url = "$API_BASE/drives?pageSize=100&fields=${urlEncode("drives(id,name)")}"
        val response = runCatching { executeAuthorized("GET", url) }.getOrNull() ?: return emptyList()
        if (response.statusCode !in 200..299) return emptyList()

        val json = JSONObject(response.body)
        val drives = json.optJSONArray("drives") ?: JSONArray()
        val result = mutableListOf<VfsNode>()
        for (i in 0 until drives.length()) {
            val obj = drives.getJSONObject(i)
            val id = obj.getString("id")
            val name = obj.optString("name", "Shared Drive")
            result.add(
                VfsNode(
                    id = "gdrive://$id",
                    uri = VfsUri.fromGoogleDrive(id, "/Shared Drives/$name"),
                    name = name,
                    isDirectory = true,
                    sizeBytes = 0L,
                    lastModified = 0L,
                    mimeType = MIME_FOLDER,
                    extension = ""
                )
            )
        }
        return result.sortedBy { it.name.lowercase() }
    }

    suspend fun getNode(uri: VfsUri): VfsNode? = withContext(Dispatchers.IO) {
        val fileId = uri.authority?.ifBlank { ROOT_FOLDER_ID } ?: return@withContext null
        if (fileId == SHARED_DRIVES_VIRTUAL_ID) {
            return@withContext VfsNode(
                id = "gdrive://$SHARED_DRIVES_VIRTUAL_ID",
                uri = VfsUri.fromGoogleDrive(SHARED_DRIVES_VIRTUAL_ID, "/Shared Drives"),
                name = "Shared Drives",
                isDirectory = true,
                sizeBytes = 0L,
                lastModified = 0L,
                mimeType = MIME_FOLDER,
                extension = ""
            )
        }

        val url = "$API_BASE/files/${urlEncode(fileId)}" +
            "?fields=${urlEncode("id,name,mimeType,size,modifiedTime,parents")}" +
            "&supportsAllDrives=true"
        val response = runCatching { executeAuthorized("GET", url) }.getOrNull() ?: return@withContext null
        if (response.statusCode !in 200..299) return@withContext null

        val parentPath = uri.path.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/My Drive" }
        parseFileJson(JSONObject(response.body), parentPath)
    }

    suspend fun createDirectory(parentUri: VfsUri, name: String): VfsNode = withContext(Dispatchers.IO) {
        val parentId = parentUri.authority?.ifBlank { ROOT_FOLDER_ID } ?: ROOT_FOLDER_ID
        val payload = JSONObject().apply {
            put("name", name)
            put("mimeType", MIME_FOLDER)
            put("parents", JSONArray().put(parentId))
        }
        val url = "$API_BASE/files?supportsAllDrives=true&fields=${urlEncode("id,name,mimeType,size,modifiedTime,parents")}"
        val response = executeAuthorized(
            method = "POST",
            url = url,
            contentType = "application/json; charset=UTF-8",
            bodyBytes = payload.toString().toByteArray(Charsets.UTF_8)
        )
        if (response.statusCode !in 200..299) {
            throw IllegalStateException("Failed to create Google Drive folder: HTTP ${response.statusCode} ${response.body}")
        }
        parseFileJson(JSONObject(response.body), parentUri.path.trimEnd('/').ifEmpty { "/My Drive" })
    }

    suspend fun createFile(parentUri: VfsUri, name: String, mimeType: String): VfsNode = withContext(Dispatchers.IO) {
        val parentId = parentUri.authority?.ifBlank { ROOT_FOLDER_ID } ?: ROOT_FOLDER_ID
        val payload = JSONObject().apply {
            put("name", name)
            if (mimeType.isNotBlank()) put("mimeType", mimeType)
            put("parents", JSONArray().put(parentId))
        }
        val url = "$API_BASE/files?supportsAllDrives=true&fields=${urlEncode("id,name,mimeType,size,modifiedTime,parents")}"
        val response = executeAuthorized(
            method = "POST",
            url = url,
            contentType = "application/json; charset=UTF-8",
            bodyBytes = payload.toString().toByteArray(Charsets.UTF_8)
        )
        if (response.statusCode !in 200..299) {
            throw IllegalStateException("Failed to create Google Drive file: HTTP ${response.statusCode} ${response.body}")
        }
        parseFileJson(JSONObject(response.body), parentUri.path.trimEnd('/').ifEmpty { "/My Drive" })
    }

    suspend fun delete(uri: VfsUri): Boolean = withContext(Dispatchers.IO) {
        val fileId = uri.authority?.takeIf { it.isNotBlank() && it != ROOT_FOLDER_ID && it != SHARED_DRIVES_VIRTUAL_ID }
            ?: return@withContext false
        val payload = JSONObject().put("trashed", true)
        val url = "$API_BASE/files/${urlEncode(fileId)}?supportsAllDrives=true"
        val response = runCatching {
            executeAuthorized(
                method = "PATCH",
                url = url,
                contentType = "application/json; charset=UTF-8",
                bodyBytes = payload.toString().toByteArray(Charsets.UTF_8)
            )
        }.getOrNull() ?: return@withContext false
        response.statusCode in 200..299
    }

    suspend fun rename(uri: VfsUri, newName: String): VfsNode = withContext(Dispatchers.IO) {
        val fileId = uri.authority?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Invalid Google Drive URI: ${uri.rawString}")
        val payload = JSONObject().put("name", newName)
        val url = "$API_BASE/files/${urlEncode(fileId)}" +
            "?supportsAllDrives=true" +
            "&fields=${urlEncode("id,name,mimeType,size,modifiedTime,parents")}"
        val response = executeAuthorized(
            method = "PATCH",
            url = url,
            contentType = "application/json; charset=UTF-8",
            bodyBytes = payload.toString().toByteArray(Charsets.UTF_8)
        )
        if (response.statusCode !in 200..299) {
            throw IllegalStateException("Failed to rename Google Drive item: HTTP ${response.statusCode} ${response.body}")
        }
        val parentPath = uri.path.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/My Drive" }
        parseFileJson(JSONObject(response.body), parentPath)
    }

    suspend fun openInputStream(uri: VfsUri): InputStream? = withContext(Dispatchers.IO) {
        val fileId = uri.authority?.takeIf { it.isNotBlank() } ?: return@withContext null
        val token = resolveValidAccessToken() ?: return@withContext null
        val node = getNode(uri)
        val exportMime = node?.mimeType?.let { WORKSPACE_EXPORT_MIME[it] }
        val url = if (exportMime != null) {
            "$API_BASE/files/${urlEncode(fileId)}/export?mimeType=${urlEncode(exportMime)}"
        } else {
            "$API_BASE/files/${urlEncode(fileId)}?alt=media&supportsAllDrives=true"
        }
        httpClient.openDownloadStream(url, token)
    }

    suspend fun openOutputStream(uri: VfsUri): OutputStream? = withContext(Dispatchers.IO) {
        val fileId = uri.authority?.takeIf { it.isNotBlank() } ?: return@withContext null
        val token = resolveValidAccessToken() ?: return@withContext null
        val buffer = ByteArrayOutputStream()
        object : OutputStream() {
            private var closed = false
            override fun write(b: Int) = buffer.write(b)
            override fun write(b: ByteArray, off: Int, len: Int) = buffer.write(b, off, len)
            override fun close() {
                if (closed) return
                closed = true
                val uploadUrl = "$UPLOAD_BASE/files/${urlEncode(fileId)}?uploadType=media&supportsAllDrives=true"
                val response = httpClient.request(
                    method = "PATCH",
                    url = uploadUrl,
                    bearerToken = token,
                    contentType = "application/octet-stream",
                    bodyBytes = buffer.toByteArray()
                )
                if (response.statusCode !in 200..299) {
                    throw java.io.IOException("Google Drive upload failed (HTTP ${response.statusCode}): ${response.body}")
                }
            }
        }
    }

    suspend fun search(query: String): List<SearchHit> = withContext(Dispatchers.IO) {
        if (!isConnected() || query.isBlank()) return@withContext emptyList()
        val driveQuery = "name contains '${escapeDriveQuery(query)}' and trashed = false"
        val url = "$API_BASE/files" +
            "?q=${urlEncode(driveQuery)}" +
            "&fields=${urlEncode("files(id,name,mimeType,size,modifiedTime,parents)")}" +
            "&pageSize=50" +
            "&supportsAllDrives=true" +
            "&includeItemsFromAllDrives=true"

        val response = runCatching { executeAuthorized("GET", url) }.getOrNull()
            ?: return@withContext emptyList()
        if (response.statusCode !in 200..299) return@withContext emptyList()

        val json = JSONObject(response.body)
        val files = json.optJSONArray("files") ?: JSONArray()
        val hits = mutableListOf<SearchHit>()
        for (i in 0 until files.length()) {
            val node = parseFileJson(files.getJSONObject(i), "/My Drive")
            hits.add(SearchHit(node = node, matchedField = "name", snippet = node.name))
        }
        hits
    }

    private suspend fun executeAuthorized(
        method: String,
        url: String,
        contentType: String? = null,
        bodyBytes: ByteArray? = null
    ): DriveHttpResponse {
        val token = resolveValidAccessToken()
            ?: throw IllegalStateException("Google Drive is not authenticated")
        val firstResponse = httpClient.request(method, url, token, contentType, bodyBytes)
        if (firstResponse.statusCode == 401) {
            val refreshed = tokenProvider.refreshAccessToken().getOrNull()
            if (!refreshed.isNullOrBlank()) {
                return httpClient.request(method, url, refreshed, contentType, bodyBytes)
            }
        }
        return firstResponse
    }

    private suspend fun resolveValidAccessToken(): String? {
        val state = tokenProvider.getTokenState() ?: return null
        if (state.isExpired() && !state.refreshToken.isNullOrBlank()) {
            val refreshed = tokenProvider.refreshAccessToken().getOrNull()
            if (!refreshed.isNullOrBlank()) return refreshed
        }
        return state.accessToken
    }

    private fun parseFileJson(obj: JSONObject, parentPath: String): VfsNode {
        val id = obj.getString("id")
        val name = obj.optString("name", "Untitled")
        val mimeType = obj.optString("mimeType", "").takeIf { it.isNotBlank() }
        val isDir = mimeType == MIME_FOLDER
        val size = obj.optString("size", "").toLongOrNull() ?: 0L
        val modifiedTime = obj.optString("modifiedTime", "").let { raw ->
            if (raw.isBlank()) 0L else runCatching { Instant.parse(raw).toEpochMilli() }.getOrDefault(0L)
        }
        val fullDisplayPath = "${parentPath.trimEnd('/')}/$name"
        val ext = if (isDir) "" else name.substringAfterLast('.', "")
        val uri = VfsUri.fromGoogleDrive(fileId = id, displayPath = fullDisplayPath)

        return VfsNode(
            id = uri.rawString,
            uri = uri,
            name = name,
            isDirectory = isDir,
            sizeBytes = size,
            lastModified = modifiedTime,
            mimeType = mimeType,
            extension = ext,
            isHidden = name.startsWith(".")
        )
    }

    companion object {
        const val ROOT_FOLDER_ID = "root"
        const val SHARED_DRIVES_VIRTUAL_ID = "shared_drives"
        const val MIME_FOLDER = "application/vnd.google-apps.folder"

        private const val API_BASE = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"

        private val WORKSPACE_EXPORT_MIME = mapOf(
            "application/vnd.google-apps.document" to "application/pdf",
            "application/vnd.google-apps.spreadsheet" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.google-apps.presentation" to "application/pdf"
        )

        internal fun escapeDriveQuery(value: String): String {
            return value.replace("\\", "\\\\").replace("'", "\\'")
        }

        private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}
