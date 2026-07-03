# CHANGELOG

Все значимые изменения проекта фиксируются в этом файле.

Формат основан на [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/).

---

## [0.4.1] — 2026-07-01 — Sprint 4 (refined): Wake Layer architecture

User follow-up on Sprint 4 pinned down the exact shape of the abstraction:
`WakeWordEngine` moves from a suspend function
(`waitForWakeWord(Flow<ByteArray>): Boolean`) to an explicit lifecycle +
events interface, and wake-word code is organized as its own layer
(`feature/voice/wake`) inside `:feature` rather than living alongside
general voice utilities.

**Changed:**
- `WakeWordEngine` (now `feature/voice/wake`) — interface is
  `fun start()`, `fun stop()`, `fun destroy()`,
  `val state: StateFlow<WakeWordEngineState>` (`IDLE`/`LISTENING`/`ERROR`),
  `val events: SharedFlow<WakeWordEvent>` (`Detected(phrase)`/
  `Error(message, throwable)`). The engine now owns its own `AudioRecorder`
  session while listening — callers no longer hand it a `Flow<ByteArray>`.
  Diagram: `Microphone -> WakeWordEngine -> VoiceEngine -> IntentParser ->
  Media Layer`.
- `PorcupineWakeWordEngine` — `start()` launches an internal coroutine that
  opens `AudioRecorder.start(allowBluetooth = false)` itself, runs
  detection, and emits `WakeWordEvent.Detected`/`Error`; the microphone and
  native Porcupine instance are torn down automatically once detection (or
  an error) occurs.
- `MockWakeWordEngine` — simplified to match: `start()` just flips `state`
  to `LISTENING`; detection only ever happens via `triggerDetection()`/
  `triggerError()`. Touches no audio at all, making the "VoiceEngine
  doesn't care which engine it's talking to" claim more obviously true.
- `VoiceEngine.runWakeWordSession()` — no longer opens `AudioRecorder` for
  the wake-wait phase at all; calls `wakeWordEngine.start()`/`stop()` and
  reacts to `events`. Subscribes via
  `async(start = CoroutineStart.UNDISPATCHED) { wakeWordEngine.events.first() }`
  *before* calling `start()`, closing a real race window (`events` is a hot
  `SharedFlow` with no replay).

**Unchanged:**
- Continuous Mode, Bluetooth SCO behavior (only the *owner* of the
  wake-wait microphone session moved, not when/how SCO opens or closes),
  the Listening Window / beep / overlay / extend-on-success flow, the
  `WakeWordPhrase`/`WakeWordPhrases` registry design, the Porcupine
  Access Key/asset requirements.
- Module graph (`app`/`core`/`data`/`feature`/`service`/`ui`, ADR-002) —
  "Wake Layer" is a package (`feature/voice/wake`) inside `:feature`, not a
  new Gradle module; the fixed graph wasn't the subject of this request.

**Verification:** `./gradlew :feature:dependencies` still resolves cleanly
after the package move; no Android SDK available to compile, same
limitation as the initial Sprint 4 pass.

---

## [0.4.0] — 2026-07-01 — Sprint 4: Dedicated Wake Word Engine (Porcupine)

Wake Mode's wake-word spotting moves off Vosk entirely and onto a
dedicated engine (Picovoice Porcupine). Vosk is NOT removed — it remains
the only engine ever used to recognize actual voice commands, in both
Continuous Mode and inside a Wake Mode Listening Window — it just never
runs anymore purely to listen for "Диджей".

**Added:**
- `WakeWordEngine` (`feature/voice`) — new abstraction replacing Sprint
  3.1's `WakeWordDetector`. `VoiceEngine` depends only on the interface.
- `PorcupineWakeWordEngine` — real implementation: buffers the
  `AudioRecorder` byte stream into fixed-length frames
  (`Porcupine.frameLength`), feeds them to `porcupine.process()`; creates
  and `.delete()`s a native `Porcupine` instance per wake-wait session
  (same per-phase lifecycle already used for `AudioRecorder` sessions and
  the overlay).
- `MockWakeWordEngine` — test double, detects only via an explicit
  `triggerDetection()` call; not bound in `AppModule`, proves `VoiceEngine`
  isn't coupled to any specific engine.
- `PorcupineAssetProvisioner` — copies `.ppn`/`.pv` files from assets into
  internal storage (same pattern as `VoskModelProvisioner`).
