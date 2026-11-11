package com.salmonllama.filematrix.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
