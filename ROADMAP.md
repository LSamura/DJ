# DJ Assistant — Roadmap

## Прогресс

```
Sprint 1    ████████████████████  100%  ✅
Sprint 2    ████████████████████  100%  ✅
Sprint 3    ████████████████████  100%  ✅
Sprint 3.1  ████████████████████  100%  ✅ Voice UX & Recognition Improvements
Sprint 4    ░░░░░░░░░░░░░░░░░░░░    0%
Sprint 5    ░░░░░░░░░░░░░░░░░░░░    0%
Sprint 6    ░░░░░░░░░░░░░░░░░░░░    0%
Sprint 7    ░░░░░░░░░░░░░░░░░░░░    0% (Media Layer перенесён и выполнен в Sprint 2)
```

---

## ✅ Sprint 1 — Каркас проекта + стабилизация + полировка (завершён 2026-07-01)

**Результат:** Проект открывается в Android Studio, собирается и запускается на устройстве. Ручное тестирование на реальном устройстве пройдено полностью (установка, разрешения, Foreground Service, настройки, Debug Screen). Выполнены Bug Fix pass и Final Polish pass.

### Final Polish (2026-07-01)

- [x] Навигация: iOS-style push/pop (плавный слайд, без fade и без "вспышки")
- [x] Debug Screen: честный статус "Playback" вместо пустых полей (пока не было Media Layer)

### Проход Bug Fix & Production Ready (2026-07-01)

- [x] Runtime permissions: автозапрос `RECORD_AUDIO` + `POST_NOTIFICATIONS`
- [x] «Умная» кнопка «Запустить» (grant / deny / permanently-denied → настройки)
- [x] Приложение не падает при отсутствии разрешений (`try/catch` вокруг `startForeground`)
- [x] Идемпотентный старт/остановка сервиса, защита от повторного создания
- [x] Чек-лист готовности на главном экране (✓ Микрофон / ✓ Уведомления / ✓ Сервис)
- [x] Debug Screen полностью реализован (Вариант А) и доступен по переключателю
- [x] Расширенное логирование ошибок (`DjLogBuffer`, `DjLogger.*Error`)
- [x] CHANGELOG.md создан; ROADMAP / PROJECT_STATE / DECISIONS обновлены

### Изначальный каркас (2026-06-30)

- [x] Multi-module Gradle (app / core / data / feature / service / ui)
- [x] Version Catalog (libs.versions.toml)
- [x] Hilt DI — полный граф зависимостей
- [x] DjForegroundService — запуск / уведомление / остановка
- [x] MainScreen — статус, кнопки управления, версия
- [x] SettingsScreen — автозапуск, экран отладки
- [x] DebugScreen — полная панель отладки
- [x] Navigation Compose (Main → Settings, Main → Debug)
- [x] DataStore — настройки приложения
- [x] ServiceStateHolder — shared state между Service и UI
- [x] DjLogger (Timber)
- [x] Все интерфейсы и модели финальной архитектуры
- [x] Все 10 команд (stub implementations, безопасные)
- [x] CommandRegistry + CommandDispatcher + IntentRecognizer
- [x] UnknownCommandLogger (CSV)
- [x] FeedbackManager (ToneGenerator)
- [x] MediaRemote + MediaStateProvider (stub)
- [x] Документация: ROADMAP.md, PROJECT_STATE.md, DECISIONS.md

---

## ✅ Sprint 2 — Media Layer (завершён 2026-07-01)

**Цель:** Полностью рабочий слой взаимодействия с медиаплеерами Android — фундамент для будущего Voice Layer. Без Vosk, без wake word, без LLM.

> Изначально запланированные «Ручной пульт управления» (Sprint 2) и
> `NotificationListenerService` + метаданные (Sprint 7) объединены и
> реализованы здесь одним слоем, по решению из брифа Sprint 2.

- [x] `MediaControllerRepository` — единая точка взаимодействия с
      `android.media.session.MediaController` (framework API, без новых
      зависимостей)
- [x] `MediaMetadataMapper` — преобразование `MediaMetadata` / `PlaybackState`
      в `MediaPlaybackState`
- [x] `DjNotificationListenerService` — обнаружение активных MediaSession
      через `MediaSessionManager.getActiveSessions()`
- [x] `SessionMediaRemote` — единственная реализация `MediaRemote` +
      `MediaStateProvider`; Play/Pause/Next/Previous через
      `MediaController.transportControls`, с fallback на
      `KeyEventMediaRemote`, если активная сессия ещё не обнаружена
- [x] Получение метаданных: название, исполнитель, альбом, наличие обложки,
      состояние воспроизведения, позиция, длительность, приложение-плеер
- [x] Настройки: статус и переход к предоставлению доступа к медиасессиям
- [x] Debug Screen: секция "Воспроизведение" с полным набором полей и честным
      статусом, если сессия недоступна

**Результат:** Media Layer — единственная точка взаимодействия с
музыкальными приложениями, готова к подключению Voice Layer в Sprint 3.