- `WakeWordPhrase` / `WakeWordPhrases` — extensible registry of activation
  phrases instead of a hardcoded "Диджей" string. Only one phrase ships
  today, but adding "Музыка", "Ассистент", or a custom phrase later is one
  new registry entry plus its trained assets — no `VoiceEngine` changes.
- Settings: Wake Word phrase picker (chips, one option for now) + a
  Porcupine Access Key field (`DjSettings.wakeWordPhraseId`,
  `porcupineAccessKey`, persisted via DataStore).
- `feature/src/main/assets/porcupine/README.md` — exact steps to obtain
  the trained keyword file and language model needed to actually run this
  on-device.

**Changed:**
- `VoiceEngine.runWakeWordSession()` now calls `wakeWordEngine.waitForWakeWord()`
  instead of the old Vosk-grammar detector; everything else in the Wake
  Mode dialog window (beep, overlay, Listening Window, extend-on-success,
  Vosk teardown, return to idle) is unchanged from Sprint 3.2/3.3 — it was
  already structured to make this swap a one-line change.
- `SpeechRecognizer.startListening()` dropped the `vocabulary: List<String>?`
  parameter — it existed only for the old wake-word grammar and had no
  remaining callers; `VoskSpeechRecognizer` always builds the full command
  grammar now.
- `AppModule` binds `WakeWordEngine` to `PorcupineWakeWordEngine`.

**Removed:**
- `feature/voice/WakeWordDetector.kt`, `feature/voice/impl/VoskWakeWordDetector.kt`
  — Vosk no longer ever does wake-word spotting.

**Unaffected by design:**
- Continuous Mode — no file touched by `runContinuousSession()` changed.
- Bluetooth SCO lifecycle — unchanged from Sprint 3.1.1/3.3; only the idle
  detector changed, not when/how SCO opens or closes.

**Limitations:**
- Porcupine requires a personal Access Key and a keyword file (`.ppn`)
  trained specifically for "Диджей" in Russian — neither ships in this
  repo nor can be generated offline (one-time setup via
  console.picovoice.ai, same category of manual step the original Vosk
  model needed). Until configured, `PorcupineWakeWordEngine.isReady ==
  false`, the error is logged and visible on Debug Screen, and the app
  does not crash — but Wake Mode won't detect anything.
- No Android SDK/device in this sandbox — the Porcupine Android SDK
  integration (`Porcupine.Builder`, `setKeywordPaths`, `setModelPath`,
  `.process(ShortArray)`, `.frameLength`, `.delete()`) is written from
  recollection of the library's public API, not verified by compiling.
- As with every Voice Layer sprint since 3.1, final on-device verification
  (including whether the exact Porcupine API used here compiles against
  the pinned `3.0.2` version) is the user's responsibility.

---

## [0.3.4] — 2026-07-01 — Sprint 3.3: Stabilization

Sprint 3.2 не был принят пользователем после теста на реальном устройстве:
приложение падало при произнесении "Диджей", Settings не прокручивался, а
часть состояний Voice Layer зависала. Этот релиз — чистая стабилизация,
без новых функций.

**Исправлено:**
- **Падение приложения при произнесении "Диджей" (первопричина, не
  обходной путь).** `VoiceOverlayController` мутировал `View` и вызывал
  `WindowManager.addView()`/`removeView()` прямо из корутины `VoiceEngine`
  на `Dispatchers.IO` — потоке без `Looper`. Первый же вызов `show()`
  (ровно момент обнаружения wake word) кидал необработанное исключение,
  валившее весь процесс. Вся работа с `View`/`WindowManager` перенесена на
  главный поток через `Handler(Looper.getMainLooper())`.
- **`SettingsScreen` не прокручивался** — у корневого `Column` отсутствовал
  `Modifier.verticalScroll(...)` (в отличие от `DebugScreen`). Добавлен.
- **Зависшее состояние Voice Layer.** `handleRecognition()` переводил
  `VoiceEngineState` в `Processing`/`Executing`, но при отклонении по
  confidence или переключении режима не возвращал его в `Listening` —
  Debug Screen застревал на «Распознаёт...» до конца диалогового окна.
  Исправлено единым `try/finally`, гарантированно возвращающим `Listening`
  при выходе из функции любым путём.
- **Оверлей теперь надёжно появляется** при выданном `SYSTEM_ALERT_WINDOW`
  — до фикса потока `windowManager.addView()` мог непредсказуемо падать
  из-за отсутствия `Looper` даже при выданном разрешении.

