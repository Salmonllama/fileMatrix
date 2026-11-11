package com.salmonllama.filematrix.feature.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salmonllama.filematrix.core.designsystem.theme.DesktopDensity
import com.salmonllama.filematrix.core.model.VfsNode
import com.salmonllama.filematrix.core.model.VfsUri
import com.salmonllama.filematrix.core.vfs.VirtualFileSystem

data class PaneTab(
    val id: String,
    val title: String,
    val currentUri: VfsUri,
    val historyBack: List<VfsUri> = emptyList(),
    val historyForward: List<VfsUri> = emptyList(),
    val viewMode: FileViewMode = FileViewMode.DENSE_TABLE,
    val selectedNodes: Set<VfsNode> = emptySet()
)

@Composable
fun PaneView(
    tab: PaneTab,
    files: List<VfsNode>,
    vfs: VirtualFileSystem,
    isDualPane: Boolean,
    isActivePane: Boolean,
    onActivatePane: () -> Unit,
    onNavigateUri: (VfsUri) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateForward: () -> Unit,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onChangeViewMode: (FileViewMode) -> Unit,
    onToggleDualPane: () -> Unit,
    onToggleSelectFile: (VfsNode) -> Unit,
    onInspectChecksum: (VfsNode) -> Unit,
    onBatchRename: (List<VfsNode>) -> Unit,
    onDeleteFile: (VfsNode) -> Unit,
    onOpenInTerminal: (VfsNode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onActivatePane)
            .border(
                DesktopDensity.BorderWidth,
                if (isActivePane && isDualPane) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent
            )
    ) {
        // Breadcrumb Navigation Bar
        BreadcrumbBar(
            currentUri = tab.currentUri,
            viewMode = tab.viewMode,
            canGoBack = tab.historyBack.isNotEmpty(),
            canGoForward = tab.historyForward.isNotEmpty(),
            isDualPane = isDualPane,
            onNavigateBack = onNavigateBack,
            onNavigateForward = onNavigateForward,
            onNavigateUp = onNavigateUp,
            onRefresh = onRefresh,
            onNavigateToSegment = { segmentPath ->
                val newUri = tab.currentUri.copy(path = segmentPath)
                onNavigateUri(newUri)
            },
            onNavigateToRawPath = { raw ->
                val newUri = tab.currentUri.copy(path = raw)
                onNavigateUri(newUri)
            },
            onChangeViewMode = onChangeViewMode,
            onToggleDualPane = onToggleDualPane
        )

        // View Mode Canvas
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (tab.viewMode) {
                FileViewMode.DENSE_TABLE -> {
                    DenseTableView(
                        files = files,
                        selectedFiles = tab.selectedNodes,
                        onToggleSelectFile = onToggleSelectFile,
                        onOpenFile = { node ->
                            if (node.isDirectory) {
                                onNavigateUri(node.uri)
                            } else {
                                onInspectChecksum(node)
                            }
                        },
                        onInspectChecksum = onInspectChecksum,
                        onBatchRename = onBatchRename,
                        onDeleteFile = onDeleteFile,
                        onOpenInTerminal = onOpenInTerminal
                    )
                }
                FileViewMode.MILLER_COLUMNS -> {
                    MillerColumnsView(
                        rootFiles = files,
                        vfs = vfs,
                        onOpenFile = { node ->
                            if (node.isDirectory) {
                                onNavigateUri(node.uri)
                            } else {
                                onInspectChecksum(node)
                            }
                        },
                        onInspectChecksum = onInspectChecksum,
                        onOpenInTerminal = onOpenInTerminal
                    )
                }
                FileViewMode.ADAPTIVE_GRID -> {
                    AdaptiveGridView(
                        files = files,
                        selectedFiles = tab.selectedNodes,
                        onToggleSelectFile = onToggleSelectFile,
                        onOpenFile = { node ->
                            if (node.isDirectory) {
                                onNavigateUri(node.uri)
                            } else {
                                onInspectChecksum(node)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TabStrip(
    tabs: List<PaneTab>,
    activeTabId: String,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DesktopDensity.TabHeight)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val isSelected = tab.id == activeTabId
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.surfaceContainerLow
                        else Color.Transparent
                    )
                    .border(
                        DesktopDensity.BorderWidth,
                        if (isSelected) MaterialTheme.colorScheme.outlineVariant else Color.Transparent,
                        RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                    )
                    .clickable { onSelectTab(tab.id) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tab.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp
                )
                if (tabs.size > 1) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { onCloseTab(tab.id) },
                        modifier = Modifier.size(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Tab",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        IconButton(onClick = onNewTab, modifier = Modifier.size(22.dp)) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New Tab",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
