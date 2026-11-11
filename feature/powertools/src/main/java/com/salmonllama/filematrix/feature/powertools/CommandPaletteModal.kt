package com.salmonllama.filematrix.feature.powertools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.salmonllama.filematrix.core.designsystem.components.ShortcutBadge
import com.salmonllama.filematrix.core.designsystem.theme.DesktopDensity
import com.salmonllama.filematrix.core.model.GooglebookRootType
import com.salmonllama.filematrix.core.model.SearchHit
import com.salmonllama.filematrix.core.model.VfsNode
import com.salmonllama.filematrix.core.vfs.VirtualFileSystem
import kotlinx.coroutines.delay

data class PaletteAction(
    val title: String,
    val category: String,
    val shortcut: String? = null,
    val onExecute: () -> Unit
)

@Composable
fun CommandPaletteModal(
    vfs: VirtualFileSystem,
    availableActions: List<PaletteAction>,
    onSelectNode: (VfsNode) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var searchHits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    val isActionCommand = query.startsWith(">")

    // Federated search debouncer
    LaunchedEffect(query) {
        if (!isActionCommand && query.trim().length >= 2) {
            isSearching = true
            delay(150)
            searchHits = vfs.searchFederated(query.trim())
            isSearching = false
        } else {
            searchHits = emptyList()
            isSearching = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(620.dp)
                .border(
                    DesktopDensity.BorderWidth,
                    MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(DesktopDensity.CornerRadiusLarge)
                ),
            shape = RoundedCornerShape(DesktopDensity.CornerRadiusLarge),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Search Input Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(DesktopDensity.CornerRadiusMedium))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .border(
                            DesktopDensity.BorderWidth,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(DesktopDensity.CornerRadiusMedium)
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search files across all 5 roots or type '>' for commands...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Results Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                ) {
                    if (isActionCommand) {
                        val filteredActions = availableActions.filter {
                            val actionQuery = query.removePrefix(">").trim()
                            actionQuery.isEmpty() || it.title.contains(actionQuery, ignoreCase = true)
                        }
                        LazyColumn {
                            items(filteredActions) { action ->
                                ActionRow(action = action, onClick = {
                                    onDismiss()
                                    action.onExecute()
                                })
                            }
                        }
                    } else if (query.trim().length >= 2) {
                        if (searchHits.isEmpty() && !isSearching) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No files found across mounted roots.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn {
                                items(searchHits) { hit ->
                                    SearchHitRow(hit = hit, onClick = {
                                        onDismiss()
                                        onSelectNode(hit.node)
                                    })
                                }
                            }
                        }
                    } else {
                        // Quick hint commands
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Pro-Tips:",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Type '> Dual Pane' or press Ctrl+\\ to toggle dual-pane layout\n" +
                                        "• Type '> Terminal' or press Ctrl+` to open the Linux VM / Antigravity shell\n" +
                                        "• Press Ctrl+1/2/3 to switch between Dense Table, Miller Columns, and Grid\n" +
                                        "• Type any filename to search across Googlebook, Linux VM, Antigravity, and Drive",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Esc to close",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ShortcutBadge(shortcut = "Ctrl+K")
                }
            }
        }
    }
}

@Composable
private fun ActionRow(action: PaletteAction, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Terminal,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = action.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (action.shortcut != null) {
            ShortcutBadge(shortcut = action.shortcut)
        }
    }
}

@Composable
private fun SearchHitRow(hit: SearchHit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (hit.node.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = if (hit.node.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hit.node.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = hit.node.targetPosixPath ?: hit.node.uri.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        RootBadge(hit.node.uri.rootType)
    }
}

@Composable
fun RootBadge(rootType: GooglebookRootType) {
    val (label, color) = when (rootType) {
        GooglebookRootType.GOOGLEBOOK_LOCAL -> "Local" to Color(0xFF58A6FF)
        GooglebookRootType.LINUX_VM -> "Linux VM" to Color(0xFF00E676)
        GooglebookRootType.ANTIGRAVITY -> "Antigravity" to Color(0xFF00E5FF)
        GooglebookRootType.GOOGLE_DRIVE -> "Drive" to Color(0xFFFFB74D)
        GooglebookRootType.MOBILE_FILES -> "Mobile" to Color(0xFFE040FB)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = color
        )
    }
}