**Добавлено:**
- Обработка исключений с указанием этапа: `VoiceEngine.currentStage`
  (`waiting_wake_word`, `overlay_show`, `dialog_window`,
  `recognition_processing`, `command_dispatch` и т.д.) + `try/catch` вокруг
  каждой сессии в основном цикле — любой сбой логируется через
  `DjLogger.voiceError("Ошибка на этапе '$stage'", e)` (виден в «Последняя
  ошибка» на Debug Screen) и переводит состояние в `Error("[$stage] ...")`
  вместо падения приложения; ресурсы (`AudioRecorder`, оверлей)
  гарантированно освобождаются в `catch`. Добавлен
  `CoroutineExceptionHandler` на `engineScope` как последний рубеж защиты.
- `VoiceOverlayController.hideImmediately()` — немедленное закрытие без
  анимации, используется в `VoiceEngine.stop()`.

**Проверено (ревизия без изменений кода):**
- Полная трассировка переходов состояний Voice Layer — единственная
  найденная проблема (зависшее состояние выше) исправлена, других не
  найдено.
- Жизненный цикл `DjForegroundService`/`DjVoiceService`/
  `AndroidAudioRecorder` — уже был defensively обёрнут `try/catch` с
  Sprint 1/3.1.1, новых проблем не найдено.

**Ограничения:**
- Нет Android SDK/устройства/Bluetooth-гарнитуры в среде сборки — все
  исправления проверены чтением кода и трассировкой потоков выполнения, не
  запуском на устройстве.
- По прямому требованию пользователя Sprint 3.2 считается завершённым
  только после успешного прохождения полного чек-листа на реальном
  устройстве — это ещё не подтверждено.

---

## [0.3.3] — 2026-07-01 — Sprint 3.2: Voice UX Polish

Полировка голосового UX по итогам ручного тестирования на устройстве:
исправлен корневой баг, из-за которого Wake Mode ощущалось как Continuous
Mode, добавлены визуальная/звуковая обратная связь и честный таймер
диалогового окна.

**Исправлено:**
- **Корневая причина "Wake Mode ощущается как Continuous Mode".**
  `VoskWakeWordDetector.waitForWakeWord()` завершал ожидание по любому
  финальному результату Vosk (`.first { it.isFinal && it.text.isNotBlank() }`),
  а в Grammar Mode любая речь/шум вне словаря wake-фраз возвращается как
  непустая строка `"[unk]"` — почти любой звук завершал ожидание с
  `detected = false`, заставляя `VoiceEngine` пересоздавать всю сессию
  (новый `AudioRecord`/`Recognizer`, при выборе Bluetooth — новый SCO
  хендшейк) практически непрерывно. Предикат сужен до совпадения с
  реальной wake-фразой; `"[unk]"`/пустые финалы больше не прерывают сессию.
- Таймер диалогового окна раньше продлевался на любой финальный результат
  (включая `[unk]`-шум и отклонённые/неудавшиеся команды) — теперь
  продлевается только после успешно выполненной команды.

**Добавлено:**
- `VoiceOverlayController` (`service/overlay`) — компактный несблокирующий
  floating-оверлей ("🎧 DJ — Слушаю... Ns") на чистых Android View +
  `WindowManager` (без Compose — `:service` остаётся Compose-free);
  fade/scale-появление с `OvershootInterpolator`, пульсирующий индикатор
  через `ValueAnimator`. Появляется сразу после wake word, исчезает по
  завершении диалогового окна или при остановке `VoiceEngine`.
- `OverlayAccess` helper (`ui/permissions`) + карточка в `SettingsScreen`
  для выдачи `SYSTEM_ALERT_WINDOW` («Draw over other apps»).
- `FeedbackManager.onActivation()` теперь реально вызывается в момент
  обнаружения wake word — короткий сигнал активации звучит (успех/ошибка
  уже были подключены через `CommandDispatcher`).
- Настройка «Звуковые сигналы» (`DjSettings.soundFeedbackEnabled`,
  по умолчанию включена) — `BeepFeedbackManager` теперь проверяет её перед
  каждым сигналом.
- `VoiceStateHolder.remainingWindowSeconds: StateFlow<Int?>` — обратный
  отсчёт диалогового окна, показывается на Debug Screen и в оверлее.

