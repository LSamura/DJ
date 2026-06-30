# PROJECT_STATE.md

_Обновлено: 2026-06-30 | Sprint 1_

---

## Текущее состояние

**Sprint 1 завершён.** Каркас проекта создан полностью.

Проект:
- Открывается в Android Studio
- Собирается без ошибок
- Запускается на устройстве Android 8.0+ (minSdk 26)
- Готов к реализации Sprint 2

---

## Что реализовано

### Инфраструктура
- Multi-module Gradle проект (6 модулей: app, core, data, feature, service, ui)
- Version Catalog (`gradle/libs.versions.toml`)
- Hilt DI — полный граф зависимостей, все интерфейсы привязаны
- `DjLogger` на базе Timber — централизованное логирование

### Сервис
- `DjForegroundService` — запуск, уведомление, остановка
- `ServiceStateHolder` — shared StateFlow между сервисом и UI
- `NotificationHelper` — канал уведомлений, persistent notification
- `ServiceController` / `DjServiceController` — управление сервисом из UI

### Архитектурные слои (все интерфейсы финальной архитектуры)
- **Voice**: `AudioRecorder`, `SpeechRecognizer`, `GrammarBuilder` (stubs)
- **Intent**: `DjIntent` (10 вариантов), `IntentRecognizer` / `KeywordIntentRecognizer`
- **Command**: `DjCommand`, `CommandContext`, `CommandResult`, `CommandRegistry`, `CommandDispatcher`
- **Media**: `MediaRemote`, `MediaStateProvider`, `MediaPlaybackState`
- **Feedback**: `FeedbackManager` / `BeepFeedbackManager` (ToneGenerator)
- **Settings**: `DjSettings`, `SettingsRepository` / `DataStoreSettingsRepository`

### Команды (10 штук, все с безопасными реализациями)
| Команда | Intent | Триггеры |
|---|---|---|
| PauseCommand | Pause | пауза, стоп, остановить |
| PlayCommand | Play | играй, продолжи, воспроизведи |
| NextTrackCommand | Next | следующий, дальше |
| PreviousTrackCommand | Previous | предыдущий, назад |
| VolumeUpCommand | VolumeUp | громче, прибавь |
| VolumeDownCommand | VolumeDown | тише, убавь |
| NowPlayingCommand | QueryNowPlaying | что играет, что за песня |
| ArtistCommand | QueryArtist | кто исполнитель, чья песня |
| IsPlayingCommand | QueryIsPlaying | музыка играет, включена музыка |
| VolumeQueryCommand | QueryVolume | какая громкость, уровень звука |

### UI
- `MainScreen` — статус сервиса, кнопки Start/Stop, версия, результат команды
- `SettingsScreen` — автозапуск, показ DebugScreen
- `DebugScreen` — полная панель: сервис, аудио, ASR, медиасессия, журнал
- Navigation Compose: Main ↔ Settings, Main ↔ Debug
- Dark theme (Material3)

### Data
- `UnknownCommandLogger` / `CsvUnknownCommandLogger` — append-only CSV, ротация 512KB

---

## Что НЕ реализовано (ожидается в следующих Sprint)

| Компонент | Sprint |
|---|---|
| Кнопки ручного управления плеером | Sprint 2 |
| AndroidAudioRecorder (реальный AudioRecord) | Sprint 3 |
| VoskSpeechRecognizer (реальный Vosk SDK) | Sprint 4 |
| Полный голосовой цикл | Sprint 5 |
| SessionMediaRemote (реальный MediaController) | Sprint 7 |
| NotificationListenerService | Sprint 7 |
| Porcupine wake word | Будущее |

---

## Известные ограничения Sprint 1

1. **Воспроизведение не управляется** — `KeyEventMediaRemote` работает, но голос ещё не подключён
2. **Vosk не интегрирован** — ASR stub возвращает пустой поток
3. **AudioRecord не активен** — уровень сигнала всегда 0
4. **MediaStateProvider возвращает пустое состояние** — Sprint 7
5. **Разрешение на микрофон не запрашивается** — нужно в Sprint 3
6. **DebugScreen / Settings доступны** — навигация настроена, но в NavGraph нет кнопки на Debug (добавить в Sprint 2)
7. **`runBlocking` в `autoStartServiceIfNeeded`** — приемлемо для чтения начальных настроек, но в Sprint 3 стоит перенести в корутину

---

## Следующая задача

**Sprint 2: Ручной пульт управления**

Добавить кнопки Play / Pause / Next / Previous / Vol+ / Vol− в MainScreen и убедиться что `KeyEventMediaRemote` управляет реальными плеерами.
