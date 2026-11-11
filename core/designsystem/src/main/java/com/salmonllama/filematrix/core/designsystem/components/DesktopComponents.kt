package com.salmonllama.filematrix.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.salmonllama.filematrix.core.designsystem.theme.DesktopDensity

@Composable
fun ShortcutBadge(
    shortcut: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f))
            .border(
                DesktopDensity.BorderWidth,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = shortcut,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

data class ContextAction(
    val title: String,
    val icon: ImageVector? = null,
    val shortcut: String? = null,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun DesktopContextMenuContainer(
    modifier: Modifier = Modifier,
    actions: List<ContextAction>,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var touchOffset by remember { mutableStateOf(DpOffset.Zero) }

    Box(
        modifier = modifier.pointerInput(actions) {
            detectTapGestures(
                onTap = {},
                onLongPress = { offset ->
                    touchOffset = DpOffset(offset.x.toDp(), offset.y.toDp())
                    expanded = true
                }
            )
        }
    ) {
        content()

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = touchOffset,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(
                    DesktopDensity.BorderWidth,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    RoundedCornerShape(DesktopDensity.CornerRadiusMedium)
                )
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = action.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (action.isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (action.shortcut != null) {
                                ShortcutBadge(
                                    shortcut = action.shortcut,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                    },
                    leadingIcon = action.icon?.let { icon ->
                        {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (action.isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        action.onClick()
                    }
                )
            }
        }
    }
}

@Composable
fun Modifier.desktopHover(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    hoverColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
): Modifier {
    val isHovered by interactionSource.collectIsHoveredAsState()
    return this
        .hoverable(interactionSource)
        .then(if (isHovered) Modifier.background(hoverColor) else Modifier)
}
