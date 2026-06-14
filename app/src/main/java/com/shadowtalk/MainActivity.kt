package com.shadowtalk

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.shadowtalk.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var audioPlayerManager: AudioPlayerManager
    private lateinit var audioRecorderManager: AudioRecorderManager

    private var targetAudioUri: Uri? = null
    private var targetFileName: String? = null
    private var recordedFilePath: String? = null

    private var isRecordingSession = false
    private var isPlayingRecorded = false
    private var isPlayingTarget = false
    private var isPlayingPrior = false
    private var priorPlayingPath: String? = null

    private var recordingsDialog: BottomSheetDialog? = null

    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            handleSelectedAudio(uri)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        handlePermissionResults(results)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioPlayerManager = AudioPlayerManager(this)
        audioRecorderManager = AudioRecorderManager(this)

        setupClickListeners()
        requestPermissionsIfNeeded()
        updateUiForPermissions()
    }

    private fun setupClickListeners() {
        binding.btnSelectAudio.setOnClickListener {
            if (!PermissionHelper.hasStoragePermission(this)) {
                showToast(getString(R.string.permission_denied_storage))
                return@setOnClickListener
            }
            pickAudioLauncher.launch("audio/*")
        }

        binding.btnPlaySelected.setOnClickListener {
            if (isPlayingTarget) {
                stopTargetPlayback()
            } else {
                playTargetAudio()
            }
        }

        binding.btnRecord.setOnClickListener {
            if (isRecordingSession) {
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

        binding.btnRecordings.setOnClickListener {
            showRecordingsDialog()
        }
    }

    private fun requestPermissionsIfNeeded() {
        if (!PermissionHelper.hasAllPermissions(this)) {
            if (PermissionHelper.shouldShowRationale(this)) {
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

    private fun handlePermissionResults(@Suppress("UNUSED_PARAMETER") results: Map<String, Boolean>) {
        updateUiForPermissions()

        if (!PermissionHelper.hasStoragePermission(this)) {
            showToast(getString(R.string.permission_denied_storage))
        }
        if (!PermissionHelper.hasRecordPermission(this)) {
            showToast(getString(R.string.permission_denied_mic))
        }
    }

    private fun updateUiForPermissions() {
        val hasStorage = PermissionHelper.hasStoragePermission(this)
        val hasMic = PermissionHelper.hasRecordPermission(this)
        val hasTarget = targetAudioUri != null

        binding.btnSelectAudio.isEnabled = hasStorage
        binding.btnPlaySelected.isEnabled = hasMic && hasTarget && !isRecordingSession && !isPlayingRecorded && !isPlayingPrior
        binding.btnRecord.isEnabled = hasMic && hasTarget && !isPlayingRecorded && !isPlayingTarget && !isPlayingPrior
        binding.btnPlayRecorded.isEnabled = hasMic && recordedFilePath != null && !isRecordingSession && !isPlayingTarget && !isPlayingPrior
        binding.btnRecordings.isEnabled = true
    }

    private fun handleSelectedAudio(uri: Uri) {
        targetAudioUri = uri
        targetFileName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "audio"
        recordedFilePath = null

        binding.tvTargetFileName.text = targetFileName
        binding.tvRecordedFileName.text = getString(R.string.no_recording_yet)
        binding.waveformRecorded.clearWaveform()

        stopAllPlayback()
        updateUiForPermissions()

        loadTargetWaveform(uri)
    }

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

    private fun playTargetAudio() {
        val uri = targetAudioUri ?: return

        stopAllPlayback()
        audioPlayerManager.onPlaybackComplete = null

        isPlayingTarget = true
        binding.btnPlaySelected.text = getString(R.string.stop_target)
        binding.btnRecord.isEnabled = false
        binding.btnPlayRecorded.isEnabled = false

        audioPlayerManager.playUri(uri, onComplete = {
            runOnUiThread { stopTargetPlayback() }
        })
    }

    private fun stopTargetPlayback() {
        isPlayingTarget = false
        audioPlayerManager.stop()
        binding.btnPlaySelected.text = getString(R.string.play_selected)
        updateUiForPermissions()
    }

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

        stopAllPlayback()

        val recordingPath = audioRecorderManager.startRecording()
        if (recordingPath == null) {
            showToast(getString(R.string.recording_failed))
            return
        }

        isRecordingSession = true
        recordedFilePath = null
        binding.waveformRecorded.clearWaveform()
        binding.tvRecordedFileName.text = getString(R.string.no_recording_yet)

        binding.btnRecord.text = if (binding.checkManualStop.isChecked) {
            getString(R.string.stop_record)
        } else {
            getString(R.string.record)
        }

        binding.btnRecord.isEnabled = binding.checkManualStop.isChecked
        binding.btnSelectAudio.isEnabled = false
        binding.btnPlaySelected.isEnabled = false
        binding.btnPlayRecorded.isEnabled = false

        showToast(getString(R.string.recording_started))

        val muteTarget = binding.checkMuteTarget.isChecked
        val manualStop = binding.checkManualStop.isChecked

        if (manualStop) {
            if (!muteTarget) {
                audioPlayerManager.playUri(uri, muteWhenPlaying = false)
            }
        } else {
            audioPlayerManager.onPlaybackComplete = {
                stopRecordingSession()
            }

            if (!muteTarget) {
                audioPlayerManager.playUri(uri, muteWhenPlaying = false)
            } else {
                startMutedAutoStopTimer(uri)
            }
        }
    }

    private fun startMutedAutoStopTimer(uri: Uri) {
        audioPlayerManager.onPlaybackComplete = {
            stopRecordingSession()
        }
        audioPlayerManager.playUri(uri, muteWhenPlaying = true)
    }

    private fun stopRecordingSession() {
        if (!isRecordingSession) return

        isRecordingSession = false
        audioPlayerManager.onPlaybackComplete = null
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

    private fun loadRecordedWaveform(filePath: String) {
        lifecycleScope.launch {
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

    private fun playRecordedAudio() {
        val path = recordedFilePath ?: return

        stopAllPlayback()

        isPlayingRecorded = true
        binding.btnPlayRecorded.text = getString(R.string.stop_playback)
        binding.btnRecord.isEnabled = false
        binding.btnSelectAudio.isEnabled = false
        binding.btnPlaySelected.isEnabled = false

        audioPlayerManager.playFile(path) {
            runOnUiThread { stopRecordedPlayback() }
        }
    }

    private fun stopRecordedPlayback() {
        isPlayingRecorded = false
        audioPlayerManager.stop()
        binding.btnPlayRecorded.text = getString(R.string.play_recorded)
        updateUiForPermissions()
    }

    private fun stopAllPlayback() {
        audioPlayerManager.stop()
        isPlayingTarget = false
        isPlayingRecorded = false
        isPlayingPrior = false
        priorPlayingPath = null
        binding.btnPlaySelected.text = getString(R.string.play_selected)
        binding.btnPlayRecorded.text = getString(R.string.play_recorded)
    }

    private fun showRecordingsDialog() {
        if (recordingsDialog?.isShowing == true) return

        val dialog = BottomSheetDialog(this).apply {
            behavior.isDraggable = false
            setOnDismissListener { recordingsDialog = null }
        }
        recordingsDialog = dialog

        val sheetView = LayoutInflater.from(this)
            .inflate(R.layout.bottom_sheet_recordings, null) as LinearLayout

        val recycler = sheetView.findViewById<RecyclerView>(R.id.recyclerRecordings)
        val progressBar = sheetView.findViewById<View>(R.id.progressLoading)
        val emptyText = sheetView.findViewById<TextView>(R.id.tvEmptyRecordings)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.isNestedScrollingEnabled = false
        progressBar.visibility = View.VISIBLE
        recycler.visibility = View.GONE
        emptyText.visibility = View.GONE

        sheetView.findViewById<View>(R.id.btnCloseSheet).setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(sheetView)
        dialog.show()

        lifecycleScope.launch {
            val recordings = withContext(Dispatchers.IO) { getRecordingsList() }

            if (dialog.isShowing) {
                progressBar.visibility = View.GONE

                if (recordings.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                } else {
                    recycler.visibility = View.VISIBLE
                    recycler.adapter = RecordingAdapter(recordings) { recording ->
                        playPriorRecording(recording.absolutePath)
                    }
                }
            }
        }
    }

    data class RecordingEntry(val name: String, val absolutePath: String, val date: Date)

    private fun getRecordingsList(): List<RecordingEntry> {
        val filesDir = filesDir ?: return emptyList()
        val recordings = filesDir.listFiles { file ->
            file.isFile && file.name.startsWith("recording_") && file.name.endsWith(".m4a")
        } ?: return emptyList()

        return recordings
            .map { file ->
                val date = try {
                    val dateStr = file.name
                        .removePrefix("recording_")
                        .removeSuffix(".m4a")
                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(dateStr)
                } catch (_: Exception) {
                    Date(file.lastModified())
                }
                RecordingEntry(file.name, file.absolutePath, date ?: Date(file.lastModified()))
            }
            .sortedByDescending { it.date }
    }

    private fun playPriorRecording(path: String) {
        stopAllPlayback()

        isPlayingPrior = true
        priorPlayingPath = path
        updateUiForPermissions()

        audioPlayerManager.playFile(path) {
            runOnUiThread { stopPriorPlayback() }
        }
    }

    private fun stopPriorPlayback() {
        isPlayingPrior = false
        priorPlayingPath = null
        audioPlayerManager.stop()
        updateUiForPermissions()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        audioPlayerManager.stop()
        if (audioRecorderManager.isRecording()) {
            audioRecorderManager.stopRecording()
        }
    }
}
