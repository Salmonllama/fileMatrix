package com.salmonllama.filematrix.core.vfs

import com.salmonllama.filematrix.core.model.GooglebookRootType
import com.salmonllama.filematrix.core.model.PathMappingRule
import com.salmonllama.filematrix.core.model.VfsUri

class PathTranslationRegistry(
    private val rules: MutableList<PathMappingRule> = mutableListOf()
) {

    init {
        // Default Googlebook & Linux VM / Antigravity path translations
        rules.add(
            PathMappingRule(
                id = "rule_linux_vm_home",
                name = "Linux VM Home Directory",
                rootType = GooglebookRootType.LINUX_VM,
                safAuthority = "org.chromium.arc.chromecontentprovider",
                safPrefix = "LinuxFiles",
                posixPrefix = "/home"
            )
        )
        rules.add(
            PathMappingRule(
                id = "rule_antigravity_workspace",
                name = "Antigravity Workspace",
                rootType = GooglebookRootType.ANTIGRAVITY,
                safAuthority = "com.google.android.antigravity.documents",
                safPrefix = "workspace",
                posixPrefix = "/home/antigravity"
            )
        )
        rules.add(
            PathMappingRule(
                id = "rule_chromeos_shared",
                name = "ChromeOS Shared Mount",
                rootType = GooglebookRootType.GOOGLEBOOK_LOCAL,
                safAuthority = "com.android.externalstorage.documents",
                safPrefix = "primary:Download",
                posixPrefix = "/mnt/chromeos/MyFiles/Downloads"
            )
        )
    }

    fun toPosixPath(uri: VfsUri): String? {
        if (uri.isFileScheme) {
            return uri.path
        }
        if (uri.isSftpScheme) {
            return uri.path
        }

        // Check active rules for SAF content URIs
        for (rule in rules.filter { it.isEnabled && it.rootType == uri.rootType }) {
            if (uri.authority?.contains(rule.safAuthority) == true || uri.path.contains(rule.safPrefix)) {
                val relative = uri.path.substringAfter(rule.safPrefix).trimStart('/', ':')
                return if (relative.isEmpty()) rule.posixPrefix else "${rule.posixPrefix}/$relative"
            }
        }

        // Heuristic fallback for Linux VM / Antigravity
        return when (uri.rootType) {
            GooglebookRootType.ANTIGRAVITY -> {
                val sub = uri.path.substringAfterLast(":")
                "/home/antigravity/${sub.trimStart('/')}"
            }
            GooglebookRootType.LINUX_VM -> {
                val sub = uri.path.substringAfterLast(":")
                "/home/${sub.trimStart('/')}"
            }
            GooglebookRootType.GOOGLEBOOK_LOCAL -> {
                val sub = uri.path.substringAfterLast(":")
                "/sdcard/${sub.trimStart('/')}"
            }
            else -> null
        }
    }

    fun addRule(rule: PathMappingRule) {
        rules.removeAll { it.id == rule.id }
        rules.add(0, rule)
    }

    fun getRules(): List<PathMappingRule> = rules.toList()
}
