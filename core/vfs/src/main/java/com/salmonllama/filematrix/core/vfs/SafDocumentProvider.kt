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

    /**
     * Resolves the document ID for both bare SAF tree URIs (`content://.../tree/<id>`)
     * and document-within-tree URIs (`content://.../tree/<treeId>/document/<docId>`).
     */
    internal fun resolveDocumentId(parsedUri: Uri): String {
        return when {
            DocumentsContract.isDocumentUri(context, parsedUri) ->
                DocumentsContract.getDocumentId(parsedUri)
            DocumentsContract.isTreeUri(parsedUri) ->
                DocumentsContract.getTreeDocumentId(parsedUri)
            else ->
                DocumentsContract.getDocumentId(parsedUri)
        }
    }

    /**
     * Converts a bare SAF tree URI into a queryable document URI using its tree document ID.
     * Document URIs are returned as-is.
     */
    internal fun resolveDocumentUri(parsedUri: Uri): Uri {
        return when {
            DocumentsContract.isDocumentUri(context, parsedUri) -> parsedUri
            DocumentsContract.isTreeUri(parsedUri) -> {
                val treeDocId = DocumentsContract.getTreeDocumentId(parsedUri)
                DocumentsContract.buildDocumentUriUsingTree(parsedUri, treeDocId)
            }
            else -> parsedUri
        }
    }

    suspend fun listChildren(uri: VfsUri): List<VfsNode> = withContext(Dispatchers.IO) {
        val parsedUri = Uri.parse(uri.rawString)
        val docId = try {
            resolveDocumentId(parsedUri)
        } catch (_: Exception) {
            return@withContext emptyList()
        }

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parsedUri, docId)

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
                    val childDocId = if (idIdx >= 0) cursor.getString(idIdx) else continue
                    val name = (if (nameIdx >= 0) cursor.getString(nameIdx) else null) ?: "Unknown"
                    val mimeType = if (mimeIdx >= 0) cursor.getString(mimeIdx) else null
                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                    val lastModified = if (modIdx >= 0 && !cursor.isNull(modIdx)) cursor.getLong(modIdx) else 0L

                    val isDir = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                    val childDocUri = DocumentsContract.buildDocumentUriUsingTree(parsedUri, childDocId)

                    nodes.add(
                        VfsNode(
                            id = childDocUri.toString(),
                            uri = VfsUri.fromContent(
                                contentUri = childDocUri.toString(),
                                rootType = uri.rootType,
                                authority = childDocUri.authority,
                                documentPath = childDocId
                            ),
                            name = name,
                            isDirectory = isDir,
                            sizeBytes = size,
                            lastModified = lastModified,
                            mimeType = mimeType,
                            extension = if (isDir) "" else name.substringAfterLast('.', "")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Tree access may have been revoked or provider unavailable
        }

        nodes.sortedWith(
            compareBy<VfsNode> { !it.isDirectory }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
    }

    suspend fun getNode(uri: VfsUri): VfsNode? = withContext(Dispatchers.IO) {
        val parsedUri = Uri.parse(uri.rawString)
        val docUri = try {
            resolveDocumentUri(parsedUri)
        } catch (_: Exception) {
            return@withContext null
        }

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        try {
            contentResolver.query(docUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    val name = (if (nameIdx >= 0) cursor.getString(nameIdx) else null) ?: "Document"
                    val mimeType = if (mimeIdx >= 0) cursor.getString(mimeIdx) else null
                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                    val lastMod = if (modIdx >= 0 && !cursor.isNull(modIdx)) cursor.getLong(modIdx) else 0L
                    val isDir = mimeType == DocumentsContract.Document.MIME_TYPE_DIR

                    return@withContext VfsNode(
                        id = docUri.toString(),
                        uri = uri,
                        name = name,
                        isDirectory = isDir,
                        sizeBytes = size,
                        lastModified = lastMod,
                        mimeType = mimeType,
                        extension = if (isDir) "" else name.substringAfterLast('.', "")
                    )
                }
            }
        } catch (_: Exception) {
        }
        null
    }

    suspend fun createDirectory(parentUri: VfsUri, name: String): VfsNode = withContext(Dispatchers.IO) {
        val parentDocUri = resolveDocumentUri(Uri.parse(parentUri.rawString))
        val created = DocumentsContract.createDocument(
            contentResolver,
            parentDocUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            name
        ) ?: throw IllegalStateException("Failed to create SAF directory: $name")

        VfsNode(
            id = created.toString(),
            uri = VfsUri.fromContent(
                contentUri = created.toString(),
                rootType = parentUri.rootType,
                authority = created.authority,
                documentPath = resolveDocumentId(created)
            ),
            name = name,
            isDirectory = true,
            sizeBytes = 0L,
            lastModified = System.currentTimeMillis(),
            mimeType = DocumentsContract.Document.MIME_TYPE_DIR
        )
    }

    suspend fun createFile(parentUri: VfsUri, name: String, mimeType: String): VfsNode = withContext(Dispatchers.IO) {
        val parentDocUri = resolveDocumentUri(Uri.parse(parentUri.rawString))
        val resolvedMime = if (mimeType.isNotBlank() && mimeType != "application/octet-stream") {
            mimeType
        } else {
            val ext = name.substringAfterLast('.', "")
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }

        val created = DocumentsContract.createDocument(
            contentResolver,
            parentDocUri,
            resolvedMime,
            name
        ) ?: throw IllegalStateException("Failed to create SAF file: $name")

        VfsNode(
            id = created.toString(),
            uri = VfsUri.fromContent(
                contentUri = created.toString(),
                rootType = parentUri.rootType,
                authority = created.authority,
                documentPath = resolveDocumentId(created)
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
            val docUri = resolveDocumentUri(Uri.parse(uri.rawString))
            DocumentsContract.deleteDocument(contentResolver, docUri)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun rename(uri: VfsUri, newName: String): VfsNode = withContext(Dispatchers.IO) {
        val existingNode = getNode(uri)
        val docUri = resolveDocumentUri(Uri.parse(uri.rawString))
        val renamed = DocumentsContract.renameDocument(contentResolver, docUri, newName)
            ?: throw IllegalStateException("Failed to rename SAF document to $newName")

        val isDir = existingNode?.isDirectory == true
        VfsNode(
            id = renamed.toString(),
            uri = VfsUri.fromContent(
                contentUri = renamed.toString(),
                rootType = uri.rootType,
                authority = renamed.authority,
                documentPath = resolveDocumentId(renamed)
            ),
            name = newName,
            isDirectory = isDir,
            sizeBytes = existingNode?.sizeBytes ?: 0L,
            lastModified = System.currentTimeMillis(),
            mimeType = existingNode?.mimeType,
            extension = if (isDir) "" else newName.substringAfterLast('.', "")
        )
    }

    fun openInputStream(uri: VfsUri): InputStream? {
        val docUri = resolveDocumentUri(Uri.parse(uri.rawString))
        return contentResolver.openInputStream(docUri)
    }

    fun openOutputStream(uri: VfsUri): OutputStream? {
        val docUri = resolveDocumentUri(Uri.parse(uri.rawString))
        return contentResolver.openOutputStream(docUri)
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
                    val docId = if (idIdx >= 0) cursor.getString(idIdx) else continue
                    val name = (if (nameIdx >= 0) cursor.getString(nameIdx) else null) ?: "Document"
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) else null
                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                    val mod = if (modIdx >= 0 && !cursor.isNull(modIdx)) cursor.getLong(modIdx) else 0L
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, docId)
                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR

                    val node = VfsNode(
                        id = docUri.toString(),
                        uri = VfsUri.fromContent(docUri.toString(), rootType, docUri.authority, docId),
                        name = name,
                        isDirectory = isDir,
                        sizeBytes = size,
                        lastModified = mod,
                        mimeType = mime,
                        extension = if (isDir) "" else name.substringAfterLast('.', "")
                    )
                    hits.add(SearchHit(node = node, matchedField = "name", snippet = name))
                }
            }
        } catch (_: Exception) {
        }
        hits
    }
}
