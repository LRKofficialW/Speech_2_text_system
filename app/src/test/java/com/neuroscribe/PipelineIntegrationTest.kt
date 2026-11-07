package com.neuroscribe

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
import com.neuroscribe.core.storage.PipelineStage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PipelineIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testEndToEndProcessingCycle() = runBlocking {
        val baseDir = tempFolder.newFolder("neuroscribe_test")
        val checkpointsDir = File(baseDir, "checkpoints").apply { mkdirs() }
        val testWavFile = File(baseDir, "test_audio.wav")
        val outputJsonFile = File(baseDir, "output.json")

        // Create a synthetic 16kHz mono 16-bit WAV file (3 seconds)
        createSyntheticWav(testWavFile, sampleRate = 16000, durationSec = 3.0)

        val audioProcessor = AudioProcessor()
        val memoryBudgetManager = MemoryBudgetManager()
        val asrEngine = AsrEngine()
        val diarizationEngine = DiarizationEngine()
        val llmEngine = LlmEngine()

        val modelManager = AiModelManager(
            asrEngine = asrEngine,
            diarizationEngine = diarizationEngine,
            llmEngine = llmEngine,
            memoryBudgetManager = memoryBudgetManager
        )

        val transcriptAssembler = TranscriptAssembler()
        val jsonExporter = JsonExporter()
        val checkpointRepository = CheckpointRepository(checkpointsDir)

        val coordinator = ProcessingCoordinator(
            audioProcessor = audioProcessor,
            modelManager = modelManager,
            transcriptAssembler = transcriptAssembler,
            jsonExporter = jsonExporter,
            checkpointRepository = checkpointRepository
        )

        val result = coordinator.processAudio(testWavFile, outputJsonFile)

        assertNotNull(result)
        assertTrue(result.transcript.isNotEmpty())
        assertTrue(outputJsonFile.exists())
        assertEquals(PipelineStage.COMPLETED, coordinator.state.value.stage)
        assertEquals(100, coordinator.state.value.progressPercent)

        // Verify valid JSON formatting
        val parsed = jsonExporter.parseAndValidate(outputJsonFile.readText())
        assertEquals(result.transcript.size, parsed.transcript.size)

        // Verify all 3 models are completely unloaded after pipeline finishes
        assertTrue(!asrEngine.isLoaded())
        assertTrue(!diarizationEngine.isLoaded())
        assertTrue(!llmEngine.isLoaded())
    }

    private fun createSyntheticWav(file: File, sampleRate: Int, durationSec: Double) {
        val totalSamples = (sampleRate * durationSec).toInt()
        val dataSize = totalSamples * 2
        val fos = FileOutputStream(file)

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16) // Subchunk1Size (16 for PCM)
        header.putShort(1) // AudioFormat (1 for PCM)
        header.putShort(1) // NumChannels (1 = Mono)
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2) // ByteRate
        header.putShort(2) // BlockAlign
        header.putShort(16) // BitsPerSample
        header.put("data".toByteArray())
        header.putInt(dataSize)

        fos.write(header.array())

        val pcm = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until totalSamples) {
            val s = (kotlin.math.sin(2.0 * Math.PI * 400.0 * i / sampleRate) * 16000).toInt().toShort()
            pcm.putShort(s)
        }
        fos.write(pcm.array())
        fos.close()
    }
}
