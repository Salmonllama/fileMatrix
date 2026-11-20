package com.salmonllama.filematrix.core.vfs

import com.salmonllama.filematrix.core.model.GooglebookRootType
import com.salmonllama.filematrix.core.model.VfsUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SftpFileSystemProviderTest {

    @Test
    fun testParseHostPort() {
        val uri = VfsUri.fromSftp("penguin.linux.test", 2222, "/home/user", GooglebookRootType.LINUX_VM)
        assertEquals("penguin.linux.test" to 2222, SftpFileSystemProvider.parseHostPort(uri))

        val defaultPortUri = VfsUri(
            rawString = "sftp://192.168.1.50/root",
            scheme = "sftp",
            authority = "192.168.1.50",
            path = "/root",
            rootType = GooglebookRootType.LINUX_VM
        )
        assertEquals("192.168.1.50" to 22, SftpFileSystemProvider.parseHostPort(defaultPortUri))

        val invalidUri = VfsUri.fromFile("/sdcard")
        assertNull(SftpFileSystemProvider.parseHostPort(invalidUri))
    }

    @Test
    fun testRemotePathHelpers() {
        assertEquals("/", SftpFileSystemProvider.normalizeRemotePath(""))
        assertEquals("/home/user", SftpFileSystemProvider.normalizeRemotePath("home/user"))
        assertEquals("/home/user/docs", SftpFileSystemProvider.joinRemotePath("/home/user/", "docs"))
        assertEquals("/docs", SftpFileSystemProvider.joinRemotePath("/", "docs"))
        assertEquals("/home/user", SftpFileSystemProvider.parentRemotePath("/home/user/docs"))
        assertEquals("/", SftpFileSystemProvider.parentRemotePath("/home"))
        assertEquals("/", SftpFileSystemProvider.parentRemotePath("/"))
    }
}