**Изменено:**
- `VoiceEngineState` расширен: `Idle, Initializing, WaitingWakeWord,
  Listening, Processing, Executing, Sleep, Error` (было 4 значения) —
  отражает полный жизненный цикл распознавания и виден на Debug Screen.
- `VoiceEngine.handleRecognition()` теперь возвращает
  `RecognitionOutcome(modeSwitch, extendWindow)` вместо голого
  `VoiceListeningMode?`, чтобы явно разделить «переключение режима» и
  «нужно ли продлевать окно».

**Ограничения:**
- Нет Android SDK/устройства/Bluetooth-гарнитуры в среде сборки — сборка
  (`./gradlew :service:compileDebugKotlin`) падает с `SDK location not
  found`; изменения проверены только чтением и трассировкой кода, не
  компиляцией. Финальная проверка на устройстве — за пользователем.
- Анимации оверлея — стандартные `View.animate()`/`ValueAnimator`, без
  `androidx.dynamicanimation`-пружин и без `RenderEffect`-блюра (API 31+):
  не требовалось для выполнения десяти пунктов задания буквально.

---

## [0.3.2] — 2026-07-01 — Sprint 3.1.1: Bluetooth SCO lifecycle redesign

Редизайн обработки Bluetooth-микрофона по итогам ревью: первая версия
(Sprint 3.1) держала SCO активным на весь период работы голосового
движка, а не только во время реальной записи команды.

**Добавлено:**
- `MicrophoneSource` (`AUTO`/`PHONE`/`BLUETOOTH`) — новая настройка в
  `DjSettings`, персистится через DataStore, выбирается тремя чипами в
  `SettingsScreen`.
- Ожидание SCO-хендшейка: `AndroidAudioRecorder` слушает
  `AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED` и ждёт
  `SCO_AUDIO_STATE_CONNECTED` (таймаут 2 сек) перед началом записи через
  Bluetooth — не захватывает тишину/неверный источник во время хендшейка.
- Debug Screen: отдельные строки «Настройка источника» (что выбрано в
  Settings) и «Активный источник сейчас» (что реально используется).

**Изменено:**
- `AudioRecorder.start()` принимает `allowBluetooth: Boolean = false` —
  Bluetooth теперь решение конкретной сессии записи, а не скрытое
  поведение рекордера. При `false` Bluetooth SCO API не вызываются вовсе.
- `AUTO` и `PHONE` никогда не обращаются к Bluetooth — только явный выбор
  `BLUETOOTH` активирует SCO-логику.
- `VoiceEngine` больше не держит один `shareIn`-разделяемый аудиопоток на
  весь срок жизни движка. Вместо этого:
  - Ожидание wake word (Wake Mode) — отдельная сессия, всегда
    `allowBluetooth = false`, вне зависимости от настройки.
  - Диалоговое окно (Wake Mode) / вся сессия Continuous Mode — сессия с
    `allowBluetooth = resolveAllowBluetooth()`, закрывается сразу по
    завершении окна/распознавания.
- `stopBluetoothSco()` вызывается в `finally`-блоке сразу после остановки
  `AudioRecord` — A2DP восстанавливается как можно быстрее.

**Исправлено:**
- `AndroidAudioRecorder`'s `finally`-блок теперь также сбрасывает
  `_isRecording` в `false` — раньше это делал только явный вызов `stop()`,
  что могло рассинхронизировать состояние при завершении потока через
  отмену (например, когда `WakeWordDetector` останавливает сбор через
  `Flow.first`).

**Ограничения:**
- В Continuous Mode с явно выбранным `BLUETOOTH` SCO неизбежно остаётся
  активным на всё время сессии — режим по определению не имеет состояния
  простоя. Осознанный компромисс, не дефект.
- Не проверено на реальном Bluetooth-устройстве агентом (нет физической
  гарнитуры/Android SDK в этой среде) — логика хендшейка и таймаутов
  проверена по документации `AudioManager` и структуре корутин.

---

## [0.3.1] — 2026-07-01 — Sprint 3.1: Voice UX & Recognition Improvements

Доведение голосового управления до состояния, пригодного для ежедневного
использования. Без TTS, LLM и облачных сервисов — всё по-прежнему локально.

**Добавлено:**
- Расширенный `commands.json`: словоформы и разговорные варианты для
  PAUSE/PLAY/NEXT/PREVIOUS/QUERY_NOW_PLAYING, плюс новые ключи
  VOLUME_UP/VOLUME_DOWN/VOLUME_MAX/VOLUME_MIN/MODE_CONTINUOUS/MODE_WAKE.
