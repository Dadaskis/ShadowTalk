package com.shadowtalk

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles microphone recording using MediaRecorder.
 * Recordings are saved to the app's private internal storage with timestamp filenames.
 */
class AudioRecorderManager(private val context: Context) {

    // Active MediaRecorder instance while recording is in progress
    private var mediaRecorder: MediaRecorder? = null

    // Path to the file currently being recorded
    private var currentOutputPath: String? = null

    /**
     * Returns true when a recording session is active.
     */
    fun isRecording(): Boolean = mediaRecorder != null

    /**
     * Returns the path of the last completed recording, or null if none yet.
     */
    fun getLastRecordingPath(): String? = currentOutputPath

    /**
     * Creates a unique output file in app-private storage.
     * Example filename: recording_20250607_143022.m4a
     */
    private fun createOutputFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "recording_$timestamp.m4a"
        return File(context.filesDir, fileName)
    }

    /**
     * Starts recording from the device microphone.
     *
     * @return Absolute path to the output file, or null if recording failed to start
     */
    fun startRecording(): String? {
        // Stop any previous session before starting a new one
        stopRecording()

        val outputFile = createOutputFile()

        return try {
            mediaRecorder = createMediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            currentOutputPath = outputFile.absolutePath
            outputFile.absolutePath
        } catch (exception: Exception) {
            mediaRecorder?.release()
            mediaRecorder = null
            currentOutputPath = null
            null
        }
    }

    /**
     * Stops the active recording and releases MediaRecorder resources.
     *
     * @return Path to the saved recording file, or null if nothing was recording
     */
    fun stopRecording(): String? {
        val path = currentOutputPath

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (exception: Exception) {
            // stop() can throw if recording was too short; file may still be usable
        } finally {
            mediaRecorder = null
        }

        return path
    }

    /**
     * Creates a MediaRecorder instance compatible with the current Android version.
     */
    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }
}
