package com.neuroscribe.core.android

import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.neuroscribe.NeuroScribeApp
import com.neuroscribe.core.processing.ProcessingCoordinator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class ProcessingForegroundService : LifecycleService() {

    companion object {
        const val EXTRA_AUDIO_PATH = "extra_audio_path"
        const val EXTRA_OUTPUT_PATH = "extra_output_path"

        fun start(context: Context, audioPath: String, outputPath: String) {
            val intent = Intent(context, ProcessingForegroundService::class.java).apply {
                putExtra(EXTRA_AUDIO_PATH, audioPath)
                putExtra(EXTRA_OUTPUT_PATH, outputPath)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private lateinit var liveUpdatesNotifier: LiveUpdatesNotifier
    private lateinit var coordinator: ProcessingCoordinator
    private var wakeLock: PowerManager.WakeLock? = null
    private var processingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        liveUpdatesNotifier = LiveUpdatesNotifier(this)
        val app = application as NeuroScribeApp
        coordinator = app.processingCoordinator

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "neuroscribe:transcription_wakelock"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == LiveUpdatesNotifier.ACTION_CANCEL) {
            coordinator.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val audioPath = intent?.getStringExtra(EXTRA_AUDIO_PATH)
        val outputPath = intent?.getStringExtra(EXTRA_OUTPUT_PATH)

        if (audioPath.isNullOrEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val audioFile = File(audioPath)
        val outputFile = if (!outputPath.isNullOrEmpty()) File(outputPath) else null
        val fileName = audioFile.name

        // Start foreground notification immediately
        val initialNotification = liveUpdatesNotifier.buildNotification(fileName, coordinator.state.value)
        startForeground(LiveUpdatesNotifier.NOTIFICATION_ID, initialNotification)

        wakeLock?.acquire(60 * 60 * 1000L) // 1 hour safety timeout

        // Observe progress state and update lock screen live notification
        lifecycleScope.launch {
            coordinator.state.collectLatest { state ->
                liveUpdatesNotifier.updateNotification(fileName, state)
            }
        }

        // Run processing in background coroutine
        processingJob = lifecycleScope.launch {
            try {
                coordinator.processAudio(audioFile, outputFile)
            } catch (_: Throwable) {
            } finally {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        processingJob?.cancel()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
