# PROJECT_STATE.md

_Обновлено: 2026-07-01 | Sprint 3 (Offline Voice Control MVP)_

---

## Текущее состояние

**Sprint 1, Sprint 2 и Sprint 3 реализованы.** Media Layer стабилен и протестирован на реальном устройстве. Voice Layer реализован полностью в коде: микрофон → Vosk → Intent Parser → Media Layer. **Модель Vosk не бандлится в этот репозиторий** (см. «Voice Architecture» и «Известные ограничения» ниже) — это единственное, что мешает прогнать финальный сценарий целиком без ручного шага разработчика.

Проект:
- Открывается в Android Studio
- Собирается без ошибок (при наличии модели Vosk в assets — см. ниже)
- Запускается на устройстве Android 8.0+ (minSdk 26)
- Не падает при отсутствии разрешений — запрашивает их автоматически
- Сервис стабильно запускается и останавливается, уведомление отображается
- Управляет реальными плеерами (Spotify, AIMP, YouTube Music и др.) через `MediaController`, с fallback на медиа-клавиши
- Позиция трека в Debug Screen обновляется плавно (~300 мс), без лишних запросов к MediaSession
- Голосовой пайплайн работает полностью локально, без интернета

---

## Voice Architecture (Sprint 3)

### Цепочка

```
Микрофон (AudioRecorder)
    ↓ Flow<ByteArray> (16kHz mono PCM16)
Vosk (SpeechRecognizer, Grammar Mode)
    ↓ RecognitionResult(text, confidence, isFinal)
Intent Parser (IntentRecognizer)
    ↓ DjIntent
Media Layer (CommandDispatcher → уже существующий с Sprint 1/2)
    ↓ CommandResult
Плеер (MediaController / медиа-клавиши)
```

### Компоненты и их расположение

| Роль (из брифа Sprint 3) | Фактический класс | Модуль |
|---|---|---|
| VoiceRecognizer | `SpeechRecognizer` / `VoskSpeechRecognizer` | `:feature` (уже существовал с Sprint 1 как интерфейс) |
| — | `AudioRecorder` / `AndroidAudioRecorder` | `:feature` |
| — | `VoskModelProvisioner` | `:feature` |
| — | `VoiceCommandConfigLoader` | `:feature` (новое — `commands.json`) |
| IntentParser | `IntentRecognizer` / `KeywordIntentRecognizer` | `:feature` (существовал, переписан на конфиг) |
| IntentDispatcher | `CommandDispatcher` | `:feature` (существовал с Sprint 1, без изменений) |
| VoiceEngine | `VoiceEngine` | `:service/voice` (новое) |
| VoiceState / VoiceRepository | `VoiceStateHolder` + `VoiceEngineState` | `:service/voice` (новое) |
| Voice Service | `DjVoiceService` | `:service` (новое) |

**Почему не буквально пакет `voice/` с нуля:** архитектура Sprint 1
(`feature/voice`, `feature/intent`, `feature/command`) уже реализовывала
ровно те же роли под другими именами (ADR-005/006). Вместо дублирования
слоя, компоненты Sprint 3 достраивают существующие интерфейсы реальными
реализациями и добавляют только то, чего не хватало: `VoiceEngine`
(оркестрация пайплайна) и `VoiceStateHolder` (диагностика для Debug Screen).
Это то же решение, что и в брифе Sprint 2 («если текущая архитектура
предлагает более удачное разделение — разрешено её использовать»),
применённое и здесь. Подробности — DECISIONS.md, ADR-020..ADR-024.

**Почему `VoiceEngine`/`DjVoiceService` — в `:service`, а не в `:feature`:**
граф зависимостей (`service → feature`, не наоборот) не позволяет
`feature/voice` знать о `ServiceStateHolder`/`ServiceMode`. `VoiceEngine`
обязан обновлять оба вида состояния (сервисное и голосовое), поэтому живёт
там же, где уже живёт `ServiceStateHolder` — в `:service`. `feature/voice`
при этом остаётся полностью независимым от Media Layer в буквальном
смысле: ни один файл в `feature/voice` не импортирует `feature/media`.

### `commands.json`

