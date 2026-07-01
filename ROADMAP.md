# DJ Assistant — Roadmap

## Прогресс

```
Sprint 1  ████████████████████  100%  ✅
Sprint 2  ░░░░░░░░░░░░░░░░░░░░    0%
Sprint 3  ░░░░░░░░░░░░░░░░░░░░    0%
Sprint 4  ░░░░░░░░░░░░░░░░░░░░    0%
Sprint 5  ░░░░░░░░░░░░░░░░░░░░    0%
Sprint 6  ░░░░░░░░░░░░░░░░░░░░    0%
Sprint 7  ░░░░░░░░░░░░░░░░░░░░    0%
```

---

## ✅ Sprint 1 — Каркас проекта + стабилизация (завершён 2026-07-01)

**Результат:** Проект открывается в Android Studio, собирается и запускается на устройстве. После ручного тестирования выполнен проход Bug Fix & Production Ready.

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

## Sprint 2 — Ручной пульт управления

**Цель:** Управление активным плеером вручную — без голоса.

- [ ] Добавить кнопки Play / Pause / Next / Previous / Vol+ / Vol− в MainScreen
- [ ] Проверить KeyEventMediaRemote на Spotify, AIMP, VK Музыка
- [ ] Показывать текущий статус кнопок (disabled когда нет активного плеера)
- [ ] Запрос разрешения `POST_NOTIFICATIONS` (Android 13+)
- [ ] Базовая обработка отсутствия активного плеера

**Результат:** Приложение управляет любым плеером кнопками на экране.

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

## Sprint 7 — NotificationListenerService + метаданные

**Цель:** Читать что сейчас играет.

- [ ] `NotificationListenerService` — реализация
- [ ] Onboarding: запрос разрешения "Доступ к уведомлениям"
- [ ] `SessionMediaRemote`: реальное управление через MediaController
- [ ] Информационные команды работают: "Что играет?", "Кто исполнитель?"
- [ ] MediaState в DebugScreen показывает реальные данные

**Результат:** Приложение знает что играет и отвечает на информационные запросы.

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
