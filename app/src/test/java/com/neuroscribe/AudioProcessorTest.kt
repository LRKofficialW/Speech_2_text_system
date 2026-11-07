package com.neuroscribe

import com.neuroscribe.core.audio.AudioProcessor
import com.neuroscribe.core.audio.NativeDspEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AudioProcessorTest {

    private val audioProcessor = AudioProcessor()

    @Test
    fun testResampleAndNormalizePureKotlinFallback() {
        // Generate a 44.1kHz sine wave stereo buffer
        val inSampleRate = 44100
        val durationSec = 1.0
        val inCount = (inSampleRate * durationSec * 2).toInt() // stereo
        val stereoInput = FloatArray(inCount)

        for (i in 0 until (inCount / 2)) {
            val s = kotlin.math.sin(2.0 * Math.PI * 440.0 * i / inSampleRate).toFloat() * 0.5f
            stereoInput[i * 2] = s
            stereoInput[i * 2 + 1] = s
        }

        val resampled = NativeDspEngine.resampleAndNormalize(
            inSamples = stereoInput,
            inSampleRate = inSampleRate,
            outSampleRate = 16000,
            channels = 2
        )

        // Resampled size should correspond to ~16000 samples
        val expectedSize = 16000
        val diff = abs(resampled.size - expectedSize)
        assertTrue("Resampled size ($resampled.size) should be close to $expectedSize", diff <= 2)

        // Peak normalized within bounds [-1.0, 1.0]
        for (sample in resampled) {
            assertTrue("Sample must be in range [-1.0, 1.0]", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun testDetectSpeechSegments() {
        val sampleRate = 16000
        val totalSamples = sampleRate * 3 // 3 seconds
        val samples = FloatArray(totalSamples)

        // Insert 1 second of speech (loud sine) in the middle [1.0s, 2.0s]
        for (i in sampleRate until (sampleRate * 2)) {
            samples[i] = kotlin.math.sin(2.0 * Math.PI * 300.0 * i / sampleRate).toFloat() * 0.6f
        }

        val segments = NativeDspEngine.detectSpeechSegments(samples, sampleRate)
        assertTrue("Should detect at least 1 speech segment", segments.isNotEmpty())

        val speechSeg = segments.first()
        assertTrue("Speech segment start should be near 1000ms", speechSeg.first in 800L..1200L)
        assertTrue("Speech segment end should be near 2000ms", speechSeg.second in 1800L..2200L)
    }

    @Test
    fun testSmartChunking() {
        val sampleRate = 16000
        // Create 35 seconds of audio (exceeding MAX_CHUNK_DURATION_MS = 25s)
        val samples = FloatArray(sampleRate * 35)

        val chunks = audioProcessor.createSmartChunks(samples, sampleRate)
        assertTrue("Should create more than 1 chunk for 35s audio", chunks.size >= 2)

        for (chunk in chunks) {
            assertTrue("Chunk start should be >= 0", chunk.startMs >= 0)
            assertTrue("Chunk end should be > start", chunk.endMs > chunk.startMs)
        }
    }
}