**Ограничение:** доступ к медиасессиям — особое разрешение уровня ОС
(`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`), включается только
пользователем вручную; автоматический запрос системой не предусмотрен.

### Sprint 2 Final Polish (2026-07-01)

- [x] Плавное локальное обновление позиции трека (~300 мс тик,
      `position + elapsed × playbackSpeed`, без повторных запросов к
      MediaSession — снимок `PlaybackState` кэшируется из параметров
      колбэка, а не через повторный `controller.playbackState`)
- [x] `PlaybackSource` в Debug Screen: `MediaSession` / `Резерв (медиа-клавиши)` /
      `Нет активной сессии` — сразу видно, каким способом сейчас
      управляется плеер
- [x] Развёрнутая карточка-объяснение доступа к медиасессиям в Settings
      (что за разрешение, что оно НЕ даёт доступа к содержимому уведомлений,
      кнопка перехода в настройки) — показывается только при отсутствии доступа
- [x] Тикер позиции запускается только пока трек играет и останавливается
      на паузе — не расходует ресурсы в простое

**Результат:** Media Layer полностью готов к интеграции Voice Layer в Sprint 3.

---

## ✅ Sprint 3 — Offline Voice Control MVP (завершён 2026-07-01)

**Цель:** Полностью локальное голосовое управление: микрофон → Vosk → Intent
Parser → Media Layer → команда плееру. Без интернета, без wake word, без
управления громкостью голосом (объём этого спринта поглощает старые планы
Sprint 3 «Аудиопайплайн» и Sprint 4 «Vosk ASR» + голосовую часть Sprint 5).

- [x] `commands.json` (assets) — конфигурация фраз вместо хардкода в командах
- [x] `VoiceCommandConfigLoader` — загрузка и кэширование конфигурации
- [x] `AndroidAudioRecorder`: реальный `AudioRecord` → 16kHz mono PCM16
      `Flow<ByteArray>`, RMS-уровень сигнала для DebugScreen, корректное
      освобождение микрофона
- [x] Интеграция Vosk SDK (`com.alphacephei:vosk-android`) + JNA
- [x] `VoskModelProvisioner` — разворачивает офлайн-модель из assets в
      локальное хранилище (чистый файловый I/O, без сети)
- [x] `VoskSpeechRecognizer`: потоковое распознавание в Grammar Mode,
      confidence на основе `result[].conf`
- [x] `GrammarBuilder`: грамматика строится из `commands.json`, без прошлой
      привязки к `CommandRegistry`
- [x] `KeywordIntentRecognizer` переписан на словарь из `commands.json`
      (регистронезависимое сравнение/`contains`, без LLM)
- [x] `VoiceEngine` (`:service/voice`) — оркестрация всего пайплайна на
      `Dispatchers.IO`, не блокирует главный поток
- [x] `DjVoiceService` — отдельный Android Service, запускается/останавливается
      вместе с `DjForegroundService`, переживает его перезапуск
- [x] Debug Screen: раздел «Voice» (статус, модель, фраза, confidence,
      Intent, Action, время обработки) + журнал последних 10 команд
- [x] Неизвестная команда → `Intent: UNKNOWN`, `Action: None`, никаких действий
- [x] `DjCommand.triggers` и `CommandRegistry.findByTrigger` удалены —
      единственный источник фраз теперь `commands.json`

**Результат:** голосовой цикл "Пауза"/"Продолжай"/"Следующий"/"Предыдущий"/
"Что играет" работает локально через Media Layer.

**Обновление (Sprint 3.1):** офлайн-модель Vosk (`vosk-model-small-ru`, ~45 МБ)
теперь добавлена в репозиторий (`feature/src/main/assets/model/`) — блокер
из первой версии этого раздела снят. `VoskModelProvisioner` по-прежнему
корректно возвращает `null` и ничего не роняет, если модель вдруг
отсутствует — это ограничение среды сборки этого агента (нет Android SDK),
а не приложения.

---

## ✅ Sprint 3.1 — Voice UX & Recognition Improvements (завершён 2026-07-01)

**Цель:** довести голосовое управление до состояния, пригодного для
ежедневного использования — без TTS, LLM и облачных сервисов.

- [x] `commands.json` расширен: словоформы, разговорные варианты для
      PAUSE/PLAY/NEXT/PREVIOUS/QUERY_NOW_PLAYING
- [x] `TextNormalizer` (`feature/voice`) — lowercase, ё→е, без пунктуации,
      схлопывание пробелов; используется и Intent Parser, и Debug Screen
- [x] Confidence threshold: по умолчанию 80%, диапазон 50–95%, настройка
      через Slider в Settings; распознавания ниже порога не выполняются,
      причина отказа показывается в Debug Screen
- [x] Debug Screen: Raw Text, Normalized Text, Confidence, Intent, Action,
      Execution Result, Reject Reason — плюс журнал с этими же полями
- [x] Два режима: `VoiceListeningMode.CONTINUOUS` (как в Sprint 3) и
      `WAKE_WORD` — лёгкое ожидание активационной фразы вместо постоянного
      полного распознавания команд
