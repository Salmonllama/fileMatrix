package com.salmonllama.filematrix.core.vfs

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.webkit.MimeTypeMap
import com.salmonllama.filematrix.core.model.GooglebookRootType
import com.salmonllama.filematrix.core.model.StorageRoot
import com.salmonllama.filematrix.core.model.VfsNode
import com.salmonllama.filematrix.core.model.VfsUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class LocalFileSystemProvider(private val context: Context) {

    private val _volumeChangeCounter = MutableStateFlow(0L)
    val volumeChangeCounter: StateFlow<Long> = _volumeChangeCounter.asStateFlow()

    init {
        registerVolumeCallback()
    }

    private fun registerVolumeCallback() {
        try {
            val storageManager = context.getSystemService(StorageManager::class.java) ?: return
            storageManager.registerStorageVolumeCallback(
                context.mainExecutor,
                object : StorageManager.StorageVolumeCallback() {
                    override fun onStateChanged(volume: StorageVolume) {
                        refreshVolumes()
                    }
                }
            )
        } catch (_: Exception) {
            // Best-effort volume observer; manual refreshVolumes() still works
        }
    }

    fun refreshVolumes() {
        _volumeChangeCounter.value = _volumeChangeCounter.value + 1L
    }

    fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

    fun getDefaultRoots(): List<StorageRoot> {
        val roots = mutableListOf<StorageRoot>()
        val seenPaths = mutableSetOf<String>()
        val allFilesGranted = hasAllFilesAccess()

        val storageManager = try {
            context.getSystemService(StorageManager::class.java)
        } catch (_: Exception) {
            null
        }

        val volumes = try {
            storageManager?.storageVolumes.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

        for (volume in volumes) {
            if (volume.state != Environment.MEDIA_MOUNTED) continue
            val dir = volume.directory ?: continue
            val absPath = dir.absolutePath
            if (!seenPaths.add(absPath)) continue

            val totalBytes = runCatching { dir.totalSpace }.getOrDefault(0L)
            val freeBytes = runCatching { dir.freeSpace }.getOrDefault(0L)
            val usedBytes = if (totalBytes > 0L) (totalBytes - freeBytes).coerceAtLeast(0L) else null
            val description = runCatching { volume.getDescription(context) }.getOrNull().orEmpty()

            if (volume.isPrimary) {
                roots.add(
                    StorageRoot(
                        id = "local_primary",
                        rootType = GooglebookRootType.GOOGLEBOOK_LOCAL,
                        displayName = "Googlebook Storage",
                        baseUri = "file://$absPath",
                        targetPosixPath = absPath,
                        isMounted = true,
                        isAccessible = allFilesGranted || dir.canRead(),
                        quotaUsedBytes = usedBytes,
                        quotaTotalBytes = if (totalBytes > 0L) totalBytes else null,
                        isAutoDiscovered = true
                    )
                )
            } else {
                val volId = volume.uuid ?: dir.name.ifBlank { absPath.hashCode().toString() }
                roots.add(
                    StorageRoot(
                        id = "local_vol_$volId",
                        rootType = GooglebookRootType.MOBILE_FILES,
                        displayName = description.ifBlank { "External Drive (${dir.name})" },
                        baseUri = "file://$absPath",
                        targetPosixPath = absPath,
                        isMounted = true,
                        isAccessible = allFilesGranted || dir.canRead(),
                        quotaUsedBytes = usedBytes,
                        quotaTotalBytes = if (totalBytes > 0L) totalBytes else null,
                        isAutoDiscovered = true
                    )
                )
            }
        }

        // Fallback to primary external storage if StorageManager yielded no primary root
        if (roots.none { it.rootType == GooglebookRootType.GOOGLEBOOK_LOCAL }) {
            val externalStorage = Environment.getExternalStorageDirectory()
            val absPath = externalStorage.absolutePath
            if (seenPaths.add(absPath)) {
                val totalBytes = runCatching { externalStorage.totalSpace }.getOrDefault(0L)
                val freeBytes = runCatching { externalStorage.freeSpace }.getOrDefault(0L)
                val usedBytes = if (totalBytes > 0L) (totalBytes - freeBytes).coerceAtLeast(0L) else null
                roots.add(
                    0,
                    StorageRoot(
                        id = "local_primary",
                        rootType = GooglebookRootType.GOOGLEBOOK_LOCAL,
                        displayName = "Googlebook Storage",
                        baseUri = "file://$absPath",
                        targetPosixPath = absPath,
                        isMounted = true,
                        isAccessible = allFilesGranted || externalStorage.canRead(),
                        quotaUsedBytes = usedBytes,
                        quotaTotalBytes = if (totalBytes > 0L) totalBytes else null,
                        isAutoDiscovered = true
                    )
                )
            }
        }

        // Auto-discover direct ChromeOS / Googlebook / Linux filesystem mounts if readable
        val directMounts = listOf(
            Triple("/mnt/chromeos/MyFiles", GooglebookRootType.GOOGLEBOOK_LOCAL, "ChromeOS MyFiles"),
            Triple("/mnt/chromeos/LinuxFiles", GooglebookRootType.LINUX_VM, "Linux VM (Direct)"),
            Triple("/mnt/chromeos/GoogleDrive/MyDrive", GooglebookRootType.GOOGLE_DRIVE, "Google Drive (Local)"),
            Triple("/home/antigravity", GooglebookRootType.ANTIGRAVITY, "Antigravity (Local)")
        )
        for ((mountPath, rootType, label) in directMounts) {
            val dir = File(mountPath)
            if (dir.exists() && dir.isDirectory && dir.canRead() && seenPaths.add(dir.absolutePath)) {
                val totalBytes = runCatching { dir.totalSpace }.getOrDefault(0L)
                val freeBytes = runCatching { dir.freeSpace }.getOrDefault(0L)
                val usedBytes = if (totalBytes > 0L) (totalBytes - freeBytes).coerceAtLeast(0L) else null
                roots.add(
                    StorageRoot(
                        id = "direct_${rootType.name.lowercase()}_${dir.name.lowercase()}",
                        rootType = rootType,
                        displayName = label,
                        baseUri = "file://${dir.absolutePath}",
                        targetPosixPath = dir.absolutePath,
                        isMounted = true,
                        isAccessible = true,
                        quotaUsedBytes = usedBytes,
                        quotaTotalBytes = if (totalBytes > 0L) totalBytes else null,
                        isAutoDiscovered = true
                    )
                )
            }
        }

        return roots
    }

    suspend fun listChildren(uri: VfsUri): List<VfsNode> = withContext(Dispatchers.IO) {
        val dir = File(uri.path)
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()

        val files = dir.listFiles() ?: return@withContext emptyList()
        files.map { file ->
            toVfsNode(file, uri.rootType)
        }.sortedWith(
            compareBy<VfsNode> { !it.isDirectory }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
    }

    suspend fun getNode(uri: VfsUri): VfsNode? = withContext(Dispatchers.IO) {
        val file = File(uri.path)
        if (!file.exists()) return@withContext null
        toVfsNode(file, uri.rootType)
    }

    suspend fun createDirectory(parentUri: VfsUri, name: String): VfsNode = withContext(Dispatchers.IO) {
        val parent = File(parentUri.path)
        val newDir = File(parent, name)
        if (!newDir.exists()) {
            newDir.mkdirs()
        }
        toVfsNode(newDir, parentUri.rootType)
    }

    suspend fun createFile(parentUri: VfsUri, name: String): VfsNode = withContext(Dispatchers.IO) {
        val parent = File(parentUri.path)
        val newFile = File(parent, name)
        if (!newFile.exists()) {
            newFile.createNewFile()
        }
        toVfsNode(newFile, parentUri.rootType)
    }

    suspend fun delete(uri: VfsUri): Boolean = withContext(Dispatchers.IO) {
        val file = File(uri.path)
        if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }

    suspend fun rename(uri: VfsUri, newName: String): VfsNode = withContext(Dispatchers.IO) {
        val file = File(uri.path)
        val target = File(file.parentFile, newName)
        val success = file.renameTo(target)
        if (!success) {
            Files.move(file.toPath(), target.toPath())
        }
        toVfsNode(target, uri.rootType)
    }

    fun openInputStream(uri: VfsUri): InputStream? {
        val file = File(uri.path)
        return if (file.exists() && file.isFile) FileInputStream(file) else null
    }

    fun openOutputStream(uri: VfsUri): OutputStream? {
        val file = File(uri.path)
        return FileOutputStream(file)
    }

    private fun toVfsNode(file: File, rootType: GooglebookRootType): VfsNode {
        val isDirectory = file.isDirectory
        val mimeType = if (isDirectory) null else {
            val extension = file.extension.lowercase()
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        }

        val posixPerms = try {
            val perms = Files.getPosixFilePermissions(file.toPath())
            PosixFilePermissions.toString(perms)
        } catch (_: Exception) {
            val r = if (file.canRead()) "r" else "-"
            val w = if (file.canWrite()) "w" else "-"
            val x = if (file.canExecute()) "x" else "-"
            "$r$w$x------"
        }

        val childCount = if (isDirectory) {
            file.list()?.size ?: 0
        } else null

        return VfsNode(
            id = file.absolutePath,
            uri = VfsUri.fromFile(file.absolutePath, rootType),
            name = file.name.ifEmpty { file.absolutePath },
            isDirectory = isDirectory,
            sizeBytes = if (isDirectory) 0L else file.length(),
            lastModified = file.lastModified(),
            mimeType = mimeType,
            extension = file.extension,
            isHidden = file.name.startsWith("."),
            isSymlink = Files.isSymbolicLink(file.toPath()),
            permissionsPosix = posixPerms,
            childCount = childCount,
            targetPosixPath = file.absolutePath
        )
    }
}
