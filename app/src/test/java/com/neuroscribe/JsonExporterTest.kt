package com.neuroscribe

import com.neuroscribe.core.storage.ContestResult
import com.neuroscribe.core.storage.ContestTranscriptEntry
import com.neuroscribe.core.storage.JsonExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonExporterTest {

    private val exporter = JsonExporter()

    @Test
    fun testValidExportAndParse() {
        val sample = ContestResult(
            transcript = listOf(
                ContestTranscriptEntry(
                    speaker_id = "speaker_1",
                    start_ms = 1250,
                    end_ms = 4380,
                    text = "Добрый день!"
                ),
                ContestTranscriptEntry(
                    speaker_id = "speaker_2",
                    start_ms = 4500,
                    end_ms = 7200,
                    text = "Здравствуйте!"
                )
            )
        )

        val jsonString = exporter.exportToJson(sample)
        assertTrue(jsonString.contains("\"speaker_1\""))
        assertTrue(jsonString.contains("\"start_ms\": 1250"))
        assertTrue(jsonString.contains("\"end_ms\": 4380"))
        assertTrue(jsonString.contains("Добрый день!"))

        val parsed = exporter.parseAndValidate(jsonString)
        assertEquals(2, parsed.transcript.size)
        assertEquals("speaker_1", parsed.transcript[0].speaker_id)
        assertEquals(1250L, parsed.transcript[0].start_ms)
        assertEquals(4380L, parsed.transcript[0].end_ms)
        assertEquals("Добрый день!", parsed.transcript[0].text)
    }

    @Test
    fun testValidationRejectsInvalidSpeakerId() {
        val invalid = ContestResult(
            transcript = listOf(
                ContestTranscriptEntry(
                    speaker_id = "speaker_7", // allowed only speaker_1..speaker_6 or unknown
                    start_ms = 100,
                    end_ms = 200,
                    text = "Привет"
                )
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            exporter.validate(invalid)
        }
    }

    @Test
    fun testValidationRejectsNegativeStartMs() {
        val invalid = ContestResult(
            transcript = listOf(
                ContestTranscriptEntry(
                    speaker_id = "speaker_1",
                    start_ms = -50,
                    end_ms = 200,
                    text = "Привет"
                )
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            exporter.validate(invalid)
        }
    }

    @Test
    fun testValidationRejectsEndMsLessThanStartMs() {
        val invalid = ContestResult(
            transcript = listOf(
                ContestTranscriptEntry(
                    speaker_id = "speaker_1",
                    start_ms = 500,
                    end_ms = 400,
                    text = "Привет"
                )
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            exporter.validate(invalid)
        }
    }
}
