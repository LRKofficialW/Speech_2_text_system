package com.neuroscribe.core.processing

import com.neuroscribe.core.ai.AiModelManager
import com.neuroscribe.core.audio.AudioProcessor
import com.neuroscribe.core.audio.PreparedAudioSegment
import com.neuroscribe.core.storage.AsrWord
import com.neuroscribe.core.storage.CheckpointRepository
import com.neuroscribe.core.storage.ContestResult
import com.neuroscribe.core.storage.ContestTranscriptEntry
import com.neuroscribe.core.storage.DiarizationInterval
import com.neuroscribe.core.storage.JsonExporter
import com.neuroscribe.core.storage.PipelineStage
import com.neuroscribe.core.storage.ProcessingCheckpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

data class ProcessingState(
    val stage: PipelineStage = PipelineStage.STAGE_0_PREPARATION,
    val progressPercent: Int = 0,
    val statusMessage: String = "Инициализация...",
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false,
    val errorMessage: String? = null,
    val result: ContestResult? = null
)

class ProcessingCoordinator(
    private val audioProcessor: AudioProcessor,
    private val modelManager: AiModelManager,
    private val transcriptAssembler: TranscriptAssembler,
    private val jsonExporter: JsonExporter,
    private val checkpointRepository: CheckpointRepository
) {

    private val _state = MutableStateFlow(ProcessingState())
    val state: StateFlow<ProcessingState> = _state.asStateFlow()

    private var isCancelled = false

    fun cancel() {
        isCancelled = true
        _state.value = _state.value.copy(
            isCancelled = true,
            statusMessage = "Обработка отменена пользователем"
        )
    }

    suspend fun processAudio(audioFile: File, outputFile: File? = null): ContestResult = withContext(Dispatchers.Default) {
        isCancelled = false
        val audioPath = audioFile.absolutePath

        try {
            // Check for existing checkpoint to resume
            val checkpoint = checkpointRepository.loadCheckpoint(audioPath)

            // Stage 0: Audio Preparation
            _state.value = ProcessingState(
                stage = PipelineStage.STAGE_0_PREPARATION,
                progressPercent = 5,
                statusMessage = "Декодирование и нормализация аудио..."
            )
            checkCancellation()

            val (metadata, samples) = audioProcessor.loadAndPreprocessWav(audioFile)
            val chunks = audioProcessor.createSmartChunks(samples)

            _state.value = _state.value.copy(
                progressPercent = 15,
                statusMessage = "Подготовка завершена (${metadata.durationMs / 1000} сек, ${chunks.size} фрагментов)"
            )
            checkCancellation()

            // Stage 1: ASR (Model #1)
            val asrWords: List<AsrWord> = if (checkpoint != null && checkpoint.stage >= 1 && checkpoint.asrWords.isNotEmpty()) {
                _state.value = _state.value.copy(
                    stage = PipelineStage.STAGE_1_ASR,
                    progressPercent = 40,
                    statusMessage = "ASR восстановлен из контрольной точки"
                )
                checkpoint.asrWords
            } else {
                _state.value = ProcessingState(
                    stage = PipelineStage.STAGE_1_ASR,
                    progressPercent = 20,
                    statusMessage = "Загрузка модели ASR..."
                )
                checkCancellation()

                val recognized = modelManager.executeWithAsr { asr ->
                    _state.value = _state.value.copy(statusMessage = "Распознавание речи...")
                    asr.transcribe(chunks) { chunkProgress ->
                        val p = 20 + (chunkProgress * 20).toInt()
                        _state.value = _state.value.copy(progressPercent = p)
                    }
                }
                checkpointRepository.saveCheckpoint(
                    ProcessingCheckpoint(
                        audioPath = audioPath,
                        stage = 1,
                        asrWords = recognized
                    )
                )
                recognized
            }
            checkCancellation()

            // Stage 2: Diarization (Model #2)
            val diarizationIntervals: List<DiarizationInterval> = if (checkpoint != null && checkpoint.stage >= 2 && checkpoint.diarizationIntervals.isNotEmpty()) {
                _state.value = _state.value.copy(
                    stage = PipelineStage.STAGE_2_DIARIZATION,
                    progressPercent = 65,
                    statusMessage = "Диаризация восстановлена из контрольной точки"
                )
                checkpoint.diarizationIntervals
            } else {
                _state.value = ProcessingState(
                    stage = PipelineStage.STAGE_2_DIARIZATION,
                    progressPercent = 45,
                    statusMessage = "Загрузка модели диаризации..."
                )
                checkCancellation()

                val diarized = modelManager.executeWithDiarization { diar ->
                    _state.value = _state.value.copy(statusMessage = "Определение говорящих...")
                    diar.diarize(samples) { diarProgress ->
                        val p = 45 + (diarProgress * 20).toInt()
                        _state.value = _state.value.copy(progressPercent = p)
                    }
                }
                checkpointRepository.saveCheckpoint(
                    ProcessingCheckpoint(
                        audioPath = audioPath,
                        stage = 2,
                        asrWords = asrWords,
                        diarizationIntervals = diarized
                    )
                )
                diarized
            }
            checkCancellation()

            // Stage 3: Algorithmic Alignment (Transcript Alignment)
            _state.value = ProcessingState(
                stage = PipelineStage.STAGE_3_ALIGNMENT,
                progressPercent = 70,
                statusMessage = "Сопоставление слов и говорящих..."
            )
            checkCancellation()

            val alignedUtterances = transcriptAssembler.alignTranscript(asrWords, diarizationIntervals)

            checkpointRepository.saveCheckpoint(
                ProcessingCheckpoint(
                    audioPath = audioPath,
                    stage = 3,
                    asrWords = asrWords,
                    diarizationIntervals = diarizationIntervals,
                    alignedTranscript = alignedUtterances
                )
            )
            checkCancellation()

            // Stage 4: Local LLM (Model #3)
            _state.value = ProcessingState(
                stage = PipelineStage.STAGE_4_LLM,
                progressPercent = 80,
                statusMessage = "Загрузка локальной LLM..."
            )
            checkCancellation()

            val formattedUtterances = modelManager.executeWithLlm { llm ->
                _state.value = _state.value.copy(statusMessage = "Восстановление пунктуации и форматирования...")
                alignedUtterances.mapIndexed { idx, utterance ->
                    val punctuated = llm.formatText(utterance.text)
                    val p = 80 + ((idx + 1).toFloat() / alignedUtterances.size * 10).toInt()
                    _state.value = _state.value.copy(progressPercent = p)
                    utterance.copy(text = punctuated)
                }
            }
            checkCancellation()

            // Stage 5: Final Result & JSON Export
            _state.value = ProcessingState(
                stage = PipelineStage.STAGE_5_JSON_EXPORT,
                progressPercent = 95,
                statusMessage = "Формирование и валидация JSON..."
            )

            val contestResult = ContestResult(transcript = formattedUtterances)
            jsonExporter.validate(contestResult)

            if (outputFile != null) {
                jsonExporter.exportToFile(contestResult, outputFile)
            }

            // Clean up completed checkpoint
            checkpointRepository.clearCheckpoint(audioPath)

            _state.value = ProcessingState(
                stage = PipelineStage.COMPLETED,
                progressPercent = 100,
                statusMessage = "Стенограмма успешно сформирована",
                isCompleted = true,
                result = contestResult
            )

            contestResult
        } catch (e: CancellationException) {
            _state.value = _state.value.copy(
                isCancelled = true,
                statusMessage = "Обработка отменена"
            )
            modelManager.unloadAll()
            throw e
        } catch (e: Throwable) {
            _state.value = ProcessingState(
                stage = PipelineStage.FAILED,
                progressPercent = 0,
                statusMessage = "Ошибка: ${e.localizedMessage ?: e.message}",
                errorMessage = e.message ?: e.toString()
            )
            modelManager.unloadAll()
            throw e
        }
    }

    private fun checkCancellation() {
        if (isCancelled) {
            throw CancellationException("Processing was cancelled by user")
        }
    }
}
