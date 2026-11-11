package com.salmonllama.filematrix.core.vfs

import com.salmonllama.filematrix.core.model.GooglebookRootType
import com.salmonllama.filematrix.core.model.PathMappingRule
import com.salmonllama.filematrix.core.model.VfsUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class PathTranslationRegistryTest {

    private lateinit var registry: PathTranslationRegistry

    @Before
    fun setup() {
        registry = PathTranslationRegistry()
    }

    @Test
    fun testFileSchemePassThrough() {
        val uri = VfsUri.fromFile("/sdcard/Download/test.zip")
        val posix = registry.toPosixPath(uri)
        assertEquals("/sdcard/Download/test.zip", posix)
    }

    @Test
    fun testSftpSchemePassThrough() {
        val uri = VfsUri.fromSftp("127.0.0.1", 2222, "/home/antigravity/project", GooglebookRootType.ANTIGRAVITY)
        val posix = registry.toPosixPath(uri)
        assertEquals("/home/antigravity/project", posix)
    }

    @Test
    fun testAntigravitySafTranslation() {
        val uri = VfsUri.fromContent(
            contentUri = "content://com.google.android.antigravity.documents/tree/workspace/document/workspace:src/main.rs",
            rootType = GooglebookRootType.ANTIGRAVITY,
            authority = "com.google.android.antigravity.documents",
            documentPath = "workspace:src/main.rs"
        )
        val posix = registry.toPosixPath(uri)
        assertNotNull(posix)
        assertEquals("/home/antigravity/src/main.rs", posix)
    }

    @Test
    fun testLinuxVmSafTranslation() {
        val uri = VfsUri.fromContent(
            contentUri = "content://org.chromium.arc.chromecontentprovider/tree/LinuxFiles/document/LinuxFiles:mycode/main.py",
            rootType = GooglebookRootType.LINUX_VM,
            authority = "org.chromium.arc.chromecontentprovider",
            documentPath = "LinuxFiles:mycode/main.py"
        )
        val posix = registry.toPosixPath(uri)
        assertNotNull(posix)
        assertEquals("/home/mycode/main.py", posix)
    }

    @Test
    fun testCustomRuleAddition() {
        val customRule = PathMappingRule(
            id = "custom_crostini",
            name = "Custom Crostini Mount",
            rootType = GooglebookRootType.LINUX_VM,
            safAuthority = "custom.authority",
            safPrefix = "root",
            posixPrefix = "/mnt/shared/crostini"
        )
        registry.addRule(customRule)

        val uri = VfsUri.fromContent(
            contentUri = "content://custom.authority/tree/root/document/root:docs/notes.txt",
            rootType = GooglebookRootType.LINUX_VM,
            authority = "custom.authority",
            documentPath = "root:docs/notes.txt"
        )
        val posix = registry.toPosixPath(uri)
        assertEquals("/mnt/shared/crostini/docs/notes.txt", posix)
    }
}
