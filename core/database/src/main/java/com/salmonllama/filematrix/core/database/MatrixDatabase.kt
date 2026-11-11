package com.salmonllama.filematrix.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import com.salmonllama.filematrix.core.model.GooglebookRootType
import kotlinx.coroutines.flow.Flow

@Dao
interface PersistedRootDao {
    @Query("SELECT * FROM persisted_roots ORDER BY grantedAtEpochMs DESC")
    fun getAllRoots(): Flow<List<PersistedRootEntity>>

    @Query("SELECT * FROM persisted_roots WHERE rootType = :type")
    suspend fun getRootsByType(type: GooglebookRootType): List<PersistedRootEntity>

    @Query("SELECT * FROM persisted_roots WHERE id = :id LIMIT 1")
    suspend fun getRootById(id: String): PersistedRootEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(root: PersistedRootEntity)

    @Delete
    suspend fun delete(root: PersistedRootEntity)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY sortOrder ASC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)
}

@Dao
interface SshProfileDao {
    @Query("SELECT * FROM ssh_profiles ORDER BY name ASC")
    fun getAllProfiles(): Flow<List<SshProfileEntity>>

    @Query("SELECT * FROM ssh_profiles WHERE rootType = :type LIMIT 1")
    suspend fun getProfileForRoot(type: GooglebookRootType): SshProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: SshProfileEntity)

    @Delete
    suspend fun delete(profile: SshProfileEntity)
}

@Dao
interface FileIndexDao {
    @Query("SELECT rowid, * FROM file_index WHERE file_index MATCH :query LIMIT :limit")
    suspend fun searchFts(query: String, limit: Int = 50): List<FileIndexEntity>

    @Query("SELECT rowid, * FROM file_index WHERE name LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun searchLike(query: String, limit: Int = 50): List<FileIndexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<FileIndexEntity>)

    @Query("DELETE FROM file_index WHERE rootType = :rootType")
    suspend fun clearForRoot(rootType: GooglebookRootType)
}

@Database(
    entities = [
        PersistedRootEntity::class,
        BookmarkEntity::class,
        SshProfileEntity::class,
        FileIndexEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MatrixDatabase : RoomDatabase() {
    abstract fun persistedRootDao(): PersistedRootDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun sshProfileDao(): SshProfileDao
    abstract fun fileIndexDao(): FileIndexDao
}
