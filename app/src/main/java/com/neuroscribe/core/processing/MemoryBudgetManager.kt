package com.neuroscribe.core.processing

import java.io.File
import kotlin.math.max

class MemoryBudgetManager(
    private val maxBudgetBytes: Long = 2L * 1024L * 1024L * 1024L, // 2 GiB Hard Limit
    private val targetPeakBytes: Long = (1.5 * 1024 * 1024 * 1024).toLong() // 1.5 GiB Target Peak
) {

    /**
     * Estimates current total memory consumption (JVM heap + Native heap).
     */
    fun getCurrentMemoryUsageBytes(): Long {
        val runtime = Runtime.getRuntime()
        val jvmHeapUsed = runtime.totalMemory() - runtime.freeMemory()

        // Read VmRSS from /proc/self/status on Linux/Android if accessible
        val procRss = getProcessRssBytes()
        return max(jvmHeapUsed, procRss)
    }

    /**
     * Checks if allocating requestedBytes would exceed the hard limit.
     * Proactively collects garbage if approaching target peak.
     */
    fun checkAndEnforceBudget(requestedBytes: Long) {
        val currentUsage = getCurrentMemoryUsageBytes()
        val projected = currentUsage + requestedBytes

        if (projected > targetPeakBytes) {
            collectGarbage()
        }

        val afterGcUsage = getCurrentMemoryUsageBytes()
        if (afterGcUsage + requestedBytes > maxBudgetBytes) {
            collectGarbage()
            val finalUsage = getCurrentMemoryUsageBytes()
            if (finalUsage + requestedBytes > maxBudgetBytes) {
                throw OutOfMemoryError(
                    "Memory budget violation: Projected memory (${(finalUsage + requestedBytes) / (1024 * 1024)} MiB) " +
                            "exceeds hard limit 2048 MiB."
                )
            }
        }
    }

    fun collectGarbage() {
        System.gc()
        System.runFinalization()
        System.gc()
    }

    private fun getProcessRssBytes(): Long {
        return try {
            val statusFile = File("/proc/self/status")
            if (statusFile.exists()) {
                val lines = statusFile.readLines()
                for (line in lines) {
                    if (line.startsWith("VmRSS:")) {
                        val parts = line.split(Regex("\\s+"))
                        if (parts.size >= 2) {
                            val kb = parts[1].toLongOrNull() ?: 0L
                            return kb * 1024L
                        }
                    }
                }
            }
            0L
        } catch (_: Throwable) {
            0L
        }
    }
}
