package com.salmonllama.filematrix

import android.app.Application
import androidx.room.Room
import com.salmonllama.filematrix.core.database.MatrixDatabase
import com.salmonllama.filematrix.core.terminal.TerminalSessionManager
import com.salmonllama.filematrix.core.vfs.GoogleDriveAuthStore
import com.salmonllama.filematrix.core.vfs.LocalFileSystemProvider
import com.salmonllama.filematrix.core.vfs.PathTranslationRegistry
import com.salmonllama.filematrix.core.vfs.SafDocumentProvider
import com.salmonllama.filematrix.core.vfs.SftpCredentialStore
import com.salmonllama.filematrix.core.vfs.TransferQueueManager
import com.salmonllama.filematrix.core.vfs.VirtualFileSystemImpl

class FileMatrixApplication : Application() {

    lateinit var database: MatrixDatabase
        private set

    lateinit var vfs: VirtualFileSystemImpl
        private set

    lateinit var transferManager: TransferQueueManager
        private set

    lateinit var sessionManager: TerminalSessionManager
        private set

    lateinit var pathRegistry: PathTranslationRegistry
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = Room.databaseBuilder(
            applicationContext,
            MatrixDatabase::class.java,
            "filematrix_db"
        ).fallbackToDestructiveMigration().build()

        pathRegistry = PathTranslationRegistry()
        val localProvider = LocalFileSystemProvider(this)
        val safProvider = SafDocumentProvider(this)
        val sftpCredentialStore = SftpCredentialStore(this)
        val googleDriveAuthStore = GoogleDriveAuthStore(this)

        vfs = VirtualFileSystemImpl(
            context = this,
            localProvider = localProvider,
            safProvider = safProvider,
            sftpCredentialStore = sftpCredentialStore,
            googleDriveAuthStore = googleDriveAuthStore,
            persistedRootDao = database.persistedRootDao(),
            sshProfileDao = database.sshProfileDao(),
            fileIndexDao = database.fileIndexDao(),
            pathRegistry = pathRegistry
        )

        transferManager = TransferQueueManager(vfs = vfs)
        sessionManager = TerminalSessionManager()
    }

    companion object {
        lateinit var instance: FileMatrixApplication
            private set
    }
}
