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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.salmonllama.filematrix.core.designsystem.theme.DesktopDensity
import com.salmonllama.filematrix.core.model.TransferJob
import com.salmonllama.filematrix.core.model.TransferStatus
import com.salmonllama.filematrix.core.vfs.TransferQueueManager

@Composable
fun TransferQueueModal(
    transferManager: TransferQueueManager,
    onDismiss: () -> Unit
) {
    val jobs by transferManager.jobs.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(580.dp)
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
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Transfer Queue (${jobs.count { it.status == TransferStatus.RUNNING }} active)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (jobs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No active file transfers.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        items(jobs) { job ->
                            TransferJobRow(
                                job = job,
                                onPause = { transferManager.pauseJob(job.id) },
                                onResume = { transferManager.resumeJob(job.id) },
                                onCancel = { transferManager.cancelJob(job.id) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { transferManager.clearCompleted() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("Clear Completed")
                    }
                    Button(onClick = onDismiss) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferJobRow(
    job: TransferJob,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(DesktopDensity.BorderWidth, MaterialTheme.colorScheme.outlineVariant)
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = job.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // Status chip
                val statusColor = when (job.status) {
                    TransferStatus.RUNNING -> MaterialTheme.colorScheme.primary
                    TransferStatus.COMPLETED -> Color(0xFF00E676)
                    TransferStatus.FAILED -> Color(0xFFFF5252)
                    TransferStatus.PAUSED -> Color(0xFFFFB74D)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = job.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (job.status == TransferStatus.RUNNING) {
                    IconButton(onClick = onPause, modifier = Modifier.size(22.dp)) {
                        Icon(imageVector = Icons.Default.Pause, contentDescription = "Pause", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (job.status == TransferStatus.PAUSED) {
                    IconButton(onClick = onResume, modifier = Modifier.size(22.dp)) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Resume", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                IconButton(onClick = onCancel, modifier = Modifier.size(22.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { job.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val speedMb = job.speedBytesPerSec / (1024.0 * 1024.0)
                val transferredMb = job.bytesTransferred / (1024.0 * 1024.0)
                val totalMb = job.totalBytes / (1024.0 * 1024.0)

                Text(
                    text = "%.1f MB / %.1f MB".format(transferredMb, totalMb),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (job.status == TransferStatus.RUNNING) {
                    Text(
                        text = "%.1f MB/s • %ds remaining".format(speedMb, job.etaSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}
