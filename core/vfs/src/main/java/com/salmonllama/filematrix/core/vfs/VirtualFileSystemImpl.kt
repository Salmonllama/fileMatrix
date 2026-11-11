package com.salmonllama.filematrix.core.vfs

import android.content.Context
import android.net.Uri
import com.salmonllama.filematrix.core.database.FileIndexDao
import com.salmonllama.filematrix.core.database.FileIndexEntity
import com.salmonllama.filematrix.core.database.PersistedRootDao
import com.salmonllama.filematrix.core.database.PersistedRootEntity
import com.salmonllama.filematrix.core.model.GooglebookRootType
import com.salmonllama.filematrix.core.model.SearchHit
import com.salmonllama.filematrix.core.model.StorageRoot
import com.salmonllama.filematrix.core.model.VfsNode
import com.salmonllama.filematrix.core.model.VfsUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class VirtualFileSystemImpl(
    private val context: Context,
    private val localProvider: LocalFileSystemProvider,
    private val safProvider: SafDocumentProvider,
    private val persistedRootDao: PersistedRootDao,
    private val fileIndexDao: FileIndexDao,
    val pathRegistry: PathTranslationRegistry
) : VirtualFileSystem {

    override fun observeRoots(): Flow<List<StorageRoot>> {
        return persistedRootDao.getAllRoots().map { persisted ->
            val localRoots = localProvider.getDefaultRoots()
            val safRoots = persisted.map { p ->
                StorageRoot(
                    id = p.id,
                    rootType = p.rootType,
                    displayName = p.displayName,
                    baseUri = p.treeUriString,
                    targetPosixPath = p.customMountPosixPath,
                    isMounted = true,
                    isAccessible = true,
                    authority = p.authority
                )
            }
            localRoots + safRoots
        }
    }

    override suspend fun getRoots(): List<StorageRoot> = withContext(Dispatchers.IO) {
        val local = localProvider.getDefaultRoots()
        val persisted = persistedRootDao.getAllRoots()
        // Synchronous retrieval from local or Room
        local
    }

    override suspend fun listChildren(uri: VfsUri): List<VfsNode> = withContext(Dispatchers.IO) {
        val nodes = when {
            uri.isFileScheme -> localProvider.listChildren(uri)
            uri.isContentScheme -> safProvider.listChildren(uri)
            else -> emptyList()
        }

        // Augment nodes with translated POSIX paths
        val augmented = nodes.map { node ->
            val posix = pathRegistry.toPosixPath(node.uri)
            node.copy(targetPosixPath = posix)
        }

        // Index local files asynchronously in Room for instant FTS5 search
        if (uri.isFileScheme && augmented.isNotEmpty()) {
            val entities = augmented.map { n ->
                FileIndexEntity(
                    id = n.id,
                    name = n.name,
                    path = n.uri.path,
                    uriString = n.uri.rawString,
                    rootType = n.uri.rootType,
                    isDirectory = n.isDirectory,
                    sizeBytes = n.sizeBytes,
                    lastModified = n.lastModified,
                    mimeType = n.mimeType,
                    extension = n.extension
                )
            }
            fileIndexDao.insertAll(entities)
        }

        augmented
    }

    override suspend fun getNode(uri: VfsUri): VfsNode? = withContext(Dispatchers.IO) {
        val node = when {
            uri.isFileScheme -> localProvider.getNode(uri)
            uri.isContentScheme -> safProvider.getNode(uri)
            else -> null
        }
        node?.let {
            it.copy(targetPosixPath = pathRegistry.toPosixPath(it.uri))
        }
    }

    override suspend fun createDirectory(parentUri: VfsUri, name: String): VfsNode = withContext(Dispatchers.IO) {
        val created = when {
            parentUri.isFileScheme -> localProvider.createDirectory(parentUri, name)
            parentUri.isContentScheme -> safProvider.createDirectory(parentUri, name)
            else -> throw UnsupportedOperationException("Unsupported scheme: ${parentUri.scheme}")
        }
        created.copy(targetPosixPath = pathRegistry.toPosixPath(created.uri))
    }

    override suspend fun createFile(parentUri: VfsUri, name: String, mimeType: String): VfsNode = withContext(Dispatchers.IO) {
        val created = when {
            parentUri.isFileScheme -> localProvider.createFile(parentUri, name)
            parentUri.isContentScheme -> safProvider.createFile(parentUri, name, mimeType)
            else -> throw UnsupportedOperationException("Unsupported scheme: ${parentUri.scheme}")
        }
        created.copy(targetPosixPath = pathRegistry.toPosixPath(created.uri))
    }

    override suspend fun delete(uri: VfsUri): Boolean = withContext(Dispatchers.IO) {
        when {
            uri.isFileScheme -> localProvider.delete(uri)
            uri.isContentScheme -> safProvider.delete(uri)
            else -> false
        }
    }

    override suspend fun rename(uri: VfsUri, newName: String): VfsNode = withContext(Dispatchers.IO) {
        val renamed = when {
            uri.isFileScheme -> localProvider.rename(uri, newName)
            uri.isContentScheme -> safProvider.rename(uri, newName)
            else -> throw UnsupportedOperationException("Unsupported scheme: ${uri.scheme}")
        }
        renamed.copy(targetPosixPath = pathRegistry.toPosixPath(renamed.uri))
    }

    override suspend fun openInputStream(uri: VfsUri): InputStream? {
        return when {
            uri.isFileScheme -> localProvider.openInputStream(uri)
            uri.isContentScheme -> safProvider.openInputStream(uri)
            else -> null
        }
    }

    override suspend fun openOutputStream(uri: VfsUri): OutputStream? {
        return when {
            uri.isFileScheme -> localProvider.openOutputStream(uri)
            uri.isContentScheme -> safProvider.openOutputStream(uri)
            else -> null
        }
    }

    override suspend fun searchFederated(query: String, rootType: GooglebookRootType?): List<SearchHit> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchHit>()

        // 1. Search Room FTS5 index
        val localEntities = try {
            fileIndexDao.searchFts(query)
        } catch (_: Exception) {
            fileIndexDao.searchLike(query)
        }

        results.addAll(
            localEntities.map { entity ->
                val vfsUri = if (entity.rootType == GooglebookRootType.GOOGLEBOOK_LOCAL) {
                    VfsUri.fromFile(entity.path)
                } else {
                    VfsUri.fromContent(entity.uriString, entity.rootType, null, entity.path)
                }
                val node = VfsNode(
                    id = entity.id,
                    uri = vfsUri,
                    name = entity.name,
                    isDirectory = entity.isDirectory,
                    sizeBytes = entity.sizeBytes,
                    lastModified = entity.lastModified,
                    mimeType = entity.mimeType,
                    extension = entity.extension,
                    targetPosixPath = pathRegistry.toPosixPath(vfsUri)
                )
                SearchHit(node = node, matchedField = "name", snippet = entity.name)
            }
        )

        results
    }

    override suspend fun registerSafRoot(
        treeUriString: String,
        rootType: GooglebookRootType,
        displayName: String?
    ): StorageRoot = withContext(Dispatchers.IO) {
        val parsedUri = Uri.parse(treeUriString)
        val authority = parsedUri.authority ?: "com.android.documents"
        val resolvedName = displayName ?: rootType.title
        val id = "root_${rootType.name.lowercase()}_${System.currentTimeMillis()}"

        val entity = PersistedRootEntity(
            id = id,
            rootType = rootType,
            displayName = resolvedName,
            treeUriString = treeUriString,
            authority = authority
        )
        persistedRootDao.insertOrUpdate(entity)

        StorageRoot(
            id = id,
            rootType = rootType,
            displayName = resolvedName,
            baseUri = treeUriString,
            authority = authority
        )
    }

    override suspend fun unregisterRoot(rootId: String) = withContext(Dispatchers.IO) {
        val existing = persistedRootDao.getRootById(rootId)
        if (existing != null) {
            persistedRootDao.delete(existing)
        }
    }
}
