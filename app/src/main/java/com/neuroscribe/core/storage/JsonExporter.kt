package com.neuroscribe.core.storage

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class JsonExporter {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    /**
     * Serializes a ContestResult to exact JSON string matching organizer schema.
     */
    fun exportToJson(result: ContestResult): String {
        validate(result)
        return json.encodeToString(result)
    }

    /**
     * Exports ContestResult to a target file with UTF-8 encoding.
     */
    fun exportToFile(result: ContestResult, destinationFile: File): File {
        val jsonString = exportToJson(result)
        destinationFile.parentFile?.mkdirs()
        destinationFile.writeText(jsonString, Charsets.UTF_8)
        return destinationFile
    }

    /**
     * Rigorous validation of ContestResult against hackathon guidelines.
     */
    fun validate(result: ContestResult) {
        val allowedSpeakerRegex = Regex("^speaker_[1-6]$|^unknown$")

        for ((index, item) in result.transcript.withIndex()) {
            require(item.start_ms >= 0) {
                "Validation Error at item #$index: start_ms (${item.start_ms}) cannot be negative"
            }
            require(item.end_ms > item.start_ms) {
                "Validation Error at item #$index: end_ms (${item.end_ms}) must be strictly greater than start_ms (${item.start_ms})"
            }
            require(allowedSpeakerRegex.matches(item.speaker_id)) {
                "Validation Error at item #$index: speaker_id '${item.speaker_id}' is invalid. Allowed: speaker_1..speaker_6 or unknown"
            }
            require(item.text.isNotBlank()) {
                "Validation Error at item #$index: text cannot be blank"
            }
        }
    }

    /**
     * Parses and validates a JSON string.
     */
    fun parseAndValidate(jsonString: String): ContestResult {
        val parsed = json.decodeFromString<ContestResult>(jsonString)
        validate(parsed)
        return parsed
    }
}
