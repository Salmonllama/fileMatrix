package com.salmonllama.filematrix.feature.workspace

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salmonllama.filematrix.core.designsystem.theme.DesktopDensity
import com.salmonllama.filematrix.core.model.GooglebookRootType
import com.salmonllama.filematrix.core.model.StorageRoot
import com.salmonllama.filematrix.core.model.TransferStatus
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
    hasAllFilesAccess: Boolean,
    refreshSignal: Long,
    pendingNavigateRoot: StorageRoot?,
    onConsumedNavigateRoot: () -> Unit,
    statusBannerMessage: String?,
    onDismissStatusBanner: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onRequestSafPicker: (GooglebookRootType) -> Unit,
    onLaunchOAuthUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val roots by vfs.observeRoots().collectAsState(initial = emptyList())
    val transferJobs by transferManager.jobs.collectAsState()

    // Default starting Uri (Primary external storage root when All Files Access granted, else Downloads)
    val defaultUri = remember {
        val rootDir = Environment.getExternalStorageDirectory()
        val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        when {
            vfs.hasAllFilesAccess() && rootDir.exists() -> VfsUri.fromFile(rootDir.absolutePath)
            dl.exists() -> VfsUri.fromFile(dl.absolutePath)
            else -> VfsUri.fromFile(rootDir.absolutePath)
        }
    }

    // Left Pane Tabs
    val leftTabs = remember {
        mutableStateListOf(
            PaneTab(
                id = "tab_left_1",
                title = defaultUri.path.substringAfterLast('/').ifEmpty { "Storage" },
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
    var dismissPermissionBanner by remember { mutableStateOf(false) }

    // Terminal Drawer state
    var isTerminalOpen by remember { mutableStateOf(false) }
    var followActiveFolder by remember { mutableStateOf(true) }

    // Modals
    var showCommandPalette by remember { mutableStateOf(false) }
    var showTransferQueue by remember { mutableStateOf(false) }
    var mountDialogRootType by remember { mutableStateOf<GooglebookRootType?>(null) }
    var checksumInspectNode by remember { mutableStateOf<VfsNode?>(null) }
    var batchRenameTargets by remember { mutableStateOf<List<VfsNode>?>(null) }

    // File listings cache
    var leftFiles by remember { mutableStateOf<List<VfsNode>>(emptyList()) }
    var rightFiles by remember { mutableStateOf<List<VfsNode>>(emptyList()) }

    val activeLeftTab = leftTabs.find { it.id == activeLeftTabId } ?: leftTabs.first()
    val activeRightTab = rightTabs.find { it.id == activeRightTabId } ?: rightTabs.first()

    fun navigateActivePaneToRoot(root: StorageRoot) {
        val targetUri = root.toVfsUri()
        if (activePaneIndex == 0) {
            val idx = leftTabs.indexOfFirst { it.id == activeLeftTabId }
            if (idx != -1) {
                leftTabs[idx] = leftTabs[idx].copy(
                    currentUri = targetUri,
                    title = root.displayName,
                    historyBack = leftTabs[idx].historyBack + leftTabs[idx].currentUri,
                    historyForward = emptyList(),
                    selectedNodes = emptySet()
                )
            }
        } else {
            val idx = rightTabs.indexOfFirst { it.id == activeRightTabId }
            if (idx != -1) {
                rightTabs[idx] = rightTabs[idx].copy(
                    currentUri = targetUri,
                    title = root.displayName,
                    historyBack = rightTabs[idx].historyBack + rightTabs[idx].currentUri,
                    historyForward = emptyList(),
                    selectedNodes = emptySet()
                )
            }
        }
    }

    // Auto-navigate when a new root finishes mounting (OAuth callback / SAF / SFTP / Local)
    LaunchedEffect(pendingNavigateRoot) {
        if (pendingNavigateRoot != null) {
            navigateActivePaneToRoot(pendingNavigateRoot)
            onConsumedNavigateRoot()
        }
    }

    // Load Left Tab files
    LaunchedEffect(activeLeftTab.currentUri, refreshSignal, hasAllFilesAccess) {
        leftFiles = vfs.listChildren(activeLeftTab.currentUri)
    }

    // Load Right Tab files
    LaunchedEffect(activeRightTab.currentUri, isDualPane, refreshSignal, hasAllFilesAccess) {
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
        PaletteAction("Grant All Files Access (Full Drive Root)", "Storage") { onRequestAllFilesAccess() },
        PaletteAction("Refresh Local & USB Volumes", "Storage") { vfs.refreshLocalVolumes() },
        PaletteAction("Connect Linux VM (SFTP / Direct)", "Storage") { mountDialogRootType = GooglebookRootType.LINUX_VM },
        PaletteAction("Connect Antigravity Workspace (SFTP)", "Storage") { mountDialogRootType = GooglebookRootType.ANTIGRAVITY },
        PaletteAction("Connect Google Drive (API v3 Root)", "Storage") { mountDialogRootType = GooglebookRootType.GOOGLE_DRIVE },
        PaletteAction("Connect Mobile / USB Storage", "Storage") { mountDialogRootType = GooglebookRootType.MOBILE_FILES },
        PaletteAction("Show File Transfer Queue", "Transfers") { showTransferQueue = true }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Optional OAuth / Mount Status Banner
        statusBannerMessage?.let { message ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(DesktopDensity.BorderWidth, MaterialTheme.colorScheme.outlineVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFFB74D),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismissStatusBanner, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(14.dp))
                }
            }
        }

        // All Files Access Onboarding Banner (for direct drive root access without SAF)
        if (!hasAllFilesAccess && !dismissPermissionBanner) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1F2A38))
                    .border(DesktopDensity.BorderWidth, Color(0xFF58A6FF).copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = Color(0xFF58A6FF),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Grant All Files Access to browse full internal storage, Downloads, SD cards & USB drive roots directly — no native file picker needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onRequestAllFilesAccess,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F6FEB))
                ) {
                    Text("Enable All Files Access", fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = { dismissPermissionBanner = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(14.dp))
                }
            }
        }

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Sidebar with all auto-discovered and custom roots
            Sidebar(
                roots = roots,
                activeUri = activeTab.currentUri,
                hasAllFilesAccess = hasAllFilesAccess,
                onSelectRoot = { root -> navigateActivePaneToRoot(root) },
                onRequestMountRoot = { rootType -> mountDialogRootType = rootType },
                onUnmountRoot = { root ->
                    coroutineScope.launch {
                        vfs.unregisterRoot(root.id)
                    }
                },
                onRequestAllFilesAccess = onRequestAllFilesAccess,
                onRefreshVolumes = { vfs.refreshLocalVolumes() },
                onOpenCommandPalette = { showCommandPalette = true },
                onToggleTerminal = { isTerminalOpen = !isTerminalOpen },
                onOpenTransfers = { showTransferQueue = true },
                activeTransferCount = transferJobs.count { it.status == TransferStatus.RUNNING }
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
                                val name = newUri.path.trimEnd('/').substringAfterLast('/').ifEmpty { newUri.rootType.title }
                                leftTabs[idx] = leftTabs[idx].copy(
                                    currentUri = newUri,
                                    title = name,
                                    historyBack = leftTabs[idx].historyBack + leftTabs[idx].currentUri,
                                    historyForward = emptyList(),
                                    selectedNodes = emptySet()
                                )
                            }
                        },
                        onNavigateBack = {
                            val idx = leftTabs.indexOfFirst { it.id == activeLeftTabId }
                            if (idx != -1 && leftTabs[idx].historyBack.isNotEmpty()) {
                                val prev = leftTabs[idx].historyBack.last()
                                leftTabs[idx] = leftTabs[idx].copy(
                                    currentUri = prev,
                                    title = prev.path.trimEnd('/').substringAfterLast('/').ifEmpty { prev.rootType.title },
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
                                    title = next.path.trimEnd('/').substringAfterLast('/').ifEmpty { next.rootType.title },
                                    historyForward = leftTabs[idx].historyForward.dropLast(1),
                                    historyBack = leftTabs[idx].historyBack + leftTabs[idx].currentUri
                                )
                            }
                        },
                        onNavigateUp = {
                            val idx = leftTabs.indexOfFirst { it.id == activeLeftTabId }
                            if (idx != -1) {
                                val current = leftTabs[idx].currentUri
                                val parent = current.parentUri()
                                if (parent != null) {
                                    leftTabs[idx] = leftTabs[idx].copy(
                                        currentUri = parent,
                                        title = parent.path.trimEnd('/').substringAfterLast('/').ifEmpty { parent.rootType.title },
                                        historyBack = leftTabs[idx].historyBack + current
                                    )
                                } else if (leftTabs[idx].historyBack.isNotEmpty()) {
                                    val prev = leftTabs[idx].historyBack.last()
                                    leftTabs[idx] = leftTabs[idx].copy(
                                        currentUri = prev,
                                        title = prev.path.trimEnd('/').substringAfterLast('/').ifEmpty { prev.rootType.title },
                                        historyBack = leftTabs[idx].historyBack.dropLast(1),
                                        historyForward = leftTabs[idx].historyForward + current
                                    )
                                }
                            }
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
                                    val name = newUri.path.trimEnd('/').substringAfterLast('/').ifEmpty { newUri.rootType.title }
                                    rightTabs[idx] = rightTabs[idx].copy(
                                        currentUri = newUri,
                                        title = name,
                                        historyBack = rightTabs[idx].historyBack + rightTabs[idx].currentUri,
                                        historyForward = emptyList(),
                                        selectedNodes = emptySet()
                                    )
                                }
                            },
                            onNavigateBack = {
                                val idx = rightTabs.indexOfFirst { it.id == activeRightTabId }
                                if (idx != -1 && rightTabs[idx].historyBack.isNotEmpty()) {
                                    val prev = rightTabs[idx].historyBack.last()
                                    rightTabs[idx] = rightTabs[idx].copy(
                                        currentUri = prev,
                                        title = prev.path.trimEnd('/').substringAfterLast('/').ifEmpty { prev.rootType.title },
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
                                        title = next.path.trimEnd('/').substringAfterLast('/').ifEmpty { next.rootType.title },
                                        historyForward = rightTabs[idx].historyForward.dropLast(1),
                                        historyBack = rightTabs[idx].historyBack + rightTabs[idx].currentUri
                                    )
                                }
                            },
                            onNavigateUp = {
                                val idx = rightTabs.indexOfFirst { it.id == activeRightTabId }
                                if (idx != -1) {
                                    val current = rightTabs[idx].currentUri
                                    val parent = current.parentUri()
                                    if (parent != null) {
                                        rightTabs[idx] = rightTabs[idx].copy(
                                            currentUri = parent,
                                            title = parent.path.trimEnd('/').substringAfterLast('/').ifEmpty { parent.rootType.title },
                                            historyBack = rightTabs[idx].historyBack + current
                                        )
                                    } else if (rightTabs[idx].historyBack.isNotEmpty()) {
                                        val prev = rightTabs[idx].historyBack.last()
                                        rightTabs[idx] = rightTabs[idx].copy(
                                            currentUri = prev,
                                            title = prev.path.trimEnd('/').substringAfterLast('/').ifEmpty { prev.rootType.title },
                                            historyBack = rightTabs[idx].historyBack.dropLast(1),
                                            historyForward = rightTabs[idx].historyForward + current
                                        )
                                    }
                                }
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

    // Guided Storage Mount Dialog (SFTP, Direct Root, Google Drive API v3, SAF fallback)
    mountDialogRootType?.let { targetType ->
        StorageMountDialog(
            rootType = targetType,
            vfs = vfs,
            hasAllFilesAccess = hasAllFilesAccess,
            onRequestAllFilesAccess = onRequestAllFilesAccess,
            onRequestSafPicker = onRequestSafPicker,
            onLaunchOAuthUrl = onLaunchOAuthUrl,
            onRootMounted = { mountedRoot ->
                navigateActivePaneToRoot(mountedRoot)
            },
            onDismiss = { mountDialogRootType = null }
        )
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
