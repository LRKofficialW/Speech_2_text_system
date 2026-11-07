package com.neuroscribe.core.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.neuroscribe.core.processing.ProcessingState
import com.neuroscribe.core.storage.PipelineStage

class LiveUpdatesNotifier(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "neuroscribe_transcription_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_CANCEL = "com.neuroscribe.action.CANCEL_PROCESSING"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Стенографирование аудио",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Фоновое распознавание и диаризация речи"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        fileName: String,
        state: ProcessingState
    ): Notification {
        val cancelIntent = Intent(context, ProcessingForegroundService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            context,
            0,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stageTitle = getStageTitle(state.stage)
        val contentText = "${state.statusMessage} (${state.progressPercent}%)"

        // Android 16 (API 36+) Live Updates Notification.ProgressStyle
        // For older APIs, use NotificationCompat with setProgress
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("NeuroScribe — $fileName")
            .setContentText(contentText)
            .setSubText(stageTitle)
            .setOngoing(!state.isCompleted && !state.isCancelled)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Don't expose private transcript content on lock screen
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отмена", cancelPendingIntent)

        if (state.isCompleted) {
            builder.setProgress(0, 0, false)
                .setContentText("Стенограмма готова!")
                .setOngoing(false)
        } else {
            builder.setProgress(100, state.progressPercent, false)
        }

        return builder.build()
    }

    fun updateNotification(fileName: String, state: ProcessingState) {
        val notification = buildNotification(fileName, state)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun getStageTitle(stage: PipelineStage): String {
        return when (stage) {
            PipelineStage.STAGE_0_PREPARATION -> "Этап 1/6: Подготовка аудио"
            PipelineStage.STAGE_1_ASR -> "Этап 2/6: Распознавание речи"
            PipelineStage.STAGE_2_DIARIZATION -> "Этап 3/6: Определение говорящих"
            PipelineStage.STAGE_3_ALIGNMENT -> "Этап 4/6: Сопоставление реплик"
            PipelineStage.STAGE_4_LLM -> "Этап 5/6: Обработка LLM"
            PipelineStage.STAGE_5_JSON_EXPORT -> "Этап 6/6: Формирование JSON"
            PipelineStage.COMPLETED -> "Завершено"
            PipelineStage.FAILED -> "Ошибка"
        }
    }
}
