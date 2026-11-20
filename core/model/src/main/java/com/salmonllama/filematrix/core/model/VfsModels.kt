package com.salmonllama.filematrix.core.model

enum class GooglebookRootType(
    val title: String,
    val description: String,
    val defaultScheme: String
) {
    GOOGLEBOOK_LOCAL(
        title = "Googlebook Local",
        description = "Internal storage, Downloads, and Documents",
        defaultScheme = "file"
    ),
    LINUX_VM(
        title = "Linux VM",
        description = "Debian / Crostini / Android Virtualization environment",
        defaultScheme = "content"
    ),
    ANTIGRAVITY(
        title = "Antigravity",
        description = "Antigravity coding workspace & paired repository",
        defaultScheme = "content"
    ),
    GOOGLE_DRIVE(
        title = "Google Drive",
        description = "Cloud storage, Shared Drives, and offline files",
        defaultScheme = "content"
    ),
    MOBILE_FILES(
        title = "Mobile Files",
        description = "Cross-device tethered phone and connected media",
        defaultScheme = "content"
    )
}

data class StorageRoot(
    val id: String,
    val rootType: GooglebookRootType,
    val displayName: String,
    val baseUri: String,
    val targetPosixPath: String? = null,
    val isMounted: Boolean = true,
    val isAccessible: Boolean = true,
    val quotaUsedBytes: Long? = null,
    val quotaTotalBytes: Long? = null,
    val authority: String? = null,
    val documentId: String? = null,
    val isAutoDiscovered: Boolean = false
) {
    fun toVfsUri(): VfsUri {
        return when {
            baseUri.startsWith("file://") -> {
                VfsUri.fromFile(baseUri.removePrefix("file://"), rootType)
            }
            baseUri.startsWith("sftp://") -> {
                VfsUri.parseSftp(baseUri, rootType)
            }
            baseUri.startsWith("gdrive://") -> {
                val fileId = baseUri.removePrefix("gdrive://").substringBefore('/').ifBlank { "root" }
                val displayPath = targetPosixPath ?: if (fileId == "root") "/My Drive" else "/$displayName"
                VfsUri.fromGoogleDrive(fileId = fileId, displayPath = displayPath)
            }
            else -> {
                VfsUri.fromContent(
                    contentUri = baseUri,
                    rootType = rootType,
                    authority = authority,
                    documentPath = documentId ?: baseUri
                )
            }
        }
    }
}

data class VfsUri(
    val rawString: String,
    val scheme: String,
    val authority: String?,
    val path: String,
    val rootType: GooglebookRootType
) {
    val isFileScheme: Boolean get() = scheme == "file"
    val isContentScheme: Boolean get() = scheme == "content"
    val isSftpScheme: Boolean get() = scheme == "sftp"
    val isGoogleDriveScheme: Boolean get() = scheme == "gdrive"

    fun withPath(newPath: String): VfsUri {
        val normalized = if (newPath.startsWith("/")) newPath else "/$newPath"
        return when {
            isFileScheme -> fromFile(normalized, rootType)
            isSftpScheme -> {
                val auth = authority ?: "127.0.0.1:22"
                val host = auth.substringBefore(':')
                val port = auth.substringAfter(':', "22").toIntOrNull() ?: 22
                fromSftp(host, port, normalized, rootType)
            }
            else -> copy(path = normalized)
        }
    }

    fun parentUri(): VfsUri? {
        return when {
            isFileScheme || isSftpScheme -> {
                val trimmed = path.trimEnd('/')
                if (trimmed.isEmpty() || trimmed == "/") return null
                val parent = trimmed.substringBeforeLast('/', "").ifEmpty { "/" }
                withPath(parent)
            }
            else -> null
        }
    }

    companion object {
        fun fromFile(
            path: String,
            rootType: GooglebookRootType = GooglebookRootType.GOOGLEBOOK_LOCAL
        ): VfsUri {
            val normalized = if (path.isBlank()) "/" else path
            return VfsUri(
                rawString = "file://$normalized",
                scheme = "file",
                authority = null,
                path = normalized,
                rootType = rootType
            )
        }

        fun fromContent(
            contentUri: String,
            rootType: GooglebookRootType,
            authority: String?,
            documentPath: String
        ): VfsUri = VfsUri(
            rawString = contentUri,
            scheme = "content",
            authority = authority,
            path = documentPath,
            rootType = rootType
        )

        fun fromSftp(
            host: String,
            port: Int,
            path: String,
            rootType: GooglebookRootType
        ): VfsUri {
            val normalizedPath = if (path.startsWith("/")) path else "/$path"
            return VfsUri(
                rawString = "sftp://$host:$port$normalizedPath",
                scheme = "sftp",
                authority = "$host:$port",
                path = normalizedPath,
                rootType = rootType
            )
        }

        fun parseSftp(rawUri: String, rootType: GooglebookRootType): VfsUri {
            val withoutScheme = rawUri.removePrefix("sftp://")
            val slashIndex = withoutScheme.indexOf('/')
            val hostPort = if (slashIndex >= 0) withoutScheme.substring(0, slashIndex) else withoutScheme
            val remotePath = if (slashIndex >= 0) withoutScheme.substring(slashIndex) else "/"
            val host = hostPort.substringBefore(':')
            val port = hostPort.substringAfter(':', "22").toIntOrNull() ?: 22
            return fromSftp(host, port, remotePath, rootType)
        }

        fun fromGoogleDrive(
            fileId: String = "root",
            displayPath: String = "/My Drive"
        ): VfsUri = VfsUri(
            rawString = "gdrive://$fileId",
            scheme = "gdrive",
            authority = fileId,
            path = displayPath,
            rootType = GooglebookRootType.GOOGLE_DRIVE
        )
    }
}

data class VfsNode(
    val id: String,
    val uri: VfsUri,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val mimeType: String?,
    val extension: String = name.substringAfterLast('.', ""),
    val isHidden: Boolean = name.startsWith('.'),
    val isSymlink: Boolean = false,
    val permissionsPosix: String? = null,
    val childCount: Int? = null,
    val targetPosixPath: String? = null
) {
    val isArchive: Boolean
        get() = extension.lowercase() in setOf("zip", "tar", "gz", "tgz", "rar", "7z", "apk", "jar")

    val isMedia: Boolean
        get() = mimeType?.startsWith("image/") == true ||
                mimeType?.startsWith("video/") == true ||
                mimeType?.startsWith("audio/") == true

    val isDocument: Boolean
        get() = mimeType?.startsWith("text/") == true ||
                extension.lowercase() in setOf("pdf", "md", "txt", "json", "xml", "kt", "java", "py", "rs", "cpp", "c", "h", "html", "css", "js", "ts")
}
