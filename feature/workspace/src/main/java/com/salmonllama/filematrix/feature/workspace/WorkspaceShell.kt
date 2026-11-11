package com.salmonllama.filematrix.feature.workspace

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.salmonllama.filematrix.core.designsystem.theme.DesktopDensity
import com.salmonllama.filematrix.core.model.GooglebookRootType
import com.salmonllama.filematrix.core.model.StorageRoot
import com.salmonllama.filematrix.core.model.VfsNode
import com.salmonllama.filematrix.core.model.VfsUri
import com.salmonllama.filematrix.core.terminal.TerminalDrawer
import com.salmonllama.filematrix.core.terminal.TerminalSessionManager
import com.salmonllama.filematrix.core.vfs.TransferQueueManager
import com.salmonllama.filematrix.core.vfs.VirtualFileSystem
import com.salmonllama.filematrix.feature.powertools.BatchRenameModal
import com.salmonllama.filematrix.feature.powertools.ChecksumInspectorDialog
import com.salmonllama.filematrix.feature.powertools.CommandPaletteModal
import com.salmonllama.filematrix.feature.powertools.PaletteAction
import com.salmonllama.filematrix.feature.powertools.TransferQueueModal
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun WorkspaceShell(
    vfs: VirtualFileSystem,
    transferManager: TransferQueueManager,
    sessionManager: TerminalSessionManager,
    onRequestMountRoot: (GooglebookRootType) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val roots by vfs.observeRoots().collectAsState(initial = emptyList())
    val transferJobs by transferManager.jobs.collectAsState()

    // Default starting Uri (Local Download or External Storage)
    val defaultUri = remember {
        val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (dl.exists()) VfsUri.fromFile(dl.absolutePath)
        else VfsUri.fromFile(Environment.getExternalStorageDirectory().absolutePath)
    }

    // Left Pane Tabs
    val leftTabs = remember {
        mutableStateListOf(
            PaneTab(
                id = "tab_left_1",
                title = "Downloads",
                currentUri = defaultUri
            )
        )
    }
    var activeLeftTabId by remember { mutableStateOf("tab_left_1") }

    // Right Pane Tabs (for Dual Pane)
    val rightTabs = remember {
        mutableStateListOf(
            PaneTab(
                id = "tab_right_1",
                title = "Workspace",
                currentUri = defaultUri
            )
        )
    }
    var activeRightTabId by remember { mutableStateOf("tab_right_1") }

    var isDualPane by remember { mutableStateOf(false) }
    var activePaneIndex by remember { mutableStateOf(0) } // 0: Left, 1: Right

    // Terminal Drawer state
    var isTerminalOpen by remember { mutableStateOf(false) }
    var followActiveFolder by remember { mutableStateOf(true) }

    // Modals
    var showCommandPalette by remember { mutableStateOf(false) }
    var showTransferQueue by remember { mutableStateOf(false) }
    var checksumInspectNode by remember { mutableStateOf<VfsNode?>(null) }
    var batchRenameTargets by remember { mutableStateOf<List<VfsNode>?>(null) }

    // File listings cache
    var leftFiles by remember { mutableStateOf<List<VfsNode>>(emptyList()) }
    var rightFiles by remember { mutableStateOf<List<VfsNode>>(emptyList()) }

    val activeLeftTab = leftTabs.find { it.id == activeLeftTabId } ?: leftTabs.first()
    val activeRightTab = rightTabs.find { it.id == activeRightTabId } ?: rightTabs.first()

    // Load Left Tab files
    LaunchedEffect(activeLeftTab.currentUri) {
        leftFiles = vfs.listChildren(activeLeftTab.currentUri)
    }

    // Load Right Tab files
    LaunchedEffect(activeRightTab.currentUri, isDualPane) {
        if (isDualPane) {
            rightFiles = vfs.listChildren(activeRightTab.currentUri)
        }
    }

    val activeTab = if (activePaneIndex == 0) activeLeftTab else activeRightTab
    val activePosixPath = activeTab.currentUri.path

    val availableActions = listOf(
        PaletteAction("Toggle Dual-Pane Split", "View", "Ctrl+\\") { isDualPane = !isDualPane },
        PaletteAction("Toggle Terminal Drawer", "Terminal", "Ctrl+`") { isTerminalOpen = !isTerminalOpen },
        PaletteAction("Switch View: Dense Table", "View", "Ctrl+1") {
            if (activePaneIndex == 0) {
                val idx = leftTabs.indexOfFirst { it.id == activeLeftTabId }
                if (idx != -1) leftTabs[idx] = leftTabs[idx].copy(viewMode = FileViewMode.DENSE_TABLE)
            } else {
                val idx = rightTabs.indexOfFirst { it.id == activeRightTabId }
                if (idx != -1) rightTabs[idx] = rightTabs[idx].copy(viewMode = FileViewMode.DENSE_TABLE)
            }
        },
        PaletteAction("Switch View: Miller Columns", "View", "Ctrl+2") {
            if (activePaneIndex == 0) {
                val idx = leftTabs.indexOfFirst { it.id == activeLeftTabId }
                if (idx != -1) leftTabs[idx] = leftTabs[idx].copy(viewMode = FileViewMode.MILLER_COLUMNS)
            } else {
                val idx = rightTabs.indexOfFirst { it.id == activeRightTabId }
                if (idx != -1) rightTabs[idx] = rightTabs[idx].copy(viewMode = FileViewMode.MILLER_COLUMNS)
            }
        },
        PaletteAction("Switch View: Adaptive Grid", "View", "Ctrl+3") {
            if (activePaneIndex == 0) {
                val idx = leftTabs.indexOfFirst { it.id == activeLeftTabId }
                if (idx != -1) leftTabs[idx] = leftTabs[idx].copy(viewMode = FileViewMode.ADAPTIVE_GRID)
            } else {
                val idx = rightTabs.indexOfFirst { it.id == activeRightTabId }
                if (idx != -1) rightTabs[idx] = rightTabs[idx].copy(viewMode = FileViewMode.ADAPTIVE_GRID)
            }
        },
        PaletteAction("Mount Linux VM", "Storage") { onRequestMountRoot(GooglebookRootType.LINUX_VM) },
        PaletteAction("Mount Antigravity Workspace", "Storage") { onRequestMountRoot(GooglebookRootType.ANTIGRAVITY) },
        PaletteAction("Mount Google Drive", "Storage") { onRequestMountRoot(GooglebookRootType.GOOGLE_DRIVE) },
        PaletteAction("Mount Mobile Files", "Storage") { onRequestMountRoot(GooglebookRootType.MOBILE_FILES) },
        PaletteAction("Show File Transfer Queue", "Transfers") { showTransferQueue = true }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Sidebar with 5-root cards
            Sidebar(
                roots = roots,
                selectedRootType = activeTab.currentUri.rootType,
                onSelectRoot = { root ->
                    val uri = if (root.rootType == GooglebookRootType.GOOGLEBOOK_LOCAL) {
                        VfsUri.fromFile(root.baseUri.removePrefix("file://"))
                    } else {
                        VfsUri.fromContent(root.baseUri, root.rootType, root.authority, root.baseUri)
                    }
                    if (activePaneIndex == 0) {
                        val idx = leftTabs.indexOfFirst { it.id == activeLeftTabId }
                        if (idx != -1) leftTabs[idx] = leftTabs[idx].copy(currentUri = uri, title = root.displayName)
                    } else {
                        val idx = rightTabs.indexOfFirst { it.id == activeRightTabId }
                        if (idx != -1) rightTabs[idx] = rightTabs[idx].copy(currentUri = uri, title = root.displayName)
                    }
                },
                onRequestMountRoot = onRequestMountRoot,
                onOpenCommandPalette = { showCommandPalette = true },
                onToggleTerminal = { isTerminalOpen = !isTerminalOpen },
                onOpenTransfers = { showTransferQueue = true },
                activeTransferCount = transferJobs.count { it.status == com.salmonllama.filematrix.core.model.TransferStatus.RUNNING }
            )

            // Main Dual-Pane Workspace Area
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Tab Strips
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        TabStrip(
                            tabs = leftTabs,
                            activeTabId = activeLeftTabId,
                            onSelectTab = { activeLeftTabId = it; activePaneIndex = 0 },
                            onCloseTab = { id ->
                                if (leftTabs.size > 1) {
                                    leftTabs.removeAll { it.id == id }
                                    if (activeLeftTabId == id) activeLeftTabId = leftTabs.first().id
                                }
                            },
                            onNewTab = {
                                val newId = "tab_left_${System.currentTimeMillis()}"
                                leftTabs.add(PaneTab(newId, "New Tab", defaultUri))
                                activeLeftTabId = newId
                                activePaneIndex = 0
                            }
                        )
                    }

                    if (isDualPane) {
                        Box(modifier = Modifier.weight(1f)) {
                            TabStrip(
                                tabs = rightTabs,
                                activeTabId = activeRightTabId,
                                onSelectTab = { activeRightTabId = it; activePaneIndex = 1 },
                                onCloseTab = { id ->
                                    if (rightTabs.size > 1) {
                                        rightTabs.removeAll { it.id == id }
                                        if (activeRightTabId == id) activeRightTabId = rightTabs.first().id
                                    }
                                },
                                onNewTab = {
                                    val newId = "tab_right_${System.currentTimeMillis()}"
                                    rightTabs.add(PaneTab(newId, "New Tab", defaultUri))
                                    activeRightTabId = newId
                                    activePaneIndex = 1
                                }
                            )
                        }
                    }
                }

                // File Panes
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    PaneView(
                        tab = activeLeftTab,
                        files = leftFiles,
                        vfs = vfs,
                        isDualPane = isDualPane,
                        isActivePane = activePaneIndex == 0,
                        onActivatePane = { activePaneIndex = 0 },
                        onNavigateUri = { newUri ->
                            val idx = leftTabs.indexOfFirst { it.id == activeLeftTabId }
                            if (idx != -1) {
                                val name = newUri.path.substringAfterLast('/').ifEmpty { newUri.rootType.title }
                                leftTabs[idx] = leftTabs[idx].copy(
                                    currentUri = newUri,
                                    title = name,
                                    historyBack = leftTabs[idx].historyBack + leftTabs[idx].currentUri
                                )
                            }
                        },
                        onNavigateBack = {
                            val idx = leftTabs.indexOfFirst { it.id == activeLeftTabId }
                            if (idx != -1 && leftTabs[idx].historyBack.isNotEmpty()) {
                                val prev = leftTabs[idx].historyBack.last()
                                leftTabs[idx] = leftTabs[idx].copy(
                                    currentUri = prev,
                                    historyBack = leftTabs[idx].historyBack.dropLast(1),
                                    historyForward = leftTabs[idx].historyForward + leftTabs[idx].currentUri
                                )
                            }
                        },
                        onNavigateForward = {
                            val idx = leftTabs.indexOfFirst { it.id == activeLeftTabId }
                            if (idx != -1 && leftTabs[idx].historyForward.isNotEmpty()) {
                                val next = leftTabs[idx].historyForward.last()
                                leftTabs[idx] = leftTabs[idx].copy(
                                    currentUri = next,
                                    historyForward = leftTabs[idx].historyForward.dropLast(1),
                                    historyBack = leftTabs[idx].historyBack + leftTabs[idx].currentUri
                                )
                            }
                        },
                        onNavigateUp = {
                            val parentPath = File(activeLeftTab.currentUri.path).parent ?: "/"
                            val newUri = activeLeftTab.currentUri.copy(path = parentPath)
                            val idx = leftTabs.indexOfFirst { it.id == activeLeftTabId }
                            if (idx != -1) leftTabs[idx] = leftTabs[idx].copy(currentUri = newUri)
                        },
                        onRefresh = {
                            coroutineScope.launch {
                                leftFiles = vfs.listChildren(activeLeftTab.currentUri)
                            }
                        },
                        onChangeViewMode = { mode ->
                            val idx = leftTabs.indexOfFirst { it.id == activeLeftTabId }
                            if (idx != -1) leftTabs[idx] = leftTabs[idx].copy(viewMode = mode)
                        },
                        onToggleDualPane = { isDualPane = !isDualPane },
                        onToggleSelectFile = { node ->
                            val idx = leftTabs.indexOfFirst { it.id == activeLeftTabId }
                            if (idx != -1) {
                                val current = leftTabs[idx].selectedNodes
                                val updated = if (current.contains(node)) current - node else current + node
                                leftTabs[idx] = leftTabs[idx].copy(selectedNodes = updated)
                            }
                        },
                        onInspectChecksum = { checksumInspectNode = it },
                        onBatchRename = { batchRenameTargets = it },
                        onDeleteFile = { node ->
                            coroutineScope.launch {
                                vfs.delete(node.uri)
                                leftFiles = vfs.listChildren(activeLeftTab.currentUri)
                            }
                        },
                        onOpenInTerminal = { node ->
                            isTerminalOpen = true
                            node.targetPosixPath?.let { path ->
                                sessionManager.activeSessionId.value?.let { sess ->
                                    sessionManager.cdToDirectory(sess, if (node.isDirectory) path else File(path).parent ?: path)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    if (isDualPane) {
                        Spacer(modifier = Modifier.width(DesktopDensity.SplitDividerWidth).background(MaterialTheme.colorScheme.outlineVariant))

                        PaneView(
                            tab = activeRightTab,
                            files = rightFiles,
                            vfs = vfs,
                            isDualPane = isDualPane,
                            isActivePane = activePaneIndex == 1,
                            onActivatePane = { activePaneIndex = 1 },
                            onNavigateUri = { newUri ->
                                val idx = rightTabs.indexOfFirst { it.id == activeRightTabId }
                                if (idx != -1) {
                                    val name = newUri.path.substringAfterLast('/').ifEmpty { newUri.rootType.title }
                                    rightTabs[idx] = rightTabs[idx].copy(
                                        currentUri = newUri,
                                        title = name,
                                        historyBack = rightTabs[idx].historyBack + rightTabs[idx].currentUri
                                    )
                                }
                            },
                            onNavigateBack = {
                                val idx = rightTabs.indexOfFirst { it.id == activeRightTabId }
                                if (idx != -1 && rightTabs[idx].historyBack.isNotEmpty()) {
                                    val prev = rightTabs[idx].historyBack.last()
                                    rightTabs[idx] = rightTabs[idx].copy(
                                        currentUri = prev,
                                        historyBack = rightTabs[idx].historyBack.dropLast(1),
                                        historyForward = rightTabs[idx].historyForward + rightTabs[idx].currentUri
                                    )
                                }
                            },
                            onNavigateForward = {
                                val idx = rightTabs.indexOfFirst { it.id == activeRightTabId }
                                if (idx != -1 && rightTabs[idx].historyForward.isNotEmpty()) {
                                    val next = rightTabs[idx].historyForward.last()
                                    rightTabs[idx] = rightTabs[idx].copy(
                                        currentUri = next,
                                        historyForward = rightTabs[idx].historyForward.dropLast(1),
                                        historyBack = rightTabs[idx].historyBack + rightTabs[idx].currentUri
                                    )
                                }
                            },
                            onNavigateUp = {
                                val parentPath = File(activeRightTab.currentUri.path).parent ?: "/"
                                val newUri = activeRightTab.currentUri.copy(path = parentPath)
                                val idx = rightTabs.indexOfFirst { it.id == activeRightTabId }
                                if (idx != -1) rightTabs[idx] = rightTabs[idx].copy(currentUri = newUri)
                            },
                            onRefresh = {
                                coroutineScope.launch {
                                    rightFiles = vfs.listChildren(activeRightTab.currentUri)
                                }
                            },
                            onChangeViewMode = { mode ->
                                val idx = rightTabs.indexOfFirst { it.id == activeRightTabId }
                                if (idx != -1) rightTabs[idx] = rightTabs[idx].copy(viewMode = mode)
                            },
                            onToggleDualPane = { isDualPane = !isDualPane },
                            onToggleSelectFile = { node ->
                                val idx = rightTabs.indexOfFirst { it.id == activeRightTabId }
                                if (idx != -1) {
                                    val current = rightTabs[idx].selectedNodes
                                    val updated = if (current.contains(node)) current - node else current + node
                                    rightTabs[idx] = rightTabs[idx].copy(selectedNodes = updated)
                                }
                            },
                            onInspectChecksum = { checksumInspectNode = it },
                            onBatchRename = { batchRenameTargets = it },
                            onDeleteFile = { node ->
                                coroutineScope.launch {
                                    vfs.delete(node.uri)
                                    rightFiles = vfs.listChildren(activeRightTab.currentUri)
                                }
                            },
                            onOpenInTerminal = { node ->
                                isTerminalOpen = true
                                node.targetPosixPath?.let { path ->
                                    sessionManager.activeSessionId.value?.let { sess ->
                                        sessionManager.cdToDirectory(sess, if (node.isDirectory) path else File(path).parent ?: path)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Terminal Drawer Dock
        if (isTerminalOpen) {
            TerminalDrawer(
                sessionManager = sessionManager,
                currentPosixPath = activePosixPath,
                followActivePane = followActiveFolder,
                onToggleFollowActivePane = { followActiveFolder = it },
                onClose = { isTerminalOpen = false }
            )
        }
    }

    // Command Palette Modal
    if (showCommandPalette) {
        CommandPaletteModal(
            vfs = vfs,
            availableActions = availableActions,
            onSelectNode = { node ->
                if (node.isDirectory) {
                    val idx = leftTabs.indexOfFirst { it.id == activeLeftTabId }
                    if (idx != -1) leftTabs[idx] = leftTabs[idx].copy(currentUri = node.uri, title = node.name)
                } else {
                    checksumInspectNode = node
                }
            },
            onDismiss = { showCommandPalette = false }
        )
    }

    // Transfer Queue Modal
    if (showTransferQueue) {
        TransferQueueModal(
            transferManager = transferManager,
            onDismiss = { showTransferQueue = false }
        )
    }

    // Checksum Inspector Dialog
    checksumInspectNode?.let { node ->
        ChecksumInspectorDialog(
            fileNode = node,
            vfs = vfs,
            onDismiss = { checksumInspectNode = null }
        )
    }

    // Batch Rename Modal
    batchRenameTargets?.let { targets ->
        BatchRenameModal(
            selectedFiles = targets,
            onApply = { renamePlan ->
                coroutineScope.launch {
                    renamePlan.forEach { (node, newName) ->
                        if (node.name != newName) {
                            try {
                                vfs.rename(node.uri, newName)
                            } catch (_: Exception) {}
                        }
                    }
                    leftFiles = vfs.listChildren(activeLeftTab.currentUri)
                    if (isDualPane) rightFiles = vfs.listChildren(activeRightTab.currentUri)
                    batchRenameTargets = null
                }
            },
            onDismiss = { batchRenameTargets = null }
        )
    }
}
