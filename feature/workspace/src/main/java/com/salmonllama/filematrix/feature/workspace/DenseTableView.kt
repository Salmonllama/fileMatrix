package com.salmonllama.filematrix.feature.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salmonllama.filematrix.core.designsystem.components.ContextAction
import com.salmonllama.filematrix.core.designsystem.components.DesktopContextMenuContainer
import com.salmonllama.filematrix.core.designsystem.theme.DesktopDensity
import com.salmonllama.filematrix.core.model.VfsNode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DenseTableView(
    files: List<VfsNode>,
    selectedFiles: Set<VfsNode>,
    onToggleSelectFile: (VfsNode) -> Unit,
    onOpenFile: (VfsNode) -> Unit,
    onInspectChecksum: (VfsNode) -> Unit,
    onBatchRename: (List<VfsNode>) -> Unit,
    onDeleteFile: (VfsNode) -> Unit,
    onOpenInTerminal: (VfsNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var sortByNameAsc by remember { mutableStateOf(true) }

    val sortedFiles = remember(files, sortByNameAsc) {
        if (sortByNameAsc) {
            files.sortedWith(compareBy<VfsNode> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        } else {
            files.sortedWith(compareBy<VfsNode> { !it.isDirectory }.thenByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DesktopDensity.RowHeightDense)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(DesktopDensity.BorderWidth, MaterialTheme.colorScheme.outlineVariant)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(28.dp))
            Text(
                text = "Name ${if (sortByNameAsc) "▲" else "▼"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(3f)
                    .clickable { sortByNameAsc = !sortByNameAsc }
            )
            Text(
                text = "Size",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Modified",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1.5f)
            )
            Text(
                text = "Permissions / POSIX",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1.5f)
            )
        }

        // File Rows
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(sortedFiles, key = { it.id }) { node ->
                val isSelected = selectedFiles.contains(node)

                val contextActions = listOf(
                    ContextAction(
                        title = "Open",
                        onClick = { onOpenFile(node) }
                    ),
                    ContextAction(
                        title = "Open in Terminal",
                        icon = Icons.Default.Terminal,
                        shortcut = "Ctrl+`",
                        onClick = { onOpenInTerminal(node) }
                    ),
                    ContextAction(
                        title = "Inspect Checksums",
                        icon = Icons.Default.Fingerprint,
                        shortcut = "Alt+Enter",
                        onClick = { onInspectChecksum(node) }
                    ),
                    ContextAction(
                        title = "Batch Rename",
                        icon = Icons.Default.DriveFileRenameOutline,
                        shortcut = "Shift+F2",
                        onClick = {
                            val targets = if (selectedFiles.isNotEmpty()) selectedFiles.toList() else listOf(node)
                            onBatchRename(targets)
                        }
                    ),
                    ContextAction(
                        title = "Copy POSIX Path",
                        onClick = {
                            val path = node.targetPosixPath ?: node.uri.path
                            clipboardManager.setText(AnnotatedString(path))
                        }
                    ),
                    ContextAction(
                        title = "Delete",
                        icon = Icons.Default.Delete,
                        shortcut = "Del",
                        isDestructive = true,
                        onClick = { onDeleteFile(node) }
                    )
                )

                DesktopContextMenuContainer(
                    actions = contextActions,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(DesktopDensity.RowHeightDense)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else Color.Transparent
                            )
                            .clickable {
                                onToggleSelectFile(node)
                            }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelectFile(node) },
                            modifier = Modifier.size(20.dp),
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        Icon(
                            imageVector = if (node.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = if (node.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        // Name
                        Text(
                            text = node.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(3f)
                                .clickable { onOpenFile(node) }
                        )

                        // Size
                        Text(
                            text = if (node.isDirectory) "${node.childCount ?: "--"} items" else formatBytes(node.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )

                        // Modified Date
                        Text(
                            text = formatDate(node.lastModified),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.weight(1.5f)
                        )

                        // POSIX Permissions or path
                        Text(
                            text = node.permissionsPosix ?: node.targetPosixPath ?: "--",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1.5f)
                        )
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return "%.1f %s".format(bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatDate(epochMs: Long): String {
    if (epochMs <= 0) return "--"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMs))
}
