#!/usr/bin/env python3
"""
Synthetic Audio and Ground Truth Generator for NeuroScribe Benchmarking.
Generates multi-speaker conversational WAV files (1 to 6 speakers)
with overlapping speech, short interjections, and pauses.
"""

import os
import math
import wave
import struct
import json

SAMPLE_RATE = 16000

# Base harmonic voice fundamental frequencies for up to 6 distinct speakers
SPEAKER_PITCHES = {
    "speaker_1": 130.0, # Male baritone
    "speaker_2": 210.0, # Female mezzo-soprano
    "speaker_3": 160.0, # Male tenor
    "speaker_4": 260.0, # Female soprano
    "speaker_5": 110.0, # Male bass
    "speaker_6": 185.0  # Countertenor / alto
}

RUSSIAN_LEXICON = {
    "speaker_1": ["добрый", "день", "уважаемые", "коллеги", "начинаем", "наше", "совещание"],
    "speaker_2": ["здравствуйте", "повестка", "дня", "готова", "можем", "приступать"],
    "speaker_3": ["да", "согласен", "давайте", "обсудим", "первый", "пункт"],
    "speaker_4": ["хорошо", "по", "моему", "мнению", "все", "сроки", "соблюдены"],
    "speaker_5": ["угу", "проверил", "отчетность", "все", "показатели", "в", "норме"],
    "speaker_6": ["отлично", "тогда", "принимаем", "единогласно", "спасибо", "всем"]
}

def generate_voice_tone(duration_sec, pitch_hz, sample_rate=SAMPLE_RATE, amplitude=0.6):
    """Synthesizes human-like voiced vowel sound with fundamental pitch + harmonics."""
    num_samples = int(duration_sec * sample_rate)
    samples = []
    for i in range(num_samples):
        t = i / sample_rate
        # Formant synthesis approximation (f0, 2*f0, 3*f0)
        val = 0.5 * math.sin(2 * math.pi * pitch_hz * t) + \
              0.3 * math.sin(2 * math.pi * 2 * pitch_hz * t) + \
              0.2 * math.sin(2 * math.pi * 3 * pitch_hz * t)
        # Apply gentle Hann envelope to avoid clicks
        env = 0.5 * (1.0 - math.cos(2 * math.pi * i / num_samples))
        samples.append(val * env * amplitude)
    return samples

def write_wav(filename, samples, sample_rate=SAMPLE_RATE):
    """Writes mono 16-bit PCM WAV file."""
    os.makedirs(os.path.dirname(filename), exist_ok=True)
    with wave.open(filename, 'wb') as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        raw_bytes = bytearray()
        for s in samples:
            clamped = max(-1.0, min(1.0, s))
            int16_val = int(clamped * 32767.0)
            raw_bytes.extend(struct.pack('<h', int16_val))
        wf.writeframes(raw_bytes)

def generate_benchmark_scenarios(output_dir):
    """Generates 1, 2, 3, 4, 5, 6 speaker scenarios plus overlapping speech."""
    os.makedirs(output_dir, exist_ok=True)
    scenarios = []

    for num_speakers in range(1, 7):
        scenario_name = f"scenario_{num_speakers}_speakers"
        wav_path = os.path.join(output_dir, f"{scenario_name}.wav")
        json_path = os.path.join(output_dir, f"{scenario_name}_truth.json")

        audio_samples = []
        ground_truth_transcript = []
        current_time_ms = 500  # 500ms initial silence

        # Add 500ms lead silence
        audio_samples.extend([0.0] * int(0.5 * SAMPLE_RATE))

        active_speakers = [f"speaker_{i+1}" for i in range(num_speakers)]

        # Each speaker speaks 2 turns
        for round_idx in range(2):
            for spk in active_speakers:
                words = RUSSIAN_LEXICON[spk]
                turn_text = " ".join(words[:4] if round_idx == 0 else words[3:])
                turn_duration_ms = 2200
                start_ms = current_time_ms
                end_ms = start_ms + turn_duration_ms

                # Render speech tone
                pitch = SPEAKER_PITCHES[spk]
                tone = generate_voice_tone(turn_duration_ms / 1000.0, pitch)
                audio_samples.extend(tone)

                ground_truth_transcript.append({
                    "speaker_id": spk,
                    "start_ms": start_ms,
                    "end_ms": end_ms,
                    "text": turn_text
                })

                # Natural conversation pause: 400ms
                pause_samples = int(0.4 * SAMPLE_RATE)
                audio_samples.extend([0.0] * pause_samples)
                current_time_ms = end_ms + 400

        # Add 500ms trailing silence
        audio_samples.extend([0.0] * int(0.5 * SAMPLE_RATE))

        write_wav(wav_path, audio_samples)
        with open(json_path, 'w', encoding='utf-8') as f:
            json.dump({"transcript": ground_truth_transcript}, f, ensure_ascii=False, indent=2)

        scenarios.append({
            "name": scenario_name,
            "speakers": num_speakers,
            "wav": wav_path,
            "truth": json_path,
            "duration_sec": len(audio_samples) / SAMPLE_RATE
        })

    # Additional scenario: Overlapping speech and quick interruptions
    overlap_name = "scenario_overlapping_speech"
    overlap_wav = os.path.join(output_dir, f"{overlap_name}.wav")
    overlap_truth = os.path.join(output_dir, f"{overlap_name}_truth.json")

    total_len_sec = 8.0
    overlap_samples = [0.0] * int(total_len_sec * SAMPLE_RATE)

    # Speaker 1 speaks 1.0s to 5.0s
    spk1_tone = generate_voice_tone(4.0, SPEAKER_PITCHES["speaker_1"])
    start_idx1 = int(1.0 * SAMPLE_RATE)
    for i, s in enumerate(spk1_tone):
        overlap_samples[start_idx1 + i] += s

    # Speaker 2 interrupts / speaks concurrently 2.5s to 4.5s
    spk2_tone = generate_voice_tone(2.0, SPEAKER_PITCHES["speaker_2"])
    start_idx2 = int(2.5 * SAMPLE_RATE)
    for i, s in enumerate(spk2_tone):
        overlap_samples[start_idx2 + i] += s

    # Speaker 3 adds quick interjection "Да" at 3.0s to 3.8s
    spk3_tone = generate_voice_tone(0.8, SPEAKER_PITCHES["speaker_3"])
    start_idx3 = int(3.0 * SAMPLE_RATE)
    for i, s in enumerate(spk3_tone):
        overlap_samples[start_idx3 + i] += s

    write_wav(overlap_wav, overlap_samples)
    overlap_gt = {
        "transcript": [
            {"speaker_id": "speaker_1", "start_ms": 1000, "end_ms": 5000, "text": "добрый день уважаемые коллеги начинаем"},
            {"speaker_id": "speaker_2", "start_ms": 2500, "end_ms": 4500, "text": "здравствуйте повестка дня готова"},
            {"speaker_id": "speaker_3", "start_ms": 3000, "end_ms": 3800, "text": "да"}
        ]
    }
    with open(overlap_truth, 'w', encoding='utf-8') as f:
        json.dump(overlap_gt, f, ensure_ascii=False, indent=2)

    scenarios.append({
        "name": overlap_name,
        "speakers": 3,
        "wav": overlap_wav,
        "truth": overlap_truth,
        "duration_sec": total_len_sec
    })

    print(f"Generated {len(scenarios)} test scenarios in {output_dir}")
    return scenarios

if __name__ == "__main__":
    out_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "test_data")
    generate_benchmark_scenarios(out_dir)
