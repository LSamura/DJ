# CHANGELOG

Все значимые изменения проекта фиксируются в этом файле.

Формат основан на [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/).

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
