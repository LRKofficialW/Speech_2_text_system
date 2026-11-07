package com.neuroscribe.core.audio

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Native DSP Engine wrapper with automatic pure-Kotlin fallback
 * for unit tests and environments without NDK binaries.
 */
object NativeDspEngine {

    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("neuroscribe_native")
            isNativeLoaded = true
        } catch (_: UnsatisfiedLinkError) {
            isNativeLoaded = false
        }
    }

    fun isNativeAvailable(): Boolean = isNativeLoaded

    fun resampleAndNormalize(
        inSamples: FloatArray,
        inSampleRate: Int,
        outSampleRate: Int = 16000,
        channels: Int = 1
    ): FloatArray {
        if (inSamples.isEmpty()) return FloatArray(0)
        return if (isNativeLoaded) {
            try {
                nativeResampleAndNormalize(inSamples, inSampleRate, outSampleRate, channels)
                    ?: fallbackResampleAndNormalize(inSamples, inSampleRate, outSampleRate, channels)
            } catch (_: Throwable) {
                fallbackResampleAndNormalize(inSamples, inSampleRate, outSampleRate, channels)
            }
        } else {
            fallbackResampleAndNormalize(inSamples, inSampleRate, outSampleRate, channels)
        }
    }

    fun detectSpeechSegments(
        inSamples: FloatArray,
        sampleRate: Int = 16000,
        energyThresholdDb: Float = -35.0f
    ): List<Pair<Long, Long>> {
        if (inSamples.isEmpty()) return emptyList()
        val flat = if (isNativeLoaded) {
            try {
                nativeDetectSpeechSegments(inSamples, sampleRate, energyThresholdDb)
                    ?: fallbackDetectSpeechSegments(inSamples, sampleRate, energyThresholdDb)
            } catch (_: Throwable) {
                fallbackDetectSpeechSegments(inSamples, sampleRate, energyThresholdDb)
            }
        } else {
            fallbackDetectSpeechSegments(inSamples, sampleRate, energyThresholdDb)
        }

        val result = mutableListOf<Pair<Long, Long>>()
        var i = 0
        while (i + 1 < flat.size) {
            result.add(Pair(flat[i], flat[i + 1]))
            i += 2
        }
        return result
    }

    // ---------------- Pure Kotlin Fallback Implementations ----------------

    private fun fallbackResampleAndNormalize(
        inSamples: FloatArray,
        inSampleRate: Int,
        outSampleRate: Int,
        channels: Int
    ): FloatArray {
        val ch = if (channels > 0) channels else 1
        val frames = inSamples.size / ch

        // 1. Downmix to Mono
        val mono = FloatArray(frames)
        val normFactor = 1.0f / ch.toFloat()
        for (i in 0 until frames) {
            var sum = 0.0f
            for (c in 0 until ch) {
                sum += inSamples[i * ch + c]
            }
            mono[i] = sum * normFactor
        }

        // 2. DC-Offset Removal (IIR single pole filter)
        var prevIn = mono[0]
        var prevOut = 0.0f
        val alpha = 0.995f
        for (i in mono.indices) {
            val curIn = mono[i]
            val curOut = curIn - prevIn + alpha * prevOut
            prevIn = curIn
            prevOut = curOut
            mono[i] = curOut
        }

        // 3. Resample (Linear Interpolation)
        val resampled = if (inSampleRate == outSampleRate) {
            mono
        } else {
            val ratio = outSampleRate.toDouble() / inSampleRate.toDouble()
            val outCount = ceil(mono.size * ratio).toInt()
            val out = FloatArray(outCount)
            for (i in 0 until outCount) {
                val srcPos = i / ratio
                val idx0 = floor(srcPos).toInt()
                val idx1 = min(idx0 + 1, mono.size - 1)
                val frac = (srcPos - idx0).toFloat()
                out[i] = (1.0f - frac) * mono[idx0] + frac * mono[idx1]
            }
            out
        }

        // 4. Normalize Audio (-20 dBFS target, clipping guard)
        var sumSq = 0.0
        var maxAbs = 0.0f
        for (sample in resampled) {
            val a = abs(sample)
            sumSq += (a * a).toDouble()
            if (a > maxAbs) maxAbs = a
        }

        if (maxAbs > 1e-6f && resampled.isNotEmpty()) {
            val currentRms = sqrt(sumSq / resampled.size).toFloat()
            if (currentRms > 1e-6f) {
                var gain = 0.1f / currentRms
                if (maxAbs * gain > 0.98f) {
                    gain = 0.98f / maxAbs
                }
                for (i in resampled.indices) {
                    resampled[i] = (resampled[i] * gain).coerceIn(-1.0f, 1.0f)
                }
            }
        }

        return resampled
    }

    private fun fallbackDetectSpeechSegments(
        inSamples: FloatArray,
        sampleRate: Int,
        energyThresholdDb: Float
    ): LongArray {
        val frameSize = sampleRate * 30 / 1000
        val hopSize = sampleRate * 15 / 1000
        if (inSamples.size < frameSize || frameSize == 0 || hopSize == 0) {
            return longArrayOf(0L, (inSamples.size * 1000L / sampleRate))
        }

        val numFrames = (inSamples.size - frameSize) / hopSize + 1
        val frameSpeech = BooleanArray(numFrames)
        val thresholdLinear = Math.pow(10.0, (energyThresholdDb / 20.0).toDouble()).toFloat()

        for (f in 0 until numFrames) {
            val start = f * hopSize
            var sumSq = 0.0f
            var zcr = 0
            for (i in 0 until frameSize) {
                val s = inSamples[start + i]
                sumSq += s * s
                if (i > 0 && ((inSamples[start + i] >= 0f) != (inSamples[start + i - 1] >= 0f))) {
                    zcr++
                }
            }
            val rms = sqrt(sumSq / frameSize)
            if (rms > thresholdLinear && zcr < (frameSize * 0.7f).toInt()) {
                frameSpeech[f] = true
            }
        }

        // Hangover smoothing (bridge small pauses < 200ms)
        val bridgeFrames = 200 / 15
        var gap = 0
        for (f in 0 until numFrames) {
            if (!frameSpeech[f]) {
                gap++
            } else {
                if (gap in 1..bridgeFrames) {
                    for (b in (f - gap) until f) {
                        frameSpeech[b] = true
                    }
                }
                gap = 0
            }
        }

        // Form segments
        val flatList = mutableListOf<Long>()
        var inSpeech = false
        var chunkStartFrame = 0

        for (f in 0 until numFrames) {
            if (frameSpeech[f] && !inSpeech) {
                inSpeech = true
                chunkStartFrame = f
            } else if (!frameSpeech[f] && inSpeech) {
                inSpeech = false
                val startMs = (chunkStartFrame * hopSize * 1000L) / sampleRate
                val endMs = min(inSamples.size.toLong(), (f * hopSize + frameSize).toLong()) * 1000L / sampleRate
                if (endMs - startMs >= 80L) {
                    flatList.add(startMs)
                    flatList.add(endMs)
                }
            }
        }

        if (inSpeech) {
            val startMs = (chunkStartFrame * hopSize * 1000L) / sampleRate
            val endMs = inSamples.size * 1000L / sampleRate
            flatList.add(startMs)
            flatList.add(endMs)
        }

        return flatList.toLongArray()
    }

    private external fun nativeResampleAndNormalize(
        inSamples: FloatArray,
        inSampleRate: Int,
        outSampleRate: Int,
        channels: Int
    ): FloatArray?

    private external fun nativeDetectSpeechSegments(
        inSamples: FloatArray,
        sampleRate: Int,
        energyThresholdDb: Float
    ): LongArray?
}
