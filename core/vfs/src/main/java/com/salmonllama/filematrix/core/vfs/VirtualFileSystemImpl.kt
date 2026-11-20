package com.salmonllama.filematrix.core.vfs

import android.content.Context
import android.net.Uri
import com.salmonllama.filematrix.core.database.FileIndexDao
import com.salmonllama.filematrix.core.database.FileIndexEntity
import com.salmonllama.filematrix.core.database.PersistedRootDao
import com.salmonllama.filematrix.core.database.PersistedRootEntity
import com.salmonllama.filematrix.core.database.SshProfileDao
import com.salmonllama.filematrix.core.database.SshProfileEntity
import com.salmonllama.filematrix.core.model.GooglebookRootType
import com.salmonllama.filematrix.core.model.SearchHit
import com.salmonllama.filematrix.core.model.SshProfile
import com.salmonllama.filematrix.core.model.StorageRoot
import com.salmonllama.filematrix.core.model.VfsNode
import com.salmonllama.filematrix.core.model.VfsUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class VirtualFileSystemImpl(
    private val context: Context,
    private val localProvider: LocalFileSystemProvider,
    private val safProvider: SafDocumentProvider,
    private val sftpCredentialStore: SftpCredentialStore,
    private val googleDriveAuthStore: GoogleDriveAuthStore,
    private val persistedRootDao: PersistedRootDao,
    private val sshProfileDao: SshProfileDao,
    private val fileIndexDao: FileIndexDao,
    val pathRegistry: PathTranslationRegistry,
    sftpProviderOverride: SftpFileSystemProvider? = null,
    googleDriveProviderOverride: GoogleDriveProvider? = null
) : VirtualFileSystem {

    val sftpProvider: SftpFileSystemProvider = sftpProviderOverride ?: SftpFileSystemProvider(
        profileResolver = { host, port ->
            val entity = sshProfileDao.getProfileByHostPort(host, port) ?: return@SftpFileSystemProvider null
            val profile = entity.toModel()
            val creds = sftpCredentialStore.getCredentials(profile.id)
            profile to creds
        }
    )

    val googleDriveProvider: GoogleDriveProvider = googleDriveProviderOverride ?: GoogleDriveProvider(
        tokenProvider = googleDriveAuthStore
    )

    override fun hasAllFilesAccess(): Boolean = localProvider.hasAllFilesAccess()

    override fun refreshLocalVolumes() {
        localProvider.refreshVolumes()
    }

    override fun observeRoots(): Flow<List<StorageRoot>> {
        return combine(
            persistedRootDao.getAllRoots(),
            localProvider.volumeChangeCounter
        ) { persisted, _ ->
            buildCombinedRoots(persisted)
        }
    }

    override suspend fun getRoots(): List<StorageRoot> = withContext(Dispatchers.IO) {
        val persisted = persistedRootDao.getAllRootsSync()
        buildCombinedRoots(persisted)
    }

    private fun buildCombinedRoots(persisted: List<PersistedRootEntity>): List<StorageRoot> {
        val localRoots = localProvider.getDefaultRoots()
        val gdriveAccount = googleDriveProvider.getCachedAccountInfo()
        val customRoots = persisted.map { p ->
            val isGdrive = p.treeUriString.startsWith("gdrive://")
            StorageRoot(
                id = p.id,
                rootType = p.rootType,
                displayName = p.displayName,
                baseUri = p.treeUriString,
                targetPosixPath = p.customMountPosixPath,
                isMounted = true,
                isAccessible = true,
                quotaUsedBytes = if (isGdrive) gdriveAccount?.quotaUsedBytes else null,
                quotaTotalBytes = if (isGdrive) gdriveAccount?.quotaTotalBytes else null,
                authority = p.authority,
                isAutoDiscovered = false
            )
        }
        return localRoots + customRoots
    }

    override suspend fun listChildren(uri: VfsUri): List<VfsNode> = withContext(Dispatchers.IO) {
        val nodes = when {
            uri.isFileScheme -> localProvider.listChildren(uri)
            uri.isContentScheme -> safProvider.listChildren(uri)
            uri.isSftpScheme -> sftpProvider.listChildren(uri)
            uri.isGoogleDriveScheme -> googleDriveProvider.listChildren(uri)
            else -> emptyList()
        }

        val augmented = nodes.map { node ->
            val posix = pathRegistry.toPosixPath(node.uri)
            node.copy(targetPosixPath = posix)
        }

        // Index browsed files in Room FTS4 for instant federated search
        if ((uri.isFileScheme || uri.isSftpScheme) && augmented.isNotEmpty()) {
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
            runCatching { fileIndexDao.insertAll(entities) }
        }

        augmented
    }

    override suspend fun getNode(uri: VfsUri): VfsNode? = withContext(Dispatchers.IO) {
        val node = when {
            uri.isFileScheme -> localProvider.getNode(uri)
            uri.isContentScheme -> safProvider.getNode(uri)
            uri.isSftpScheme -> sftpProvider.getNode(uri)
            uri.isGoogleDriveScheme -> googleDriveProvider.getNode(uri)
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
            parentUri.isSftpScheme -> sftpProvider.createDirectory(parentUri, name)
            parentUri.isGoogleDriveScheme -> googleDriveProvider.createDirectory(parentUri, name)
            else -> throw UnsupportedOperationException("Unsupported scheme: ${parentUri.scheme}")
        }
        created.copy(targetPosixPath = pathRegistry.toPosixPath(created.uri))
    }

    override suspend fun createFile(parentUri: VfsUri, name: String, mimeType: String): VfsNode = withContext(Dispatchers.IO) {
        val created = when {
            parentUri.isFileScheme -> localProvider.createFile(parentUri, name)
            parentUri.isContentScheme -> safProvider.createFile(parentUri, name, mimeType)
            parentUri.isSftpScheme -> sftpProvider.createFile(parentUri, name)
            parentUri.isGoogleDriveScheme -> googleDriveProvider.createFile(parentUri, name, mimeType)
            else -> throw UnsupportedOperationException("Unsupported scheme: ${parentUri.scheme}")
        }
        created.copy(targetPosixPath = pathRegistry.toPosixPath(created.uri))
    }

    override suspend fun delete(uri: VfsUri): Boolean = withContext(Dispatchers.IO) {
        when {
            uri.isFileScheme -> localProvider.delete(uri)
            uri.isContentScheme -> safProvider.delete(uri)
            uri.isSftpScheme -> sftpProvider.delete(uri)
            uri.isGoogleDriveScheme -> googleDriveProvider.delete(uri)
            else -> false
        }
    }

    override suspend fun rename(uri: VfsUri, newName: String): VfsNode = withContext(Dispatchers.IO) {
        val renamed = when {
            uri.isFileScheme -> localProvider.rename(uri, newName)
            uri.isContentScheme -> safProvider.rename(uri, newName)
            uri.isSftpScheme -> sftpProvider.rename(uri, newName)
            uri.isGoogleDriveScheme -> googleDriveProvider.rename(uri, newName)
            else -> throw UnsupportedOperationException("Unsupported scheme: ${uri.scheme}")
        }
        renamed.copy(targetPosixPath = pathRegistry.toPosixPath(renamed.uri))
    }

    override suspend fun openInputStream(uri: VfsUri): InputStream? {
        return when {
            uri.isFileScheme -> localProvider.openInputStream(uri)
            uri.isContentScheme -> safProvider.openInputStream(uri)
            uri.isSftpScheme -> sftpProvider.openInputStream(uri)
            uri.isGoogleDriveScheme -> googleDriveProvider.openInputStream(uri)
            else -> null
        }
    }

    override suspend fun openOutputStream(uri: VfsUri): OutputStream? {
        return when {
            uri.isFileScheme -> localProvider.openOutputStream(uri)
            uri.isContentScheme -> safProvider.openOutputStream(uri)
            uri.isSftpScheme -> sftpProvider.openOutputStream(uri)
            uri.isGoogleDriveScheme -> googleDriveProvider.openOutputStream(uri)
            else -> null
        }
    }

    override suspend fun searchFederated(query: String, rootType: GooglebookRootType?): List<SearchHit> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchHit>()

        // 1. Search Room FTS4 index
        val localEntities = try {
            fileIndexDao.searchFts(query)
        } catch (_: Exception) {
            fileIndexDao.searchLike(query)
        }.filter { rootType == null || it.rootType == rootType }

        results.addAll(
            localEntities.map { entity ->
                val vfsUri = when {
                    entity.uriString.startsWith("file://") -> VfsUri.fromFile(entity.path, entity.rootType)
                    entity.uriString.startsWith("sftp://") -> VfsUri.parseSftp(entity.uriString, entity.rootType)
                    entity.uriString.startsWith("gdrive://") -> VfsUri.fromGoogleDrive(
                        entity.uriString.removePrefix("gdrive://"),
                        entity.path
                    )
                    else -> VfsUri.fromContent(entity.uriString, entity.rootType, null, entity.path)
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

        // 2. Live Google Drive REST API v3 search if connected
        if (googleDriveProvider.isConnected() && (rootType == null || rootType == GooglebookRootType.GOOGLE_DRIVE)) {
            results.addAll(runCatching { googleDriveProvider.search(query) }.getOrDefault(emptyList()))
        }

        // 3. Live SAF search across mounted content:// roots
        val persisted = runCatching { persistedRootDao.getAllRootsSync() }.getOrDefault(emptyList())
        for (root in persisted) {
            if (!root.treeUriString.startsWith("content://")) continue
            if (rootType != null && root.rootType != rootType) continue
            val safHits = runCatching {
                safProvider.searchSafRoot(Uri.parse(root.treeUriString), root.rootType, query)
            }.getOrDefault(emptyList())
            results.addAll(safHits)
        }

        results.distinctBy { it.node.id }
    }

    override suspend fun registerSafRoot(
        treeUriString: String,
        rootType: GooglebookRootType,
        displayName: String?
    ): StorageRoot = withContext(Dispatchers.IO) {
        val parsedUri = Uri.parse(treeUriString)
        val authority = parsedUri.authority ?: "com.android.documents"
        val resolvedName = displayName?.takeIf { it.isNotBlank() } ?: rootType.title
        val id = "root_saf_${rootType.name.lowercase()}_${System.currentTimeMillis()}"

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

    override suspend fun registerLocalPathRoot(
        localPath: String,
        rootType: GooglebookRootType,
        displayName: String?
    ): Result<StorageRoot> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanedPath = localPath.trim().ifBlank { "/" }
            val dir = File(cleanedPath)
            if (!dir.exists() || !dir.isDirectory) {
                throw IllegalArgumentException("Directory does not exist: $cleanedPath")
            }
            if (!dir.canRead()) {
                throw SecurityException("No read permission for $cleanedPath. Grant All Files Access in Settings.")
            }

            val resolvedName = displayName?.takeIf { it.isNotBlank() }
                ?: dir.name.ifBlank { rootType.title }
            val id = "root_local_${rootType.name.lowercase()}_${System.currentTimeMillis()}"
            val fileUri = "file://${dir.absolutePath}"

            val entity = PersistedRootEntity(
                id = id,
                rootType = rootType,
                displayName = resolvedName,
                treeUriString = fileUri,
                authority = "local",
                customMountPosixPath = dir.absolutePath
            )
            persistedRootDao.insertOrUpdate(entity)

            StorageRoot(
                id = id,
                rootType = rootType,
                displayName = resolvedName,
                baseUri = fileUri,
                targetPosixPath = dir.absolutePath,
                authority = "local"
            )
        }
    }

    override suspend fun registerSftpRoot(
        profile: SshProfile,
        password: String?,
        privateKey: String?,
        passphrase: String?
    ): Result<StorageRoot> = withContext(Dispatchers.IO) {
        val creds = SftpCredentials(
            password = password,
            privateKey = privateKey,
            passphrase = passphrase
        )
        val connectResult = sftpProvider.connect(profile, creds)
        if (connectResult.isFailure) {
            return@withContext Result.failure(connectResult.exceptionOrNull() ?: IllegalStateException("SFTP connection failed"))
        }

        val resolvedRemoteDir = connectResult.getOrDefault(profile.defaultRemoteDir)
        val profileId = profile.id.ifBlank { "ssh_${profile.rootType.name.lowercase()}_${System.currentTimeMillis()}" }
        val normalizedProfile = profile.copy(id = profileId, defaultRemoteDir = resolvedRemoteDir)

        sftpCredentialStore.saveCredentials(
            profileId = profileId,
            password = password,
            privateKey = privateKey,
            passphrase = passphrase
        )
        sshProfileDao.insertOrUpdate(normalizedProfile.toEntity())

        val sftpUri = VfsUri.fromSftp(
            host = normalizedProfile.host,
            port = normalizedProfile.port,
            path = resolvedRemoteDir,
            rootType = normalizedProfile.rootType
        )
        val rootId = "root_sftp_$profileId"
        val resolvedName = normalizedProfile.name.ifBlank { "${normalizedProfile.rootType.title} (SFTP)" }

        val entity = PersistedRootEntity(
            id = rootId,
            rootType = normalizedProfile.rootType,
            displayName = resolvedName,
            treeUriString = sftpUri.rawString,
            authority = "${normalizedProfile.host}:${normalizedProfile.port}",
            customMountPosixPath = resolvedRemoteDir
        )
        persistedRootDao.insertOrUpdate(entity)

        Result.success(
            StorageRoot(
                id = rootId,
                rootType = normalizedProfile.rootType,
                displayName = resolvedName,
                baseUri = sftpUri.rawString,
                targetPosixPath = resolvedRemoteDir,
                authority = entity.authority
            )
        )
    }

    override fun startGoogleDriveOAuthUrl(clientId: String, clientSecret: String?): String {
        return googleDriveAuthStore.buildPkceAuthorizationUrl(
            clientId = clientId,
            clientSecret = clientSecret
        )
    }

    override suspend fun completeGoogleDriveOAuth(code: String, state: String?): Result<StorageRoot> = withContext(Dispatchers.IO) {
        val exchangeResult = googleDriveAuthStore.exchangeAuthorizationCode(code, state)
        if (exchangeResult.isFailure) {
            return@withContext Result.failure(
                exchangeResult.exceptionOrNull() ?: IllegalStateException("OAuth token exchange failed")
            )
        }
        verifyAndPersistGoogleDriveRoot()
    }

    override suspend fun registerGoogleDriveWithToken(
        accessToken: String,
        refreshToken: String?,
        clientId: String?,
        clientSecret: String?
    ): Result<StorageRoot> = withContext(Dispatchers.IO) {
        val cleanedAccess = accessToken.trim()
        if (cleanedAccess.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Access token cannot be empty"))
        }
        googleDriveAuthStore.saveTokenState(
            GoogleDriveTokenState(
                accessToken = cleanedAccess,
                refreshToken = refreshToken?.trim()?.takeIf { it.isNotEmpty() },
                clientId = clientId?.trim()?.takeIf { it.isNotEmpty() },
                clientSecret = clientSecret?.trim()?.takeIf { it.isNotEmpty() },
                expiresAtEpochMs = System.currentTimeMillis() + 3_600_000L
            )
        )
        verifyAndPersistGoogleDriveRoot()
    }

    private suspend fun verifyAndPersistGoogleDriveRoot(): Result<StorageRoot> {
        val accountResult = googleDriveProvider.fetchAccountInfo()
        if (accountResult.isFailure) {
            googleDriveAuthStore.clear()
            return@verifyAndPersistGoogleDriveRoot Result.failure(
                accountResult.exceptionOrNull() ?: IllegalStateException("Failed to verify Google Drive account")
            )
        }
        val account = accountResult.getOrThrow()
        val rootId = "root_gdrive_primary"
        val displayName = "Google Drive (${account.email})"
        val entity = PersistedRootEntity(
            id = rootId,
            rootType = GooglebookRootType.GOOGLE_DRIVE,
            displayName = displayName,
            treeUriString = "gdrive://${GoogleDriveProvider.ROOT_FOLDER_ID}",
            authority = GoogleDriveProvider.ROOT_FOLDER_ID,
            customMountPosixPath = "/My Drive"
        )
        persistedRootDao.insertOrUpdate(entity)

        return Result.success(
            StorageRoot(
                id = rootId,
                rootType = GooglebookRootType.GOOGLE_DRIVE,
                displayName = displayName,
                baseUri = entity.treeUriString,
                targetPosixPath = "/My Drive",
                quotaUsedBytes = account.quotaUsedBytes,
                quotaTotalBytes = account.quotaTotalBytes,
                authority = entity.authority
            )
        )
    }

    override fun getSavedGoogleDriveClientId(): String? = googleDriveAuthStore.getSavedClientId()

    override suspend fun getSavedSshProfile(rootType: GooglebookRootType): SshProfile? = withContext(Dispatchers.IO) {
        sshProfileDao.getProfileForRoot(rootType)?.toModel()
    }

    override suspend fun unregisterRoot(rootId: String) = withContext(Dispatchers.IO) {
        val existing = persistedRootDao.getRootById(rootId) ?: return@withContext
        when {
            existing.treeUriString.startsWith("sftp://") -> {
                val parsed = VfsUri.parseSftp(existing.treeUriString, existing.rootType)
                SftpFileSystemProvider.parseHostPort(parsed)?.let { (host, port) ->
                    sftpProvider.disconnect(host, port)
                }
                val profileId = rootId.removePrefix("root_sftp_")
                sftpCredentialStore.clearCredentials(profileId)
                sshProfileDao.getProfileById(profileId)?.let { sshProfileDao.delete(it) }
            }
            existing.treeUriString.startsWith("gdrive://") -> {
                googleDriveAuthStore.clear()
            }
        }
        persistedRootDao.delete(existing)
    }

    private fun SshProfileEntity.toModel(): SshProfile = SshProfile(
        id = id,
        name = name,
        rootType = rootType,
        host = host,
        port = port,
        username = username,
        authType = authType,
        defaultRemoteDir = defaultRemoteDir,
        autoConnect = autoConnect
    )

    private fun SshProfile.toEntity(): SshProfileEntity = SshProfileEntity(
        id = id,
        name = name,
        rootType = rootType,
        host = host,
        port = port,
        username = username,
        authType = authType,
        defaultRemoteDir = defaultRemoteDir,
        autoConnect = autoConnect
    )
}