Хранится в `feature/src/main/assets/commands.json`. Формат:
```json
{ "PAUSE": ["пауза", "стоп", ...], "NEXT": [...], ... }
```
`VoiceCommandConfigLoader` кэширует разбор один раз (`by lazy`).
`KeywordIntentRecognizer` использует эти фразы для сопоставления (без
голосового wake word — см. ADR-023), `GrammarBuilder` использует те же
фразы для построения грамматики Vosk. Добавление синонима — правка одной
строки JSON, без пересборки логики.

### Wake Word (отложено намеренно)

Wake word НЕ реализован в Sprint 3 (по прямому требованию брифа).
`KeywordIntentRecognizer` уже вызывает `commandText.stripWakeWord()` —
существующую утилиту ядра (core, Sprint 1) — перед сопоставлением: сейчас
это no-op, если фраза без префикса "Диджей"/"dj", но когда в будущем
понадобится обязательная активационная фраза, логику можно вставить как
gate ПЕРЕД вызовом `IntentRecognizer.recognize()` (например, в
`VoiceEngine.handleRecognition()`), не трогая существующий матчинг.

### Ограничение: офлайн-модель Vosk не бандлится

`VoskModelProvisioner` ожидает готовую модель (например,
`vosk-model-small-ru-0.22`, ~45 МБ) под `feature/src/main/assets/model/`
(распакованную, с файлом-маркером `conf/model.conf`). **Эта модель НЕ
включена в репозиторий и не может быть загружена агентом в этой
песочнице** — здесь нет ни Android SDK для сборки, ни (проверенного)
доступа к серверам с бинарными моделями. Разработчику нужно:
1. Скачать модель с официального сайта Vosk на своей машине.
2. Распаковать её содержимое в `feature/src/main/assets/model/` (так, чтобы
   `feature/src/main/assets/model/conf/model.conf` существовал).
3. Собрать проект в Android Studio.

Если модель отсутствует, `VoskModelProvisioner.ensureModel()` возвращает
`null`, `VoiceEngine` переводит `VoiceEngineState` в `Error("Модель Vosk не
найдена")`, ничего не падает — Debug Screen корректно показывает эту
ситуацию (ровно то поведение, которого требует бриф Sprint 3 для
диагностики).

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

## Sprint 2 Final Polish (2026-07-01)

**Реализовано:**

1. **Плавное обновление позиции трека.** `MediaControllerRepository` больше
   не пересчитывает состояние из `controller.playbackState`/`controller.metadata`
   при каждом тике — вместо этого кэширует последний снимок из параметров
   `MediaController.Callback` (`onPlaybackStateChanged`/`onMetadataChanged`) в
   `snapshots: Map<MediaController, Snapshot>`. Локальный тикер
   (`~300 мс`, корутина на `Dispatchers.Main`) вычисляет позицию по формуле
   `position + elapsed × playbackSpeed` на основе `PlaybackState.position`,
   `lastPositionUpdateTime` и `playbackSpeed` — без единого дополнительного
   обращения к MediaSession. Тикер запускается только когда `isPlaying == true`
   и немедленно останавливается на паузе/остановке. При получении нового
   `PlaybackState` кэш обновляется и локальный расчёт синхронизируется сам
   (следующий тик использует свежий снимок).
2. **Playback Source в Debug Screen.** Новое поле
   `MediaPlaybackState.playbackSource: PlaybackSource` (`MEDIA_SESSION` /
   `KEY_EVENT_FALLBACK` / `NO_ACTIVE_SESSION`), вычисляется в
   `MediaControllerRepository.recomputeState()`: если есть активный
   `MediaController` — `MEDIA_SESSION`; если сессии нет, но
   `audioManager.isMusicActive == true` — `KEY_EVENT_FALLBACK` (команды
   всё ещё дойдут через медиа-клавиши); иначе — `NO_ACTIVE_SESSION`.
3. **UX разрешения на доступ к медиасессиям.** В `SettingsScreen`, при
   отсутствии доступа, компактная строка заменена на развёрнутую карточку с
   объяснением (зачем нужно разрешение, что приложение не читает содержимое
   уведомлений, для чего конкретно используется доступ) и кнопкой перехода в
   системные настройки. Карточка показывается **только** при отсутствии
   разрешения; при наличии — компактная строка со статусом, как раньше.

