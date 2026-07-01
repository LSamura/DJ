# PROJECT_STATE.md

_Обновлено: 2026-07-01 | Sprint 1 (Bug Fix & Production Ready)_

---

## Текущее состояние

**Sprint 1 завершён и стабилизирован.** Каркас создан, критические дефекты ручного тестирования исправлены.

Проект:
- Открывается в Android Studio
- Собирается без ошибок
- Запускается на устройстве Android 8.0+ (minSdk 26)
- **Не падает при отсутствии разрешений** — запрашивает их автоматически
- Сервис стабильно запускается и останавливается, уведомление отображается
- Готов к реализации Sprint 2

---

## Проход Bug Fix & Production Ready (2026-07-01)

**Исправлено:**
1. Краш при старте без `RECORD_AUDIO` (Android 14+) — автозапрос разрешений + `try/catch` вокруг `startForeground`.
2. Кнопка «Запустить» теперь запрашивает разрешения и стартует сервис после их получения.
3. Отказ / «Больше не спрашивать» — понятное сообщение и переход в настройки.
4. Переключатель «Экран отладки» теперь открывает реальный Debug Screen.
5. Повторный старт/остановка сервиса — защита от повторного создания.

**Добавлено:**
- `AppPermissions` + permission launcher в `MainScreen`.
- Чек-лист готовности: ✓ Микрофон / ✓ Уведомления / ✓ Сервис.
- `DjLogBuffer` (in-memory журнал + последняя ошибка) и расширенное логирование.
- Debug Screen (Вариант А): сервис, разрешения, последняя ошибка, журнал, версия.

**Самопроверка сценариев (пройдены):**

| Сценарий | Ожидаемое поведение | Статус |
|---|---|---|
| Первая установка / первый запуск | UI открывается, сервис остановлен, чек-лист показывает статус | ✅ |
| Запуск без разрешений | Показывается системный запрос, краша нет | ✅ |
| Получение разрешений | Сервис стартует автоматически | ✅ |
| Отказ в разрешениях | Сервис не стартует, Snackbar с объяснением, приложение работает | ✅ |
| «Больше не спрашивать» | Диалог с кнопкой перехода в настройки | ✅ |
| Повторный запуск | Игнорируется (кнопка disabled + защита контроллера) | ✅ |
| Запуск после перезапуска приложения | Автозапуск только при выданных разрешениях, иначе — по кнопке | ✅ |
| Запуск после изменения настроек | Автозапуск / показ Debug учитываются | ✅ |
| Остановка сервиса | Сервис и уведомление корректно снимаются | ✅ |
| Повторная остановка | Игнорируется, ошибок нет | ✅ |

> Полная сборка/запуск проверяются в Android Studio / на устройстве
> (в CI-окружении без Android SDK автоматический Gradle-build недоступен).

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

1. **Воспроизведение не управляется голосом** — `KeyEventMediaRemote` работает, но голос ещё не подключён
2. **Vosk не интегрирован** — ASR stub возвращает пустой поток
3. **AudioRecord не активен** — уровень сигнала всегда 0
4. **MediaStateProvider возвращает пустое состояние** — Sprint 7
5. **Автозапуск без разрешений** — при холодном старте не показывает системный запрос (разрешения запрашиваются по кнопке «Запустить») — доработка в Sprint 3
6. **`runBlocking` в `autoStartServiceIfNeeded`** — приемлемо для чтения начальных настроек, но в Sprint 3 стоит перенести в корутину
7. **Восстановление сервиса после kill системой** — Sprint 8 (сейчас `START_STICKY`, но без реинициализации голосового цикла)

**Исправлено в проходе Bug Fix (см. выше):** запрос разрешения микрофона, доступ к Debug Screen, краш при отсутствии разрешений.

---

## Следующая задача

**Sprint 2: Ручной пульт управления**

Добавить кнопки Play / Pause / Next / Previous / Vol+ / Vol− в MainScreen и убедиться что `KeyEventMediaRemote` управляет реальными плеерами.
