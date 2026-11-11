package com.salmonllama.filematrix

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.salmonllama.filematrix.core.designsystem.theme.FileMatrixTheme
import com.salmonllama.filematrix.core.model.GooglebookRootType
import com.salmonllama.filematrix.feature.workspace.WorkspaceShell
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val app get() = FileMatrixApplication.instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FileMatrixTheme(darkTheme = true) {
                val coroutineScope = rememberCoroutineScope()
                var pendingMountType by remember { mutableStateOf<GooglebookRootType?>(null) }

                val openDocumentTreeLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree()
                ) { treeUri: Uri? ->
                    val rootType = pendingMountType
                    if (treeUri != null && rootType != null) {
                        try {
                            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            contentResolver.takePersistableUriPermission(treeUri, takeFlags)
                        } catch (_: Exception) {}

                        coroutineScope.launch {
                            app.vfs.registerSafRoot(
                                treeUriString = treeUri.toString(),
                                rootType = rootType,
                                displayName = rootType.title
                            )
                        }
                    }
                    pendingMountType = null
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    WorkspaceShell(
                        vfs = app.vfs,
                        transferManager = app.transferManager,
                        sessionManager = app.sessionManager,
                        onRequestMountRoot = { rootType ->
                            pendingMountType = rootType
                            openDocumentTreeLauncher.launch(null)
                        }
                    )
                }
            }
        }
    }
}
