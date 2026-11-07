#pragma once

#include <vector>
#include <cstdint>
#include <cmath>
#include <cstddef>

namespace neuroscribe::dsp {

struct AudioChunk {
    int64_t startSample;
    int64_t endSample;
    int64_t startMs;
    int64_t endMs;
    bool isSpeech;
};

class DspEngine {
public:
    DspEngine() = default;
    ~DspEngine() = default;

    // Remove DC offset with single-pole high-pass filter
    static void removeDcOffset(float* samples, size_t count, float alpha = 0.995f);

    // Peak and RMS normalize audio into [-1.0, 1.0] with target RMS
    static void normalizeAudio(float* samples, size_t count, float targetRms = 0.1f);

    // Fast linear / bandlimited polyphase resampling
    static std::vector<float> resample(const float* input, size_t inCount, int inSampleRate, int outSampleRate = 16000);

    // Stereo/Multi-channel to Mono downmixing with energy preservation
    static std::vector<float> downmixToMono(const float* input, size_t inFrames, int channels);

    // Algorithmic energy + spectral flux Voice Activity Detection
    static std::vector<AudioChunk> detectSpeechActivity(
        const float* samples,
        size_t count,
        int sampleRate = 16000,
        int frameSizeMs = 30,
        int hopSizeMs = 15,
        float energyThresholdDb = -35.0f
    );

    // Smart chunking for long audio files (splits at pauses with overlap)
    static std::vector<AudioChunk> segmentAudioSmart(
        const float* samples,
        size_t count,
        int sampleRate = 16000,
        int maxChunkDurationMs = 25000,
        int minPauseDurationMs = 300,
        int overlapMs = 500
    );
};

} // namespace neuroscribe::dsp
