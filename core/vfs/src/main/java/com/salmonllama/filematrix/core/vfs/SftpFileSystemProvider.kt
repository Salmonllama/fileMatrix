package com.salmonllama.filematrix.core.vfs

import android.webkit.MimeTypeMap
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpATTRS
import com.salmonllama.filematrix.core.model.GooglebookRootType
import com.salmonllama.filematrix.core.model.SshAuthType
import com.salmonllama.filematrix.core.model.SshProfile
import com.salmonllama.filematrix.core.model.VfsNode
import com.salmonllama.filematrix.core.model.VfsUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

class SftpFileSystemProvider(
    private val profileResolver: (suspend (host: String, port: Int) -> Pair<SshProfile, SftpCredentials>?)? = null
) {

    private class ManagedConnection(
        val session: Session,
        var controlChannel: ChannelSftp
    ) {
        val lock = Any()
    }

    private val connections = ConcurrentHashMap<String, ManagedConnection>()

    suspend fun connect(
        profile: SshProfile,
        credentials: SftpCredentials
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val key = endpointKey(profile.host, profile.port)
            disconnectEndpoint(key)

            val session = openSession(profile, credentials)
            val channel = (session.openChannel("sftp") as ChannelSftp).apply {
                connect(CONNECT_TIMEOUT_MS)
            }

            // Resolve and verify default remote directory (support ~ or / or custom path)
            val resolvedDir = resolveInitialRemoteDir(channel, profile.defaultRemoteDir)
            connections[key] = ManagedConnection(session, channel)
            resolvedDir
        }
    }

    fun disconnect(host: String, port: Int) {
        disconnectEndpoint(endpointKey(host, port))
    }

    fun disconnectAll() {
        val keys = connections.keys.toList()
        keys.forEach { disconnectEndpoint(it) }
    }

    private fun disconnectEndpoint(key: String) {
        connections.remove(key)?.let { conn ->
            synchronized(conn.lock) {
                runCatching { conn.controlChannel.disconnect() }
                runCatching { conn.session.disconnect() }
            }
        }
    }

    suspend fun listChildren(uri: VfsUri): List<VfsNode> = withContext(Dispatchers.IO) {
        val (host, port) = parseHostPort(uri) ?: return@withContext emptyList()
        val conn = getOrReconnect(host, port) ?: return@withContext emptyList()
        val remoteDir = normalizeRemotePath(uri.path)

        synchronized(conn.lock) {
            val channel = ensureControlChannel(conn)
            val entries = channel.ls(remoteDir) as? java.util.Vector<*> ?: return@synchronized emptyList()
            val nodes = mutableListOf<VfsNode>()
            for (item in entries) {
                val entry = item as? ChannelSftp.LsEntry ?: continue
                val name = entry.filename
                if (name == "." || name == "..") continue
                val childPath = joinRemotePath(remoteDir, name)
                val attrs = entry.attrs
                nodes.add(
                    attrsToVfsNode(
                        host = host,
                        port = port,
                        remotePath = childPath,
                        name = name,
                        attrs = attrs,
                        rootType = uri.rootType
                    )
                )
            }
            nodes.sortedWith(
                compareBy<VfsNode> { !it.isDirectory }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
        }
    }

    suspend fun getNode(uri: VfsUri): VfsNode? = withContext(Dispatchers.IO) {
        val (host, port) = parseHostPort(uri) ?: return@withContext null
        val conn = getOrReconnect(host, port) ?: return@withContext null
        val remotePath = normalizeRemotePath(uri.path)

        synchronized(conn.lock) {
            runCatching {
                val channel = ensureControlChannel(conn)
                val attrs = channel.lstat(remotePath)
                val name = remotePath.trimEnd('/').substringAfterLast('/').ifEmpty { "/" }
                attrsToVfsNode(host, port, remotePath, name, attrs, uri.rootType)
            }.getOrNull()
        }
    }

    suspend fun createDirectory(parentUri: VfsUri, name: String): VfsNode = withContext(Dispatchers.IO) {
        val (host, port) = parseHostPort(parentUri)
            ?: throw IllegalArgumentException("Invalid SFTP URI: ${parentUri.rawString}")
        val conn = getOrReconnect(host, port)
            ?: throw IllegalStateException("SFTP connection unavailable for $host:$port")
        val targetPath = joinRemotePath(normalizeRemotePath(parentUri.path), name)

        synchronized(conn.lock) {
            val channel = ensureControlChannel(conn)
            channel.mkdir(targetPath)
            val attrs = channel.lstat(targetPath)
            attrsToVfsNode(host, port, targetPath, name, attrs, parentUri.rootType)
        }
    }

    suspend fun createFile(parentUri: VfsUri, name: String): VfsNode = withContext(Dispatchers.IO) {
        val (host, port) = parseHostPort(parentUri)
            ?: throw IllegalArgumentException("Invalid SFTP URI: ${parentUri.rawString}")
        val conn = getOrReconnect(host, port)
            ?: throw IllegalStateException("SFTP connection unavailable for $host:$port")
        val targetPath = joinRemotePath(normalizeRemotePath(parentUri.path), name)

        synchronized(conn.lock) {
            val channel = ensureControlChannel(conn)
            channel.put(ByteArrayInputStream(ByteArray(0)), targetPath)
            val attrs = channel.lstat(targetPath)
            attrsToVfsNode(host, port, targetPath, name, attrs, parentUri.rootType)
        }
    }

    suspend fun delete(uri: VfsUri): Boolean = withContext(Dispatchers.IO) {
        val (host, port) = parseHostPort(uri) ?: return@withContext false
        val conn = getOrReconnect(host, port) ?: return@withContext false
        val targetPath = normalizeRemotePath(uri.path)

        synchronized(conn.lock) {
            runCatching {
                val channel = ensureControlChannel(conn)
                val attrs = channel.lstat(targetPath)
                if (attrs.isDir) {
                    deleteDirectoryRecursive(channel, targetPath)
                } else {
                    channel.rm(targetPath)
                }
                true
            }.getOrDefault(false)
        }
    }

    suspend fun rename(uri: VfsUri, newName: String): VfsNode = withContext(Dispatchers.IO) {
        val (host, port) = parseHostPort(uri)
            ?: throw IllegalArgumentException("Invalid SFTP URI: ${uri.rawString}")
        val conn = getOrReconnect(host, port)
            ?: throw IllegalStateException("SFTP connection unavailable for $host:$port")
        val oldPath = normalizeRemotePath(uri.path)
        val parentPath = parentRemotePath(oldPath)
        val newPath = joinRemotePath(parentPath, newName)

        synchronized(conn.lock) {
            val channel = ensureControlChannel(conn)
            channel.rename(oldPath, newPath)
            val attrs = channel.lstat(newPath)
            attrsToVfsNode(host, port, newPath, newName, attrs, uri.rootType)
        }
    }

    suspend fun openInputStream(uri: VfsUri): InputStream? = withContext(Dispatchers.IO) {
        val (host, port) = parseHostPort(uri) ?: return@withContext null
        val conn = getOrReconnect(host, port) ?: return@withContext null
        val remotePath = normalizeRemotePath(uri.path)

        val streamChannel = (conn.session.openChannel("sftp") as ChannelSftp).apply {
            connect(CONNECT_TIMEOUT_MS)
        }
        try {
            val rawStream = streamChannel.get(remotePath)
            object : FilterInputStream(rawStream) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        runCatching { streamChannel.disconnect() }
                    }
                }
            }
        } catch (e: Exception) {
            runCatching { streamChannel.disconnect() }
            throw e
        }
    }

    suspend fun openOutputStream(uri: VfsUri): OutputStream? = withContext(Dispatchers.IO) {
        val (host, port) = parseHostPort(uri) ?: return@withContext null
        val conn = getOrReconnect(host, port) ?: return@withContext null
        val remotePath = normalizeRemotePath(uri.path)

        val streamChannel = (conn.session.openChannel("sftp") as ChannelSftp).apply {
            connect(CONNECT_TIMEOUT_MS)
        }
        try {
            val rawStream = streamChannel.put(remotePath)
            object : FilterOutputStream(rawStream) {
                override fun write(b: ByteArray, off: Int, len: Int) {
                    out.write(b, off, len)
                }

                override fun close() {
                    try {
                        super.close()
                    } finally {
                        runCatching { streamChannel.disconnect() }
                    }
                }
            }
        } catch (e: Exception) {
            runCatching { streamChannel.disconnect() }
            throw e
        }
    }

    private suspend fun getOrReconnect(host: String, port: Int): ManagedConnection? {
        val key = endpointKey(host, port)
        val existing = connections[key]
        if (existing != null && existing.session.isConnected) {
            return existing
        }

        val resolved = profileResolver?.invoke(host, port) ?: return null
        val (profile, creds) = resolved
        val result = connect(profile, creds)
        return if (result.isSuccess) connections[key] else null
    }

    private fun openSession(profile: SshProfile, credentials: SftpCredentials): Session {
        val jsch = JSch()
        if (profile.authType == SshAuthType.PRIVATE_KEY && !credentials.privateKey.isNullOrBlank()) {
            val keyBytes = credentials.privateKey.toByteArray(Charsets.UTF_8)
            val passBytes = credentials.passphrase?.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)
            jsch.addIdentity(profile.id, keyBytes, null, passBytes)
        }

        val session = jsch.getSession(profile.username, profile.host, profile.port)
        if (!credentials.password.isNullOrEmpty()) {
            session.setPassword(credentials.password)
        }

        val config = Properties().apply {
            put("StrictHostKeyChecking", "no")
            put("PreferredAuthentications", "publickey,password,keyboard-interactive")
        }
        session.setConfig(config)
        session.serverAliveInterval = 15_000
        session.connect(CONNECT_TIMEOUT_MS)
        return session
    }

    private fun ensureControlChannel(conn: ManagedConnection): ChannelSftp {
        if (!conn.controlChannel.isConnected) {
            val fresh = (conn.session.openChannel("sftp") as ChannelSftp).apply {
                connect(CONNECT_TIMEOUT_MS)
            }
            conn.controlChannel = fresh
        }
        return conn.controlChannel
    }

    private fun resolveInitialRemoteDir(channel: ChannelSftp, requested: String): String {
        val trimmed = requested.trim()
        if (trimmed.isEmpty() || trimmed == "~") {
            return runCatching { channel.home }.getOrDefault("/")
        }
        val candidate = if (trimmed.startsWith("~/")) {
            val home = runCatching { channel.home }.getOrDefault("/")
            joinRemotePath(home, trimmed.removePrefix("~/"))
        } else {
            normalizeRemotePath(trimmed)
        }
        return try {
            channel.lstat(candidate)
            candidate
        } catch (_: Exception) {
            runCatching { channel.home }.getOrDefault("/")
        }
    }

    private fun deleteDirectoryRecursive(channel: ChannelSftp, path: String) {
        val entries = channel.ls(path) as? java.util.Vector<*>
        if (entries != null) {
            for (item in entries) {
                val entry = item as? ChannelSftp.LsEntry ?: continue
                val name = entry.filename
                if (name == "." || name == "..") continue
                val childPath = joinRemotePath(path, name)
                if (entry.attrs.isDir) {
                    deleteDirectoryRecursive(channel, childPath)
                } else {
                    channel.rm(childPath)
                }
            }
        }
        channel.rmdir(path)
    }

    private fun attrsToVfsNode(
        host: String,
        port: Int,
        remotePath: String,
        name: String,
        attrs: SftpATTRS,
        rootType: GooglebookRootType
    ): VfsNode {
        val isDir = attrs.isDir
        val ext = if (isDir) "" else name.substringAfterLast('.', "")
        val mime = if (isDir) null else {
            runCatching {
                MimeTypeMap.getSingleton()?.getMimeTypeFromExtension(ext.lowercase())
            }.getOrNull() ?: "application/octet-stream"
        }
        val uri = VfsUri.fromSftp(host, port, remotePath, rootType)
        return VfsNode(
            id = uri.rawString,
            uri = uri,
            name = name,
            isDirectory = isDir,
            sizeBytes = if (isDir) 0L else attrs.size,
            lastModified = attrs.mTime.toLong() * 1000L,
            mimeType = mime,
            extension = ext,
            isHidden = name.startsWith("."),
            isSymlink = attrs.isLink,
            permissionsPosix = attrs.permissionsString,
            targetPosixPath = remotePath
        )
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 8_000

        internal fun endpointKey(host: String, port: Int): String = "$host:$port"

        internal fun parseHostPort(uri: VfsUri): Pair<String, Int>? {
            val auth = uri.authority ?: return null
            if (auth.isBlank()) return null
            val host = auth.substringBefore(':')
            val port = auth.substringAfter(':', "22").toIntOrNull() ?: 22
            return host to port
        }

        internal fun normalizeRemotePath(path: String): String {
            if (path.isBlank()) return "/"
            val cleaned = path.replace("//", "/")
            return if (cleaned.startsWith("/")) cleaned else "/$cleaned"
        }

        internal fun joinRemotePath(parent: String, child: String): String {
            val cleanParent = normalizeRemotePath(parent).trimEnd('/')
            val cleanChild = child.trimStart('/')
            return if (cleanParent.isEmpty()) "/$cleanChild" else "$cleanParent/$cleanChild"
        }

        internal fun parentRemotePath(path: String): String {
            val trimmed = normalizeRemotePath(path).trimEnd('/')
            if (trimmed.isEmpty() || trimmed == "/") return "/"
            return trimmed.substringBeforeLast('/', "").ifEmpty { "/" }
        }
    }
}
