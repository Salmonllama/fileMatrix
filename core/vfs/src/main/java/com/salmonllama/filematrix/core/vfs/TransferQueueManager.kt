package com.salmonllama.filematrix.core.vfs

import com.salmonllama.filematrix.core.model.TransferConflictStrategy
import com.salmonllama.filematrix.core.model.TransferJob
import com.salmonllama.filematrix.core.model.TransferStatus
import com.salmonllama.filematrix.core.model.VfsNode
import com.salmonllama.filematrix.core.model.VfsUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class TransferQueueManager(
    private val vfs: VirtualFileSystem,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val _jobs = MutableStateFlow<List<TransferJob>>(emptyList())
    val jobs: StateFlow<List<TransferJob>> = _jobs.asStateFlow()

    private val runningJobs = mutableMapOf<String, Job>()

    fun enqueueTransfer(
        sourceNode: VfsNode,
        destinationParent: VfsUri,
        conflictStrategy: TransferConflictStrategy = TransferConflictStrategy.KEEP_BOTH
    ): String {
        val jobId = UUID.randomUUID().toString()
        val job = TransferJob(
            id = jobId,
            sourceUri = sourceNode.uri,
            destinationUri = destinationParent,
            fileName = sourceNode.name,
            totalBytes = sourceNode.sizeBytes,
            status = TransferStatus.QUEUED,
            conflictStrategy = conflictStrategy
        )

        _jobs.update { it + job }
        startJob(jobId)
        return jobId
    }

    fun pauseJob(jobId: String) {
        runningJobs[jobId]?.cancel()
        runningJobs.remove(jobId)
        _jobs.update { list ->
            list.map { if (it.id == jobId && it.status == TransferStatus.RUNNING) it.copy(status = TransferStatus.PAUSED) else it }
        }
    }

    fun resumeJob(jobId: String) {
        val job = _jobs.value.find { it.id == jobId } ?: return
        if (job.status == TransferStatus.PAUSED || job.status == TransferStatus.QUEUED) {
            startJob(jobId)
        }
    }

    fun cancelJob(jobId: String) {
        runningJobs[jobId]?.cancel()
        runningJobs.remove(jobId)
        _jobs.update { list ->
            list.map { if (it.id == jobId) it.copy(status = TransferStatus.CANCELLED) else it }
        }
    }

    fun clearCompleted() {
        _jobs.update { list ->
            list.filter { it.status == TransferStatus.RUNNING || it.status == TransferStatus.PAUSED || it.status == TransferStatus.QUEUED }
        }
    }

    private fun startJob(jobId: String) {
        val activeCoroutine = coroutineScope.launch {
            val currentJob = _jobs.value.find { it.id == jobId } ?: return@launch
            _jobs.update { list ->
                list.map { if (it.id == jobId) it.copy(status = TransferStatus.RUNNING, errorMessage = null) else it }
            }

            try {
                executeCopy(currentJob)
                _jobs.update { list ->
                    list.map {
                        if (it.id == jobId) it.copy(
                            status = TransferStatus.COMPLETED,
                            bytesTransferred = it.totalBytes,
                            speedBytesPerSec = 0L,
                            etaSeconds = 0L
                        ) else it
                    }
                }
            } catch (e: CancellationException) {
                // Handled gracefully in pause/cancel
            } catch (e: Exception) {
                _jobs.update { list ->
                    list.map {
                        if (it.id == jobId) it.copy(
                            status = TransferStatus.FAILED,
                            errorMessage = e.message ?: "Transfer error"
                        ) else it
                    }
                }
            } finally {
                runningJobs.remove(jobId)
            }
        }
        runningJobs[jobId] = activeCoroutine
    }

    private suspend fun executeCopy(job: TransferJob) {
        val inputStream: InputStream = vfs.openInputStream(job.sourceUri)
            ?: throw IllegalStateException("Cannot open input stream for: ${job.sourceUri.path}")

        // Determine destination file name based on conflict strategy
        val targetName = when (job.conflictStrategy) {
            TransferConflictStrategy.KEEP_BOTH -> {
                val base = job.fileName.substringBeforeLast('.')
                val ext = job.fileName.substringAfterLast('.', "")
                val suffix = System.currentTimeMillis() % 1000
                if (ext.isNotEmpty()) "${base}_$suffix.$ext" else "${base}_$suffix"
            }
            else -> job.fileName
        }

        val createdNode = vfs.createFile(job.destinationUri, targetName)
        val outputStream: OutputStream = vfs.openOutputStream(createdNode.uri)
            ?: throw IllegalStateException("Cannot open output stream for: ${createdNode.uri.path}")

        inputStream.use { input ->
            outputStream.use { output ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var transferred = 0L
                var lastSampleTime = System.currentTimeMillis()
                var bytesSinceSample = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    transferred += bytesRead
                    bytesSinceSample += bytesRead

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastSampleTime
                    if (elapsed >= 500) {
                        val speed = (bytesSinceSample * 1000L) / elapsed
                        val remainingBytes = (job.totalBytes - transferred).coerceAtLeast(0L)
                        val eta = if (speed > 0) remainingBytes / speed else 0L

                        _jobs.update { list ->
                            list.map {
                                if (it.id == job.id) it.copy(
                                    bytesTransferred = transferred,
                                    speedBytesPerSec = speed,
                                    etaSeconds = eta
                                ) else it
                            }
                        }
                        lastSampleTime = now
                        bytesSinceSample = 0L
                    }
                }
            }
        }
    }
}
