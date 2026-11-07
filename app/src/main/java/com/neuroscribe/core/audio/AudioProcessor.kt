package com.neuroscribe.core.audio

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

data class AudioMetadata(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val totalSamples: Long,
    val durationMs: Long
)

data class PreparedAudioSegment(
    val index: Int,
    val samples: FloatArray,
    val startMs: Long,
    val endMs: Long,
    val originalStartSample: Long
)

class AudioProcessor {

    companion object {
        const val TARGET_SAMPLE_RATE = 16000
        const val MAX_CHUNK_DURATION_MS = 25000L // 25s chunks for memory safety
        const val OVERLAP_MS = 500L              // 500ms overlap to avoid clipping boundary words
        const val MIN_PAUSE_MS = 300L            // Pauses >= 300ms used as split candidates
    }

    /**
     * Reads a WAV file into normalized float samples [-1.0, 1.0] at 16kHz mono.
     */
    fun loadAndPreprocessWav(file: File): Pair<AudioMetadata, FloatArray> {
        val fis = FileInputStream(file)
        val header = ByteArray(44)
        val readHeader = fis.read(header)
        if (readHeader < 44) {
            fis.close()
            throw IllegalArgumentException("Invalid WAV file: header too short (${file.name})")
        }

        // Validate RIFF & WAVE markers
        val riff = String(header, 0, 4)
        val wave = String(header, 8, 4)
        if (riff != "RIFF" || wave != "WAVE") {
            fis.close()
            throw IllegalArgumentException("Unsupported format (expected RIFF WAVE): ${file.name}")
        }

        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val channels = bb.getShort(22).toInt()
        val sampleRate = bb.getInt(24)
        val bitsPerSample = bb.getShort(34).toInt()

        // Read remaining audio data
        val audioBytes = fis.readBytes()
        fis.close()

        val rawSamples = decodePcmToFloat(audioBytes, bitsPerSample)
        val metadata = AudioMetadata(
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            totalSamples = (rawSamples.size / max(1, channels)).toLong(),
            durationMs = (rawSamples.size / max(1, channels) * 1000L) / sampleRate
        )

        // Resample, remove DC offset, and normalize to 16kHz Mono
        val processedSamples = NativeDspEngine.resampleAndNormalize(
            inSamples = rawSamples,
            inSampleRate = sampleRate,
            outSampleRate = TARGET_SAMPLE_RATE,
            channels = channels
        )

        return Pair(metadata, processedSamples)
    }

    /**
     * Splits long audio into manageable chunks with overlap at speech boundaries.
     */
    fun createSmartChunks(samples: FloatArray, sampleRate: Int = TARGET_SAMPLE_RATE): List<PreparedAudioSegment> {
        if (samples.isEmpty()) return emptyList()

        val totalDurationMs = (samples.size.toLong() * 1000L) / sampleRate
        if (totalDurationMs <= MAX_CHUNK_DURATION_MS) {
            return listOf(
                PreparedAudioSegment(
                    index = 0,
                    samples = samples,
                    startMs = 0L,
                    endMs = totalDurationMs,
                    originalStartSample = 0L
                )
            )
        }

        // Use algorithmic VAD to detect pauses
        val speechSegments = NativeDspEngine.detectSpeechSegments(samples, sampleRate)
        val segments = mutableListOf<PreparedAudioSegment>()

        var currentStartMs = 0L
        var chunkIndex = 0

        var segIdx = 0
        while (currentStartMs < totalDurationMs) {
            var targetEndMs = min(totalDurationMs, currentStartMs + MAX_CHUNK_DURATION_MS)

            // Look for a natural pause near the end of the chunk
            var splitMs = targetEndMs
            while (segIdx + 1 < speechSegments.size) {
                val curSeg = speechSegments[segIdx]
                val nextSeg = speechSegments[segIdx + 1]

                if (curSeg.second > currentStartMs + (MAX_CHUNK_DURATION_MS / 2) && curSeg.second <= targetEndMs) {
                    val pauseMs = nextSeg.first - curSeg.second
                    if (pauseMs >= MIN_PAUSE_MS) {
                        splitMs = curSeg.second + (pauseMs / 2)
                        break
                    }
                }
                if (curSeg.first > targetEndMs) break
                segIdx++
            }

            val chunkEndMs = min(totalDurationMs, splitMs)
            val startSample = (currentStartMs * sampleRate / 1000L).toInt()
            val endSample = min(samples.size.toLong(), (chunkEndMs * sampleRate / 1000L)).toInt()

            if (endSample > startSample) {
                val chunkSamples = samples.copyOfRange(startSample, endSample)
                segments.add(
                    PreparedAudioSegment(
                        index = chunkIndex++,
                        samples = chunkSamples,
                        startMs = currentStartMs,
                        endMs = chunkEndMs,
                        originalStartSample = startSample.toLong()
                    )
                )
            }

            if (chunkEndMs >= totalDurationMs) break

            // Apply overlap for the next chunk
            currentStartMs = max(0L, chunkEndMs - OVERLAP_MS)
        }

        return segments
    }

    private fun decodePcmToFloat(bytes: ByteArray, bitsPerSample: Int): FloatArray {
        return when (bitsPerSample) {
            16 -> {
                val count = bytes.size / 2
                val floats = FloatArray(count)
                val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until count) {
                    floats[i] = bb.short / 32768.0f
                }
                floats
            }
            8 -> {
                val floats = FloatArray(bytes.size)
                for (i in bytes.indices) {
                    // Unsigned 8-bit PCM is biased around 128
                    val unsigned = bytes[i].toInt() and 0xFF
                    floats[i] = (unsigned - 128) / 128.0f
                }
                floats
            }
            32 -> {
                val count = bytes.size / 4
                val floats = FloatArray(count)
                val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until count) {
                    floats[i] = bb.float
                }
                floats
            }
            else -> {
                throw UnsupportedOperationException("Unsupported bit depth: $bitsPerSample bits per sample")
            }
        }
    }
}
