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
    val documentId: String? = null
)

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

    companion object {
        fun fromFile(path: String): VfsUri = VfsUri(
            rawString = "file://$path",
            scheme = "file",
            authority = null,
            path = path,
            rootType = GooglebookRootType.GOOGLEBOOK_LOCAL
        )

        fun fromContent(contentUri: String, rootType: GooglebookRootType, authority: String?, documentPath: String): VfsUri = VfsUri(
            rawString = contentUri,
            scheme = "content",
            authority = authority,
            path = documentPath,
            rootType = rootType
        )

        fun fromSftp(host: String, port: Int, path: String, rootType: GooglebookRootType): VfsUri = VfsUri(
            rawString = "sftp://$host:$port$path",
            scheme = "sftp",
            authority = "$host:$port",
            path = path,
            rootType = rootType
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
