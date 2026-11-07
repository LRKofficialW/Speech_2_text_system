# NeuroScribe — On-Device AI Engine & Android Core

[![Download APK](https://img.shields.io/badge/Download-NeuroScribe_APK-brightgreen?style=for-the-badge&logo=android)](release/neuroscribe.apk)

### 📲 [👉 Нажмите здесь, чтобы скачать NeuroScribe APK на Android](release/neuroscribe.apk)
*(Прямая ссылка на скачивание из репозитория: `release/neuroscribe.apk` или [NeuroScribe-v1.0.0.apk](release/NeuroScribe-v1.0.0.apk))*

---

## О продукте
**NeuroScribe** — полностью автономная система распознавания речи, автоматической диаризации до шести говорящих и формирования итоговой стенограммы для Android.

- **Полная автономность:** 100% On-Device, работает без интернета и облачных сервисов.
- **Ровно 3 AI-модели:**
  1. **ASR (Модель №1):** Высокоточное распознавание русской речи с пословными временными метками (`start_ms`, `end_ms`).
  2. **Speaker Diarization (Модель №2):** Автоматическое определение от 1 до 6 участников на основе матрицы косинусного сходства голосовых эмбеддингов и AHC.
  3. **Local LLM (Модель №3):** Расстановка знаков препинания и заглавных букв со строгой проверкой лексической эквивалентности (исключает галлюцинации).
- **Строго последовательное выполнение:** Модели загружаются и выгружаются по очереди; в оперативной памяти никогда не находится более одной тяжелой модели.
- **Ограничение RAM:** До 2 GiB (целевой пик < 1.5 GiB).
- **Пользовательский интерфейс (UI):**
  - Экран выбора аудиозаписи (.wav, .mp3, .m4a).
  - Индикатор стадий (1/6 Подготовка -> 2/6 ASR -> 3/6 Диаризация -> 4/6 Сопоставление -> 5/6 LLM -> 6/6 JSON).
  - Диалоговая лента реплик со стилизованными цветными бейджами `speaker_1`..`speaker_6` и временными метками `[00:01 — 00:04]`.
  - Кнопка экспорта стенограммы в официальный конкурсный формат JSON.
- **Фоновая работа и Live Updates:**
  - Foreground Service (`dataSync`) с частичной блокировкой сна (`WAKE_LOCK`).
  - Системный прогресс на экране блокировки (`Notification.ProgressStyle` для Android 16+ / API 36).

---

## 🚀 Установка на Android
1. Скачайте файл [`release/neuroscribe.apk`](release/neuroscribe.apk) на смартфон.
2. Откройте скачанный APK в проводнике или шторке уведомлений.
3. Разрешите установку из доверенного источника при первом запуске.
4. Откройте приложение **NeuroScribe**, выберите аудиофайл и нажмите **«Начать расшифровку (AUTO)»**.

---

## Структура проекта
```text
Speech_2_text_system/
├── release/
│   ├── neuroscribe.apk             # Готовый установочный APK для Android
│   └── NeuroScribe-v1.0.0.apk      # Версионированный релизный APK
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── res/
│   │   │   ├── layout/             # activity_main.xml, item_transcript_utterance.xml
│   │   │   └── values/             # colors.xml, strings.xml, themes.xml
│   │   ├── java/com/neuroscribe/
│   │   │   ├── ui/                 # MainActivity (выбор аудио, лента реплик, экспорт)
│   │   │   ├── core/
│   │   │   │   ├── audio/          # DSP, декодер WAV, алгоритмический VAD, Smart Chunking
│   │   │   │   ├── ai/             # Движки 3 моделей (ASR, Diarization, LLM), AiModelManager
│   │   │   │   ├── processing/     # ProcessingCoordinator, TranscriptAssembler, MemoryBudgetManager
│   │   │   │   ├── storage/        # Модели данных, CheckpointRepository, JsonExporter
│   │   │   │   └── android/        # ProcessingForegroundService, LiveUpdatesNotifier
│   │   │   └── NeuroScribeApp.kt
│   │   └── cpp/                    # C++20 нативный DSP (ресемплинг, фильтрация, SIMD/NEON)
│   └── src/test/                   # Юнит- и интеграционные тесты
├── benchmark/
│   ├── benchmark_runner.py         # Полный запуск метрик (WER, DER, JER, RTF, Memory, JSON)
│   ├── synthetic_test_generator.py # Генератор тестов (1–6 говорящих, одновременная речь)
│   └── BENCHMARK_REPORT.md         # Отчет о тестировании
├── scripts/
│   ├── package_apk.py              # Автоматическая сборка и подпись APK
│   ├── model_research_and_export.py# Скрипты квантования INT8/INT4 и экспорта моделей
│   └── run_benchmarks.sh           # Запуск бенчмарков
├── ARCHITECTURE.md                 # Полная техническая документация архитектуры
├── build.gradle.kts
└── settings.gradle.kts
```

## Тестирование и бенчмарки
Для запуска бенчмарков на тестовых сценариях (1–6 говорящих, одновременная речь):
```bash
./scripts/run_benchmarks.sh
```

Подробное описание архитектуры и алгоритмов приведено в [ARCHITECTURE.md](ARCHITECTURE.md).
