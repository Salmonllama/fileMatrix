package com.salmonllama.filematrix.feature.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.salmonllama.filematrix.core.designsystem.theme.DesktopDensity
import com.salmonllama.filematrix.core.model.GooglebookRootType
import com.salmonllama.filematrix.core.model.SshAuthType
import com.salmonllama.filematrix.core.model.SshProfile
import com.salmonllama.filematrix.core.model.StorageRoot
import com.salmonllama.filematrix.core.vfs.GoogleDriveAuthStore
import com.salmonllama.filematrix.core.vfs.VirtualFileSystem
import kotlinx.coroutines.launch

private enum class MountTab(val label: String) {
    LOCAL_DIRECT("Direct Root"),
    SFTP("SFTP / SSH"),
    GOOGLE_DRIVE_API("Drive API v3"),
    SAF_FALLBACK("Android Picker")
}

@Composable
fun StorageMountDialog(
    rootType: GooglebookRootType,
    vfs: VirtualFileSystem,
    hasAllFilesAccess: Boolean,
    onRequestAllFilesAccess: () -> Unit,
    onRequestSafPicker: (GooglebookRootType) -> Unit,
    onLaunchOAuthUrl: (String) -> Unit,
    onRootMounted: (StorageRoot) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    val availableTabs = remember(rootType) {
        when (rootType) {
            GooglebookRootType.GOOGLEBOOK_LOCAL -> listOf(MountTab.LOCAL_DIRECT, MountTab.SAF_FALLBACK)
            GooglebookRootType.LINUX_VM -> listOf(MountTab.SFTP, MountTab.LOCAL_DIRECT, MountTab.SAF_FALLBACK)
            GooglebookRootType.ANTIGRAVITY -> listOf(MountTab.SFTP, MountTab.LOCAL_DIRECT, MountTab.SAF_FALLBACK)
            GooglebookRootType.GOOGLE_DRIVE -> listOf(MountTab.GOOGLE_DRIVE_API, MountTab.LOCAL_DIRECT, MountTab.SAF_FALLBACK)
            GooglebookRootType.MOBILE_FILES -> listOf(MountTab.LOCAL_DIRECT, MountTab.SFTP, MountTab.SAF_FALLBACK)
        }
    }

    var selectedTab by remember(rootType) { mutableStateOf(availableTabs.first()) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Direct local path fields
    var localPath by remember(rootType) {
        mutableStateOf(
            when (rootType) {
                GooglebookRootType.GOOGLEBOOK_LOCAL -> "/storage/emulated/0"
                GooglebookRootType.LINUX_VM -> "/mnt/chromeos/LinuxFiles"
                GooglebookRootType.ANTIGRAVITY -> "/home/antigravity"
                GooglebookRootType.GOOGLE_DRIVE -> "/mnt/chromeos/GoogleDrive/MyDrive"
                GooglebookRootType.MOBILE_FILES -> "/storage"
            }
        )
    }
    var localDisplayName by remember(rootType) { mutableStateOf(rootType.title) }

    // SFTP fields
    var sftpHost by remember(rootType) {
        mutableStateOf(
            when (rootType) {
                GooglebookRootType.LINUX_VM -> "penguin.linux.test"
                GooglebookRootType.ANTIGRAVITY -> "127.0.0.1"
                else -> "192.168.1.100"
            }
        )
    }
    var sftpPort by remember(rootType) {
        mutableStateOf(
            when (rootType) {
                GooglebookRootType.ANTIGRAVITY -> "2222"
                GooglebookRootType.MOBILE_FILES -> "8022"
                else -> "22"
            }
        )
    }
    var sftpUsername by remember(rootType) {
        mutableStateOf(
            when (rootType) {
                GooglebookRootType.ANTIGRAVITY -> "antigravity"
                else -> "user"
            }
        )
    }
    var sftpAuthType by remember { mutableStateOf(SshAuthType.PASSWORD) }
    var sftpPassword by remember { mutableStateOf("") }
    var sftpPrivateKey by remember { mutableStateOf("") }
    var sftpPassphrase by remember { mutableStateOf("") }
    var sftpRemoteDir by remember(rootType) {
        mutableStateOf(
            when (rootType) {
                GooglebookRootType.ANTIGRAVITY -> "/home/antigravity"
                GooglebookRootType.MOBILE_FILES -> "/storage/emulated/0"
                else -> "/"
            }
        )
    }
    var sftpDisplayName by remember(rootType) { mutableStateOf(rootType.title) }

    // Pre-populate saved SSH profile if available
    LaunchedEffect(rootType) {
        vfs.getSavedSshProfile(rootType)?.let { saved ->
            sftpHost = saved.host
            sftpPort = saved.port.toString()
            sftpUsername = saved.username
            sftpAuthType = saved.authType
            sftpRemoteDir = saved.defaultRemoteDir
            sftpDisplayName = saved.name
        }
    }

    // Google Drive API fields
    var driveUseTokenMode by remember { mutableStateOf(false) }
    var driveClientId by remember { mutableStateOf(vfs.getSavedGoogleDriveClientId().orEmpty()) }
    var driveClientSecret by remember { mutableStateOf("") }
    var driveAccessToken by remember { mutableStateOf("") }
    var driveRefreshToken by remember { mutableStateOf("") }

    val (headerIcon, accentColor) = when (rootType) {
        GooglebookRootType.GOOGLEBOOK_LOCAL -> Icons.Default.Laptop to Color(0xFF58A6FF)
        GooglebookRootType.LINUX_VM -> Icons.Default.Computer to Color(0xFF00E676)
        GooglebookRootType.ANTIGRAVITY -> Icons.Default.RocketLaunch to Color(0xFF00E5FF)
        GooglebookRootType.GOOGLE_DRIVE -> Icons.Default.Cloud to Color(0xFFFFB74D)
        GooglebookRootType.MOBILE_FILES -> Icons.Default.PhoneAndroid to Color(0xFFE040FB)
    }

    Dialog(onDismissRequest = { if (!isBusy) onDismiss() }) {
        Surface(
            modifier = Modifier
                .width(560.dp)
                .border(
                    DesktopDensity.BorderWidth,
                    MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(DesktopDensity.CornerRadiusLarge)
                ),
            shape = RoundedCornerShape(DesktopDensity.CornerRadiusLarge),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = headerIcon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connect ${rootType.title}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = rootType.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { if (!isBusy) onDismiss() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Method Selector Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableTabs.forEach { tab ->
                        FilterChip(
                            selected = selectedTab == tab,
                            onClick = {
                                selectedTab = tab
                                errorMessage = null
                                statusMessage = null
                            },
                            label = { Text(tab.label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Error / Status Banner
                errorMessage?.let { err ->
                    CalloutBox(
                        icon = Icons.Default.Warning,
                        tint = MaterialTheme.colorScheme.error,
                        background = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                        text = err
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                statusMessage?.let { msg ->
                    CalloutBox(
                        icon = Icons.Default.CheckCircle,
                        tint = Color(0xFF00E676),
                        background = Color(0xFF00E676).copy(alpha = 0.12f),
                        text = msg
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Tab Body
                when (selectedTab) {
                    MountTab.LOCAL_DIRECT -> {
                        // All Files Access Permission Card
                        CalloutBox(
                            icon = if (hasAllFilesAccess) Icons.Default.CheckCircle else Icons.Default.Security,
                            tint = if (hasAllFilesAccess) Color(0xFF00E676) else Color(0xFFFFB74D),
                            background = if (hasAllFilesAccess) Color(0xFF00E676).copy(alpha = 0.10f)
                            else Color(0xFFFFB74D).copy(alpha = 0.12f),
                            text = if (hasAllFilesAccess) {
                                "All Files Access is granted. fileMatrix can browse full drive roots (/storage/emulated/0, Downloads, SD cards, and USB drives) directly without Android's SAF picker."
                            } else {
                                "Grant \"All files access\" in Android Settings to unlock full drive-root browsing for internal storage, Downloads, SD cards, and USB drives without using the native picker."
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!hasAllFilesAccess) {
                                Button(
                                    onClick = onRequestAllFilesAccess,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Grant All Files Access", fontSize = 12.sp)
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    vfs.refreshLocalVolumes()
                                    statusMessage = "Scanned mounted local, SD, and USB storage volumes."
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan Volumes", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = localDisplayName,
                            onValueChange = { localDisplayName = it },
                            label = { Text("Mount Label") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = localPath,
                            onValueChange = { localPath = it },
                            label = { Text("Direct Filesystem Path") },
                            placeholder = { Text("/storage/emulated/0 or /mnt/chromeos/MyFiles") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(onClick = onDismiss, enabled = !isBusy) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                enabled = !isBusy && localPath.isNotBlank(),
                                onClick = {
                                    isBusy = true
                                    errorMessage = null
                                    coroutineScope.launch {
                                        val result = vfs.registerLocalPathRoot(
                                            localPath = localPath,
                                            rootType = rootType,
                                            displayName = localDisplayName
                                        )
                                        isBusy = false
                                        result.onSuccess { root ->
                                            onRootMounted(root)
                                            onDismiss()
                                        }.onFailure { err ->
                                            errorMessage = err.message ?: "Failed to mount path"
                                        }
                                    }
                                }
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Mount Direct Path")
                                }
                            }
                        }
                    }

                    MountTab.SFTP -> {
                        CalloutBox(
                            icon = Icons.Default.Info,
                            tint = Color(0xFF00E5FF),
                            background = Color(0xFF00E5FF).copy(alpha = 0.10f),
                            text = "Connect directly via SFTP over SSH to browse the full filesystem root (/) or home directory (~) without Android's SAF restrictions."
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = sftpDisplayName,
                            onValueChange = { sftpDisplayName = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = sftpHost,
                                onValueChange = { sftpHost = it },
                                label = { Text("Host / IP") },
                                singleLine = true,
                                modifier = Modifier.weight(3f)
                            )
                            OutlinedTextField(
                                value = sftpPort,
                                onValueChange = { sftpPort = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Port") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = sftpUsername,
                                onValueChange = { sftpUsername = it },
                                label = { Text("Username") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = sftpRemoteDir,
                                onValueChange = { sftpRemoteDir = it },
                                label = { Text("Remote Root Path") },
                                placeholder = { Text("/ or ~") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = sftpAuthType == SshAuthType.PASSWORD,
                                onClick = { sftpAuthType = SshAuthType.PASSWORD },
                                label = { Text("Password", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = sftpAuthType == SshAuthType.PRIVATE_KEY,
                                onClick = { sftpAuthType = SshAuthType.PRIVATE_KEY },
                                label = { Text("Private Key (PEM)", fontSize = 11.sp) }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        if (sftpAuthType == SshAuthType.PASSWORD) {
                            OutlinedTextField(
                                value = sftpPassword,
                                onValueChange = { sftpPassword = it },
                                label = { Text("SSH Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            OutlinedTextField(
                                value = sftpPrivateKey,
                                onValueChange = { sftpPrivateKey = it },
                                label = { Text("OpenSSH / PEM Private Key") },
                                placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----") },
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = sftpPassphrase,
                                onValueChange = { sftpPassphrase = it },
                                label = { Text("Key Passphrase (optional)") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(onClick = onDismiss, enabled = !isBusy) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                enabled = !isBusy && sftpHost.isNotBlank() && sftpUsername.isNotBlank(),
                                onClick = {
                                    isBusy = true
                                    errorMessage = null
                                    val portInt = sftpPort.toIntOrNull() ?: 22
                                    val profile = SshProfile(
                                        id = "ssh_${rootType.name.lowercase()}_${sftpHost.hashCode()}",
                                        name = sftpDisplayName.ifBlank { rootType.title },
                                        rootType = rootType,
                                        host = sftpHost.trim(),
                                        port = portInt,
                                        username = sftpUsername.trim(),
                                        authType = sftpAuthType,
                                        defaultRemoteDir = sftpRemoteDir.trim().ifBlank { "/" }
                                    )
                                    coroutineScope.launch {
                                        val result = vfs.registerSftpRoot(
                                            profile = profile,
                                            password = if (sftpAuthType == SshAuthType.PASSWORD) sftpPassword else null,
                                            privateKey = if (sftpAuthType == SshAuthType.PRIVATE_KEY) sftpPrivateKey else null,
                                            passphrase = if (sftpAuthType == SshAuthType.PRIVATE_KEY) sftpPassphrase else null
                                        )
                                        isBusy = false
                                        result.onSuccess { root ->
                                            onRootMounted(root)
                                            onDismiss()
                                        }.onFailure { err ->
                                            errorMessage = err.message ?: "SFTP connection failed"
                                        }
                                    }
                                }
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Connect & Mount SFTP")
                                }
                            }
                        }
                    }

                    MountTab.GOOGLE_DRIVE_API -> {
                        CalloutBox(
                            icon = Icons.Default.Cloud,
                            tint = Color(0xFFFFB74D),
                            background = Color(0xFFFFB74D).copy(alpha = 0.12f),
                            text = "Google Drive's native Android picker does not support folder-tree mounts. Connect directly via the Google Drive REST API v3 to browse your full My Drive & Shared Drives root."
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !driveUseTokenMode,
                                onClick = { driveUseTokenMode = false; errorMessage = null },
                                label = { Text("OAuth 2.0 Browser Sign-In", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = driveUseTokenMode,
                                onClick = { driveUseTokenMode = true; errorMessage = null },
                                label = { Text("Direct Access Token", fontSize = 11.sp) }
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        if (!driveUseTokenMode) {
                            OutlinedTextField(
                                value = driveClientId,
                                onValueChange = { driveClientId = it },
                                label = { Text("Google OAuth 2.0 Client ID") },
                                placeholder = { Text("xxxx.apps.googleusercontent.com") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = driveClientSecret,
                                onValueChange = { driveClientSecret = it },
                                label = { Text("Client Secret (only if using Desktop client)") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Redirect URI: ${GoogleDriveAuthStore.DEFAULT_REDIRECT_URI}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(onClick = onDismiss, enabled = !isBusy) {
                                    Text("Cancel")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    enabled = !isBusy && driveClientId.isNotBlank(),
                                    onClick = {
                                        val authUrl = vfs.startGoogleDriveOAuthUrl(
                                            clientId = driveClientId.trim(),
                                            clientSecret = driveClientSecret.trim().takeIf { it.isNotEmpty() }
                                        )
                                        onLaunchOAuthUrl(authUrl)
                                        onDismiss()
                                    }
                                ) {
                                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sign in with Google")
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = driveAccessToken,
                                onValueChange = { driveAccessToken = it },
                                label = { Text("OAuth 2.0 Access Token") },
                                placeholder = { Text("ya29.a0...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = driveRefreshToken,
                                onValueChange = { driveRefreshToken = it },
                                label = { Text("Refresh Token (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(onClick = onDismiss, enabled = !isBusy) {
                                    Text("Cancel")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    enabled = !isBusy && driveAccessToken.isNotBlank(),
                                    onClick = {
                                        isBusy = true
                                        errorMessage = null
                                        coroutineScope.launch {
                                            val result = vfs.registerGoogleDriveWithToken(
                                                accessToken = driveAccessToken,
                                                refreshToken = driveRefreshToken.takeIf { it.isNotBlank() },
                                                clientId = driveClientId.takeIf { it.isNotBlank() },
                                                clientSecret = driveClientSecret.takeIf { it.isNotBlank() }
                                            )
                                            isBusy = false
                                            result.onSuccess { root ->
                                                onRootMounted(root)
                                                onDismiss()
                                            }.onFailure { err ->
                                                errorMessage = err.message ?: "Failed to connect Google Drive"
                                            }
                                        }
                                    }
                                ) {
                                    if (isBusy) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("Connect My Drive Root")
                                    }
                                }
                            }
                        }
                    }

                    MountTab.SAF_FALLBACK -> {
                        CalloutBox(
                            icon = Icons.Default.Warning,
                            tint = Color(0xFFFFB74D),
                            background = Color(0xFFFFB74D).copy(alpha = 0.12f),
                            text = "Android Scoped Storage Notice: The system picker blocks top-level drive roots, Download/, and Android/data. When the picker opens, navigate inside a specific subfolder (or create a new folder) before tapping \"Use this folder\"."
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(onClick = onDismiss) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    onDismiss()
                                    onRequestSafPicker(rootType)
                                }
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Android Folder Picker")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalloutBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    background: Color,
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .border(DesktopDensity.BorderWidth, tint.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 16.sp
        )
    }
}