- `TextNormalizer` (`feature/voice`) — lowercase, ё→е, без пунктуации,
  схлопывание пробелов; общий для Intent Parser и Debug Screen.
- Confidence threshold: `DjSettings.voskConfidenceThreshold` (по умолчанию
  0.8, диапазон 50–95%), слайдер в Settings; распознавания ниже порога не
  выполняются, причина отказа видна в Debug Screen ("Reject Reason").
- Debug Screen: Raw Text, Normalized Text, Execution Result, Reject Reason,
  Источник микрофона, Режим прослушивания — в текущем состоянии и в журнале.
- Два режима прослушивания: `VoiceListeningMode.CONTINUOUS` (как в Sprint 3)
  и `WAKE_WORD` (лёгкое ожидание активационной фразы, полное распознавание
  команд — только в диалоговом окне после активации).
- `WakeWordDetector` (интерфейс) + `VoskWakeWordDetector` (реализация на
  урезанной Vosk-грамматике `["диджей","джей","dj"]`) — не привязывает
  Voice Layer к конкретному будущему движку активации.
- Диалоговое окно после активации: 3–15 сек (по умолчанию 6), настраивается
  слайдером в Settings, сбрасывается при каждой распознанной фразе.
- Голосовое переключение режимов: "Пока слушай" → Continuous, "Перестань
  слушать" → Wake Mode — перехватывается в `VoiceEngine`, не доходит до
  `CommandDispatcher`/Media Layer.
- Управление громкостью: `SetVolumeMax`/`SetVolumeMin`/`SetVolumePercent(N)`
  — новые intent'ы и команды; процент извлекается регэкспом
  ("громкость 50 процентов").
- Bluetooth-микрофон: автоматический выбор SCO-входа при подключённой
  гарнитуре в `AndroidAudioRecorder`, активный источник виден в Debug Screen.
- `animateContentSize()` на карточках MainScreen и секциях Debug Screen.

**Изменено:**
- `SpeechRecognizer.startListening()` получил необязательный параметр
  `vocabulary: List<String>?` — null сохраняет прежнее поведение (полная
  командная грамматика).
- `CommandContext` теперь несёт фактический `intent: DjIntent` (не только
  тип) — нужно для параметризованных команд вроде `SetVolumePercent`.
- `KeywordIntentRecognizer` использует `TextNormalizer` вместо ad-hoc
  lowercase/trim; добавлена regex-ветка для процента громкости до
  словарного сопоставления.
- `RecognitionResult`/`VoiceCommandLogEntry`/`VoiceStateHolder` расширены
  полями rawText/normalizedText/executionResult/rejectReason.

**Ограничения:**
- Bluetooth — только автоматический выбор, без ручного переключателя
  (ограничение Android API для выбора входного устройства из приложения;
  явно разрешённый в брифе запасной вариант).
- Wake word работает через Vosk с урезанной грамматикой, не через
  специализированный движок — архитектура готова к замене без переписывания.
- Проверка на реальном устройстве (естественные фразы, confidence threshold,
  оба режима, громкость, Bluetooth) не выполнена агентом — нет Android SDK,
  микрофона и Bluetooth-гарнитуры в среде сборки; проверено по коду и
  API-контрактам, финальная проверка — за пользователем.

---

## [0.3.0] — 2026-07-01 — Sprint 3: Offline Voice Control MVP

Полностью локальный голосовой цикл: микрофон → Vosk → Intent Parser →
Media Layer. Без интернета, без wake word, без синтеза речи.

**Добавлено:**
- `commands.json` (`feature/src/main/assets/`) — фразы для каждого intent
  вынесены в конфигурацию вместо хардкода в командах.
- `VoiceCommandConfigLoader` — загрузка и кэширование `commands.json`.
- Реальный `AndroidAudioRecorder`: `AudioRecord` 16kHz mono PCM16 →
  `Flow<ByteArray>`, RMS-уровень сигнала, гарантированное освобождение
  микрофона в `finally`.
- Интеграция Vosk (`com.alphacephei:vosk-android` + `jna`, официальный
  Maven-репозиторий alphacephei) — `VoskSpeechRecognizer` работает в
  Grammar Mode, вычисляет confidence из `result[].conf`.
- `VoskModelProvisioner` — разворачивает офлайн-модель из assets в
  локальное хранилище приложения при первом запуске (чистый файловый I/O).
