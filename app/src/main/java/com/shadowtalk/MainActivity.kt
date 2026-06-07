package com.shadowtalk

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shadowtalk.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main (and only) activity for ShadowTalk voice shadowing practice.
 *
 * Flow:
 * 1. User selects a target audio file
 * 2. App displays the target waveform
 * 3. User presses Record — target audio plays (unless muted) while mic records
 * 4. Recording stops automatically when target ends (unless manual stop is enabled)
 * 5. Recorded waveform is shown and can be played back
 */
class MainActivity : AppCompatActivity() {

    // ViewBinding gives type-safe access to all views in activity_main.xml
    private lateinit var binding: ActivityMainBinding

    // Managers for playback, recording, and permissions
    private lateinit var audioPlayerManager: AudioPlayerManager
    private lateinit var audioRecorderManager: AudioRecorderManager

    // Uri of the user-selected target audio file (from document picker)
    private var targetAudioUri: Uri? = null

    // Display name of the selected target file
    private var targetFileName: String? = null

    // Path to the most recent recording in app-private storage
    private var recordedFilePath: String? = null

    // True while a shadowing recording session is active
    private var isRecordingSession = false

    // True while recorded audio is playing back
    private var isPlayingRecorded = false

    /**
     * Launcher for the system document picker (audio files only).
     */
    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            handleSelectedAudio(uri)
        }
    }

    /**
     * Launcher for requesting runtime permissions on first launch.
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        handlePermissionResults(results)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate layout using ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Create audio helper classes
        audioPlayerManager = AudioPlayerManager(this)
        audioRecorderManager = AudioRecorderManager(this)

        // Wire up button click listeners
        setupClickListeners()

        // Ask for permissions the first time the activity opens
        requestPermissionsIfNeeded()

        // Disable controls until permissions are granted
        updateUiForPermissions()
    }

    /**
     * Assigns click handlers to all interactive UI elements.
     */
    private fun setupClickListeners() {
        binding.btnSelectAudio.setOnClickListener {
            if (!PermissionHelper.hasStoragePermission(this)) {
                showToast(getString(R.string.permission_denied_storage))
                return@setOnClickListener
            }
            pickAudioLauncher.launch("audio/*")
        }

        binding.btnRecord.setOnClickListener {
            if (isRecordingSession) {
                // Manual stop mode: second press ends the recording
                stopRecordingSession()
            } else {
                startRecordingSession()
            }
        }

        binding.btnPlayRecorded.setOnClickListener {
            if (isPlayingRecorded) {
                stopRecordedPlayback()
            } else {
                playRecordedAudio()
            }
        }
    }

    /**
     * Requests all required permissions if any are still missing.
     */
    private fun requestPermissionsIfNeeded() {
        if (!PermissionHelper.hasAllPermissions(this)) {
            if (PermissionHelper.shouldShowRationale(this)) {
                // User denied before — explain why we need permissions
                AlertDialog.Builder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(
                        "${getString(R.string.permission_audio_rationale)}\n\n" +
                            getString(R.string.permission_record_rationale)
                    )
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        PermissionHelper.requestAllPermissions(permissionLauncher)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } else {
                PermissionHelper.requestAllPermissions(permissionLauncher)
            }
        }
    }

    /**
     * Updates button enabled state based on granted permissions and app state.
     */
    private fun handlePermissionResults(results: Map<String, Boolean>) {
        updateUiForPermissions()

        if (!PermissionHelper.hasStoragePermission(this)) {
            showToast(getString(R.string.permission_denied_storage))
        }
        if (!PermissionHelper.hasRecordPermission(this)) {
            showToast(getString(R.string.permission_denied_mic))
        }
    }

    /**
     * Enables or disables buttons depending on permissions and current selections.
     */
    private fun updateUiForPermissions() {
        val hasStorage = PermissionHelper.hasStoragePermission(this)
        val hasMic = PermissionHelper.hasRecordPermission(this)
        val hasTarget = targetAudioUri != null

        binding.btnSelectAudio.isEnabled = hasStorage
        binding.btnRecord.isEnabled = hasMic && hasTarget && !isPlayingRecorded
        binding.btnPlayRecorded.isEnabled = hasMic && recordedFilePath != null && !isRecordingSession
    }

    /**
     * Called when the user picks an audio file from storage.
     */
    private fun handleSelectedAudio(uri: Uri) {
        targetAudioUri = uri
        targetFileName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "audio"
        recordedFilePath = null

        binding.tvTargetFileName.text = targetFileName
        binding.tvRecordedFileName.text = getString(R.string.no_recording_yet)
        binding.waveformRecorded.clearWaveform()

        updateUiForPermissions()

        // Generate target waveform on a background thread
        loadTargetWaveform(uri)
    }

    /**
     * Reads the display name of a content Uri from the content provider.
     */
    private fun queryDisplayName(uri: Uri): String? {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else {
                null
            }
        }
    }

    /**
     * Loads waveform data for the target audio using Visualizer in a coroutine.
     */
    private fun loadTargetWaveform(uri: Uri) {
        binding.tvTargetFileName.text = "$targetFileName\n${getString(R.string.loading_waveform)}"

        lifecycleScope.launch {
            val samples = withContext(Dispatchers.IO) {
                audioPlayerManager.captureWaveformFromUri(uri)
            }

            if (samples.isNullOrEmpty()) {
                binding.tvTargetFileName.text = targetFileName
                binding.waveformTarget.clearWaveform()
                showToast(getString(R.string.waveform_failed))
            } else {
                binding.tvTargetFileName.text = targetFileName
                binding.waveformTarget.setWaveformData(samples)
            }
        }
    }

    /**
     * Starts a shadowing session: plays target audio (optional) and records the microphone.
     */
    private fun startRecordingSession() {
        val uri = targetAudioUri
        if (uri == null) {
            showToast(getString(R.string.select_file_first))
            return
        }

        if (!PermissionHelper.hasRecordPermission(this)) {
            showToast(getString(R.string.permission_denied_mic))
            return
        }

        // Begin microphone recording first
        val recordingPath = audioRecorderManager.startRecording()
        if (recordingPath == null) {
            showToast(getString(R.string.recording_failed))
            return
        }

        isRecordingSession = true
        recordedFilePath = null
        binding.waveformRecorded.clearWaveform()
        binding.tvRecordedFileName.text = getString(R.string.no_recording_yet)

        // Update record button label depending on manual stop setting
        binding.btnRecord.text = if (binding.checkManualStop.isChecked) {
            getString(R.string.stop_record)
        } else {
            getString(R.string.record)
        }

        binding.btnRecord.isEnabled = binding.checkManualStop.isChecked
        binding.btnSelectAudio.isEnabled = false
        binding.btnPlayRecorded.isEnabled = false

        showToast(getString(R.string.recording_started))

        val muteTarget = binding.checkMuteTarget.isChecked
        val manualStop = binding.checkManualStop.isChecked

        if (manualStop) {
            // Manual mode: target plays once but user stops recording with the button
            if (!muteTarget) {
                audioPlayerManager.playUri(uri, muteWhenPlaying = false)
            }
        } else {
            // Automatic mode: stop recording when target playback finishes
            audioPlayerManager.onPlaybackComplete = {
                stopRecordingSession()
            }

            if (!muteTarget) {
                audioPlayerManager.playUri(uri, muteWhenPlaying = false)
            } else {
                // Muted target with auto-stop: use silent waveform pass duration as timer
                startMutedAutoStopTimer(uri)
            }
        }
    }

    /**
     * When target is muted but auto-stop is enabled, play silently to know when to stop recording.
     */
    private fun startMutedAutoStopTimer(uri: Uri) {
        audioPlayerManager.onPlaybackComplete = {
            stopRecordingSession()
        }
        audioPlayerManager.playUri(uri, muteWhenPlaying = true)
    }

    /**
     * Stops the active recording session and loads the recorded waveform.
     */
    private fun stopRecordingSession() {
        if (!isRecordingSession) return

        isRecordingSession = false
        audioPlayerManager.onPlaybackComplete = null
        // Player may already be released by the completion callback; stop() is safe to call again
        audioPlayerManager.stop()

        val path = audioRecorderManager.stopRecording()
        recordedFilePath = path

        binding.btnRecord.text = getString(R.string.record)
        binding.btnSelectAudio.isEnabled = PermissionHelper.hasStoragePermission(this)
        updateUiForPermissions()

        if (path == null) {
            showToast(getString(R.string.recording_failed))
            return
        }

        val fileName = path.substringAfterLast('/')
        binding.tvRecordedFileName.text = fileName
        showToast(getString(R.string.recording_stopped))

        loadRecordedWaveform(path)
    }

    /**
     * Generates waveform data for the saved recording file.
     */
    private fun loadRecordedWaveform(filePath: String) {
        lifecycleScope.launch {
            // Brief pause lets MediaRecorder fully release the microphone before decoding
            delay(300)

            val samples = withContext(Dispatchers.IO) {
                audioPlayerManager.captureWaveformFromFile(filePath)
            }

            if (samples.isNullOrEmpty()) {
                showToast(getString(R.string.waveform_failed))
            } else {
                binding.waveformRecorded.setWaveformData(samples)
            }
        }
    }

    /**
     * Plays back the user's recorded shadowing audio.
     */
    private fun playRecordedAudio() {
        val path = recordedFilePath ?: return

        isPlayingRecorded = true
        binding.btnPlayRecorded.text = getString(R.string.stop_playback)
        binding.btnRecord.isEnabled = false
        binding.btnSelectAudio.isEnabled = false

        audioPlayerManager.playFile(path) {
            runOnUiThread { stopRecordedPlayback() }
        }
    }

    /**
     * Stops playback of the recorded audio and restores button states.
     */
    private fun stopRecordedPlayback() {
        isPlayingRecorded = false
        audioPlayerManager.stop()
        binding.btnPlayRecorded.text = getString(R.string.play_recorded)
        updateUiForPermissions()
    }

    /**
     * Shows a short message to the user.
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Release audio resources when the activity is destroyed.
     */
    override fun onDestroy() {
        super.onDestroy()
        audioPlayerManager.stop()
        if (audioRecorderManager.isRecording()) {
            audioRecorderManager.stopRecording()
        }
    }
}
