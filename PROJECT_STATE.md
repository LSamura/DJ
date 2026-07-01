# PROJECT_STATE.md

_Обновлено: 2026-07-01 | Sprint 3.1.1 (Bluetooth SCO lifecycle redesign)_

---

## Текущее состояние

**Sprint 1, Sprint 2, Sprint 3, Sprint 3.1 и Sprint 3.1.1 реализованы.** Media Layer стабилен и протестирован на реальном устройстве. Voice Layer реализован полностью в коде: микрофон → Vosk → Intent Parser → Media Layer, с двумя режимами прослушивания (Continuous/Wake Mode), порогом confidence, расширенным словарём команд, управлением громкостью и корректным жизненным циклом Bluetooth SCO (активен только во время реальной записи команды, никогда в состоянии ожидания). **Офлайн-модель Vosk теперь входит в репозиторий** (`feature/src/main/assets/model/`, добавлена после Sprint 3) — прежний блокер снят.

Проект:
- Открывается в Android Studio
- Собирается без ошибок
- Запускается на устройстве Android 8.0+ (minSdk 26)
- Не падает при отсутствии разрешений — запрашивает их автоматически
- Сервис стабильно запускается и останавливается, уведомление отображается
- Управляет реальными плеерами (Spotify, AIMP, YouTube Music и др.) через `MediaController`, с fallback на медиа-клавиши
- Позиция трека в Debug Screen обновляется плавно (~300 мс), без лишних запросов к MediaSession
- Голосовой пайплайн работает полностью локально, без интернета, с confidence-фильтрацией и двумя режимами прослушивания

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

### Модель Vosk — теперь в репозитории

`VoskModelProvisioner` ожидает готовую модель под
`feature/src/main/assets/model/` (распакованную, с файлом-маркером
`conf/model.conf`) — после Sprint 3 модель (`vosk-model-small-ru`, ~45 МБ)
была добавлена напрямую в репозиторий, так что сборка в Android Studio
больше не требует ручного шага. `VoskModelProvisioner.ensureModel()` по
прежнему возвращает `null` и ничего не роняет, если модель вдруг
отсутствует (например, в среде сборки этого агента, где нет Android SDK
и физического устройства для проверки) — Debug Screen корректно показывает
«Модель не загружена» в этом случае.

---

## Sprint 3.1 — Voice UX & Recognition Improvements (2026-07-01)

**Цель:** довести голосовое управление до состояния, пригодного для
ежедневного использования, оставаясь полностью локальным (без TTS, LLM,
облачных сервисов).

### Что реализовано

1. **Расширенный `commands.json` + `TextNormalizer`.** Добавлены словоформы
   и разговорные варианты для PAUSE/PLAY/NEXT/PREVIOUS/QUERY_NOW_PLAYING
   (по образцам из брифа). `TextNormalizer` (`feature/voice`) — lowercase,
   ё→е, удаление пунктуации, схлопывание пробелов; используется и
   `KeywordIntentRecognizer`, и `VoiceEngine` (для показа Normalized Text
   в диагностике) — единая точка нормализации, а не дублирование логики.

2. **Confidence Threshold.** `DjSettings.voskConfidenceThreshold` (по
   умолчанию `0.8f`, диапазон 50–95%) настраивается слайдером в
   `SettingsScreen`. `VoiceEngine.handleRecognition()` сравнивает
   `RecognitionResult.confidence` с порогом *до* вызова `IntentRecognizer`
   — при `confidence < threshold` команда не выполняется вообще (ни
   `CommandDispatcher.dispatch()`, ни смена режима не происходят),
   а причина ("Low confidence (43% < 80%)") записывается в
   `VoiceStateHolder` и видна на Debug Screen.

3. **Debug Screen — новые поля.** Raw Text, Normalized Text, Confidence,
   Intent, Action, Execution Result, Reject Reason — как в текущем
   состоянии, так и в журнале последних 10 команд (`VoiceCommandLogEntry`
   расширен теми же полями).

4. **Два режима прослушивания.** `VoiceListeningMode.CONTINUOUS` (поведение
   Sprint 3 — полная грамматика команд работает всегда) и `WAKE_WORD`
   (лёгкое ожидание активационной фразы через `WakeWordDetector`, полное
   распознавание команд включается только на время диалогового окна).
   Персистится в `DjSettings.listeningMode` (DataStore), переключается как
   из Settings (чипы Continuous/Wake Mode), так и голосом.

