package com.neuroscribe.core.ai

import com.neuroscribe.core.audio.PreparedAudioSegment
import com.neuroscribe.core.storage.AsrWord
import com.neuroscribe.core.storage.DiarizationInterval

interface ModelLifecycle {
    fun isLoaded(): Boolean
    fun load()
    fun unload()
    fun getEstimatedMemoryBytes(): Long
}

interface AsrModel : ModelLifecycle {
    fun transcribe(chunks: List<PreparedAudioSegment>, onProgress: ((Float) -> Unit)? = null): List<AsrWord>
}

interface DiarizationModel : ModelLifecycle {
    fun diarize(samples: FloatArray, sampleRate: Int = 16000, onProgress: ((Float) -> Unit)? = null): List<DiarizationInterval>
}

interface LocalLlmModel : ModelLifecycle {
    fun formatText(rawText: String): String
    fun verifyLexicalEquivalence(originalText: String, formattedText: String): Boolean
}
