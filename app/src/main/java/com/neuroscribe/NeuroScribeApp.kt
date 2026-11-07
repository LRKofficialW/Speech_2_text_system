package com.neuroscribe

import android.app.Application
import com.neuroscribe.core.ai.AiModelManager
import com.neuroscribe.core.ai.AsrEngine
import com.neuroscribe.core.ai.DiarizationEngine
import com.neuroscribe.core.ai.LlmEngine
import com.neuroscribe.core.audio.AudioProcessor
import com.neuroscribe.core.processing.MemoryBudgetManager
import com.neuroscribe.core.processing.ProcessingCoordinator
import com.neuroscribe.core.processing.TranscriptAssembler
import com.neuroscribe.core.storage.CheckpointRepository
import com.neuroscribe.core.storage.JsonExporter
import java.io.File

class NeuroScribeApp : Application() {

    lateinit var audioProcessor: AudioProcessor
        private set

    lateinit var memoryBudgetManager: MemoryBudgetManager
        private set

    lateinit var modelManager: AiModelManager
        private set

    lateinit var transcriptAssembler: TranscriptAssembler
        private set

    lateinit var jsonExporter: JsonExporter
        private set

    lateinit var checkpointRepository: CheckpointRepository
        private set

    lateinit var processingCoordinator: ProcessingCoordinator
        private set

    override fun onCreate() {
        super.onCreate()

        val modelsDir = File(filesDir, "models").apply { mkdirs() }
        val checkpointsDir = File(filesDir, "checkpoints").apply { mkdirs() }

        audioProcessor = AudioProcessor()
        memoryBudgetManager = MemoryBudgetManager()

        // Three AI Models
        val asrEngine = AsrEngine(
            modelPath = File(modelsDir, "gigaam_v3_ctc.onnx"),
            vocabPath = File(modelsDir, "tokens.txt")
        )
        val diarizationEngine = DiarizationEngine(
            modelPath = File(modelsDir, "diarization_embedding.onnx")
        )
        val llmEngine = LlmEngine(
            modelPath = File(modelsDir, "local_llm_int4.bin")
        )

        modelManager = AiModelManager(
            asrEngine = asrEngine,
            diarizationEngine = diarizationEngine,
            llmEngine = llmEngine,
            memoryBudgetManager = memoryBudgetManager
        )

        transcriptAssembler = TranscriptAssembler()
        jsonExporter = JsonExporter()
        checkpointRepository = CheckpointRepository(checkpointsDir)

        processingCoordinator = ProcessingCoordinator(
            audioProcessor = audioProcessor,
            modelManager = modelManager,
            transcriptAssembler = transcriptAssembler,
            jsonExporter = jsonExporter,
            checkpointRepository = checkpointRepository
        )
    }
}
