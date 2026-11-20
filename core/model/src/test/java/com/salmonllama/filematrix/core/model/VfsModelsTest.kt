package com.salmonllama.filematrix.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VfsModelsTest {

    @Test
    fun testVfsNodeCategorization() {
        val archiveNode = VfsNode(
            id = "zip_node",
            uri = VfsUri.fromFile("/sdcard/archive.zip"),
            name = "archive.zip",
            isDirectory = false,
            sizeBytes = 1024L,
            lastModified = System.currentTimeMillis(),
            mimeType = "application/zip"
        )
        assertTrue(archiveNode.isArchive)
        assertFalse(archiveNode.isDocument)
        assertEquals("zip", archiveNode.extension)
        assertFalse(archiveNode.isHidden)

        val docNode = VfsNode(
            id = "code_node",
            uri = VfsUri.fromFile("/sdcard/Main.kt"),
            name = "Main.kt",
            isDirectory = false,
            sizeBytes = 2048L,
            lastModified = System.currentTimeMillis(),
            mimeType = "text/x-kotlin"
        )
        assertTrue(docNode.isDocument)
        assertFalse(docNode.isArchive)

        val hiddenNode = VfsNode(
            id = "hidden_node",
            uri = VfsUri.fromFile("/sdcard/.git"),
            name = ".git",
            isDirectory = true,
            sizeBytes = 0L,
            lastModified = System.currentTimeMillis(),
            mimeType = null
        )
        assertTrue(hiddenNode.isHidden)
    }

    @Test
    fun testTransferProgressFraction() {
        val job = TransferJob(
            id = "job1",
            sourceUri = VfsUri.fromFile("/a"),
            destinationUri = VfsUri.fromFile("/b"),
            fileName = "sample.iso",
            bytesTransferred = 500L,
            totalBytes = 1000L
        )
        assertEquals(0.5f, job.progressFraction, 0.001f)
    }

    @Test
    fun testUriNavigationHelpersAcrossSchemes() {
        val fileUri = VfsUri.fromFile("/storage/emulated/0/Download", GooglebookRootType.GOOGLEBOOK_LOCAL)
        val fileParent = fileUri.parentUri()
        assertEquals("file:///storage/emulated/0", fileParent?.rawString)
        assertEquals("/storage/emulated/0", fileParent?.path)
        assertNull(VfsUri.fromFile("/").parentUri())

        val sftpUri = VfsUri.parseSftp("sftp://penguin.linux.test:22/home/user/code", GooglebookRootType.LINUX_VM)
        assertTrue(sftpUri.isSftpScheme)
        assertEquals("penguin.linux.test:22", sftpUri.authority)
        assertEquals("/home/user/code", sftpUri.path)
        val sftpParent = sftpUri.parentUri()
        assertEquals("sftp://penguin.linux.test:22/home/user", sftpParent?.rawString)
        assertEquals("/home/user", sftpParent?.path)

        val gdriveUri = VfsUri.fromGoogleDrive("root", "/My Drive")
        assertTrue(gdriveUri.isGoogleDriveScheme)
        assertEquals("gdrive://root", gdriveUri.rawString)
        assertEquals("root", gdriveUri.authority)
        assertNull(gdriveUri.parentUri())
    }

    @Test
    fun testStorageRootToVfsUri() {
        val localUsb = StorageRoot(
            id = "usb1",
            rootType = GooglebookRootType.MOBILE_FILES,
            displayName = "USB Drive",
            baseUri = "file:///storage/ABCD-1234"
        )
        val usbUri = localUsb.toVfsUri()
        assertTrue(usbUri.isFileScheme)
        assertEquals(GooglebookRootType.MOBILE_FILES, usbUri.rootType)
        assertEquals("/storage/ABCD-1234", usbUri.path)

        val sftpRoot = StorageRoot(
            id = "sftp1",
            rootType = GooglebookRootType.ANTIGRAVITY,
            displayName = "Antigravity",
            baseUri = "sftp://127.0.0.1:2222/home/antigravity",
            authority = "127.0.0.1:2222"
        )
        val sftpUri = sftpRoot.toVfsUri()
        assertTrue(sftpUri.isSftpScheme)
        assertEquals("/home/antigravity", sftpUri.path)

        val gdriveRoot = StorageRoot(
            id = "gd1",
            rootType = GooglebookRootType.GOOGLE_DRIVE,
            displayName = "Google Drive",
            baseUri = "gdrive://root",
            targetPosixPath = "/My Drive"
        )
        val gdriveUri = gdriveRoot.toVfsUri()
        assertTrue(gdriveUri.isGoogleDriveScheme)
        assertEquals("root", gdriveUri.authority)
        assertEquals("/My Drive", gdriveUri.path)
    }
}
