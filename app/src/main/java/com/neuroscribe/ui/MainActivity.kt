package com.neuroscribe.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.neuroscribe.NeuroScribeApp
import com.neuroscribe.R
import com.neuroscribe.core.android.ProcessingForegroundService
import com.neuroscribe.core.processing.ProcessingCoordinator
import com.neuroscribe.core.storage.ContestResult
import com.neuroscribe.core.storage.ContestTranscriptEntry
import com.neuroscribe.core.storage.PipelineStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    private lateinit var coordinator: ProcessingCoordinator
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var tvSelectedFile: TextView
    private lateinit var btnSelectFile: Button
    private lateinit var btnStartProcessing: Button
    private lateinit var tvCurrentStage: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressStatus: TextView
    private lateinit var tvProgressPercent: TextView
    private lateinit var btnCancel: Button
    private lateinit var layoutResults: LinearLayout
    private lateinit var containerUtterances: LinearLayout
    private lateinit var btnExportJson: Button

    private var selectedAudioFile: File? = null
    private var lastResult: ContestResult? = null

    private val selectAudioLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleAudioUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val app = application as NeuroScribeApp
        coordinator = app.processingCoordinator

        initViews()
        observeState()
    }

    private fun initViews() {
        tvSelectedFile = findViewById(R.id.tvSelectedFile)
        btnSelectFile = findViewById(R.id.btnSelectFile)
        btnStartProcessing = findViewById(R.id.btnStartProcessing)
        tvCurrentStage = findViewById(R.id.tvCurrentStage)
        progressBar = findViewById(R.id.progressBar)
        tvProgressStatus = findViewById(R.id.tvProgressStatus)
        tvProgressPercent = findViewById(R.id.tvProgressPercent)
        btnCancel = findViewById(R.id.btnCancel)
        layoutResults = findViewById(R.id.layoutResults)
        containerUtterances = findViewById(R.id.containerUtterances)
        btnExportJson = findViewById(R.id.btnExportJson)

        btnSelectFile.setOnClickListener {
            selectAudioLauncher.launch("audio/*")
        }

        btnStartProcessing.setOnClickListener {
            startTranscription()
        }

        btnCancel.setOnClickListener {
            coordinator.cancel()
        }

        btnExportJson.setOnClickListener {
            shareJsonResult()
        }
    }

    private fun handleAudioUri(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val tempFile = File(cacheDir, "input_audio_${System.currentTimeMillis()}.wav")
                val outputStream = FileOutputStream(tempFile)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()

                withContext(Dispatchers.Main) {
                    selectedAudioFile = tempFile
                    tvSelectedFile.text = "${tempFile.name} (${tempFile.length() / 1024} КБ)"
                    btnStartProcessing.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка загрузки аудио: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startTranscription() {
        val file = selectedAudioFile
        if (file == null || !file.exists()) {
            Toast.makeText(this, "Пожалуйста, сначала выберите аудиофайл", Toast.LENGTH_SHORT).show()
            return
        }

        val outputFile = File(filesDir, "transcript_${System.currentTimeMillis()}.json")
        ProcessingForegroundService.start(this, file.absolutePath, outputFile.absolutePath)
    }

    private fun observeState() {
        scope.launch {
            coordinator.state.collectLatest { state ->
                progressBar.progress = state.progressPercent
                tvProgressPercent.text = "${state.progressPercent}%"
                tvProgressStatus.text = state.statusMessage
                tvCurrentStage.text = getStageDisplay(state.stage)

                btnCancel.visibility = if (!state.isCompleted && !state.isCancelled && state.progressPercent > 0) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

                if (state.isCompleted && state.result != null) {
                    lastResult = state.result
                    displayResults(state.result)
                }
            }
        }
    }

    private fun displayResults(result: ContestResult) {
        layoutResults.visibility = View.VISIBLE
        containerUtterances.removeAllViews()

        val inflater = LayoutInflater.from(this)
        for (item in result.transcript) {
            val itemView = inflater.inflate(R.layout.item_transcript_utterance, containerUtterances, false)
            val tvSpeakerId = itemView.findViewById<TextView>(R.id.tvSpeakerId)
            val tvTimestamp = itemView.findViewById<TextView>(R.id.tvTimestamp)
            val tvUtteranceText = itemView.findViewById<TextView>(R.id.tvUtteranceText)

            tvSpeakerId.text = item.speaker_id
            tvSpeakerId.setTextColor(getSpeakerColor(item.speaker_id))
            tvTimestamp.text = formatTimestampRange(item.start_ms, item.end_ms)
            tvUtteranceText.text = item.text

            containerUtterances.addView(itemView)
        }
    }

    private fun formatTimestampRange(startMs: Long, endMs: Long): String {
        return "${formatMs(startMs)} — ${formatMs(endMs)}"
    }

    private fun formatMs(ms: Long): String {
        val totalSec = ms / 1000
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun getSpeakerColor(speakerId: String): Int {
        return when (speakerId) {
            "speaker_1" -> getColor(R.color.speaker_1)
            "speaker_2" -> getColor(R.color.speaker_2)
            "speaker_3" -> getColor(R.color.speaker_3)
            "speaker_4" -> getColor(R.color.speaker_4)
            "speaker_5" -> getColor(R.color.speaker_5)
            "speaker_6" -> getColor(R.color.speaker_6)
            else -> getColor(R.color.speaker_unknown)
        }
    }

    private fun getStageDisplay(stage: PipelineStage): String {
        return when (stage) {
            PipelineStage.STAGE_0_PREPARATION -> "Этап 1/6: Подготовка аудио"
            PipelineStage.STAGE_1_ASR -> "Этап 2/6: Распознавание речи"
            PipelineStage.STAGE_2_DIARIZATION -> "Этап 3/6: Определение говорящих"
            PipelineStage.STAGE_3_ALIGNMENT -> "Этап 4/6: Сопоставление реплик"
            PipelineStage.STAGE_4_LLM -> "Этап 5/6: Обработка LLM"
            PipelineStage.STAGE_5_JSON_EXPORT -> "Этап 6/6: Формирование JSON"
            PipelineStage.COMPLETED -> "Готово • Стенограмма сформирована"
            PipelineStage.FAILED -> "Ошибка обработки"
        }
    }

    private fun shareJsonResult() {
        val res = lastResult ?: return
        val app = application as NeuroScribeApp
        val jsonStr = app.jsonExporter.exportToJson(res)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TEXT, jsonStr)
            putExtra(Intent.EXTRA_TITLE, "NeuroScribe_Transcript.json")
        }
        startActivity(Intent.createChooser(intent, "Экспорт стенограммы"))
    }
}
