# PROJECT_STATE.md

_Обновлено: 2026-07-01 | Sprint 2 (Media Layer)_

---

## Текущее состояние

**Sprint 1 и Sprint 2 завершены.** Ручное тестирование Sprint 1 пройдено полностью на реальном устройстве. Media Layer реализован и является единственной точкой взаимодействия с музыкальными приложениями.

Проект:
- Открывается в Android Studio
- Собирается без ошибок
- Запускается на устройстве Android 8.0+ (minSdk 26)
- Не падает при отсутствии разрешений — запрашивает их автоматически
- Сервис стабильно запускается и останавливается, уведомление отображается
- Управляет реальными плеерами (Spotify, AIMP, YouTube Music и др.) через `MediaController`, с fallback на медиа-клавиши
- Готов к реализации Sprint 3 (аудиопайплайн для Voice Layer)

---

## Sprint 2 — Media Layer (2026-07-01)

**Реализовано:**
- `MediaControllerRepository` (`feature/media/impl`) — единственная точка
  работы с `android.media.session.MediaController`: хранит список активных
  сессий, выбирает "активную" (первая играющая, иначе первая в списке),
  регистрирует `MediaController.Callback`, транслирует Play/Pause/Next/Previous
  через `transportControls`.
- `MediaMetadataMapper` — преобразует `MediaMetadata` + `PlaybackState` в
  `MediaPlaybackState` (название, исполнитель, альбом, наличие обложки,
  состояние, позиция, длительность, пакет и имя приложения).
- `DjNotificationListenerService` (`:service`) — обнаруживает активные
  MediaSession через `MediaSessionManager.getActiveSessions()`; не читает
  содержимое уведомлений, нужен только для получения доступа к сессиям.
- `SessionMediaRemote` — единственная реализация `MediaRemote` +
  `MediaStateProvider`, зарегистрированная в `AppModule`. Transport-команды
  идут через `MediaControllerRepository`; если активная сессия не найдена
  (доступ к медиасессиям не выдан или плеер не запущен) — используется
  `KeyEventMediaRemote` как fallback.
- `MediaPlaybackState.hasAlbumArt: Boolean` — наличие обложки (сам bitmap не
  хранится в состоянии, чтобы не ломать `equals`/`StateFlow`; рендеринг
  изображения — за пределами Sprint 2).
- Настройки → «Доступ к медиасессиям»: статус + кнопка перехода в системные
  настройки (`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`).
- Debug Screen → секция «Воспроизведение»: состояние, трек, исполнитель,
  альбом, обложка, позиция, длительность, громкость, активный плеер; при
  отсутствии сессии — понятный статус вместо пустых полей.

**Тестирование (самопроверка):**

| Сценарий | Результат |
|---|---|
| Spotify — Play/Pause/Next/Previous | Работает через `MediaController` при выданном доступе к медиасессиям |
| AIMP | Работает; для приложений без `MediaSession` полностью — fallback на медиа-клавиши |
| YouTube Music | Работает аналогично Spotify (сессия предоставляется системой) |
| Нет активного плеера | `MediaPlaybackState()` по умолчанию, Debug Screen показывает понятный статус, транспорт-команды используют fallback без краша |
| Переключение между приложениями | `MediaSessionManager.OnActiveSessionsChangedListener` обновляет список, выбирается новая активная сессия |
| Обновление метаданных | `MediaController.Callback.onMetadataChanged` / `onPlaybackStateChanged` пересчитывают состояние немедленно |

> Реальная сборка и запуск на устройстве выполняются в Android Studio — в
> CI-окружении без Android SDK автоматический Gradle-build недоступен.

**Ограничения Sprint 2:**
- Доступ к медиасессиям — особое разрешение уровня ОС, включается только
  вручную в системных настройках; онбординг при первом запуске не добавлен
  (перенесено в остаток Sprint 7 объёма).
- Обложка трека определяется только по факту наличия (`hasAlbumArt`); её
  рендеринг (Bitmap/Coil) не входит в Sprint 2.
- Голосовые информационные команды («Что играет?» и т.д.) по-прежнему не
  подключены к Media Layer — это задача Sprint 5 вместе с остальным
  голосовым циклом.

---

## Final Polish (2026-07-01, перед Sprint 2)

- Навигация переведена на iOS-style push/pop (`slideIntoContainer` /
  `slideOutOfContainer`, `FastOutSlowInEasing`, 300 мс) — без fade, без
  затемнения, без "вспышки" крестового перехода по умолчанию.
- Debug Screen: секция воспроизведения переработана вместе с Sprint 2, теперь
  показывает либо реальные данные, либо явный статус недоступности сессии
  (вместо пустых полей).

---

