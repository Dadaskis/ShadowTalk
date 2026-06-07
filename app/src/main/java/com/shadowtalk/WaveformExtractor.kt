package com.shadowtalk

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Extracts waveform peak data by decoding audio files with MediaExtractor + MediaCodec.
 * This produces a detailed, accurate static waveform (unlike Visualizer snapshot data).
 */
object WaveformExtractor {

    // Number of vertical bars drawn in WaveformView
    private const val DEFAULT_BAR_COUNT = 200

    /**
     * Reads an audio file from a content Uri and returns normalized peak amplitudes per bar.
     */
    fun extractFromUri(
        context: Context,
        uri: Uri,
        barCount: Int = DEFAULT_BAR_COUNT
    ): List<Float>? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            decodePeaks(extractor, barCount)
        } catch (exception: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    /**
     * Reads an audio file from a local path and returns normalized peak amplitudes per bar.
     */
    fun extractFromFile(filePath: String, barCount: Int = DEFAULT_BAR_COUNT): List<Float>? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(filePath)
            decodePeaks(extractor, barCount)
        } catch (exception: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    /**
     * Finds the audio track, decodes PCM samples, and buckets peak amplitudes into bars.
     */
    private fun decodePeaks(extractor: MediaExtractor, barCount: Int): List<Float>? {
        val trackIndex = findAudioTrackIndex(extractor) ?: return null
        extractor.selectTrack(trackIndex)

        val format = extractor.getTrackFormat(trackIndex)
        val mimeType = format.getString(MediaFormat.KEY_MIME) ?: return null
        val durationUs = format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L)
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)

        val peaks = FloatArray(barCount)
        val codec = MediaCodec.createDecoderByType(mimeType)

        return try {
            codec.configure(format, null, null, 0)
            codec.start()
            decodeLoop(extractor, codec, durationUs, sampleRate, barCount, peaks)
            normalizePeaks(peaks)
        } finally {
            try {
                codec.stop()
            } catch (ignored: Exception) {
                // Codec may already be stopped if decoding failed mid-way
            }
            codec.release()
        }
    }

    /**
     * Scans all tracks and returns the index of the first audio track.
     */
    private fun findAudioTrackIndex(extractor: MediaExtractor): Int? {
        for (index in 0 until extractor.trackCount) {
            val mimeType = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME) ?: continue
            if (mimeType.startsWith("audio/")) {
                return index
            }
        }
        return null
    }

    /**
     * Feeds encoded samples to the decoder and records peak amplitude for each time bucket.
     */
    private fun decodeLoop(
        extractor: MediaExtractor,
        codec: MediaCodec,
        durationUs: Long,
        sampleRate: Int,
        barCount: Int,
        peaks: FloatArray
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var decodedSampleIndex = 0L

        while (!outputDone) {
            if (!inputDone) {
                val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: continue
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)

                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            sampleSize,
                            extractor.sampleTime,
                            0
                        )
                        extractor.advance()
                    }
                }
            }

            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputBufferIndex >= 0 -> {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }

                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        decodedSampleIndex = accumulatePeaks(
                            buffer = outputBuffer,
                            info = bufferInfo,
                            durationUs = durationUs,
                            sampleRate = sampleRate,
                            barCount = barCount,
                            peaks = peaks,
                            startSampleIndex = decodedSampleIndex
                        )
                    }

                    codec.releaseOutputBuffer(outputBufferIndex, false)
                }

                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // New output format — nothing to do, peaks use input sample rate mapping
                }
            }
        }
    }

    /**
     * Walks 16-bit PCM samples in a decoded buffer and stores the loudest value per bar.
     */
    private fun accumulatePeaks(
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        durationUs: Long,
        sampleRate: Int,
        barCount: Int,
        peaks: FloatArray,
        startSampleIndex: Long
    ): Long {
        buffer.position(info.offset)
        val limit = info.offset + info.size
        var sampleIndex = startSampleIndex

        while (buffer.position() < limit - 1) {
            val pcmSample = buffer.short.toInt()
            val amplitude = abs(pcmSample) / PCM_MAX_AMPLITUDE

            // Map this sample's timestamp to one waveform bar
            val timeUs = (sampleIndex * 1_000_000L) / sampleRate
            val barIndex = ((timeUs.toDouble() / durationUs) * barCount)
                .toInt()
                .coerceIn(0, barCount - 1)

            if (amplitude > peaks[barIndex]) {
                peaks[barIndex] = amplitude
            }

            sampleIndex++
        }

        return sampleIndex
    }

    /**
     * Scales peaks so the loudest bar is 1.0 while preserving relative shape.
     */
    private fun normalizePeaks(peaks: FloatArray): List<Float> {
        val maxPeak = peaks.maxOrNull() ?: 0f
        if (maxPeak <= 0f) {
            return peaks.map { 0f }
        }
        return peaks.map { (it / maxPeak).coerceIn(0f, 1f) }
    }

    private const val TIMEOUT_US = 10_000L
    private const val PCM_MAX_AMPLITUDE = 32768f
}
