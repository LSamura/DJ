package com.djassistant.ui.main

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.djassistant.service.ServiceMode
import com.djassistant.ui.components.StatusIndicator
import com.djassistant.ui.permissions.AppPermissions
import com.djassistant.ui.permissions.PermissionStatus
import com.djassistant.ui.permissions.findActivity
import com.djassistant.ui.theme.DjTeal
import com.djassistant.ui.theme.StatusRunning
import com.djassistant.ui.theme.StatusStopped

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToDebug: () -> Unit,
    appVersion: String,
    viewModel: MainViewModel = hiltViewModel()
) {
    val serviceMode by viewModel.serviceMode.collectAsStateWithLifecycle()
    val lastCommandInfo by viewModel.lastCommandInfo.collectAsStateWithLifecycle()
    val showDebugScreen by viewModel.showDebugScreen.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Permission status, refreshed whenever the screen resumes (e.g. returning
    // from the system settings screen) so the checklist stays accurate.
    var permissionStatus by remember { mutableStateOf(AppPermissions.status(context)) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionStatus = AppPermissions.status(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionStatus = AppPermissions.status(context)
        val denied = results.filterValues { !it }.keys.toList()
        if (denied.isEmpty()) {
            viewModel.onPermissionsGranted()
        } else {
            val activity = context.findActivity()
            val permanentlyDenied = activity != null && denied.any { perm ->
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
            }
            if (permanentlyDenied) {
                viewModel.onPermissionsPermanentlyDenied(denied)
                showSettingsDialog = true
            } else {
                viewModel.onPermissionsDenied(denied)
                snackbarMessage = "Без разрешений (${denied.joinToString { AppPermissions.displayName(it) }}) " +
                    "сервис не может работать"
            }
        }
    }

    fun onStartClicked() {
        val missing = AppPermissions.missing(context)
        if (missing.isEmpty()) {
            viewModel.startService()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Нужны разрешения") },
            text = {
                Text(
                    "Разрешения отключены. Чтобы DJ Assistant мог слушать команды, " +
                        "включите доступ к микрофону и уведомлениям в настройках приложения."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsDialog = false
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }) { Text("Открыть настройки") }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("Отмена") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("DJ Assistant") },
                actions = {
                    if (showDebugScreen) {
                        IconButton(onClick = onNavigateToDebug) {
                            Icon(Icons.Outlined.BugReport, contentDescription = "Отладка")
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            StatusCard(mode = serviceMode)

            Spacer(modifier = Modifier.height(16.dp))

            ReadinessChecklist(
                permissionStatus = permissionStatus,
                mode = serviceMode
            )

            Spacer(modifier = Modifier.height(32.dp))

            ServiceControls(
                mode = serviceMode,
                onStart = ::onStartClicked,
                onStop = viewModel::stopService
            )

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(
                visible = lastCommandInfo != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                lastCommandInfo?.let { info ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = info,
                            style = MaterialTheme.typography.bodyMedium,
                            color = DjTeal,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "v$appVersion",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun StatusCard(mode: ServiceMode) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusIndicator(mode = mode, size = 14.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Статус",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = mode.displayName(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ReadinessChecklist(
    permissionStatus: PermissionStatus,
    mode: ServiceMode
) {
    val serviceRunning = mode !is ServiceMode.Stopped && mode !is ServiceMode.Error
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ChecklistRow("Микрофон", permissionStatus.microphone)
            Spacer(modifier = Modifier.height(6.dp))
            ChecklistRow("Уведомления", permissionStatus.notifications)
            Spacer(modifier = Modifier.height(6.dp))
            ChecklistRow("Сервис", serviceRunning)
        }
    }
}

@Composable
private fun ChecklistRow(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (ok) StatusRunning else StatusStopped,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ServiceControls(
    mode: ServiceMode,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val isRunning = mode !is ServiceMode.Stopped && mode !is ServiceMode.Error

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onStart,
            enabled = !isRunning,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Запустить")
        }

        OutlinedButton(
            onClick = onStop,
            enabled = isRunning,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Остановить")
        }
    }
}
