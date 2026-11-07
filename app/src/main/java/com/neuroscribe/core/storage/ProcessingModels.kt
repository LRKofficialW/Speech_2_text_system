package com.neuroscribe.core.storage

import kotlinx.serialization.Serializable

@Serializable
data class ContestTranscriptEntry(
    val speaker_id: String,
    val start_ms: Long,
    val end_ms: Long,
    val text: String
)

@Serializable
data class ContestResult(
    val transcript: List<ContestTranscriptEntry>
)

@Serializable
data class AsrWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float = 1.0f
)

@Serializable
data class DiarizationInterval(
    val speakerId: String,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float = 1.0f,
    val isOverlapping: Boolean = false
)

enum class PipelineStage(val stageId: Int, val description: String) {
    STAGE_0_PREPARATION(0, "Подготовка аудио"),
    STAGE_1_ASR(1, "Распознавание речи"),
    STAGE_2_DIARIZATION(2, "Определение говорящих"),
    STAGE_3_ALIGNMENT(3, "Сопоставление реплик"),
    STAGE_4_LLM(4, "Обработка LLM"),
    STAGE_5_JSON_EXPORT(5, "Формирование JSON"),
    COMPLETED(6, "Завершено"),
    FAILED(-1, "Ошибка")
}

@Serializable
data class ProcessingCheckpoint(
    val audioPath: String,
    val stage: Int,
    val asrWords: List<AsrWord> = emptyList(),
    val diarizationIntervals: List<DiarizationInterval> = emptyList(),
    val alignedTranscript: List<ContestTranscriptEntry> = emptyList(),
    val finalResultJson: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
