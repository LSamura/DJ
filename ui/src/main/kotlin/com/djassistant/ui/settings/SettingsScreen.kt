package com.djassistant.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.djassistant.feature.voice.MicrophoneSource
import com.djassistant.feature.voice.VoiceListeningMode
import com.djassistant.feature.voice.WakeWordPhrases
import com.djassistant.ui.permissions.NotificationAccess
import com.djassistant.ui.permissions.OverlayAccess
import com.djassistant.ui.theme.StatusRunning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationAccessEnabled by remember { mutableStateOf(NotificationAccess.isEnabled(context)) }
    var overlayAccessEnabled by remember { mutableStateOf(OverlayAccess.isEnabled(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccessEnabled = NotificationAccess.isEnabled(context)
                overlayAccessEnabled = OverlayAccess.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
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
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            ListItem(
                headlineContent = { Text("Автозапуск сервиса") },
                supportingContent = { Text("Запускать при открытии приложения") },
                trailingContent = {
                    Switch(
                        checked = settings.autoStartService,
                        onCheckedChange = viewModel::setAutoStartService
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ListItem(
                headlineContent = { Text("Экран отладки") },
                supportingContent = { Text("Показывать отладочную информацию") },
                trailingContent = {
                    Switch(
                        checked = settings.showDebugScreen,
                        onCheckedChange = viewModel::setShowDebugScreen
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ListItem(
                headlineContent = { Text("Звуковые сигналы") },
                supportingContent = { Text("Короткий сигнал при активации и выполнении команды") },
                trailingContent = {
                    Switch(
                        checked = settings.soundFeedbackEnabled,
                        onCheckedChange = viewModel::setSoundFeedbackEnabled
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            if (overlayAccessEnabled) {
                ListItem(
                    headlineContent = { Text("Оверлей поверх экрана") },
                    supportingContent = { Text("Предоставлен — окно \"Слушаю...\" будет показываться") },
                    trailingContent = {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Предоставлен",
                            tint = StatusRunning
                        )
                    }
                )
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Оверлей поверх экрана",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Без этого разрешения индикатор \"Слушаю...\" во время " +
                                "диалогового окна показан не будет — голосовое управление " +
                                "продолжит работать, просто без визуальной подсказки.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(onClick = { context.startActivity(OverlayAccess.settingsIntent(context)) }) {
                                Text("Открыть настройки")
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            if (notificationAccessEnabled) {
                ListItem(
                    headlineContent = { Text("Доступ к медиасессиям") },
                    supportingContent = { Text("Предоставлен — Media Layer видит активные плееры") },
                    trailingContent = {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Предоставлен",
                            tint = StatusRunning
                        )
                    }
                )
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Доступ к медиасессиям",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Android требует предоставить доступ к медиасессиям, " +
                                "чтобы приложение могло управлять воспроизведением и " +
                                "получать информацию о текущем треке.\n\n" +
                                "Приложение не читает содержимое ваших уведомлений.\n\n" +
                                "Разрешение используется только для обнаружения активной " +
                                "медиасессии (MediaSession).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(onClick = { context.startActivity(NotificationAccess.settingsIntent()) }) {
                                Text("Открыть настройки")
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ListItem(
                headlineContent = { Text("Режим прослушивания") },
                supportingContent = { Text("Continuous слушает всегда; Wake Mode ждёт \"Диджей\"") }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = settings.listeningMode == VoiceListeningMode.CONTINUOUS,
                    onClick = { viewModel.setListeningMode(VoiceListeningMode.CONTINUOUS) },
                    label = { Text("Continuous") }
                )
                FilterChip(
                    selected = settings.listeningMode == VoiceListeningMode.WAKE_WORD,
                    onClick = { viewModel.setListeningMode(VoiceListeningMode.WAKE_WORD) },
                    label = { Text("Wake Mode") }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            var thresholdSliderValue by remember(settings.voskConfidenceThreshold) {
                mutableFloatStateOf(settings.voskConfidenceThreshold)
            }
            ListItem(
                headlineContent = { Text("Порог уверенности распознавания") },
                supportingContent = {
                    Text("Команды ниже ${(thresholdSliderValue * 100).toInt()}% confidence не выполняются")
                }
            )
            Slider(
                value = thresholdSliderValue,
                onValueChange = { thresholdSliderValue = it },
                onValueChangeFinished = { viewModel.setVoskConfidenceThreshold(thresholdSliderValue) },
                valueRange = 0.5f..0.95f,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            var windowSliderValue by remember(settings.dialogWindowSeconds) {
                mutableIntStateOf(settings.dialogWindowSeconds)
            }
            ListItem(
                headlineContent = { Text("Окно диалога после активации") },
                supportingContent = { Text("$windowSliderValue сек ожидания следующей команды (Wake Mode)") }
            )
            Slider(
                value = windowSliderValue.toFloat(),
                onValueChange = { windowSliderValue = it.toInt() },
                onValueChangeFinished = { viewModel.setDialogWindowSeconds(windowSliderValue) },
                valueRange = 3f..15f,
                steps = 11,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ListItem(
                headlineContent = { Text("Источник микрофона") },
                supportingContent = {
                    Text(
                        "Auto и Phone никогда не включают Bluetooth-гарнитуру; " +
                            "Bluetooth используется только если выбран явно"
                    )
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = settings.microphoneSource == MicrophoneSource.AUTO,
                    onClick = { viewModel.setMicrophoneSource(MicrophoneSource.AUTO) },
                    label = { Text("Auto") }
                )
                FilterChip(
                    selected = settings.microphoneSource == MicrophoneSource.PHONE,
                    onClick = { viewModel.setMicrophoneSource(MicrophoneSource.PHONE) },
                    label = { Text("Phone") }
                )
                FilterChip(
                    selected = settings.microphoneSource == MicrophoneSource.BLUETOOTH,
                    onClick = { viewModel.setMicrophoneSource(MicrophoneSource.BLUETOOTH) },
                    label = { Text("Bluetooth") }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ListItem(
                headlineContent = { Text("Wake Word") },
                supportingContent = {
                    Text(
                        "Активационная фраза для Wake Mode (движок — Porcupine). " +
                            "Список расширяемый — пока доступна только одна фраза."
                    )
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WakeWordPhrases.ALL.forEach { phrase ->
                    FilterChip(
                        selected = settings.wakeWordPhraseId == phrase.id,
                        onClick = { viewModel.setWakeWordPhraseId(phrase.id) },
                        label = { Text(phrase.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            var accessKeyValue by remember(settings.porcupineAccessKey) {
                mutableStateOf(settings.porcupineAccessKey)
            }
            OutlinedTextField(
                value = accessKeyValue,
                onValueChange = { accessKeyValue = it },
                label = { Text("Porcupine Access Key") },
                supportingText = {
                    Text(
                        "Бесплатный ключ на console.picovoice.ai. Без него и без " +
                            "обученного файла ключевого слова Wake Mode не сможет " +
                            "услышать активацию — см. PROJECT_STATE.md."
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = { viewModel.setPorcupineAccessKey(accessKeyValue) }) {
                    Text("Сохранить ключ")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}
