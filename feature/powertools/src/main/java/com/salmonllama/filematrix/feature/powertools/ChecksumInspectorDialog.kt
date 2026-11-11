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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.salmonllama.filematrix.core.designsystem.theme.DesktopDensity
import com.salmonllama.filematrix.core.model.FileChecksum
import com.salmonllama.filematrix.core.model.VfsNode
import com.salmonllama.filematrix.core.vfs.VirtualFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

@Composable
fun ChecksumInspectorDialog(
    fileNode: VfsNode,
    vfs: VirtualFileSystem,
    onDismiss: () -> Unit
) {
    var checksum by remember { mutableStateOf<FileChecksum?>(null) }
    var isCalculating by remember { mutableStateOf(true) }
    var verifyHashInput by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(fileNode) {
        isCalculating = true
        checksum = calculateChecksums(vfs, fileNode)
        isCalculating = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(540.dp)
                .border(
                    DesktopDensity.BorderWidth,
                    MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(DesktopDensity.CornerRadiusLarge)
                ),
            shape = RoundedCornerShape(DesktopDensity.CornerRadiusLarge),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileNode.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Size: ${formatBytes(fileNode.sizeBytes)} • Checksum Inspector",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isCalculating) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Streaming and computing cryptographic hashes...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    checksum?.let { c ->
                        HashRow("MD5", c.md5 ?: "--", onCopy = {
                            c.md5?.let { clipboardManager.setText(AnnotatedString(it)) }
                        })
                        Spacer(modifier = Modifier.height(8.dp))
                        HashRow("SHA-1", c.sha1 ?: "--", onCopy = {
                            c.sha1?.let { clipboardManager.setText(AnnotatedString(it)) }
                        })
                        Spacer(modifier = Modifier.height(8.dp))
                        HashRow("SHA-256", c.sha256 ?: "--", onCopy = {
                            c.sha256?.let { clipboardManager.setText(AnnotatedString(it)) }
                        })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Verification input
                    OutlinedTextField(
                        value = verifyHashInput,
                        onValueChange = { verifyHashInput = it.trim() },
                        label = { Text("Compare with Expected Hash") },
                        placeholder = { Text("Paste MD5, SHA-1, or SHA-256 hash here") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (verifyHashInput.isNotBlank() && checksum != null) {
                        val inputLower = verifyHashInput.lowercase()
                        val matches = inputLower == checksum?.md5?.lowercase() ||
                                inputLower == checksum?.sha1?.lowercase() ||
                                inputLower == checksum?.sha256?.lowercase()

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (matches) Color(0xFF003816) else Color(0xFF490B0B))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (matches) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (matches) Color(0xFF00E676) else Color(0xFFFF5252),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (matches) "Hash Verification MATCH! File integrity confirmed." else "Hash MISMATCH! Verification failed.",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (matches) Color(0xFF7AFFAB) else Color(0xFFFF8A80)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun HashRow(algorithm: String, hash: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(DesktopDensity.BorderWidth, MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = algorithm,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = hash,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy Hash",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private suspend fun calculateChecksums(vfs: VirtualFileSystem, node: VfsNode): FileChecksum = withContext(Dispatchers.IO) {
    val md5Digest = MessageDigest.getInstance("MD5")
    val sha1Digest = MessageDigest.getInstance("SHA-1")
    val sha256Digest = MessageDigest.getInstance("SHA-256")

    val inputStream = vfs.openInputStream(node.uri) ?: return@withContext FileChecksum()
    val buffer = ByteArray(64 * 1024)
    var read: Int

    inputStream.use { input ->
        while (input.read(buffer).also { read = it } != -1) {
            md5Digest.update(buffer, 0, read)
            sha1Digest.update(buffer, 0, read)
            sha256Digest.update(buffer, 0, read)
        }
    }

    FileChecksum(
        md5 = md5Digest.digest().joinToString("") { "%02x".format(it) },
        sha1 = sha1Digest.digest().joinToString("") { "%02x".format(it) },
        sha256 = sha256Digest.digest().joinToString("") { "%02x".format(it) }
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return "%.1f %s".format(bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
