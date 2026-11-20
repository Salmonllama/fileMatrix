package com.salmonllama.filematrix.core.vfs

import com.salmonllama.filematrix.core.model.GooglebookRootType
import com.salmonllama.filematrix.core.model.SearchHit
import com.salmonllama.filematrix.core.model.SshProfile
import com.salmonllama.filematrix.core.model.StorageRoot
import com.salmonllama.filematrix.core.model.VfsNode
import com.salmonllama.filematrix.core.model.VfsUri
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream

interface VirtualFileSystem {
    fun observeRoots(): Flow<List<StorageRoot>>
    suspend fun getRoots(): List<StorageRoot>
    fun hasAllFilesAccess(): Boolean
    fun refreshLocalVolumes()

    suspend fun listChildren(uri: VfsUri): List<VfsNode>
    suspend fun getNode(uri: VfsUri): VfsNode?
    suspend fun createDirectory(parentUri: VfsUri, name: String): VfsNode
    suspend fun createFile(parentUri: VfsUri, name: String, mimeType: String = "application/octet-stream"): VfsNode
    suspend fun delete(uri: VfsUri): Boolean
    suspend fun rename(uri: VfsUri, newName: String): VfsNode
    suspend fun openInputStream(uri: VfsUri): InputStream?
    suspend fun openOutputStream(uri: VfsUri): OutputStream?
    suspend fun searchFederated(query: String, rootType: GooglebookRootType? = null): List<SearchHit>

    suspend fun registerSafRoot(treeUriString: String, rootType: GooglebookRootType, displayName: String? = null): StorageRoot
    suspend fun registerLocalPathRoot(localPath: String, rootType: GooglebookRootType, displayName: String? = null): Result<StorageRoot>
    suspend fun registerSftpRoot(
        profile: SshProfile,
        password: String? = null,
        privateKey: String? = null,
        passphrase: String? = null
    ): Result<StorageRoot>

    fun startGoogleDriveOAuthUrl(clientId: String, clientSecret: String? = null): String
    suspend fun completeGoogleDriveOAuth(code: String, state: String?): Result<StorageRoot>
    suspend fun registerGoogleDriveWithToken(
        accessToken: String,
        refreshToken: String? = null,
        clientId: String? = null,
        clientSecret: String? = null
    ): Result<StorageRoot>
    fun getSavedGoogleDriveClientId(): String?
    suspend fun getSavedSshProfile(rootType: GooglebookRootType): SshProfile?

    suspend fun unregisterRoot(rootId: String)
}
