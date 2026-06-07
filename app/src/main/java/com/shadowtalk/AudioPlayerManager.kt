package com.shadowtalk

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build

/**
 * Manages audio playback with MediaPlayer and audio focus handling.
 */
class AudioPlayerManager(private val context: Context) {

    // MediaPlayer instance used for target and recorded playback
    private var mediaPlayer: MediaPlayer? = null

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
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        abandonAudioFocus()
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
                // Release the player on the next main-loop frame — calling release()
                // synchronously inside onCompletion can crash on some devices.
                val completeCallback = onComplete
                val playbackCompleteCallback = onPlaybackComplete
                onPlaybackComplete = null

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    releaseCompletedPlayer()
                    completeCallback?.invoke()
                    playbackCompleteCallback?.invoke()
                }
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
                val completeCallback = onComplete
                val playbackCompleteCallback = onPlaybackComplete
                onPlaybackComplete = null

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    releaseCompletedPlayer()
                    completeCallback?.invoke()
                    playbackCompleteCallback?.invoke()
                }
            }

            start()
        }
    }

    /**
     * Releases MediaPlayer after natural playback completion (already stopped by the system).
     */
    private fun releaseCompletedPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
        abandonAudioFocus()
    }

    /**
     * Captures waveform data from an audio file by decoding PCM samples.
     *
     * @param filePath Path to the audio file
     * @param maxSamples Number of amplitude bars to return for drawing
     * @return List of normalized amplitudes (0.0 to 1.0), or null on failure
     */
    suspend fun captureWaveformFromFile(filePath: String, maxSamples: Int = 200): List<Float>? {
        return WaveformExtractor.extractFromFile(filePath, maxSamples)
    }

    /**
     * Captures waveform data from a content Uri.
     */
    suspend fun captureWaveformFromUri(uri: Uri, maxSamples: Int = 200): List<Float>? {
        return WaveformExtractor.extractFromUri(context, uri, maxSamples)
    }
}
