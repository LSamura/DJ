package com.djassistant.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.djassistant.core.logging.DjLogBuffer
import com.djassistant.feature.media.PlaybackSource
import com.djassistant.ui.components.AudioLevelBar
import com.djassistant.ui.components.StatusIndicator
import com.djassistant.ui.permissions.AppPermissions
import com.djassistant.ui.permissions.NotificationAccess
import com.djassistant.ui.theme.DjRed
import com.djassistant.ui.theme.SurfaceVariantDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    appVersion: String,
    onNavigateBack: () -> Unit,
    viewModel: DebugViewModel = hiltViewModel()
) {
    val serviceMode by viewModel.serviceMode.collectAsStateWithLifecycle()
    val audioLevel by viewModel.audioLevel.collectAsStateWithLifecycle()
    val lastText by viewModel.lastRecognizedText.collectAsStateWithLifecycle()
    val lastInfo by viewModel.lastCommandInfo.collectAsStateWithLifecycle()
    val mediaState by viewModel.mediaState.collectAsStateWithLifecycle()
    val unknownCommands by viewModel.recentUnknownCommands.collectAsStateWithLifecycle()
    val lastError by viewModel.lastError.collectAsStateWithLifecycle()
    val recentLogs by viewModel.recentLogs.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionStatus by remember { mutableStateOf(AppPermissions.status(context)) }
    var notificationAccessEnabled by remember { mutableStateOf(NotificationAccess.isEnabled(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionStatus = AppPermissions.status(context)
                notificationAccessEnabled = NotificationAccess.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Отладка", color = DjRed)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {

            DebugSection("Приложение") {
                DebugRow("Версия") { DebugValue("v$appVersion") }
            }

            DebugSection("Сервис") {
                DebugRow("Режим") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusIndicator(mode = serviceMode, size = 12.dp)
                        Text(serviceMode.displayName(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            DebugSection("Разрешения") {
                DebugRow("Микрофон") { DebugValue(if (permissionStatus.microphone) "Выдано" else "Нет") }
                DebugRow("Уведомления") { DebugValue(if (permissionStatus.notifications) "Выдано" else "Нет") }
                DebugRow("Доступ к медиасессиям") { DebugValue(if (notificationAccessEnabled) "Выдано" else "Нет") }
            }

            DebugSection("Последняя ошибка") {
                if (lastError == null) {
                    Text(
                        "Ошибок нет",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                } else {
                    lastError?.let { err ->
                        Text(
                            text = "[${formatTime(err.timestampMs)}] ${err.tag}",
                            style = MaterialTheme.typography.labelSmall,
                            color = DjRed
                        )
                        Text(text = err.message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            DebugSection("Аудио") {
                DebugLabel("Уровень сигнала")
                Spacer(modifier = Modifier.height(4.dp))
                AudioLevelBar(level = audioLevel, modifier = Modifier.fillMaxWidth())
            }

            DebugSection("Распознавание") {
                DebugRow("Последний текст") { DebugValue(lastText.ifBlank { "—" }) }
                DebugRow("Последний результат") { DebugValue(lastInfo ?: "—") }
            }

            DebugSection("Воспроизведение") {
                DebugRow("Источник") { DebugValue(mediaState.playbackSource.displayName()) }
                if (mediaState.activeAppPackage == null) {
                    Text(
                        text = "Недоступно — нет активной медиасессии.\n" +
                            "Нужен доступ к медиасессиям (Настройки) и запущенный плеер.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    DebugRow("Состояние") { DebugValue(if (mediaState.isPlaying) "Играет" else "Пауза") }
                    DebugRow("Трек") { DebugValue(mediaState.trackTitle ?: "—") }
                    DebugRow("Исполнитель") { DebugValue(mediaState.artist ?: "—") }
                    DebugRow("Альбом") { DebugValue(mediaState.album ?: "—") }
                    DebugRow("Обложка") { DebugValue(if (mediaState.hasAlbumArt) "Есть" else "Нет") }
                    DebugRow("Позиция") { DebugValue(formatDuration(mediaState.positionMs)) }
                    DebugRow("Длительность") { DebugValue(formatDuration(mediaState.durationMs)) }
                    DebugRow("Громкость") { DebugValue("${mediaState.volumePercent}%") }
                    DebugRow("Плеер") { DebugValue(mediaState.activeAppName ?: mediaState.activeAppPackage ?: "—") }
                }
            }

            DebugSection("Журнал (${recentLogs.size})") {
                if (recentLogs.isEmpty()) {
                    Text(
                        "Нет записей",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                } else {
                    recentLogs.takeLast(15).reversed().forEach { entry ->
                        Text(
                            text = "[${formatTime(entry.timestampMs)}] ${entry.level} ${entry.tag}: ${entry.message}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entry.level == DjLogBuffer.Level.ERROR) DjRed
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }

            DebugSection("Неизвестные команды (${unknownCommands.size})") {
                if (unknownCommands.isEmpty()) {
                    Text("Нет записей", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                } else {
                    unknownCommands.forEach { entry ->
                        Text(
                            text = "\"${entry.rawText}\"",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = viewModel::clearLog) {
                    Text("Очистить журнал")
                }
                Text(
                    text = viewModel.logExportPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

private fun formatTime(timestampMs: Long): String = timeFormat.format(Date(timestampMs))

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "—"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun PlaybackSource.displayName(): String = when (this) {
    PlaybackSource.MEDIA_SESSION -> "MediaSession"
    PlaybackSource.KEY_EVENT_FALLBACK -> "Резерв (медиа-клавиши)"
    PlaybackSource.NO_ACTIVE_SESSION -> "Нет активной сессии"
}

@Composable
private fun DebugSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceVariantDark, shape = MaterialTheme.shapes.medium)
                .padding(12.dp)
        ) {
            content()
        }
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun DebugRow(label: String, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        DebugLabel(label)
        value()
    }
}

@Composable
private fun DebugLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
}

@Composable
private fun DebugValue(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall)
}
