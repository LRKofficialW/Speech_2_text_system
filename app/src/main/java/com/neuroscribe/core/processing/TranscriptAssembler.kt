package com.neuroscribe.core.processing

import com.neuroscribe.core.storage.AsrWord
import com.neuroscribe.core.storage.ContestTranscriptEntry
import com.neuroscribe.core.storage.DiarizationInterval
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class TranscriptAssembler {

    companion object {
        const val MAX_SPEECH_PAUSE_MS = 750L // Max pause between words to combine into a single utterance
        const val MAX_DISTANCE_FALLBACK_MS = 600L // Proximity window for short words falling outside diarization
    }

    /**
     * Mathematically aligns word-level timestamps with speaker intervals.
     * Produces clean utterances with start_ms, end_ms, speaker_id, and text.
     */
    fun alignTranscript(
        words: List<AsrWord>,
        diarizationIntervals: List<DiarizationInterval>
    ): List<ContestTranscriptEntry> {
        if (words.isEmpty()) return emptyList()

        if (diarizationIntervals.isEmpty()) {
            return listOf(
                ContestTranscriptEntry(
                    speaker_id = "speaker_1",
                    start_ms = words.first().startMs,
                    end_ms = words.last().endMs,
                    text = words.joinToString(" ") { it.text }
                )
            )
        }

        // 1. Assign each word to the best matching speaker interval based on temporal overlap
        data class TaggedWord(
            val word: AsrWord,
            val speakerId: String
        )

        var lastKnownSpeakerId = "speaker_1"
        val taggedWords = mutableListOf<TaggedWord>()

        for (w in words) {
            var bestSpeakerId = "unknown"
            var maxOverlapMs = 0L

            for (interval in diarizationIntervals) {
                val overlapStart = max(w.startMs, interval.startMs)
                val overlapEnd = min(w.endMs, interval.endMs)
                val overlap = max(0L, overlapEnd - overlapStart)

                if (overlap > maxOverlapMs) {
                    maxOverlapMs = overlap
                    bestSpeakerId = interval.speakerId
                }
            }

            // If no direct overlap, check proximity to nearest interval
            if (maxOverlapMs == 0L) {
                var minDistance = Long.MAX_VALUE
                var nearestSpeaker = "unknown"

                for (interval in diarizationIntervals) {
                    val dist = if (w.startMs >= interval.endMs) {
                        w.startMs - interval.endMs
                    } else if (w.endMs <= interval.startMs) {
                        interval.startMs - w.endMs
                    } else {
                        0L
                    }

                    if (dist < minDistance) {
                        minDistance = dist
                        nearestSpeaker = interval.speakerId
                    }
                }

                bestSpeakerId = if (minDistance <= MAX_DISTANCE_FALLBACK_MS && nearestSpeaker != "unknown") {
                    nearestSpeaker
                } else {
                    // For short interjections ("да", "нет", "угу"), carry forward previous speaker
                    if (isShortInterjection(w.text)) {
                        lastKnownSpeakerId
                    } else {
                        "unknown"
                    }
                }
            }

            if (bestSpeakerId != "unknown") {
                lastKnownSpeakerId = bestSpeakerId
            }

            taggedWords.add(TaggedWord(w, bestSpeakerId))
        }

        // 2. Assemble tagged words into continuous utterances
        val utterances = mutableListOf<ContestTranscriptEntry>()
        val currentGroup = mutableListOf<TaggedWord>()

        fun flushGroup() {
            if (currentGroup.isEmpty()) return
            val speaker = currentGroup.first().speakerId
            val startMs = currentGroup.first().word.startMs
            val endMs = currentGroup.last().word.endMs
            val text = currentGroup.joinToString(" ") { it.word.text }

            if (endMs > startMs && text.isNotBlank()) {
                utterances.add(
                    ContestTranscriptEntry(
                        speaker_id = speaker,
                        start_ms = startMs,
                        end_ms = endMs,
                        text = text
                    )
                )
            }
            currentGroup.clear()
        }

        for (tw in taggedWords) {
            if (currentGroup.isEmpty()) {
                currentGroup.add(tw)
                continue
            }

            val last = currentGroup.last()
            val sameSpeaker = (last.speakerId == tw.speakerId)
            val pauseDuration = tw.word.startMs - last.word.endMs

            if (sameSpeaker && pauseDuration <= MAX_SPEECH_PAUSE_MS) {
                currentGroup.add(tw)
            } else {
                flushGroup()
                currentGroup.add(tw)
            }
        }
        flushGroup()

        return utterances
    }

    private fun isShortInterjection(word: String): Boolean {
        val interjections = setOf("да", "нет", "хорошо", "угу", "ага", "ок", "ясно", "понял", "точно")
        return interjections.contains(word.lowercase().trim())
    }
}