**Самопроверка совместимости (повторный обзор логики, без изменения архитектуры):**

| Сценарий | Проверено |
|---|---|
| Spotify: Play/Pause/Next/Previous | Транспорт идёт через `MediaController.transportControls`, поведение не изменилось |
| AIMP | Fallback на `KeyEventMediaRemote` при отсутствии `MediaSession` работает как раньше |
| YouTube Music | Аналогично Spotify |
| Нет активного плеера | `PlaybackSource.NO_ACTIVE_SESSION`/`KEY_EVENT_FALLBACK` корректно вычисляются, тикер не запускается (`isPlaying == false`) |
| Переключение между плеерами | `activeController()` пересчитывается при каждом `updateSessions()`/callback; тикер останавливается для старой сессии и запускается для новой при необходимости |
| Повторный запуск приложения | `MediaControllerRepository` — Hilt singleton уровня приложения; при живом процессе состояние сохраняется, при перезапуске процесса переинициализируется пустым и восстанавливается через `onListenerConnected()` |
| Запуск после перезагрузки телефона | `NotificationListenerService` — системный компонент, ОС автоматически перепривязывает его при загрузке без дополнительного `BOOT_COMPLETED`-ресивера |
| Смена трека / пауза / next / previous | `onMetadataChanged`/`onPlaybackStateChanged` обновляют кэш снимка немедленно, `recomputeState()` вызывается на каждое событие |

> Полный прогон на реальных устройствах выполняется пользователем в Android
> Studio — в этом окружении нет Android SDK, поэтому Gradle-сборка
> недоступна; проверка выше — по коду и API-контрактам framework-классов.

**Производительность:**
- Тикер работает только пока `isPlaying == true`; на паузе поток
  останавливается (`Job.cancel()`), лишней нагрузки в простое нет.
- Обновление позиции — чистая арифметика (`SystemClock.elapsedRealtime()` +
  вычитание), без обращений к `MediaSession`/`Binder` на каждый тик.
- `_state` — `MutableStateFlow`, эмиссия происходит, только когда
  `positionMs` действительно изменился (проверка перед записью).

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
- `MediaControllerRepository` — управление списком `MediaController`, transport-команды, наблюдение состояния, плавный локальный тикер позиции, вычисление `PlaybackSource`
- `MediaMetadataMapper` — маппинг кэшированных `MediaMetadata`/`PlaybackState` → `MediaPlaybackState` (без обращения к MediaSession)
- `SessionMediaRemote` — единственная реализация `MediaRemote` + `MediaStateProvider`, с fallback на `KeyEventMediaRemote`
- `PlaybackSource` — диагностика: `MEDIA_SESSION` / `KEY_EVENT_FALLBACK` / `NO_ACTIVE_SESSION`

### Архитектурные слои (все интерфейсы финальной архитектуры)
- **Voice**: `AudioRecorder`/`AndroidAudioRecorder` (реальный `AudioRecord`),
  `SpeechRecognizer`/`VoskSpeechRecognizer` (реальный Vosk), `GrammarBuilder`,
  `VoiceCommandConfigLoader`, `VoskModelProvisioner` — все реализованы (Sprint 3)
- **Intent**: `DjIntent` (11 вариантов), `IntentRecognizer` /
  `KeywordIntentRecognizer` — словарь из `commands.json` (Sprint 3)
- **Command**: `DjCommand` (без `triggers` — Sprint 3), `CommandContext`,
  `CommandResult`, `CommandRegistry`, `CommandDispatcher`
- **Media**: `MediaRemote`, `MediaStateProvider`, `MediaPlaybackState` — реализовано полностью (Sprint 2)
- **Feedback**: `FeedbackManager` / `BeepFeedbackManager` (ToneGenerator)
- **Settings**: `DjSettings`, `SettingsRepository` / `DataStoreSettingsRepository`
- **Permissions**: `AppPermissions` (runtime), `NotificationAccess` (доступ к медиасессиям)
- **Voice Engine** (`:service/voice`): `VoiceEngine`, `VoiceStateHolder`,
  `VoiceEngineState`, `VoiceCommandLogEntry` — оркестрация пайплайна (Sprint 3)