- `VoiceEngine` (`:service/voice`) — оркестрация всего пайплайна на
  `Dispatchers.IO`, не блокирует главный поток.
- `VoiceStateHolder` + `VoiceEngineState` + `VoiceCommandLogEntry` —
  диагностика голосового пайплайна для Debug Screen.
- `DjVoiceService` — отдельный Android Service, запускается/останавливается
  вместе с `DjForegroundService`.
- Debug Screen: раздел «Voice» (статус сервиса, статус Vosk/модели,
  последняя фраза, confidence, Intent, Action, время обработки) + журнал
  последних 10 распознанных команд.

**Изменено:**
- `KeywordIntentRecognizer` переписан на словарь из `commands.json` вместо
  чтения `triggers` у команд из `CommandRegistry`.
- `GrammarBuilder` строит грамматику Vosk из `commands.json`, без wake-word
  префикса (см. ниже).
- `DjCommand.triggers` и `CommandRegistry.findByTrigger()` удалены — стали
  мёртвым кодом после переноса источника фраз в JSON; все 10 реализаций
  команд лишились соответствующего поля.
- `RecognitionResult.confidence` стал `Float?` (раньше `Float = 1.0f`) —
  честно отражает, что Vosk не всегда предоставляет confidence.

**Исправлено:**
- Баг в `DjForegroundService.onStartCommand()`: null-интент (redelivery
  после `START_STICKY`) обрабатывался как сигнал остановки вместо сигнала
  возобновления — сервис не переживал перезапуск процесса. Обнаружено при
  реализации требования «Voice Service автоматически восстанавливается
  после перезапуска».

**Ограничения:**
- Wake word намеренно не реализован (по требованию брифа); архитектура уже
  готова к добавлению (`stripWakeWord()` вызывается уже сейчас как no-op).
- **Офлайн-модель Vosk (~45 МБ) не бандлится в этот репозиторий** — среда
  сборки агента не имеет доступа ни к интернету для скачивания модели, ни
  к Android SDK для сборки/прогона на устройстве. `VoskModelProvisioner`
  корректно возвращает `null`, если модель отсутствует; приложение не
  падает, Debug Screen показывает «Модель не загружена». Разработчику нужно
  вручную добавить модель в `feature/src/main/assets/model/` перед сборкой
  (см. PROJECT_STATE.md). Финальный сценарий демонстрации Sprint 3 не был
  прогнан целиком на реальном устройстве в этой сессии по этой причине.
- Голосовое управление громкостью, синтез речи, поиск музыки — не
  реализованы (явно исключены из объёма Sprint 3).

---

## [0.2.1] — 2026-07-01 — Sprint 2 Final Polish

Media Layer подтверждён как стабильно работающий (управление воспроизведением
и информация о треке проверены). Финальная полировка перед Sprint 3.

**Добавлено:**
- Плавное локальное обновление позиции трека: `MediaControllerRepository`
  кэширует последний `MediaMetadata`/`PlaybackState` из параметров
  `MediaController.Callback` и вычисляет текущую позицию по формуле
  `position + elapsed × playbackSpeed` локальным тикером (~300 мс,
  `Dispatchers.Main`) — без единого дополнительного запроса к MediaSession.
  Тикер работает только пока трек играет, немедленно останавливается на
  паузе; при получении нового `PlaybackState` расчёт синхронизируется сам.
- `PlaybackSource` (`feature/media`) — `MEDIA_SESSION` / `KEY_EVENT_FALLBACK` /
  `NO_ACTIVE_SESSION`, вычисляется в `MediaControllerRepository` и показывается
  в Debug Screen новой строкой «Источник».
- Развёрнутая карточка-объяснение доступа к медиасессиям в `SettingsScreen`:
  зачем нужно разрешение, явное уточнение, что приложение не читает
  содержимое уведомлений, кнопка перехода в системные настройки. Показывается
  только при отсутствии разрешения; при наличии — прежняя компактная строка
  со статусом.

**Изменено:**
- `MediaMetadataMapper.map()` больше не принимает `MediaController` — только
  кэшированные `metadata`/`playbackState`/`packageName`/`appName`, что
  устраняет побочные IPC-вызовы при каждом пересчёте состояния.
- `MediaControllerRepository` хранит per-controller снимок
  (`Map<MediaController, Snapshot>`), обновляемый непосредственно из
  параметров колбэков, а не через повторные геттеры контроллера.

