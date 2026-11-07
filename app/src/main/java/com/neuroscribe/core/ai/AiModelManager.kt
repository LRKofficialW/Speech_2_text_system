package com.neuroscribe.core.ai

import com.neuroscribe.core.processing.MemoryBudgetManager
import java.io.File

class AiModelManager(
    private val asrEngine: AsrEngine,
    private val diarizationEngine: DiarizationEngine,
    private val llmEngine: LlmEngine,
    private val memoryBudgetManager: MemoryBudgetManager
) {

    private var currentlyLoadedModel: ModelLifecycle? = null

    @Synchronized
    fun <T> executeWithAsr(action: (AsrEngine) -> T): T {
        ensureAllUnloadedExcept(asrEngine)
        memoryBudgetManager.checkAndEnforceBudget(asrEngine.getEstimatedMemoryBytes())
        asrEngine.load()
        currentlyLoadedModel = asrEngine

        return try {
            action(asrEngine)
        } finally {
            asrEngine.unload()
            currentlyLoadedModel = null
            memoryBudgetManager.collectGarbage()
        }
    }

    @Synchronized
    fun <T> executeWithDiarization(action: (DiarizationEngine) -> T): T {
        ensureAllUnloadedExcept(diarizationEngine)
        memoryBudgetManager.checkAndEnforceBudget(diarizationEngine.getEstimatedMemoryBytes())
        diarizationEngine.load()
        currentlyLoadedModel = diarizationEngine

        return try {
            action(diarizationEngine)
        } finally {
            diarizationEngine.unload()
            currentlyLoadedModel = null
            memoryBudgetManager.collectGarbage()
        }
    }

    @Synchronized
    fun <T> executeWithLlm(action: (LlmEngine) -> T): T {
        ensureAllUnloadedExcept(llmEngine)
        memoryBudgetManager.checkAndEnforceBudget(llmEngine.getEstimatedMemoryBytes())
        llmEngine.load()
        currentlyLoadedModel = llmEngine

        return try {
            action(llmEngine)
        } finally {
            llmEngine.unload()
            currentlyLoadedModel = null
            memoryBudgetManager.collectGarbage()
        }
    }

    @Synchronized
    fun unloadAll() {
        asrEngine.unload()
        diarizationEngine.unload()
        llmEngine.unload()
        currentlyLoadedModel = null
        memoryBudgetManager.collectGarbage()
    }

    private fun ensureAllUnloadedExcept(target: ModelLifecycle) {
        if (currentlyLoadedModel != null && currentlyLoadedModel != target) {
            currentlyLoadedModel?.unload()
            currentlyLoadedModel = null
            memoryBudgetManager.collectGarbage()
        }
        if (target != asrEngine && asrEngine.isLoaded()) asrEngine.unload()
        if (target != diarizationEngine && diarizationEngine.isLoaded()) diarizationEngine.unload()
        if (target != llmEngine && llmEngine.isLoaded()) llmEngine.unload()
    }
}
