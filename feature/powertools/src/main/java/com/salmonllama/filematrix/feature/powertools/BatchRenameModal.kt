package com.salmonllama.filematrix.feature.powertools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.salmonllama.filematrix.core.designsystem.theme.DesktopDensity
import com.salmonllama.filematrix.core.model.VfsNode

enum class RenameMode {
    REPLACE,
    PATTERN,
    CASE_TRANSFORM
}

@Composable
fun BatchRenameModal(
    selectedFiles: List<VfsNode>,
    onApply: (Map<VfsNode, String>) -> Unit,
    onDismiss: () -> Unit
) {
    var modeIndex by remember { mutableIntStateOf(0) }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var isRegex by remember { mutableStateOf(false) }

    var patternTemplate by remember { mutableStateOf("{name}_{counter}") }
    var startCounter by remember { mutableIntStateOf(1) }

    var caseSelection by remember { mutableIntStateOf(0) } // 0: lowercase, 1: UPPERCASE, 2: Title Case

    // Compute preview
    val previewMap = remember(selectedFiles, modeIndex, findText, replaceText, isRegex, patternTemplate, startCounter, caseSelection) {
        selectedFiles.mapIndexed { index, node ->
            val oldName = node.name
            val baseName = oldName.substringBeforeLast('.', oldName)
            val ext = if (oldName.contains('.')) "." + oldName.substringAfterLast('.') else ""

            val newName = when (modeIndex) {
                0 -> { // Replace
                    if (findText.isEmpty()) oldName
                    else if (isRegex) {
                        try {
                            oldName.replace(Regex(findText), replaceText)
                        } catch (_: Exception) {
                            oldName
                        }
                    } else {
                        oldName.replace(findText, replaceText)
                    }
                }
                1 -> { // Pattern
                    val counterVal = startCounter + index
                    val formatted = patternTemplate
                        .replace("{name}", baseName)
                        .replace("{counter}", counterVal.toString().padStart(3, '0'))
                    "$formatted$ext"
                }
                else -> { // Case transform
                    when (caseSelection) {
                        0 -> baseName.lowercase() + ext
                        1 -> baseName.uppercase() + ext
                        else -> baseName.replaceFirstChar { it.uppercase() } + ext
                    }
                }
            }
            node to newName
        }.toMap()
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
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DriveFileRenameOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Batch Rename (${selectedFiles.size} items)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mode Tabs
                TabRow(
                    selectedTabIndex = modeIndex,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Tab(
                        selected = modeIndex == 0,
                        onClick = { modeIndex = 0 },
                        text = { Text("Find & Replace") }
                    )
                    Tab(
                        selected = modeIndex == 1,
                        onClick = { modeIndex = 1 },
                        text = { Text("Pattern Template") }
                    )
                    Tab(
                        selected = modeIndex == 2,
                        onClick = { modeIndex = 2 },
                        text = { Text("Case Transform") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Inputs based on mode
                when (modeIndex) {
                    0 -> {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = findText,
                                onValueChange = { findText = it },
                                label = { Text("Find") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = replaceText,
                                onValueChange = { replaceText = it },
                                label = { Text("Replace with") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    1 -> {
                        OutlinedTextField(
                            value = patternTemplate,
                            onValueChange = { patternTemplate = it },
                            label = { Text("Template (e.g. {name}_{counter})") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    2 -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("lowercase", "UPPERCASE", "Title Case").forEachIndexed { i, label ->
                                Button(
                                    onClick = { caseSelection = i },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (caseSelection == i) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = if (caseSelection == i) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Live Rename Preview:",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Live Preview Table
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .border(DesktopDensity.BorderWidth, MaterialTheme.colorScheme.outlineVariant)
                        .padding(8.dp)
                ) {
                    LazyColumn {
                        items(selectedFiles) { node ->
                            val newName = previewMap[node] ?: node.name
                            val isChanged = newName != node.name

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = node.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "→",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                Text(
                                    text = newName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isChanged) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onApply(previewMap) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Apply Rename")
                    }
                }
            }
        }
    }
}