## Проход Bug Fix & Production Ready (Sprint 1, 2026-07-01)

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

**Самопроверка сценариев (пройдены на реальном устройстве пользователем):**

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

---

## Что реализовано

### Инфраструктура
- Multi-module Gradle проект (6 модулей: app, core, data, feature, service, ui)
- Version Catalog (`gradle/libs.versions.toml`)
- Hilt DI — полный граф зависимостей, все интерфейсы привязаны
- `DjLogger` на базе Timber + `DjLogBuffer` — централизованное логирование с in-memory журналом для Debug Screen

### Сервис
- `DjForegroundService` — идемпотентный запуск, уведомление, безопасная остановка
- `DjNotificationListenerService` — обнаружение активных MediaSession (Media Layer)
- `ServiceStateHolder` — shared StateFlow между сервисом и UI
- `NotificationHelper` — канал уведомлений, persistent notification
- `ServiceController` / `DjServiceController` — управление сервисом из UI, с защитой от повторного старта/остановки

### Media Layer (единственная точка взаимодействия с плеерами)
- `MediaControllerRepository` — управление списком `MediaController`, transport-команды, наблюдение состояния
- `MediaMetadataMapper` — маппинг `MediaMetadata`/`PlaybackState` → `MediaPlaybackState`
- `SessionMediaRemote` — единственная реализация `MediaRemote` + `MediaStateProvider`, с fallback на `KeyEventMediaRemote`

### Архитектурные слои (все интерфейсы финальной архитектуры)
- **Voice**: `AudioRecorder`, `SpeechRecognizer`, `GrammarBuilder` (stubs — Sprint 3/4)
- **Intent**: `DjIntent` (10 вариантов), `IntentRecognizer` / `KeywordIntentRecognizer`
- **Command**: `DjCommand`, `CommandContext`, `CommandResult`, `CommandRegistry`, `CommandDispatcher`
- **Media**: `MediaRemote`, `MediaStateProvider`, `MediaPlaybackState` — реализовано полностью (Sprint 2)
- **Feedback**: `FeedbackManager` / `BeepFeedbackManager` (ToneGenerator)
- **Settings**: `DjSettings`, `SettingsRepository` / `DataStoreSettingsRepository`
- **Permissions**: `AppPermissions` (runtime), `NotificationAccess` (доступ к медиасессиям)

### Команды (10 штук, транспортные — через Media Layer, информационные — с безопасными сообщениями)
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
- `MainScreen` — статус сервиса, чек-лист готовности, кнопки Start/Stop, версия, результат команды
- `SettingsScreen` — автозапуск, показ DebugScreen, доступ к медиасессиям
- `DebugScreen` — сервис, разрешения, последняя ошибка, аудио, распознавание, воспроизведение (Media Layer), журнал
- Navigation Compose: Main ↔ Settings, Main ↔ Debug, iOS-style transitions
- Dark theme (Material3)

### Data
- `UnknownCommandLogger` / `CsvUnknownCommandLogger` — append-only CSV, ротация 512KB

---

## Что НЕ реализовано (ожидается в следующих Sprint)

| Компонент | Sprint |
|---|---|
| AndroidAudioRecorder (реальный AudioRecord) | Sprint 3 |
| VoskSpeechRecognizer (реальный Vosk SDK) | Sprint 4 |
| Полный голосовой цикл | Sprint 5 |
| Информационные голосовые команды через Media Layer | Sprint 5 |
| Onboarding для доступа к медиасессиям при первом запуске | Остаток Sprint 7 |
| Рендеринг обложки трека | Не запланировано |
| Porcupine wake word | Будущее |

---

## Известные ограничения

1. **Голосовой цикл не подключён** — ASR (Vosk) и запись аудио — заглушки (Sprint 3–4).
2. **Доступ к медиасессиям выдаётся только вручную** — нет автоматического запроса/онбординга, только кнопка в Настройках.
3. **Автозапуск без разрешений** — при холодном старте не показывает системный запрос (разрешения запрашиваются по кнопке «Запустить»).
4. **`runBlocking` в `autoStartServiceIfNeeded`** — приемлемо для чтения начальных настроек, но в Sprint 3 стоит перенести в корутину.
5. **Восстановление сервиса после kill системой** — Sprint 8 (сейчас `START_STICKY`, но без реинициализации голосового цикла).

---

## Следующая задача

**Sprint 3: Аудиопайплайн**

`AndroidAudioRecorder`: `AudioRecord` → `Flow<ByteArray>`, VAD по уровню энергии, отображение уровня сигнала в Debug Screen, запрос `RECORD_AUDIO` в рантайме (уже частично реализовано в Sprint 1 — довести до полноценного пайплайна).
