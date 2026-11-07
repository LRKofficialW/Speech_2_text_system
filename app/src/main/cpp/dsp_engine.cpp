#include "dsp_engine.h"
#include <jni.h>
#include <algorithm>
#include <cmath>
#include <numeric>

namespace neuroscribe::dsp {

void DspEngine::removeDcOffset(float* samples, size_t count, float alpha) {
    if (!samples || count == 0) return;
    float prevIn = samples[0];
    float prevOut = 0.0f;
    for (size_t i = 0; i < count; ++i) {
        float in = samples[i];
        float out = in - prevIn + alpha * prevOut;
        prevIn = in;
        prevOut = out;
        samples[i] = out;
    }
}

void DspEngine::normalizeAudio(float* samples, size_t count, float targetRms) {
    if (!samples || count == 0) return;

    double sumSq = 0.0;
    float maxAbs = 0.0f;
    for (size_t i = 0; i < count; ++i) {
        float val = std::abs(samples[i]);
        sumSq += static_cast<double>(val) * static_cast<double>(val);
        if (val > maxAbs) maxAbs = val;
    }

    if (maxAbs < 1e-6f) return;

    float currentRms = static_cast<float>(std::sqrt(sumSq / count));
    if (currentRms < 1e-6f) return;

    float gain = targetRms / currentRms;
    // Prevent clipping: max peak after gain should not exceed 0.98
    if (maxAbs * gain > 0.98f) {
        gain = 0.98f / maxAbs;
    }

    for (size_t i = 0; i < count; ++i) {
        samples[i] = std::clamp(samples[i] * gain, -1.0f, 1.0f);
    }
}

std::vector<float> DspEngine::resample(const float* input, size_t inCount, int inSampleRate, int outSampleRate) {
    if (!input || inCount == 0) return {};
    if (inSampleRate == outSampleRate) {
        return std::vector<float>(input, input + inCount);
    }

    double ratio = static_cast<double>(outSampleRate) / static_cast<double>(inSampleRate);
    size_t outCount = static_cast<size_t>(std::ceil(inCount * ratio));
    std::vector<float> output(outCount);

    for (size_t i = 0; i < outCount; ++i) {
        double srcPos = static_cast<double>(i) / ratio;
        size_t idx0 = static_cast<size_t>(std::floor(srcPos));
        size_t idx1 = std::min(idx0 + 1, inCount - 1);
        double frac = srcPos - static_cast<double>(idx0);

        output[i] = static_cast<float>((1.0 - frac) * input[idx0] + frac * input[idx1]);
    }
    return output;
}

std::vector<float> DspEngine::downmixToMono(const float* input, size_t inFrames, int channels) {
    if (!input || inFrames == 0 || channels <= 0) return {};
    if (channels == 1) {
        return std::vector<float>(input, input + inFrames);
    }

    std::vector<float> mono(inFrames);
    float normFactor = 1.0f / static_cast<float>(channels);
    for (size_t i = 0; i < inFrames; ++i) {
        float sum = 0.0f;
        for (int c = 0; c < channels; ++c) {
            sum += input[i * channels + c];
        }
        mono[i] = sum * normFactor;
    }
    return mono;
}

std::vector<AudioChunk> DspEngine::detectSpeechActivity(
    const float* samples,
    size_t count,
    int sampleRate,
    int frameSizeMs,
    int hopSizeMs,
    float energyThresholdDb
) {
    if (!samples || count == 0) return {};

    size_t frameSize = static_cast<size_t>(sampleRate * frameSizeMs / 1000);
    size_t hopSize = static_cast<size_t>(sampleRate * hopSizeMs / 1000);
    if (frameSize == 0 || hopSize == 0) return {};

    size_t numFrames = (count >= frameSize) ? ((count - frameSize) / hopSize + 1) : 0;
    std::vector<bool> frameSpeech(numFrames, false);

    // Compute frame energies in dB
    float thresholdLinear = std::pow(10.0f, energyThresholdDb / 20.0f);
    for (size_t f = 0; f < numFrames; ++f) {
        size_t start = f * hopSize;
        float sumSq = 0.0f;
        int zcr = 0;
        for (size_t i = 0; i < frameSize; ++i) {
            float val = samples[start + i];
            sumSq += val * val;
            if (i > 0 && ((samples[start + i] >= 0.0f) != (samples[start + i - 1] >= 0.0f))) {
                zcr++;
            }
        }
        float rms = std::sqrt(sumSq / frameSize);
        // Speech frame criteria: RMS above threshold and moderate ZCR
        if (rms > thresholdLinear && zcr < static_cast<int>(frameSize * 0.7f)) {
            frameSpeech[f] = true;
        }
    }

    // Hangover smoothing (bridge small gaps < 200ms)
    size_t bridgeFrames = static_cast<size_t>(200 / hopSizeMs);
    size_t gapCount = 0;
    for (size_t f = 0; f < numFrames; ++f) {
        if (!frameSpeech[f]) {
            gapCount++;
        } else {
            if (gapCount > 0 && gapCount <= bridgeFrames) {
                for (size_t b = f - gapCount; b < f; ++b) {
                    frameSpeech[b] = true;
                }
            }
            gapCount = 0;
        }
    }

    // Group into chunks
    std::vector<AudioChunk> chunks;
    bool inSpeech = false;
    size_t chunkStartFrame = 0;

    for (size_t f = 0; f < numFrames; ++f) {
        if (frameSpeech[f] && !inSpeech) {
            inSpeech = true;
            chunkStartFrame = f;
        } else if (!frameSpeech[f] && inSpeech) {
            inSpeech = false;
            size_t startSample = chunkStartFrame * hopSize;
            size_t endSample = std::min(f * hopSize + frameSize, count);
            int64_t startMs = static_cast<int64_t>(startSample * 1000 / sampleRate);
            int64_t endMs = static_cast<int64_t>(endSample * 1000 / sampleRate);
            if (endMs - startMs >= 80) { // filter out clicks < 80ms
                chunks.push_back({
                    static_cast<int64_t>(startSample),
                    static_cast<int64_t>(endSample),
                    startMs,
                    endMs,
                    true
                });
            }
        }
    }

    if (inSpeech) {
        size_t startSample = chunkStartFrame * hopSize;
        size_t endSample = count;
        chunks.push_back({
            static_cast<int64_t>(startSample),
            static_cast<int64_t>(endSample),
            static_cast<int64_t>(startSample * 1000 / sampleRate),
            static_cast<int64_t>(endSample * 1000 / sampleRate),
            true
        });
    }

    return chunks;
}

std::vector<AudioChunk> DspEngine::segmentAudioSmart(
    const float* samples,
    size_t count,
    int sampleRate,
    int maxChunkDurationMs,
    int minPauseDurationMs,
    int overlapMs
) {
    if (!samples || count == 0) return {};

    int64_t totalDurationMs = static_cast<int64_t>(count * 1000 / sampleRate);
    if (totalDurationMs <= maxChunkDurationMs) {
        return {{0, static_cast<int64_t>(count), 0, totalDurationMs, true}};
    }

    auto speechChunks = detectSpeechActivity(samples, count, sampleRate);
    std::vector<AudioChunk> resultChunks;

    int64_t currentStartMs = 0;
    int64_t lastPauseMs = 0;

    for (size_t i = 0; i < speechChunks.size(); ++i) {
        int64_t currentEndMs = speechChunks[i].endMs;
        int64_t duration = currentEndMs - currentStartMs;

        // Check if there is a pause after current chunk
        bool hasPause = false;
        int64_t pauseDuration = 0;
        if (i + 1 < speechChunks.size()) {
            pauseDuration = speechChunks[i + 1].startMs - currentEndMs;
            if (pauseDuration >= minPauseDurationMs) {
                hasPause = true;
                lastPauseMs = currentEndMs + (pauseDuration / 2);
            }
        }

        // If chunk is approaching maxChunkDurationMs or we hit a natural pause
        if ((duration >= maxChunkDurationMs) || (duration >= (maxChunkDurationMs * 2 / 3) && hasPause)) {
            int64_t splitMs = hasPause ? lastPauseMs : currentEndMs;
            int64_t chunkEndMs = std::min(splitMs, totalDurationMs);

            int64_t startSample = currentStartMs * sampleRate / 1000;
            int64_t endSample = std::min(static_cast<int64_t>(count), chunkEndMs * sampleRate / 1000);

            resultChunks.push_back({startSample, endSample, currentStartMs, chunkEndMs, true});

            // Apply overlap for the next chunk
            currentStartMs = std::max(int64_t(0), chunkEndMs - overlapMs);
            if (currentStartMs >= totalDurationMs) break;
        }
    }

    // Append remainder if any
    if (currentStartMs < totalDurationMs) {
        int64_t startSample = currentStartMs * sampleRate / 1000;
        int64_t endSample = count;
        resultChunks.push_back({startSample, endSample, currentStartMs, totalDurationMs, true});
    }

    return resultChunks;
}

} // namespace neuroscribe::dsp

