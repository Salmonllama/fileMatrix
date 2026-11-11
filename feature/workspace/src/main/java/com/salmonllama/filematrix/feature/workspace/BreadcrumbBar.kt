package com.salmonllama.filematrix.feature.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salmonllama.filematrix.core.designsystem.theme.DesktopDensity
import com.salmonllama.filematrix.core.model.VfsUri

enum class FileViewMode {
    DENSE_TABLE,
    MILLER_COLUMNS,
    ADAPTIVE_GRID
}

@Composable
fun BreadcrumbBar(
    currentUri: VfsUri,
    viewMode: FileViewMode,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isDualPane: Boolean,
    onNavigateBack: () -> Unit,
    onNavigateForward: () -> Unit,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateToSegment: (String) -> Unit,
    onNavigateToRawPath: (String) -> Unit,
    onChangeViewMode: (FileViewMode) -> Unit,
    onToggleDualPane: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditingRawPath by remember { mutableStateOf(false) }
    var rawPathInput by remember(currentUri) { mutableStateOf(currentUri.path) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(DesktopDensity.BreadcrumbHeight)
            .border(DesktopDensity.BorderWidth, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // History Controls
            IconButton(
                onClick = onNavigateBack,
                enabled = canGoBack,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = onNavigateForward,
                enabled = canGoForward,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Forward",
                    tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = onNavigateUp,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = "Up",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Breadcrumb / Path Bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(DesktopDensity.BorderWidth, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                    .clickable { isEditingRawPath = true }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isEditingRawPath) {
                    BasicTextField(
                        value = rawPathInput,
                        onValueChange = { rawPathInput = it },
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentUri.rootType.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )

                        val segments = currentUri.path.split('/').filter { it.isNotBlank() }
                        segments.forEachIndexed { index, seg ->
                            Text(
                                text = seg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (index == segments.lastIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(2.dp))
                                    .clickable {
                                        val target = "/" + segments.take(index + 1).joinToString("/")
                                        onNavigateToSegment(target)
                                    }
                                    .padding(horizontal = 2.dp)
                            )
                            if (index < segments.lastIndex) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // View Mode Switchers
            ViewModeButton(
                icon = Icons.AutoMirrored.Filled.ViewList,
                isSelected = viewMode == FileViewMode.DENSE_TABLE,
                onClick = { onChangeViewMode(FileViewMode.DENSE_TABLE) }
            )
            ViewModeButton(
                icon = Icons.Default.ViewColumn,
                isSelected = viewMode == FileViewMode.MILLER_COLUMNS,
                onClick = { onChangeViewMode(FileViewMode.MILLER_COLUMNS) }
            )
            ViewModeButton(
                icon = Icons.Default.GridView,
                isSelected = viewMode == FileViewMode.ADAPTIVE_GRID,
                onClick = { onChangeViewMode(FileViewMode.ADAPTIVE_GRID) }
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Dual Pane Toggle
            IconButton(
                onClick = onToggleDualPane,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Splitscreen,
                    contentDescription = "Toggle Dual Pane",
                    tint = if (isDualPane) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ViewModeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
    }
}