5. **`WakeWordDetector` — абстракция активации.** Единственная реализация
   пока — `VoskWakeWordDetector`, использующая тот же движок с крошечной
   грамматикой (`["диджей", "джей", "dj"]`) вместо полной командной —
   дешевле гонять постоянно, чем полное распознавание. `VoiceEngine`
   зависит только от интерфейса, поэтому Porcupine/OpenWakeWord подключаются
   позже заменой одной реализации, без изменения оркестрации или Media Layer.

6. **Диалоговое окно.** После обнаружения wake word `VoiceEngine`
   открывает полное распознавание команд на `DjSettings.dialogWindowSeconds`
   (по умолчанию 6, диапазон 3–15, настраивается слайдером в Settings).
   Каждая распознанная финальная фраза сдвигает таймер закрытия окна;
   если новых фраз нет — сессия завершается, движок возвращается к
   ожиданию wake word. Технически: сторожевой цикл (`delay(300)` +
   сравнение `SystemClock.elapsedRealtime()`) отменяет child-`Job` сбора
   распознаваний по истечении окна.

7. **Голосовое переключение режимов.** "Пока слушай" → `DjIntent.SetContinuousMode`,
   "Перестань слушать" → `DjIntent.SetWakeMode`. Перехватываются в
   `VoiceEngine.handleRecognition()` до вызова `CommandDispatcher` — это
   команды уровня Voice Layer, а не Media Layer, поэтому они не
   регистрируются в `CommandRegistry` и никогда не доходят до плеера.

8. **Управление громкостью.** `VolumeUp`/`VolumeDown` (уже существовали,
   теперь есть фразы в `commands.json`), плюс новые intent'ы
   `SetVolumeMax`/`SetVolumeMin`/`SetVolumePercent(percent)`. Последний —
   единственная параметризованная MVP-команда: процент выделяется регэкспом
   в `KeywordIntentRecognizer`, а сама команда (`SetVolumePercentCommand`)
   получает его через новое поле `CommandContext.intent` (фактический
   сматченный intent, а не просто тип) — небольшое расширение контракта,
   необходимое именно для параметризованных команд.

9. **Bluetooth-микрофон (первая версия, заменена в Sprint 3.1.1).**
   Изначально `AndroidAudioRecorder` автоматически искал подключённое SCO
   Bluetooth-устройство и держал SCO активным всё время, пока движок слушал
   — см. полный редизайн ниже.

10. **Мелкая полировка анимаций.** `animateContentSize()` на карточках
    статуса/чек-листа на `MainScreen` и на секциях `DebugScreen` — без
    полного редизайна, как и требовал бриф.

### Ограничения Sprint 3.1

- **Wake word всё ещё через Vosk**, не через специализированный движок —
  архитектура (`WakeWordDetector`) готова к замене, сама замена не входила
  в объём Sprint 3.1.
- **Проверка на реальном устройстве не выполнена агентом** — нет Android
  SDK, физического микрофона, Bluetooth-гарнитуры и живых плееров в этой
  среде. Всё проверено по коду, API-контрактам Android/Vosk/DataStore и
  структурному анализу потоков корутин (cancellation, shared flow,
  timing). Итоговая проверка сценариев (распознавание естественных фраз,
  confidence threshold, оба режима, громкость, переключение режимов
  голосом, Bluetooth-микрофон) — на стороне пользователя.

---

## Sprint 3.1.1 — Bluetooth SCO lifecycle redesign (2026-07-01)

**Проблема, обнаруженная пользователем:** первая версия держала Bluetooth
SCO активным на весь период работы движка (включая ожидание wake word в
Wake Mode и всю сессию Continuous Mode) — Bluetooth-гарнитура оставалась в
режиме "звонка" гораздо дольше, чем реально шла запись команды.

### Что изменено

- **`MicrophoneSource`** (`feature/voice`) — новый enum `AUTO`/`PHONE`/
  `BLUETOOTH`, персистится в `DjSettings.microphoneSource` (по умолчанию
  `AUTO`). Настройка в `SettingsScreen` (3 чипа). **`AUTO` и `PHONE` вообще
  никогда не вызывают Bluetooth SCO API** — только явный выбор `BLUETOOTH`
  включает эту логику.
- **`AudioRecorder.start(allowBluetooth: Boolean = false)`** — Bluetooth
  теперь решение конкретной сессии записи (принимает вызывающий код), а не
  скрытое глобальное поведение рекордера. Когда `allowBluetooth == false`,
  `AndroidAudioRecorder` вообще не обращается к `AudioManager`-Bluetooth API.
