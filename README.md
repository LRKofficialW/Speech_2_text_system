# NeuroScribe — On-Device AI Engine & Android Core

Интеллектуальная система автономного распознавания речи и автоматической диаризации до шести говорящих на Android (Nuclear IT Hack).

## О проекте
- **Полная автономность:** Работа без подключения к сети Интернет.
- **Ровно 3 AI-модели:**
  1. **ASR:** Распознавание русской речи с пословными временными метками (GigaAM-v3 CTC).
  2. **Diarization:** Автоматическое определение 1–6 говорящих (Single-artifact Voice Embeddings + AHC).
  3. **Local LLM:** Пунктуация и форматирование со строгой проверкой лексической эквивалентности (без галлюцинаций).
- **Строго последовательное выполнение:** Модели загружаются и выгружаются по очереди; в памяти находится не более одной тяжелой модели.
- **Ограничение по памяти:** Не более 2 GiB (целевой пик < 1.5 GiB).
- **Фоновая работа:** Android Foreground Service с системными уведомлениями Live Updates (`Notification.ProgressStyle` на Android 16+ / API 36).
- **Экспорт в JSON:** Полное соответствие официальному конкурсному формату организаторов.

## Структура проекта
```text
Speech_2_text_system/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/neuroscribe/
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
│   ├── model_research_and_export.py# Скрипты квантования INT8/INT4 и экспорта моделей
│   └── run_benchmarks.sh           # Быстрый запуск бенчмарков
├── ARCHITECTURE.md                 # Полная техническая документация архитектуры
├── build.gradle.kts
└── settings.gradle.kts
```

## Запуск бенчмарков
```bash
./scripts/run_benchmarks.sh
```

Подробное описание архитектуры и алгоритмов приведено в [ARCHITECTURE.md](ARCHITECTURE.md).
