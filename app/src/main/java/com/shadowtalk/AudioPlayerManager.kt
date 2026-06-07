package com.shadowtalk

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Manages audio playback with MediaPlayer, audio focus, and Visualizer-based waveform capture.
 */
class AudioPlayerManager(private val context: Context) {

    // MediaPlayer instance used for target and recorded playback
    private var mediaPlayer: MediaPlayer? = null

    // Visualizer attached to the active MediaPlayer session
    private var visualizer: Visualizer? = null

    // System audio manager for requesting audio focus
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Focus request object used on Android 8.0+
    private var focusRequest: AudioFocusRequest? = null

    // Listener invoked when target playback completes during a recording session
    var onPlaybackComplete: (() -> Unit)? = null

    /**
     * Returns true if MediaPlayer is currently playing audio.
     */
    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    /**
     * Requests exclusive audio focus so playback is not interrupted by other apps.
     * Returns true when focus was granted.
     */
    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { /* Keep playing; shadowing needs continuous audio */ }
                .build()

            return audioManager.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            return audioManager.requestAudioFocus(
                { /* No-op listener for legacy API */ },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    /**
     * Releases audio focus when playback stops.
     */
    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    /**
     * Stops playback, releases Visualizer and MediaPlayer, and abandons audio focus.
     */
    fun stop() {
        releaseVisualizer()
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        abandonAudioFocus()
    }

    /**
     * Safely releases the Visualizer instance.
     */
    private fun releaseVisualizer() {
        visualizer?.apply {
            enabled = false
            release()
        }
        visualizer = null
    }

    /**
     * Plays audio from a file path through the device speaker.
     *
     * @param filePath Absolute path to an audio file
     * @param muteWhenPlaying If true, volume is set to zero (used when "mute target" is checked)
     * @param onComplete Called on the main thread when playback finishes
     */
    fun playFile(
        filePath: String,
        muteWhenPlaying: Boolean = false,
        onComplete: (() -> Unit)? = null
    ) {
        stop()

        if (!requestAudioFocus()) {
            onComplete?.invoke()
            return
        }

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(filePath)
            prepare()

            if (muteWhenPlaying) {
                setVolume(0f, 0f)
            }

            setOnCompletionListener {
                onComplete?.invoke()
                onPlaybackComplete?.invoke()
                stop()
            }

            start()
        }
    }

    /**
     * Plays audio from a content Uri (used when user picks a file from the document picker).
     */
    fun playUri(
        uri: Uri,
        muteWhenPlaying: Boolean = false,
        onComplete: (() -> Unit)? = null
    ) {
        stop()

        if (!requestAudioFocus()) {
            onComplete?.invoke()
            return
        }

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(context, uri)
            prepare()

            if (muteWhenPlaying) {
                setVolume(0f, 0f)
            }

            setOnCompletionListener {
                onComplete?.invoke()
                onPlaybackComplete?.invoke()
                stop()
            }

            start()
        }
    }

    /**
     * Captures waveform data from an audio file using Android's Visualizer class.
     * The file is played silently while samples are collected, then playback stops.
     *
     * @param filePath Path to the audio file
     * @param maxSamples Number of amplitude bars to return for drawing
     * @return List of normalized amplitudes (0.0 to 1.0), or null on failure
     */
    suspend fun captureWaveformFromFile(filePath: String, maxSamples: Int = 100): List<Float>? {
        return captureWaveformFromSource(maxSamples) { player ->
            player.setDataSource(filePath)
        }
    }

    /**
     * Captures waveform data from a content Uri.
     */
    suspend fun captureWaveformFromUri(uri: Uri, maxSamples: Int = 100): List<Float>? {
        return captureWaveformFromSource(maxSamples) { player ->
            player.setDataSource(context, uri)
        }
    }

    /**
     * Shared implementation: prepare MediaPlayer, attach Visualizer, play silently, collect samples.
     */
    private suspend fun captureWaveformFromSource(
        maxSamples: Int = 100,
        configure: (MediaPlayer) -> Unit
    ): List<Float>? = suspendCoroutine { continuation ->
        stop()

        val samples = mutableListOf<Float>()
        var player: MediaPlayer? = null

        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                configure(this)
                prepare()

                // Mute playback so waveform analysis does not produce audible sound
                setVolume(0f, 0f)

                visualizer = Visualizer(audioSessionId).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[1]
                    scalingMode = Visualizer.SCALING_MODE_NORMALIZED

                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(
                                visualizer: Visualizer?,
                                waveform: ByteArray?,
                                samplingRate: Int
                            ) {
                                if (waveform == null) return

                                // Convert raw waveform bytes into a single amplitude value
                                var maxAmplitude = 0
                                for (byte in waveform) {
                                    val amplitude = kotlin.math.abs(byte.toInt())
                                    if (amplitude > maxAmplitude) {
                                        maxAmplitude = amplitude
                                    }
                                }
                                val normalized = maxAmplitude / 128f
                                samples.add(normalized.coerceIn(0f, 1f))
                            }

                            override fun onFftDataCapture(
                                visualizer: Visualizer?,
                                fft: ByteArray?,
                                samplingRate: Int
                            ) {
                                // Waveform capture uses onWaveFormDataCapture; FFT not needed here
                            }
                        },
                        Visualizer.getMaxCaptureRate(),
                        true,
                        false
                    )
                    enabled = true
                }

                setOnCompletionListener {
                    releaseVisualizer()
                    player?.release()
                    mediaPlayer = null
                    continuation.resume(downsample(samples, maxSamples))
                }

                start()
            }

            mediaPlayer = player
        } catch (exception: Exception) {
            releaseVisualizer()
            player?.release()
            mediaPlayer = null
            continuation.resume(null)
        }
    }

    /**
     * Reduces a large list of samples to a fixed number of bars for the waveform view.
     */
    private fun downsample(samples: List<Float>, maxSamples: Int): List<Float> {
        if (samples.isEmpty()) return emptyList()
        if (samples.size <= maxSamples) return samples

        val step = samples.size.toFloat() / maxSamples
        return List(maxSamples) { index ->
            val sampleIndex = (index * step).toInt().coerceIn(0, samples.lastIndex)
            samples[sampleIndex]
        }
    }
}