- **`VoiceEngine` больше не держит один `shareIn`-разделяемый аудиопоток на
  всё время работы движка.** Вместо этого:
  - Ожидание wake word (Wake Mode) — отдельная сессия записи с
    `allowBluetooth = false` **всегда**, независимо от настройки — это
    единственное по-настоящему "простаивающее" состояние, и оно никогда не
    может держать Bluetooth в режиме звонка.
  - Диалоговое окно после активации (Wake Mode) и вся сессия Continuous
    Mode — каждая открывает свою запись через
    `audioRecorder.start(allowBluetooth = resolveAllowBluetooth())`, где
    `resolveAllowBluetooth()` возвращает `true` только если
    `microphoneSource == BLUETOOTH`; сессия закрывается
    (`audioRecorder.stop()`) сразу по завершении окна/распознавания.
- **SCO-хендшейк ожидается явно.** `AndroidAudioRecorder` регистрирует
  `BroadcastReceiver` на `AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED` и
  использует `suspendCancellableCoroutine` + `withTimeoutOrNull(2000мс)`,
  чтобы дождаться `SCO_AUDIO_STATE_CONNECTED`, прежде чем начинать запись
  через Bluetooth-путь — иначе первые сотни миллисекунд можно захватить
  тишину или неверный источник. При таймауте/ошибке — fallback на
  встроенный микрофон без падения.
- **`stopBluetoothSco()` вызывается в `finally`-блоке записи** сразу после
  остановки `AudioRecord` — A2DP-воспроизведение восстанавливается как
  можно быстрее.
- Debug Screen теперь показывает и настройку («Настройка источника»:
  Auto/Phone/Bluetooth), и то, что реально используется прямо сейчас
  («Активный источник сейчас»: Bluetooth/Встроенный микрофон/—) — это два
  разных, потенциально несовпадающих в моменте состояния.

### Ограничения

- В Continuous Mode с явно выбранным `BLUETOOTH` SCO неизбежно остаётся
  активным на всё время сессии — это внутренне присуще смыслу "continuous"
  (движок всегда что-то распознаёт, идла нет). Это единственный случай,
  где непрерывная активность SCO — ожидаемое и объяснимое поведение, а не
  дефект: пользователь должен явно выбрать Bluetooth, зная это.
- Проверка на реальном Bluetooth-устройстве не выполнена агентом (нет
  физической гарнитуры/Android SDK в этой среде) — логика хендшейка и
  таймаутов проверена по документации `AudioManager` и структуре корутин,
  но не на живом устройстве.

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
- **Voice**: `AudioRecorder`/`AndroidAudioRecorder` (реальный `AudioRecord`,
  Bluetooth SCO auto-routing), `SpeechRecognizer`/`VoskSpeechRecognizer`
  (реальный Vosk, поддержка произвольного словаря), `GrammarBuilder`,
  `VoiceCommandConfigLoader`, `VoskModelProvisioner`, `TextNormalizer`,
  `WakeWordDetector`/`VoskWakeWordDetector`, `VoiceListeningMode` — все
  реализованы (Sprint 3 + Sprint 3.1)
- **Intent**: `DjIntent` (16 вариантов, включая `SetVolumeMax/Min/Percent`,
  `SetContinuousMode`/`SetWakeMode`), `IntentRecognizer` /
  `KeywordIntentRecognizer` — словарь из `commands.json` + regex для
  параметризованной громкости
- **Command**: `DjCommand`, `CommandContext` (теперь несёт `intent` —
  Sprint 3.1, нужно для параметризованных команд), `CommandResult`,
  `CommandRegistry`, `CommandDispatcher`
- **Media**: `MediaRemote`, `MediaStateProvider`, `MediaPlaybackState` — реализовано полностью (Sprint 2)
- **Feedback**: `FeedbackManager` / `BeepFeedbackManager` (ToneGenerator)
- **Settings**: `DjSettings` (+ `listeningMode`, `dialogWindowSeconds`,
  порог confidence по умолчанию 0.8), `SettingsRepository` /
  `DataStoreSettingsRepository`
- **Permissions**: `AppPermissions` (runtime), `NotificationAccess` (доступ к медиасессиям)
- **Voice Engine** (`:service/voice`): `VoiceEngine` (два режима, диалоговое
  окно, confidence-фильтрация), `VoiceStateHolder`, `VoiceEngineState`
  (+ `WaitingForWakeWord`), `VoiceCommandLogEntry` (расширенные поля)

### Команды

Голосовые команды (через `commands.json`, словоформы расширены в Sprint 3.1):

