package com.salmonllama.filematrix.core.vfs

import android.os.Environment
import com.salmonllama.filematrix.core.model.GooglebookRootType
import com.salmonllama.filematrix.core.model.StorageRoot
import com.salmonllama.filematrix.core.model.VfsNode
import com.salmonllama.filematrix.core.model.VfsUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class LocalFileSystemProvider {

    fun getDefaultRoots(): List<StorageRoot> {
        val externalStorage = Environment.getExternalStorageDirectory()
        val totalBytes = externalStorage.totalSpace
        val freeBytes = externalStorage.freeSpace
        val usedBytes = if (totalBytes > 0) totalBytes - freeBytes else null

        return listOf(
            StorageRoot(
                id = "local_primary",
                rootType = GooglebookRootType.GOOGLEBOOK_LOCAL,
                displayName = "Googlebook Storage",
                baseUri = "file://${externalStorage.absolutePath}",
                targetPosixPath = externalStorage.absolutePath,
                isMounted = true,
                isAccessible = externalStorage.canRead(),
                quotaUsedBytes = usedBytes,
                quotaTotalBytes = if (totalBytes > 0) totalBytes else null
            )
        )
    }

    suspend fun listChildren(uri: VfsUri): List<VfsNode> = withContext(Dispatchers.IO) {
        val dir = File(uri.path)
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()

        val files = dir.listFiles() ?: return@withContext emptyList()
        files.map { file ->
            toVfsNode(file)
        }.sortedWith(
            compareBy<VfsNode> { !it.isDirectory }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
    }

    suspend fun getNode(uri: VfsUri): VfsNode? = withContext(Dispatchers.IO) {
        val file = File(uri.path)
        if (!file.exists()) return@withContext null
        toVfsNode(file)
    }

    suspend fun createDirectory(parentUri: VfsUri, name: String): VfsNode = withContext(Dispatchers.IO) {
        val parent = File(parentUri.path)
        val newDir = File(parent, name)
        if (!newDir.exists()) {
            newDir.mkdirs()
        }
        toVfsNode(newDir)
    }

    suspend fun createFile(parentUri: VfsUri, name: String): VfsNode = withContext(Dispatchers.IO) {
        val parent = File(parentUri.path)
        val newFile = File(parent, name)
        if (!newFile.exists()) {
            newFile.createNewFile()
        }
        toVfsNode(newFile)
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
        toVfsNode(target)
    }

    fun openInputStream(uri: VfsUri): InputStream? {
        val file = File(uri.path)
        return if (file.exists() && file.isFile) FileInputStream(file) else null
    }

    fun openOutputStream(uri: VfsUri): OutputStream? {
        val file = File(uri.path)
        return FileOutputStream(file)
    }

    private fun toVfsNode(file: File): VfsNode {
        val isDirectory = file.isDirectory
        val mimeType = if (isDirectory) null else {
            val extension = file.extension.lowercase()
            android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
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
            uri = VfsUri.fromFile(file.absolutePath),
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
