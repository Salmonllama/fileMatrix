package com.salmonllama.filematrix.feature.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.salmonllama.filematrix.core.designsystem.components.ShortcutBadge
import com.salmonllama.filematrix.core.designsystem.theme.DesktopDensity
import com.salmonllama.filematrix.core.model.GooglebookRootType
import com.salmonllama.filematrix.core.model.StorageRoot
import com.salmonllama.filematrix.core.model.VfsUri

@Composable
fun Sidebar(
    roots: List<StorageRoot>,
    activeUri: VfsUri,
    hasAllFilesAccess: Boolean,
    onSelectRoot: (StorageRoot) -> Unit,
    onRequestMountRoot: (GooglebookRootType) -> Unit,
    onUnmountRoot: (StorageRoot) -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onRefreshVolumes: () -> Unit,
    onOpenCommandPalette: () -> Unit,
    onToggleTerminal: () -> Unit,
    onOpenTransfers: () -> Unit,
    activeTransferCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(DesktopDensity.SidebarWidth)
            .fillMaxHeight()
            .border(
                DesktopDensity.BorderWidth,
                MaterialTheme.colorScheme.outlineVariant
            ),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(8.dp)
        ) {
            // App Brand Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "M",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "fileMatrix",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Googlebook Edition",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onRefreshVolumes,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Storage Volumes",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Search / Command Palette Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(
                        DesktopDensity.BorderWidth,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable(onClick = onOpenCommandPalette)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Command Palette...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                ShortcutBadge(shortcut = "Ctrl+K")
            }

            if (!hasAllFilesAccess) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFFFB74D).copy(alpha = 0.14f))
                        .border(
                            DesktopDensity.BorderWidth,
                            Color(0xFFFFB74D).copy(alpha = 0.5f),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable(onClick = onRequestAllFilesAccess)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Unlock Full Drive Root",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFB74D)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Googlebook Storage Roots Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STORAGE MATRIX",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                GooglebookRootType.entries.forEach { rootType ->
                    val matchingRoots = roots.filter { it.rootType == rootType }
                    if (matchingRoots.isEmpty()) {
                        item(key = "unmounted_${rootType.name}") {
                            RootItemCard(
                                rootType = rootType,
                                mountedRoot = null,
                                isSelected = false,
                                onClick = { onRequestMountRoot(rootType) },
                                onMountClick = { onRequestMountRoot(rootType) },
                                onUnmountClick = null
                            )
                        }
                    } else {
                        items(matchingRoots, key = { it.id }) { mounted ->
                            val isSelected = isRootActive(mounted, activeUri)
                            RootItemCard(
                                rootType = rootType,
                                mountedRoot = mounted,
                                isSelected = isSelected,
                                onClick = { onSelectRoot(mounted) },
                                onMountClick = { onRequestMountRoot(rootType) },
                                onUnmountClick = if (!mounted.isAutoDiscovered) {
                                    { onUnmountRoot(mounted) }
                                } else null
                            )
                        }
                    }
                }
            }

            // Bottom Quick Drawer Actions
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Terminal Drawer Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .border(DesktopDensity.BorderWidth, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                        .clickable(onClick = onToggleTerminal)
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Terminal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Transfers Queue Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .border(DesktopDensity.BorderWidth, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                        .clickable(onClick = onOpenTransfers)
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = if (activeTransferCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (activeTransferCount > 0) "$activeTransferCount Jobs" else "Transfers",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private fun isRootActive(root: StorageRoot, activeUri: VfsUri): Boolean {
    if (root.rootType != activeUri.rootType) return false
    return when {
        root.baseUri.startsWith("file://") && activeUri.isFileScheme -> {
            val basePath = root.baseUri.removePrefix("file://").trimEnd('/')
            val curPath = activeUri.path.trimEnd('/')
            curPath == basePath || curPath.startsWith("$basePath/")
        }
        root.baseUri.startsWith("sftp://") && activeUri.isSftpScheme -> {
            root.authority == activeUri.authority
        }
        root.baseUri.startsWith("gdrive://") && activeUri.isGoogleDriveScheme -> true
        else -> activeUri.rawString.startsWith(root.baseUri)
    }
}

private fun protocolTag(root: StorageRoot): String {
    return when {
        root.baseUri.startsWith("file://") -> "LOCAL"
        root.baseUri.startsWith("sftp://") -> "SFTP"
        root.baseUri.startsWith("gdrive://") -> "DRIVE API"
        else -> "SAF"
    }
}

@Composable
private fun RootItemCard(
    rootType: GooglebookRootType,
    mountedRoot: StorageRoot?,
    isSelected: Boolean,
    onClick: () -> Unit,
    onMountClick: () -> Unit,
    onUnmountClick: (() -> Unit)?
) {
    val (icon, tint) = when (rootType) {
        GooglebookRootType.GOOGLEBOOK_LOCAL -> Icons.Default.Laptop to Color(0xFF58A6FF)
        GooglebookRootType.LINUX_VM -> Icons.Default.Computer to Color(0xFF00E676)
        GooglebookRootType.ANTIGRAVITY -> Icons.Default.RocketLaunch to Color(0xFF00E5FF)
        GooglebookRootType.GOOGLE_DRIVE -> Icons.Default.Cloud to Color(0xFFFFB74D)
        GooglebookRootType.MOBILE_FILES -> Icons.Default.PhoneAndroid to Color(0xFFE040FB)
    }

    val isMounted = mountedRoot != null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent)
            .border(
                DesktopDensity.BorderWidth,
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isMounted) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mountedRoot?.displayName ?: rootType.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isMounted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val totalBytes = mountedRoot?.quotaTotalBytes
                val usedBytes = mountedRoot?.quotaUsedBytes
                if (isMounted && totalBytes != null && usedBytes != null && totalBytes > 0) {
                    val progress = (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = tint,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                } else {
                    Text(
                        text = if (isMounted) "${protocolTag(mountedRoot!!)} • Mounted" else "Click to Connect",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = if (isMounted) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isMounted) {
                IconButton(onClick = onMountClick, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Connect",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMountClick, modifier = Modifier.size(20.dp)) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Mount",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    if (onUnmountClick != null) {
                        IconButton(onClick = onUnmountClick, modifier = Modifier.size(20.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Unmount",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