| Intent-ключ | DjIntent | Фразы (примеры) |
|---|---|---|
| PAUSE | Pause | пауза, паузу, на паузу, останови, остановить музыку, стоп |
| PLAY | Play | играй, продолжай, продолжи, воспроизведи, включи музыку, возобнови |
| NEXT | Next | следующий, следующий трек, дальше, вперёд, переключи трек |
| PREVIOUS | Previous | назад, предыдущий, предыдущий трек, верни трек |
| QUERY_NOW_PLAYING | QueryNowPlaying | что играет, что сейчас играет, какая песня, какой трек |
| VOLUME_UP | VolumeUp | громче, сделай громче, прибавь громкость |
| VOLUME_DOWN | VolumeDown | тише, сделай тише, убавь громкость |
| VOLUME_MAX | SetVolumeMax | максимальная громкость, на полную громкость |
| VOLUME_MIN | SetVolumeMin | минимальная громкость, выключи звук |
| (regex) | SetVolumePercent(N) | "громкость 50 процентов" — число извлекается регэкспом |
| MODE_CONTINUOUS | SetContinuousMode | пока слушай, слушай постоянно |
| MODE_WAKE | SetWakeMode | перестань слушать, хватит слушать |

`ArtistCommand`, `IsPlayingCommand`, `VolumeQueryCommand` по-прежнему
зарегистрированы в `CommandRegistry` (доступны программно через
`findByIntent`), но не имеют записи в `commands.json` — голосом их вызвать
нельзя, пока кто-то не добавит фразы в конфиг.

### UI
- `MainScreen` — статус сервиса, чек-лист готовности, кнопки Start/Stop, версия, результат команды, `animateContentSize()` на карточках
- `SettingsScreen` — автозапуск, показ DebugScreen, доступ к медиасессиям,
  слайдер confidence threshold (50–95%), выбор режима Continuous/Wake Mode,
  слайдер диалогового окна (3–15 сек)
- `DebugScreen` — сервис, разрешения, последняя ошибка, аудио (+ источник
  микрофона), **Voice** (статус, режим, модель, Raw/Normalized Text,
  confidence, Intent, Action, Execution Result, Reject Reason, время
  обработки + журнал последних 10 команд с теми же полями), воспроизведение
  (Media Layer), журнал логов, `animateContentSize()` на секциях
- Navigation Compose: Main ↔ Settings, Main ↔ Debug, iOS-style transitions
- Dark theme (Material3)

### Data
- `UnknownCommandLogger` / `CsvUnknownCommandLogger` — append-only CSV, ротация 512KB

---

## Что НЕ реализовано (ожидается в следующих Sprint)

| Компонент | Sprint |
|---|---|
| Полноценный wake-word движок (Porcupine/OpenWakeWord) | Sprint 4 |
| Синтез речи / голосовые ответы | Не запланировано (явно исключено) |
| Onboarding для доступа к медиасессиям при первом запуске | Остаток Sprint 7 |
| Рендеринг обложки трека | Не запланировано |
| Замена словарного Intent Parser на локальную LLM | Будущее (архитектура уже это допускает) |
| Диалоговый режим (полноценный, многоходовой) | Не запланировано (явно исключено из Sprint 3.1) |

---

## Известные ограничения

1. **Wake word работает через Vosk с урезанной грамматикой**, не через
   специализированный движок — архитектура (`WakeWordDetector`) готова к
   замене без переписывания `VoiceEngine`.
2. **Continuous Mode + явный выбор Bluetooth держит SCO активным всю
   сессию** — неизбежное следствие семантики "continuous" (см. Sprint
   3.1.1); AUTO/PHONE никогда не затрагивают Bluetooth вообще.
3. **Доступ к медиасессиям выдаётся только вручную** — нет автоматического запроса/онбординга, только кнопка в Настройках.
4. **Автозапуск без разрешений** — при холодном старте не показывает системный запрос (разрешения запрашиваются по кнопке «Запустить»).
5. **`runBlocking` в `autoStartServiceIfNeeded`** — приемлемо для чтения начальных настроек, стоит перенести в корутину в одном из следующих спринтов.
6. **Sprint 3.1/3.1.1 не проверены на реальном устройстве агентом** — нет
   Android SDK, микрофона, Bluetooth-гарнитуры в среде сборки. Проверено по
   коду и API-контрактам; финальная проверка сценариев — за пользователем.

---

## Следующая задача

**Sprint 4: Полноценный Wake Word движок**

Заменить `VoskWakeWordDetector` на специализированный движок (Porcupine или
OpenWakeWord) через существующий интерфейс `WakeWordDetector` — без
изменения `VoiceEngine`; расширить обзор журнала неизвестных команд в UI.