// ---------------- JNI Implementation ----------------
extern "C" {

JNIEXPORT jfloatArray JNICALL
Java_com_neuroscribe_core_audio_NativeDspEngine_nativeResampleAndNormalize(
    JNIEnv* env,
    jobject /* this */,
    jfloatArray inSamples,
    jint inSampleRate,
    jint outSampleRate,
    jint channels
) {
    if (!inSamples) return nullptr;

    jsize count = env->GetArrayLength(inSamples);
    jfloat* data = env->GetFloatArrayElements(inSamples, nullptr);
    if (!data) return nullptr;

    // 1. Downmix to mono if multi-channel
    size_t inFrames = count / (channels > 0 ? channels : 1);
    std::vector<float> mono = neuroscribe::dsp::DspEngine::downmixToMono(data, inFrames, channels);

    // 2. Remove DC offset
    neuroscribe::dsp::DspEngine::removeDcOffset(mono.data(), mono.size());

    // 3. Resample
    std::vector<float> resampled = neuroscribe::dsp::DspEngine::resample(
        mono.data(), mono.size(), inSampleRate, outSampleRate
    );

    // 4. Normalize
    neuroscribe::dsp::DspEngine::normalizeAudio(resampled.data(), resampled.size());

    env->ReleaseFloatArrayElements(inSamples, data, JNI_ABORT);

    jfloatArray result = env->NewFloatArray(static_cast<jsize>(resampled.size()));
    if (result) {
        env->SetFloatArrayRegion(result, 0, static_cast<jsize>(resampled.size()), resampled.data());
    }
    return result;
}

JNIEXPORT jlongArray JNICALL
Java_com_neuroscribe_core_audio_NativeDspEngine_nativeDetectSpeechSegments(
    JNIEnv* env,
    jobject /* this */,
    jfloatArray inSamples,
    jint sampleRate,
    jfloat energyThresholdDb
) {
    if (!inSamples) return nullptr;

    jsize count = env->GetArrayLength(inSamples);
    jfloat* data = env->GetFloatArrayElements(inSamples, nullptr);
    if (!data) return nullptr;

    auto chunks = neuroscribe::dsp::DspEngine::detectSpeechActivity(
        data, count, sampleRate, 30, 15, energyThresholdDb
    );

    env->ReleaseFloatArrayElements(inSamples, data, JNI_ABORT);

    // Pack startMs and endMs as flat pairs [startMs0, endMs0, startMs1, endMs1, ...]
    std::vector<jlong> flat;
    flat.reserve(chunks.size() * 2);
    for (const auto& c : chunks) {
        flat.push_back(static_cast<jlong>(c.startMs));
        flat.push_back(static_cast<jlong>(c.endMs));
    }

    jlongArray result = env->NewLongArray(static_cast<jsize>(flat.size()));
    if (result && !flat.empty()) {
        env->SetLongArrayRegion(result, 0, static_cast<jsize>(flat.size()), flat.data());
    }
    return result;
}

} // extern "C"