**Проверено (самопроверка логики, без изменения архитектуры):**
Spotify, AIMP, YouTube Music (play/pause/next/previous), отсутствие
активного плеера, переключение между несколькими плеерами, повторный запуск
приложения, запуск после перезагрузки телефона (системная перепривязка
`NotificationListenerService`), смена трека, пауза/возобновление.

**Производительность:**
- Тикер позиции запускается/останавливается вместе с `isPlaying`, не работает
  в простое.
- Эмиссия `MutableStateFlow` при тике происходит только если позиция
  действительно изменилась.
- Расчёт позиции — чистая арифметика, без обращений к Binder/MediaSession.

**Ограничения, оставшиеся после Sprint 2:**
- Доступ к медиасессиям включается только вручную в системных настройках;
  онбординга при первом запуске по-прежнему нет.
- Рендеринг обложки трека не реализован (только флаг наличия).
- Информационные голосовые команды не подключены к Media Layer (Sprint 5).

---

## [0.2.0] — 2026-07-01 — Sprint 1 Final Polish + Sprint 2: Media Layer

Sprint 1 подтверждён как завершённый после полного ручного тестирования на
реальном устройстве. Перед Sprint 2 выполнена точечная полировка интерфейса,
затем реализован Media Layer — единственная точка взаимодействия с
музыкальными приложениями Android.

### Sprint 1 Final Polish

**Изменено:**
- Навигация между экранами переведена на iOS-style push/pop:
  новый экран плавно появляется справа, предыдущий уходит влево; при
  возврате — зеркальная анимация. Fade, затемнение и "вспышка" перехода
  устранены (`enterTransition`/`exitTransition`/`popEnterTransition`/
  `popExitTransition` на `NavHost`, `slideIntoContainer`/`slideOutOfContainer`,
  300 мс, `FastOutSlowInEasing`).
- Debug Screen: секция воспроизведения при отсутствии активной медиасессии
  показывает явный, понятный статус вместо пустых полей (переработана в
  контексте Sprint 2 — см. ниже, финальный вид уже содержит реальные данные,
  а не временную заглушку).

### Sprint 2: Media Layer

**Добавлено:**
- `MediaControllerRepository` (`feature/media/impl`) — единственная точка
  работы с `android.media.session.MediaController`: список активных сессий,
  выбор "активной" сессии, регистрация `MediaController.Callback`,
  диспетчеризация Play/Pause/Next/Previous через `transportControls`.
- `MediaMetadataMapper` — `MediaMetadata`/`PlaybackState` → `MediaPlaybackState`.
- `DjNotificationListenerService` (`:service`) — обнаружение активных
  `MediaSession` через `MediaSessionManager.getActiveSessions()`; не читает
  содержимое уведомлений.
- `MediaPlaybackState.hasAlbumArt: Boolean` — наличие обложки трека.
- `NotificationAccess` (UI) — проверка и запрос особого разрешения "доступ к
  уведомлениям" для Media Layer, кнопка в SettingsScreen.
- Debug Screen: секция «Воспроизведение» — состояние, трек, исполнитель,
  альбом, обложка, позиция, длительность, громкость, активный плеер.

**Изменено:**
- `SessionMediaRemote` переписан: теперь единственная реализация `MediaRemote`
  + `MediaStateProvider` (ранее `MediaRemote` был напрямую привязан к
  `KeyEventMediaRemote`, а `SessionMediaRemote` был пустой заглушкой).
  Транспортные команды идут через `MediaController`; при отсутствии активной
  сессии — fallback на `KeyEventMediaRemote` (медиа-клавиши), без изменения
  внешнего контракта.
- `AppModule.bindMediaRemote` теперь привязывает `SessionMediaRemote` вместо
  `KeyEventMediaRemote`.

**Ограничения Sprint 2:**
- Доступ к медиасессиям — особое разрешение ОС, включается только вручную
  через системные настройки; онбординга при первом запуске нет.
- Обложка трека определяется только по наличию, без рендеринга изображения.
- Информационные голосовые команды по-прежнему не подключены к Media Layer
  (это Sprint 5).

---

## [0.1.1] — 2026-07-01 — Sprint 1: Bug Fix & Production Ready

Стабилизация Sprint 1 по результатам ручного тестирования на реальном
устройстве. Архитектура не менялась, новые возможности не добавлялись —
только исправление дефектов и доведение существующей функциональности
до рабочего состояния.

