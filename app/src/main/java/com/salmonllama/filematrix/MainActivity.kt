package com.salmonllama.filematrix

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.salmonllama.filematrix.core.designsystem.theme.FileMatrixTheme
import com.salmonllama.filematrix.core.model.GooglebookRootType
import com.salmonllama.filematrix.core.model.StorageRoot
import com.salmonllama.filematrix.feature.workspace.WorkspaceShell
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val app get() = FileMatrixApplication.instance

    private var hasAllFilesAccessState by mutableStateOf(false)
    private var refreshSignalState by mutableLongStateOf(0L)
    private var pendingNavigateRootState by mutableStateOf<StorageRoot?>(null)
    private var statusBannerState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasAllFilesAccessState = app.vfs.hasAllFilesAccess()
        handleOAuthRedirectIntent(intent)

        setContent {
            FileMatrixTheme(darkTheme = true) {
                val coroutineScope = rememberCoroutineScope()
                var pendingSafMountType by remember { mutableStateOf<GooglebookRootType?>(null) }

                val openDocumentTreeLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree()
                ) { treeUri: Uri? ->
                    val rootType = pendingSafMountType
                    if (treeUri != null && rootType != null) {
                        try {
                            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            contentResolver.takePersistableUriPermission(treeUri, takeFlags)
                        } catch (_: Exception) {
                        }

                        coroutineScope.launch {
                            val mounted = app.vfs.registerSafRoot(
                                treeUriString = treeUri.toString(),
                                rootType = rootType,
                                displayName = rootType.title
                            )
                            pendingNavigateRootState = mounted
                            refreshSignalState += 1L
                        }
                    }
                    pendingSafMountType = null
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    WorkspaceShell(
                        vfs = app.vfs,
                        transferManager = app.transferManager,
                        sessionManager = app.sessionManager,
                        hasAllFilesAccess = hasAllFilesAccessState,
                        refreshSignal = refreshSignalState,
                        pendingNavigateRoot = pendingNavigateRootState,
                        onConsumedNavigateRoot = { pendingNavigateRootState = null },
                        statusBannerMessage = statusBannerState,
                        onDismissStatusBanner = { statusBannerState = null },
                        onRequestAllFilesAccess = { launchAllFilesAccessSettings() },
                        onRequestSafPicker = { rootType ->
                            pendingSafMountType = rootType
                            openDocumentTreeLauncher.launch(null)
                        },
                        onLaunchOAuthUrl = { authUrl ->
                            launchBrowserForOAuth(authUrl)
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val grantedNow = app.vfs.hasAllFilesAccess()
        if (grantedNow != hasAllFilesAccessState) {
            hasAllFilesAccessState = grantedNow
            app.vfs.refreshLocalVolumes()
            refreshSignalState += 1L
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthRedirectIntent(intent)
    }

    private fun launchAllFilesAccessSettings() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (e: Exception) {
                statusBannerState = "Unable to open All Files Access settings: ${e.message}"
            }
        }
    }

    private fun launchBrowserForOAuth(authUrl: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
            startActivity(browserIntent)
        } catch (e: Exception) {
            statusBannerState = "Unable to open browser for Google Sign-In: ${e.message}"
        }
    }

    private fun handleOAuthRedirectIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "com.salmonllama.filematrix" || data.path != "/oauth2redirect") return

        val oauthError = data.getQueryParameter("error")
        if (!oauthError.isNullOrBlank()) {
            statusBannerState = "Google Drive sign-in cancelled or denied: $oauthError"
            return
        }

        val code = data.getQueryParameter("code") ?: return
        val state = data.getQueryParameter("state")

        lifecycleScope.launch {
            val result = app.vfs.completeGoogleDriveOAuth(code, state)
            result.onSuccess { mountedRoot ->
                statusBannerState = null
                pendingNavigateRootState = mountedRoot
                refreshSignalState += 1L
            }.onFailure { err ->
                statusBannerState = "Google Drive sign-in failed: ${err.message}"
            }
        }
    }
}
