package com.salmonllama.filematrix.core.vfs

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import com.salmonllama.filematrix.core.model.GooglebookRootType
import com.salmonllama.filematrix.core.model.SearchHit
import com.salmonllama.filematrix.core.model.VfsNode
import com.salmonllama.filematrix.core.model.VfsUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class SafDocumentProvider(private val context: Context) {

    private val contentResolver get() = context.contentResolver

    suspend fun listChildren(uri: VfsUri): List<VfsNode> = withContext(Dispatchers.IO) {
        val parsedUri = Uri.parse(uri.rawString)
        val treeUri = DocumentsContract.buildDocumentUriUsingTree(
            parsedUri,
            DocumentsContract.getDocumentId(parsedUri)
        )
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getDocumentId(treeUri)
        )

        val nodes = mutableListOf<VfsNode>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS
        )

        try {
            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIdx)
                    val name = cursor.getString(nameIdx) ?: "Unknown"
                    val mimeType = cursor.getString(mimeIdx)
                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                    val lastModified = if (modIdx >= 0 && !cursor.isNull(modIdx)) cursor.getLong(modIdx) else 0L

                    val isDir = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                    val childDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                    nodes.add(
                        VfsNode(
                            id = childDocUri.toString(),
                            uri = VfsUri.fromContent(
                                contentUri = childDocUri.toString(),
                                rootType = uri.rootType,
                                authority = childDocUri.authority,
                                documentPath = docId
                            ),
                            name = name,
                            isDirectory = isDir,
                            sizeBytes = size,
                            lastModified = lastModified,
                            mimeType = mimeType,
                            extension = name.substringAfterLast('.', "")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Log or fallback if tree access is revoked/unavailable
        }

        nodes.sortedWith(
            compareBy<VfsNode> { !it.isDirectory }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
    }

    suspend fun getNode(uri: VfsUri): VfsNode? = withContext(Dispatchers.IO) {
        val parsedUri = Uri.parse(uri.rawString)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        try {
            contentResolver.query(parsedUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    val docId = cursor.getString(idIdx)
                    val name = cursor.getString(nameIdx) ?: "Document"
                    val mimeType = cursor.getString(mimeIdx)
                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                    val lastMod = if (modIdx >= 0 && !cursor.isNull(modIdx)) cursor.getLong(modIdx) else 0L

                    return@withContext VfsNode(
                        id = uri.rawString,
                        uri = uri,
                        name = name,
                        isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR,
                        sizeBytes = size,
                        lastModified = lastMod,
                        mimeType = mimeType,
                        extension = name.substringAfterLast('.', "")
                    )
                }
            }
        } catch (_: Exception) {
        }
        null
    }

    suspend fun createDirectory(parentUri: VfsUri, name: String): VfsNode = withContext(Dispatchers.IO) {
        val parent = Uri.parse(parentUri.rawString)
        val created = DocumentsContract.createDocument(
            contentResolver,
            parent,
            DocumentsContract.Document.MIME_TYPE_DIR,
            name
        ) ?: throw IllegalStateException("Failed to create SAF directory: $name")

        VfsNode(
            id = created.toString(),
            uri = VfsUri.fromContent(
                contentUri = created.toString(),
                rootType = parentUri.rootType,
                authority = created.authority,
                documentPath = DocumentsContract.getDocumentId(created)
            ),
            name = name,
            isDirectory = true,
            sizeBytes = 0L,
            lastModified = System.currentTimeMillis(),
            mimeType = DocumentsContract.Document.MIME_TYPE_DIR
        )
    }

    suspend fun createFile(parentUri: VfsUri, name: String, mimeType: String): VfsNode = withContext(Dispatchers.IO) {
        val parent = Uri.parse(parentUri.rawString)
        val resolvedMime = if (mimeType.isNotBlank() && mimeType != "application/octet-stream") {
            mimeType
        } else {
            val ext = name.substringAfterLast('.', "")
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }

        val created = DocumentsContract.createDocument(
            contentResolver,
            parent,
            resolvedMime,
            name
        ) ?: throw IllegalStateException("Failed to create SAF file: $name")

        VfsNode(
            id = created.toString(),
            uri = VfsUri.fromContent(
                contentUri = created.toString(),
                rootType = parentUri.rootType,
                authority = created.authority,
                documentPath = DocumentsContract.getDocumentId(created)
            ),
            name = name,
            isDirectory = false,
            sizeBytes = 0L,
            lastModified = System.currentTimeMillis(),
            mimeType = resolvedMime,
            extension = name.substringAfterLast('.', "")
        )
    }

    suspend fun delete(uri: VfsUri): Boolean = withContext(Dispatchers.IO) {
        try {
            DocumentsContract.deleteDocument(contentResolver, Uri.parse(uri.rawString))
        } catch (_: Exception) {
            false
        }
    }

    suspend fun rename(uri: VfsUri, newName: String): VfsNode = withContext(Dispatchers.IO) {
        val parsedUri = Uri.parse(uri.rawString)
        val renamed = DocumentsContract.renameDocument(contentResolver, parsedUri, newName)
            ?: throw IllegalStateException("Failed to rename SAF document to $newName")

        VfsNode(
            id = renamed.toString(),
            uri = VfsUri.fromContent(
                contentUri = renamed.toString(),
                rootType = uri.rootType,
                authority = renamed.authority,
                documentPath = DocumentsContract.getDocumentId(renamed)
            ),
            name = newName,
            isDirectory = false,
            sizeBytes = 0L,
            lastModified = System.currentTimeMillis(),
            mimeType = null,
            extension = newName.substringAfterLast('.', "")
        )
    }

    fun openInputStream(uri: VfsUri): InputStream? {
        return contentResolver.openInputStream(Uri.parse(uri.rawString))
    }

    fun openOutputStream(uri: VfsUri): OutputStream? {
        return contentResolver.openOutputStream(Uri.parse(uri.rawString))
    }

    suspend fun searchSafRoot(rootUri: Uri, rootType: GooglebookRootType, query: String): List<SearchHit> = withContext(Dispatchers.IO) {
        val hits = mutableListOf<SearchHit>()
        try {
            val authority = rootUri.authority ?: return@withContext emptyList()
            val rootId = try { DocumentsContract.getRootId(rootUri) } catch (_: Exception) { "" }
            val searchUri = DocumentsContract.buildSearchDocumentsUri(authority, rootId, query)

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            contentResolver.query(searchUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIdx)
                    val name = cursor.getString(nameIdx) ?: "Document"
                    val mime = cursor.getString(mimeIdx)
                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                    val mod = if (modIdx >= 0 && !cursor.isNull(modIdx)) cursor.getLong(modIdx) else 0L
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, docId)

                    val node = VfsNode(
                        id = docUri.toString(),
                        uri = VfsUri.fromContent(docUri.toString(), rootType, docUri.authority, docId),
                        name = name,
                        isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                        sizeBytes = size,
                        lastModified = mod,
                        mimeType = mime,
                        extension = name.substringAfterLast('.', "")
                    )
                    hits.add(SearchHit(node = node, matchedField = "name", snippet = name))
                }
            }
        } catch (_: Exception) {
        }
        hits
    }
}