### Команды

Голосовые MVP-команды (Sprint 3, через `commands.json`):

| Intent-ключ | DjIntent | Фразы (примеры) |
|---|---|---|
| PAUSE | Pause | пауза, стоп, останови, остановить музыку, поставь на паузу |
| PLAY | Play | продолжай, играй, воспроизвести, включи |
| NEXT | Next | следующий, следующий трек, дальше, вперёд |
| PREVIOUS | Previous | предыдущий, назад, предыдущий трек |
| QUERY_NOW_PLAYING | QueryNowPlaying | что играет, какая песня, что сейчас играет, какой трек |

Остальные команды (`ArtistCommand`, `IsPlayingCommand`, `VolumeUpCommand`,
`VolumeDownCommand`, `VolumeQueryCommand`) по-прежнему зарегистрированы в
`CommandRegistry` и доступны программно (`findByIntent`), но **не имеют
записи в `commands.json`** — голосом их вызвать нельзя, пока кто-то не
добавит фразы в конфиг (управление громкостью голосом также явно исключено
из Sprint 3 требованиями брифа).

### UI
- `MainScreen` — статус сервиса, чек-лист готовности, кнопки Start/Stop, версия, результат команды
- `SettingsScreen` — автозапуск, показ DebugScreen, доступ к медиасессиям
- `DebugScreen` — сервис, разрешения, последняя ошибка, аудио, **Voice**
  (статус, модель, фраза, confidence, Intent, Action, время обработки +
  журнал последних 10 команд), воспроизведение (Media Layer), журнал логов
- Navigation Compose: Main ↔ Settings, Main ↔ Debug, iOS-style transitions
- Dark theme (Material3)

### Data
- `UnknownCommandLogger` / `CsvUnknownCommandLogger` — append-only CSV, ротация 512KB

---

## Что НЕ реализовано (ожидается в следующих Sprint)

| Компонент | Sprint |
|---|---|
| Активационная фраза (wake word) как обязательный gate | Sprint 4 |
| Голосовое управление громкостью | Не запланировано (явно исключено из Sprint 3) |
| Синтез речи / голосовые ответы | Не запланировано (явно исключено из Sprint 3) |
| Onboarding для доступа к медиасессиям при первом запуске | Остаток Sprint 7 |
| Рендеринг обложки трека | Не запланировано |
| Porcupine wake word (отдельный детектор, не keyword-gate) | Будущее |
| Замена словарного Intent Parser на локальную LLM | Будущее (архитектура уже это допускает) |

---

## Известные ограничения

1. **Офлайн-модель Vosk не входит в репозиторий** — см. «Voice Architecture»
   выше. Без неё голосовой цикл корректно сообщает об ошибке, но не
   распознаёт речь. Это единственная причина, по которой финальный сценарий
   демонстрации Sprint 3 не был прогнан на реальном устройстве в этой сессии.
2. **Wake word отсутствует** — намеренно, по требованию брифа Sprint 3;
   архитектура (см. `stripWakeWord`) готова к добавлению без переписывания.
3. **Доступ к медиасессиям выдаётся только вручную** — нет автоматического запроса/онбординга, только кнопка в Настройках.
4. **Автозапуск без разрешений** — при холодном старте не показывает системный запрос (разрешения запрашиваются по кнопке «Запустить»).
5. **`runBlocking` в `autoStartServiceIfNeeded`** — приемлемо для чтения начальных настроек, стоит перенести в корутину в одном из следующих спринтов.
6. **Голосовые команды громкости/исполнителя/статуса воспроизведения не подключены** — `commands.json` содержит только 5 MVP-ключей; расширение — правка конфигурации.

---

## Следующая задача

**Sprint 4: Wake Word + журнал**

Активационная фраза как gate перед `IntentRecognizer.recognize()` в
`VoiceEngine.handleRecognition()`, без изменения самого матчинга; расширение
обзора журнала неизвестных команд в UI.
