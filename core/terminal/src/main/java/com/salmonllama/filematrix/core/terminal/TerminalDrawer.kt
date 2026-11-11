package com.salmonllama.filematrix.core.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salmonllama.filematrix.core.designsystem.components.ShortcutBadge
import com.salmonllama.filematrix.core.designsystem.theme.DesktopDensity

@Composable
fun TerminalDrawer(
    sessionManager: TerminalSessionManager,
    currentPosixPath: String?,
    followActivePane: Boolean,
    onToggleFollowActivePane: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sessions by sessionManager.sessions.collectAsState()
    val activeSessionId by sessionManager.activeSessionId.collectAsState()
    val outputMap by sessionManager.sessionOutput.collectAsState()

    var commandInput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    val currentOutput = activeSessionId?.let { outputMap[it] } ?: ""

    // Auto-scroll when new output arrives
    LaunchedEffect(currentOutput.length) {
        if (currentOutput.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // Auto-sync directory if followActivePane is enabled
    LaunchedEffect(currentPosixPath, followActivePane, activeSessionId) {
        if (followActivePane && currentPosixPath != null && activeSessionId != null) {
            sessionManager.cdToDirectory(activeSessionId!!, currentPosixPath)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .border(DesktopDensity.BorderWidth, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        color = Color(0xFF090D12)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: Tabs + Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(Color(0xFF161B22))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))

                // Session Tabs
                sessions.forEach { session ->
                    val isSelected = session.id == activeSessionId
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(if (isSelected) Color(0xFF090D12) else Color.Transparent)
                            .clickable { sessionManager.selectSession(session.id) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF8B949E)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Spacer(modifier = Modifier.weight(1f))

                // Follow Active Directory Chip
                FilterChip(
                    selected = followActivePane,
                    onClick = { onToggleFollowActivePane(!followActivePane) },
                    label = {
                        Text(
                            "Follow Folder",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.height(24.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))
                ShortcutBadge(shortcut = "Ctrl+`")
                Spacer(modifier = Modifier.width(4.dp))

                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Terminal",
                        tint = Color(0xFF8B949E),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Terminal Screen Output
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .verticalScroll(scrollState)
            ) {
                val annotatedOutput = remember(currentOutput) {
                    AnsiParser.parse(currentOutput)
                }
                Text(
                    text = annotatedOutput,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            // Command Input Bar & Quick Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(Color(0xFF11161D))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$",
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(8.dp))

                BasicTextField(
                    value = commandInput,
                    onValueChange = { commandInput = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                // Quick buttons
                TerminalQuickButton("Clear") {
                    activeSessionId?.let { sessionManager.sendCommand(it, "clear") }
                }
                Spacer(modifier = Modifier.width(4.dp))
                TerminalQuickButton("ls -la") {
                    activeSessionId?.let { sessionManager.sendCommand(it, "ls -la") }
                }
                Spacer(modifier = Modifier.width(4.dp))
                TerminalQuickButton("git status") {
                    activeSessionId?.let { sessionManager.sendCommand(it, "git status") }
                }
                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        val active = activeSessionId
                        if (active != null && commandInput.isNotBlank()) {
                            sessionManager.sendCommand(active, commandInput)
                            commandInput = ""
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Send Command",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalQuickButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF21262D))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = Color(0xFFC9D1D9)
        )
    }
}
