package com.neuroscribe.core.storage

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class CheckpointRepository(private val baseDirectory: File) {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    init {
        baseDirectory.mkdirs()
    }

    private fun getCheckpointFile(audioPath: String): File {
        val safeName = audioPath.hashCode().toString() + ".checkpoint.json"
        return File(baseDirectory, safeName)
    }

    fun saveCheckpoint(checkpoint: ProcessingCheckpoint) {
        val file = getCheckpointFile(checkpoint.audioPath)
        val data = json.encodeToString(checkpoint)
        file.writeText(data, Charsets.UTF_8)
    }

    fun loadCheckpoint(audioPath: String): ProcessingCheckpoint? {
        val file = getCheckpointFile(audioPath)
        if (!file.exists()) return null
        return try {
            val content = file.readText(Charsets.UTF_8)
            json.decodeFromString<ProcessingCheckpoint>(content)
        } catch (_: Exception) {
            null
        }
    }

    fun clearCheckpoint(audioPath: String) {
        val file = getCheckpointFile(audioPath)
        if (file.exists()) {
            file.delete()
        }
    }
}
