package com.salmonllama.filematrix

import android.app.Application
import androidx.room.Room
import com.salmonllama.filematrix.core.database.MatrixDatabase
import com.salmonllama.filematrix.core.terminal.TerminalSessionManager
import com.salmonllama.filematrix.core.vfs.LocalFileSystemProvider
import com.salmonllama.filematrix.core.vfs.PathTranslationRegistry
import com.salmonllama.filematrix.core.vfs.SafDocumentProvider
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
        val localProvider = LocalFileSystemProvider()
        val safProvider = SafDocumentProvider(this)

        vfs = VirtualFileSystemImpl(
            context = this,
            localProvider = localProvider,
            safProvider = safProvider,
            persistedRootDao = database.persistedRootDao(),
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
