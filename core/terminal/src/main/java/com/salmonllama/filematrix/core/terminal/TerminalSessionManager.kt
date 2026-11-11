package com.salmonllama.filematrix.core.terminal

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.salmonllama.filematrix.core.model.GooglebookRootType
import com.salmonllama.filematrix.core.model.SshProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

data class TerminalTabSession(
    val id: String,
    val title: String,
    val rootType: GooglebookRootType,
    val isConnected: Boolean = false,
    val isLocalShell: Boolean = false
)

class TerminalSessionManager(
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val _sessions = MutableStateFlow<List<TerminalTabSession>>(emptyList())
    val sessions: StateFlow<List<TerminalTabSession>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _sessionOutput = MutableStateFlow<Map<String, String>>(emptyMap())
    val sessionOutput: StateFlow<Map<String, String>> = _sessionOutput.asStateFlow()

    private val sshSessions = mutableMapOf<String, Session>()
    private val sshChannels = mutableMapOf<String, ChannelShell>()
    private val inputStreams = mutableMapOf<String, OutputStream>()
    private val localProcesses = mutableMapOf<String, Process>()

    init {
        // Create initial default sessions for Linux VM and Antigravity
        createSession("session_antigravity", "Antigravity VM", GooglebookRootType.ANTIGRAVITY)
        createSession("session_linux_vm", "Linux VM", GooglebookRootType.LINUX_VM)
    }

    fun createSession(id: String, title: String, rootType: GooglebookRootType) {
        val tab = TerminalTabSession(id = id, title = title, rootType = rootType)
        _sessions.update { it + tab }
        if (_activeSessionId.value == null) {
            _activeSessionId.value = id
        }
    }

    fun selectSession(id: String) {
        _activeSessionId.value = id
    }

    suspend fun connectSsh(sessionId: String, profile: SshProfile, password: String? = null) = withContext(Dispatchers.IO) {
        try {
            val jsch = JSch()
            val session = jsch.getSession(profile.username, profile.host, profile.port)
            if (password != null) {
                session.setPassword(password)
            }
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect(5000)

            val channel = session.openChannel("shell") as ChannelShell
            channel.setPtyType("xterm-256color")
            val outputStream = channel.outputStream
            val inputStream = channel.inputStream

            channel.connect(3000)

            sshSessions[sessionId] = session
            sshChannels[sessionId] = channel
            inputStreams[sessionId] = outputStream

            _sessions.update { list ->
                list.map { if (it.id == sessionId) it.copy(isConnected = true, isLocalShell = false) else it }
            }

            appendOutput(sessionId, "\r\n[Connected to ${profile.name} (${profile.host}:${profile.port})]\r\n")

            // Listen to output
            startReadingOutput(sessionId, inputStream)
        } catch (e: Exception) {
            appendOutput(sessionId, "\r\n[SSH Connection Failed: ${e.message}. Launching local shell fallback...]\r\n")
            launchLocalShellFallback(sessionId)
        }
    }

    fun launchLocalShellFallback(sessionId: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val process = ProcessBuilder("/system/bin/sh", "-i")
                    .redirectErrorStream(true)
                    .start()

                localProcesses[sessionId] = process
                inputStreams[sessionId] = process.outputStream

                _sessions.update { list ->
                    list.map { if (it.id == sessionId) it.copy(isConnected = true, isLocalShell = true) else it }
                }

                appendOutput(sessionId, "\r\n[Local shell ready. Type commands below:]\r\n$ ")
                startReadingOutput(sessionId, process.inputStream)
            } catch (e: Exception) {
                appendOutput(sessionId, "\r\n[Failed to launch shell: ${e.message}]\r\n")
            }
        }
    }

    fun sendCommand(sessionId: String, command: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val stream = inputStreams[sessionId]
                if (stream != null) {
                    val formatted = if (command.endsWith("\n")) command else "$command\n"
                    stream.write(formatted.toByteArray())
                    stream.flush()
                } else {
                    // Try auto-launching fallback if not connected
                    launchLocalShellFallback(sessionId)
                }
            } catch (e: Exception) {
                appendOutput(sessionId, "\r\n[Error writing to terminal: ${e.message}]\r\n")
            }
        }
    }

    fun cdToDirectory(sessionId: String, posixPath: String) {
        sendCommand(sessionId, "cd \"$posixPath\"")
    }

    private fun startReadingOutput(sessionId: String, inputStream: InputStream) {
        coroutineScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(2048)
            var bytesRead: Int
            try {
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    val text = String(buffer, 0, bytesRead)
                    appendOutput(sessionId, text)
                }
            } catch (_: Exception) {
            } finally {
                _sessions.update { list ->
                    list.map { if (it.id == sessionId) it.copy(isConnected = false) else it }
                }
            }
        }
    }

    private fun appendOutput(sessionId: String, text: String) {
        _sessionOutput.update { map ->
            val prev = map[sessionId] ?: ""
            val next = if (prev.length > 50_000) prev.takeLast(30_000) + text else prev + text
            map + (sessionId to next)
        }
    }

    fun closeSession(sessionId: String) {
        sshChannels[sessionId]?.disconnect()
        sshChannels.remove(sessionId)
        sshSessions[sessionId]?.disconnect()
        sshSessions.remove(sessionId)
        localProcesses[sessionId]?.destroy()
        localProcesses.remove(sessionId)
        inputStreams.remove(sessionId)

        _sessions.update { it.filter { tab -> tab.id != sessionId } }
        if (_activeSessionId.value == sessionId) {
            _activeSessionId.value = _sessions.value.firstOrNull()?.id
        }
    }
}
