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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
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
    selectedRootType: GooglebookRootType?,
    onSelectRoot: (StorageRoot) -> Unit,
    onRequestMountRoot: (GooglebookRootType) -> Unit,
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
                Column {
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
            }

            Spacer(modifier = Modifier.height(8.dp))

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

            Spacer(modifier = Modifier.height(14.dp))

            // Googlebook Storage Roots Section
            Text(
                text = "STORAGE MATRIX",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Group roots or show 5 slots
                GooglebookRootType.entries.forEach { rootType ->
                    val mountedRoot = roots.find { it.rootType == rootType }
                    item {
                        RootItemCard(
                            rootType = rootType,
                            mountedRoot = mountedRoot,
                            isSelected = selectedRootType == rootType,
                            onClick = {
                                if (mountedRoot != null) {
                                    onSelectRoot(mountedRoot)
                                } else {
                                    onRequestMountRoot(rootType)
                                }
                            },
                            onMountClick = { onRequestMountRoot(rootType) }
                        )
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

@Composable
private fun RootItemCard(
    rootType: GooglebookRootType,
    mountedRoot: StorageRoot?,
    isSelected: Boolean,
    onClick: () -> Unit,
    onMountClick: () -> Unit
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
                    color = if (isMounted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
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
                        text = if (isMounted) "Mounted" else "Click to Mount",
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
                        contentDescription = "Mount",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
