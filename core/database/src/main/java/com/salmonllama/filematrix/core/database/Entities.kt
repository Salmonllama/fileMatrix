package com.salmonllama.filematrix.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey
import com.salmonllama.filematrix.core.model.GooglebookRootType
import com.salmonllama.filematrix.core.model.SshAuthType

@Entity(tableName = "persisted_roots")
data class PersistedRootEntity(
    @PrimaryKey val id: String,
    val rootType: GooglebookRootType,
    val displayName: String,
    val treeUriString: String,
    val authority: String,
    val grantedAtEpochMs: Long = System.currentTimeMillis(),
    val isDefault: Boolean = false,
    val customMountPosixPath: String? = null
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val name: String,
    val uriString: String,
    val rootType: GooglebookRootType,
    val sortOrder: Int = 0
)

@Entity(tableName = "ssh_profiles")
data class SshProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val rootType: GooglebookRootType,
    val host: String = "127.0.0.1",
    val port: Int = 2222,
    val username: String = "antigravity",
    val authType: SshAuthType = SshAuthType.PASSWORD,
    val defaultRemoteDir: String = "/home/antigravity",
    val autoConnect: Boolean = true
)

@Fts4
@Entity(tableName = "file_index")
data class FileIndexEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowid: Int = 0,
    val id: String,
    val name: String,
    val path: String,
    val uriString: String,
    val rootType: GooglebookRootType,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val mimeType: String?,
    val extension: String
)
