# DJ Assistant — Roadmap

## Прогресс

```
Sprint 1  ████████████████████  100%  ✅
Sprint 2  ████████████████████  100%  ✅
Sprint 3  ░░░░░░░░░░░░░░░░░░░░    0%
Sprint 4  ░░░░░░░░░░░░░░░░░░░░    0%
Sprint 5  ░░░░░░░░░░░░░░░░░░░░    0%
Sprint 6  ░░░░░░░░░░░░░░░░░░░░    0%
Sprint 7  ░░░░░░░░░░░░░░░░░░░░    0% (Media Layer перенесён и выполнен в Sprint 2)
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

## Sprint 3 — Аудиопайплайн

**Цель:** Захват аудио в реальном времени.

- [ ] `AndroidAudioRecorder`: AudioRecord → PCM Flow<ByteArray>
- [ ] CircularAudioBuffer
- [ ] VAD по уровню энергии (silence detection)
- [ ] Отображение уровня сигнала в DebugScreen (AudioLevelBar работает)
- [ ] Управление AudioFocus при записи
- [ ] Запрос разрешения `RECORD_AUDIO` в рантайме

**Результат:** Аудиопоток работает, уровень сигнала виден в DebugScreen.

---

## Sprint 4 — Vosk ASR + Grammar Mode

**Цель:** Распознавание русской речи офлайн.

- [ ] Интеграция Vosk SDK (aar)
- [ ] Загрузка `vosk-model-small-ru` (~50 MB в assets)
- [ ] `VoskSpeechRecognizer`: потоковый режим + Grammar Mode
- [ ] `GrammarBuilder`: автогенерация грамматики из CommandRegistry
- [ ] Инициализация модели при старте сервиса
- [ ] Отображение распознанного текста в DebugScreen

**Результат:** Произносишь фразу — видишь текст на экране.

---

## Sprint 5 — Голосовые команды (полный цикл)

**Цель:** "Диджей, пауза" → музыка останавливается.

- [ ] Подключение ASR → IntentRecognizer → CommandDispatcher
- [ ] Фильтрация по wake word "диджей"
- [ ] FeedbackManager: звуковые сигналы реально работают
- [ ] Журнал неизвестных команд активен
- [ ] Показ результата команды в MainScreen (SuccessWithInfo)
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
