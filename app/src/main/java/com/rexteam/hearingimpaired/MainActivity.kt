package com.rexteam.hearingimpaired

import MainScreen
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
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
import com.arthenica.ffmpegkit.FFmpegKit
import com.assemblyai.api.RealtimeTranscriber
import com.rexteam.hearingimpaired.ui.theme.HearingImpairedTheme
import com.theokanning.openai.audio.CreateTranscriptionRequest
import com.theokanning.openai.service.OpenAiService
import kotlinx.coroutines.*
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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

    private var currentMethod = TranscriptionMethod.OPENAI

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
                    transcribedText = transcribedText.value,
                    // Transcription method callback
                    onTranscriptionMethodChanged = { method ->
                        currentMethod = method
                    }
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

    private fun getSampleFilePath(): String {
        val storageDir = this.getExternalFilesDir(null)
        return File(storageDir, "10001-90210-01803.wav").absolutePath
    }

    private fun startRecording() {

        transcribedText.value = "Listening to the voices..."
        audioFilePath = getNewAudioFileName()

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(audioFilePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)        // Attempt to record at 16 kHz
            setAudioEncodingBitRate(64000)

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
                when (currentMethod) {
                    TranscriptionMethod.OPENAI -> transcribeAudio(filePath)
                    TranscriptionMethod.VOSK   -> transcribeAudioWithVosk(filePath)
                }
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

    fun getModelPath(context: Context, modelFolderInAssets: String = "vosk-model-small-tr-0-3"): String {
        // Destination in internal storage
        val modelDir = File(context.filesDir, modelFolderInAssets)

        if (!modelDir.exists()) {
            // Copy entire folder from assets if it isn't already there
            copyAssetsFolder(context, modelFolderInAssets, modelDir)
        }
        return modelDir.absolutePath
    }

    private fun transcribeAudioWithVosk(filePath: String) {
        val encodedPath = filePath.replace(".mp4", ".wav")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val localModelPath = getModelPath(this@MainActivity, "vosk-model-small-tr-0-3") // vosk-model-small-en-us-0.15
                val model = Model(localModelPath)
                encodeToWav(filePath, encodedPath)
                val audioStream = FileInputStream(File(encodedPath))
                val recognizer = Recognizer(model, 16000.0f)
                val buffer = ByteArray(4096)
                var resultText = ""

                while (audioStream.read(buffer).also { } != -1) {
                    if (recognizer.acceptWaveForm(buffer, buffer.size)) {
                        resultText += recognizer.result
                    }
                }
                resultText += recognizer.finalResult

                Log.d("MainActivity", "Transcribed Text: $resultText")
                withContext(Dispatchers.Main) {
                    transcribedText.value = resultText
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Vosk transcription error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    transcribedText.value = "Error: Could not transcribe audio with Vosk"
                }
            } finally {
                deleteAudioFile(filePath)
                deleteAudioFile(encodedPath)
            }
        }
    }

    private fun encodeToWav(audioFilePath: String, encodedFilePath: String){
        val inputFile = File(audioFilePath)
        val outputWav = File(encodedFilePath)
        // Synchronously run FFmpeg to decode AAC -> WAV (16 kHz, mono)
        val command = "-i ${inputFile.absolutePath} -y -ar 16000 -ac 1 ${outputWav.absolutePath}"
        FFmpegKit.execute(command)
    }

    @Throws(IOException::class)
    fun copyAssetsFolder(context: Context, assetsFolderName: String, outputDir: File) {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        val assetManager = context.assets
        val files = assetManager.list(assetsFolderName) ?: emptyArray()
        for (fileName in files) {
            val inPath = "$assetsFolderName/$fileName"
            val outFile = File(outputDir, fileName)
            if (assetManager.list(inPath)?.isNotEmpty() == true) {
                // It's a subdirectory, recurse
                copyAssetsFolder(context, inPath, outFile)
            } else {
                // It's a file, copy
                assetManager.open(inPath).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
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
