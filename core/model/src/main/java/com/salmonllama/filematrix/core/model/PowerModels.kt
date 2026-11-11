package com.salmonllama.filematrix.core.model

enum class TransferStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class TransferConflictStrategy {
    OVERWRITE,
    SKIP,
    KEEP_BOTH,
    PROMPT
}

data class TransferJob(
    val id: String,
    val sourceUri: VfsUri,
    val destinationUri: VfsUri,
    val fileName: String,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val status: TransferStatus = TransferStatus.QUEUED,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val errorMessage: String? = null,
    val conflictStrategy: TransferConflictStrategy = TransferConflictStrategy.PROMPT
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

data class FileChecksum(
    val md5: String? = null,
    val sha1: String? = null,
    val sha256: String? = null,
    val sha512: String? = null
)

data class SearchHit(
    val node: VfsNode,
    val matchedField: String,
    val snippet: String? = null,
    val score: Float = 1.0f
)

data class PathMappingRule(
    val id: String,
    val name: String,
    val rootType: GooglebookRootType,
    val safAuthority: String,
    val safPrefix: String,
    val posixPrefix: String,
    val isEnabled: Boolean = true
)

enum class SshAuthType {
    PASSWORD,
    PRIVATE_KEY
}

data class SshProfile(
    val id: String,
    val name: String,
    val rootType: GooglebookRootType,
    val host: String = "127.0.0.1",
    val port: Int = 2222,
    val username: String = "antigravity",
    val authType: SshAuthType = SshAuthType.PASSWORD,
    val defaultRemoteDir: String = "/home/antigravity",
    val autoConnect: Boolean = true
)

data class Bookmark(
    val id: String,
    val name: String,
    val uri: VfsUri,
    val rootType: GooglebookRootType,
    val iconName: String = "folder"
)
