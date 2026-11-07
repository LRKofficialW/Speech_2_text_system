package com.neuroscribe

import com.neuroscribe.core.processing.TranscriptAssembler
import com.neuroscribe.core.storage.AsrWord
import com.neuroscribe.core.storage.DiarizationInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptAssemblerTest {

    private val assembler = TranscriptAssembler()

    @Test
    fun testAlignWordsToTwoDistinctSpeakers() {
        val words = listOf(
            AsrWord("добрый", 1000, 1400),
            AsrWord("день", 1500, 1900),
            AsrWord("здравствуйте", 2200, 2900),
            AsrWord("коллеги", 3000, 3500)
        )

        val diarization = listOf(
            DiarizationInterval("speaker_1", 900, 2000),
            DiarizationInterval("speaker_2", 2100, 3600)
        )

        val utterances = assembler.alignTranscript(words, diarization)
        assertEquals(2, utterances.size)

        assertEquals("speaker_1", utterances[0].speaker_id)
        assertEquals(1000L, utterances[0].start_ms)
        assertEquals(1900L, utterances[0].end_ms)
        assertEquals("добрый день", utterances[0].text)

        assertEquals("speaker_2", utterances[1].speaker_id)
        assertEquals(2200L, utterances[1].start_ms)
        assertEquals(3500L, utterances[1].end_ms)
        assertEquals("здравствуйте коллеги", utterances[1].text)
    }

    @Test
    fun testShortInterjectionInheritsContext() {
        val words = listOf(
            AsrWord("да", 2500, 2700)
        )
        // Interval is slightly offset from the short word
        val diarization = listOf(
            DiarizationInterval("speaker_1", 2000, 2400)
        )

        val utterances = assembler.alignTranscript(words, diarization)
        assertEquals(1, utterances.size)
        assertEquals("speaker_1", utterances[0].speaker_id)
        assertEquals("да", utterances[0].text)
    }

    @Test
    fun testOverlappingTurns() {
        val words = listOf(
            AsrWord("я", 1000, 1300),
            AsrWord("думаю", 1350, 1800),
            AsrWord("согласен", 1400, 1900) // simultaneous speech
        )

        val diarization = listOf(
            DiarizationInterval("speaker_1", 900, 1850),
            DiarizationInterval("speaker_2", 1380, 2000, isOverlapping = true)
        )

        val utterances = assembler.alignTranscript(words, diarization)
        assertTrue(utterances.isNotEmpty())
        // Start time must not be negative and end_ms must be > start_ms for all
        for (u in utterances) {
            assertTrue(u.start_ms >= 0)
            assertTrue(u.end_ms > u.start_ms)
        }
    }
}
