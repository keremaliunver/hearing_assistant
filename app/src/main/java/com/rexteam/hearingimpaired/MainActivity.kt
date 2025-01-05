package com.rexteam.hearingimpaired

import MainScreen
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.assemblyai.api.RealtimeTranscriber
import com.assemblyai.api.resources.realtime.types.FinalTranscript
import com.assemblyai.api.resources.realtime.types.PartialTranscript
import com.assemblyai.api.resources.realtime.types.SessionBegins
import com.rexteam.hearingimpaired.ui.theme.HearingImpairedTheme
import com.theokanning.openai.audio.CreateTranscriptionRequest
import com.theokanning.openai.service.OpenAiService
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    // AssemblyAI & OpenAI Keys
    private val ASSEMBLYAI_API_KEY = "03cff1af41774a3596c719e47fde10bb"
    private val OPENAI_API_KEY = "OPENAI_API_KEY"

    // File-based recording fiel"ds
    private var mediaRecorder: MediaRecorder? = null
    private var audioFilePath: String? = null

    // Real-time recording fields
    private var isRecording = false
    private var recordJob: Job? = null

    private val recordAudioRequestCode = 101

    // Observed state for UI (transcribed text)
    private var transcribedText = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check and request record audio permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                recordAudioRequestCode
            )
        }

        setContent {
            HearingImpairedTheme {
                MainScreen(
                    // Real-time callbacks
                    onStartRealtime = { startRealtimeTranscription() },
                    onStopRealtime = { stopRealtimeTranscription() },
                    // File-based callbacks
                    onStartRecorded = { startRecording() },
                    onStopRecorded = { stopRecording() },
                    // Observed text
                    transcribedText = transcribedText.value
                )
            }
        }
    }

    //------------------------------------------------------------------------------
    // File-based Recording (OpenAI Whisper)
    //------------------------------------------------------------------------------

    private fun getNewAudioFileName(): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = this.getExternalFilesDir(null)
        return File(storageDir, "audio_$timeStamp.mp4").absolutePath
    }

    private fun startRecording() {

        transcribedText.value = "Listening to the voices..."
        audioFilePath = getNewAudioFileName()

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(audioFilePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

            try {
                prepare()
            } catch (e: IOException) {
                Log.e("MainActivity", "Prepare Failed: ${e.message}", e)
            }

            try {
                start()
            } catch (e: IllegalStateException) {
                Log.e("MainActivity", "Start Failed: ${e.message}", e)
            }
        }

        Log.d("MainActivity", "Recording started, file path: $audioFilePath")
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            try {
                stop()
            } catch (e: IllegalStateException) {
                Log.e("MainActivity", "Stop Failed: ${e.message}", e)
            }
            try {
                release()
            } catch (e: IllegalStateException) {
                Log.e("MainActivity", "Release Failed: ${e.message}", e)
            }
        }
        mediaRecorder = null
        Log.d("MainActivity", "Recording stopped, file path: $audioFilePath")

        audioFilePath?.let { filePath ->
            CoroutineScope(Dispatchers.IO).launch {
                transcribeAudio(filePath)
            }
        }
    }

    private fun transcribeAudio(filePath: String) {
        val client = OpenAiService(OPENAI_API_KEY)
        val audioFile = File(filePath)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val transcriptionRequest = CreateTranscriptionRequest()
                transcriptionRequest.model = "whisper-1"
                val result = client.createTranscription(transcriptionRequest, audioFile)

                Log.d("MainActivity", "Transcribed Text: ${result.text}")
                withContext(Dispatchers.Main) {
                    transcribedText.value = result.text
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error transcribing audio: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    transcribedText.value = "Error: Could not transcribe audio"
                }
            } finally {
                deleteAudioFile(filePath)
            }
        }
    }

    private fun deleteAudioFile(filePath: String) {
        val file = File(filePath)
        if (file.exists() && file.delete()) {
            Log.d("MainActivity", "Audio file deleted: $filePath")
            audioFilePath = null
        } else {
            Log.e("MainActivity", "Failed to delete audio file: $filePath")
        }
    }

    //------------------------------------------------------------------------------
    // Real-time Transcription (AssemblyAI)
    //------------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun startRealtimeTranscription() {
        val realtimeTranscriber = RealtimeTranscriber.builder()
            .apiKey(ASSEMBLYAI_API_KEY)
            .sampleRate(16000)
            .onSessionBegins { sessionBegins ->
                Log.d("AssemblyAI", "Session opened with ID: ${sessionBegins.sessionId}")
                CoroutineScope(Dispatchers.Main).launch {
                    transcribedText.value = "Session started. Listening..."
                }
            }
            .onPartialTranscript { partialTranscript ->
                if (partialTranscript.text.isNotEmpty()) {
                    Log.d("AssemblyAI", "Partial: ${partialTranscript.text}")
                    CoroutineScope(Dispatchers.Main).launch {
                        transcribedText.value = partialTranscript.text
                    }
                }
            }
            .onFinalTranscript { finalTranscript ->
                Log.d("AssemblyAI", "Final: ${finalTranscript.text}")
                CoroutineScope(Dispatchers.Main).launch {
                    transcribedText.value = finalTranscript.text
                }
            }
            .onError { err ->
                Log.e("AssemblyAI", "Error: ${err.message}")
            }
            .build()

        Log.d("AssemblyAI", "Connecting to real-time transcript service...")
        realtimeTranscriber.connect()

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioEncoding = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioEncoding,
            minBufferSize
        )

        // Verify AudioRecord initialization
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("AssemblyAI", "AudioRecord initialization failed.")
            return
        }

        audioRecord.startRecording()
        isRecording = true
        Log.d("AssemblyAI", "Started recording from microphone.")

        // Continuously read audio data and send to AssemblyAI
        recordJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(minBufferSize)
            while (isActive && isRecording) {
                val readBytes = audioRecord.read(buffer, 0, buffer.size)
                if (readBytes > 0) {
                    realtimeTranscriber.sendAudio(buffer)
                }
            }
            // When user stops:
            audioRecord.stop()
            audioRecord.release()
            realtimeTranscriber.close()
            Log.d("AssemblyAI", "Recording & transcription stopped.")
        }
    }

    private fun stopRealtimeTranscription() {
        isRecording = false
        recordJob?.cancel()
        recordJob = null
        transcribedText.value = "Stopped real-time transcription."
    }

    //------------------------------------------------------------------------------
    // Lifecycle
    //------------------------------------------------------------------------------

    override fun onStop() {
        super.onStop()
        mediaRecorder?.release()
        mediaRecorder = null
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.release()
        mediaRecorder = null
    }

}