### Исправлено (Fixed)

- **Критический краш при отсутствии разрешения `RECORD_AUDIO`.**
  На Android 14+ `startForeground()` с типом `microphone` выбрасывал
  `SecurityException`, если разрешение не выдано, что приводило к
  аварийному завершению при нажатии «Запустить». Теперь:
  - разрешения запрашиваются автоматически до старта сервиса;
  - вызов `startForeground()` обёрнут в `try/catch`; при любой ошибке
    сервис переходит в состояние `Error`, а приложение продолжает работу
    без падения.
- **Кнопка «Запустить» не запрашивала разрешения.** Теперь кнопка
  «интеллектуальная»: проверяет `RECORD_AUDIO` и `POST_NOTIFICATIONS`
  (Android 13+), показывает системные запросы и автоматически запускает
  сервис после их получения.
- **Отказ в разрешении вёл к неопределённому поведению.** Теперь при
  отказе сервис не запускается, показывается понятное сообщение
  (Snackbar), приложение остаётся работоспособным.
- **Ситуация «Больше не спрашивать» не обрабатывалась.** Теперь она
  определяется, и пользователю предлагается диалог с кнопкой перехода
  в системные настройки приложения.
- **Переключатель «Экран отладки» ничего не делал.** Теперь он включает
  кнопку доступа к полноценному Debug Screen на главном экране.
- **Повторный запуск/остановка сервиса.** Добавлены защиты от повторного
  создания сервиса и от повторной остановки — лишние нажатия
  игнорируются на уровне контроллера и самого сервиса.

### Добавлено (Added)

- **Runtime Permissions flow** (`AppPermissions`, launcher в `MainScreen`):
  автоматический запрос, обработка grant / deny / permanently-denied,
  переход в настройки.
- **Чек-лист готовности на главном экране:** ✓ Микрофон, ✓ Уведомления,
  ✓ Сервис — сразу видно, почему сервис не может стартовать.
  Обновляется при возврате на экран (`ON_RESUME`).
- **Debug Screen (Вариант А, полностью реализован):** состояние сервиса,
  состояние разрешений, последняя ошибка, журнал последних записей,
  версия приложения, медиасессия, аудиоуровень, неизвестные команды.
- **`DjLogBuffer`** — in-memory журнал последних записей и последней
  ошибки для Debug Screen (StateFlow, без записи на диск).
- **Расширенное логирование** ошибок разрешений и запуска сервиса
  (`DjLogger.permission`, `permissionError`, `serviceError`).

### Изменено (Changed)

- `DjForegroundService` — идемпотентный старт (`isForegroundActive`),
  безопасная остановка (`STOP_FOREGROUND_REMOVE`), переход в `Starting`
  → `Running`, обработка ошибок.
- `DjServiceController` — проверка текущего режима перед стартом/остановкой,
  чтобы не создавать сервис повторно.
- `MainActivity.autoStartServiceIfNeeded()` — автозапуск выполняется
  только если все разрешения уже выданы; иначе тихо пропускается
  (запись в лог), чтобы не уходить в `Error` при холодном старте.

### Известные ограничения (по-прежнему актуальны)

- Голосовой цикл не подключён: ASR (Vosk) и запись аудио — заглушки
  (Sprint 3–4).
- `MediaStateProvider` возвращает пустое состояние — реальные метаданные
  появятся в Sprint 7 (NotificationListenerService).
- Автозапуск при холодном старте без разрешений не показывает системный
  запрос (запрос доступен по кнопке «Запустить»).
- Восстановление сервиса после принудительного завершения системой —
  Sprint 8.

---

## [0.1.0] — 2026-06-30 — Sprint 1: Каркас проекта

- Multi-module Gradle проект (app / core / data / feature / service / ui).
- Hilt DI, Version Catalog, Compose Material3 (тёмная тема).
- `DjForegroundService`, `ServiceStateHolder`, уведомление.
- MainScreen, SettingsScreen, DebugScreen, Navigation Compose.
- DataStore для настроек, `DjLogger` (Timber).
- Все интерфейсы и модели финальной архитектуры, 10 команд (заглушки),
  CommandRegistry / CommandDispatcher / IntentRecognizer.
- `UnknownCommandLogger` (CSV), `BeepFeedbackManager`.
- Документация: ROADMAP.md, PROJECT_STATE.md, DECISIONS.md.