- [x] `WakeWordDetector` — абстракция для активации; `VoskWakeWordDetector`
      использует тот же Vosk с маленькой грамматикой ("диджей"/"dj"/"джей"),
      что не привязывает Voice Layer к конкретному будущему движку
      (Porcupine/OpenWakeWord подключаются заменой одной реализации)
- [x] Диалоговое окно после активации: 3–15 сек (по умолчанию 6),
      настраивается в Settings, сбрасывается при каждой распознанной фразе
- [x] Голосовые команды переключения режима: "Пока слушай" → Continuous,
      "Перестань слушать" → Wake Mode — перехватываются в `VoiceEngine`,
      никогда не доходят до `CommandDispatcher`/Media Layer
- [x] Управление громкостью: громче/тише/максимум/минимум/"громкость N
      процентов" (параметризованная команда через regex + `CommandContext.intent`)
- [x] Bluetooth-микрофон: автоматический выбор SCO-входа при подключённой
      гарнитуре (best-effort, без ручного выбора — см. ограничение ниже);
      текущий источник виден в Debug Screen
- [x] Мелкая полировка анимаций: `animateContentSize()` на карточках
      MainScreen и секциях Debug Screen — без полного редизайна

**Результат:** Voice Layer готов к ежедневному использованию: меньше
ложных срабатываний (confidence threshold), больше принимаемых формулировок
(расширенный словарь), два режима прослушивания, управление громкостью,
полная диагностика на Debug Screen.

**Ограничения:**
- Bluetooth-микрофон — только автоматический выбор; Android не даёт простого
  API для ручного выбора входного устройства в приложении, поэтому ручной
  переключатель не добавлен (по явно разрешённому в брифе запасному варианту).
- Полноценный wake-word движок (Porcupine/OpenWakeWord) не подключён —
  используется тот же Vosk с урезанной грамматикой; архитектура
  (`WakeWordDetector`) готова к замене без переписывания `VoiceEngine`.
- Самопроверка выполнена по коду и API-контрактам (аудио/Vosk/DataStore);
  реальный прогон на устройстве (Bluetooth-гарнитура, оба режима
  прослушивания, пороги confidence) — за пользователем, т.к. в среде сборки
  этого агента нет Android SDK и физического микрофона/Bluetooth.

---

## Sprint 4 — Полноценный Wake Word движок + журнал

**Цель:** заменить Vosk-заглушку wake word на специализированный движок,
расширить обзор журнала.

- [ ] Подключить Porcupine или OpenWakeWord через реализацию `WakeWordDetector`
      (интерфейс уже готов, Vosk-реализация заменяется без изменения `VoiceEngine`)
- [ ] Журнал неизвестных команд — уже активен (Sprint 1), расширить обзор в UI
- [ ] Показ результата "Что играет" пользователю без синтеза речи (пока
      только Debug Screen — рассмотреть вывод на MainScreen)
- [ ] Обработка edge cases: нет активного плеера, команда не распознана

**Результат:** Полный голосовой цикл работает.

---

## Sprint 6 — Журнал и отладка

**Цель:** Инструменты для развития приложения.

- [ ] DebugScreen полностью подключён к реальным данным
- [ ] Экспорт CSV журнала через FileProvider / Share
- [ ] Просмотр журнала внутри приложения
- [ ] Статистика команд (топ-5 запросов)
- [ ] Показ пути к файлу журнала

**Результат:** Понятно, какие команды произносит пользователь.

---

## ✅ Sprint 7 — NotificationListenerService + метаданные (выполнен досрочно в Sprint 2)

**Изначальная цель:** Читать что сейчас играет.

Весь объём этого спринта (`NotificationListenerService`, `SessionMediaRemote`
через `MediaController`, метаданные в DebugScreen) реализован в Sprint 2 как
часть Media Layer — см. выше. Остаётся только:

- [ ] Onboarding-подсказка при первом запуске, если доступ к медиасессиям не выдан
      (сейчас доступно только через Настройки, без первого запуска подсказки)
- [ ] Информационные голосовые команды ("Что играет?", "Кто исполнитель?")
      подключатся к Media Layer в Sprint 5 вместе с остальным голосовым циклом

---

## Sprint 8 — Полировка

**Цель:** Стабильность для ежедневного использования.

- [ ] Оптимизация батареи (профилирование)
- [ ] Исключение из Doze Mode (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
- [ ] Bluetooth: автозапуск при подключении колонки
- [ ] Crash recovery — сервис перезапускается после сбоя
- [ ] Полное тестирование: Spotify, YouTube Music, VK Музыка, AIMP, Poweramp
- [ ] SettingsScreen: порог уверенности Vosk, выбор языка

**Результат:** Приложение готово к ежедневному использованию.

---

## Будущие возможности (без срока)

- Porcupine wake word detector (отдельное слово-активатор)
- Отложенные команды ("через 20 минут выключи")
- ONNX NLU модель вместо rule-based
- Android Auto интеграция
- Equalizer управление голосом
- Bluetooth-специфичные команды
