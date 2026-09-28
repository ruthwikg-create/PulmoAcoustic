package com.ruthwik.pulmoacoustic.audio

import android.media.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

data class AudioCapture(
    val samples: ShortArray,
    val sampleRateHz: Int,
    val aborted: Boolean,
)

class AcousticEngine {
    fun supportedSampleRate(): Int {
        val candidateRates = intArrayOf(48_000, 44_100, 32_000)
        return candidateRates.firstOrNull { rate ->
            AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0 &&
                AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0
        } ?: 44_100
    }

    suspend fun capture(
        durationSec: Int,
        carrierHz: Double,
        outputGain: Float,
        shouldAbort: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {},
        onSamples: (ShortArray, Int) -> Unit = { _, _ -> },
    ): AudioCapture = withContext(Dispatchers.IO) {
        val rate = supportedSampleRate()
        val inBuffer = (AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2).coerceAtLeast(8192)
        val outBuffer = (AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2).coerceAtLeast(8192)

        val source = if (android.os.Build.VERSION.SDK_INT >= 24) {
            runCatching { MediaRecorder.AudioSource.UNPROCESSED }.getOrDefault(MediaRecorder.AudioSource.MIC)
        } else {
            MediaRecorder.AudioSource.MIC
        }

        val recorder = AudioRecord(
            source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, inBuffer
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone could not be initialized" }

        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(rate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            outBuffer,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        check(track.state == AudioTrack.STATE_INITIALIZED) { "Speaker audio could not be initialized" }

        val safeGain = outputGain.coerceIn(0.015f, 0.08f)
        val pcmChunk = ShortArray(2048)
        var phase = 0.0
        val phaseStep = 2.0 * PI * carrierHz / rate
        val expected = durationSec * rate
        val captured = ShortArray(expected)
        val scratch = ShortArray(4096)
        var writeIndex = 0
        var aborted = false

        try {
            track.setVolume(safeGain)
            track.play()
            recorder.startRecording()
            val started = System.nanoTime()

            while (writeIndex < expected) {
                if (shouldAbort()) {
                    aborted = true
                    break
                }

                var p = phase
                for (i in pcmChunk.indices) {
                    pcmChunk[i] = (sin(p) * 0.80 * Short.MAX_VALUE).toInt().toShort()
                    p += phaseStep
                    if (p > 2.0 * PI) p -= 2.0 * PI
                }
                phase = p

                val written = track.write(pcmChunk, 0, pcmChunk.size, AudioTrack.WRITE_BLOCKING)
                check(written >= 0) { "Speaker write failed: $written" }

                val n = recorder.read(scratch, 0, scratch.size, AudioRecord.READ_BLOCKING)
                check(n >= 0) { "Microphone read failed: $n" }

                if (n > 0) {
                    val copy = minOf(n, expected - writeIndex)
                    scratch.copyInto(captured, writeIndex, 0, copy)
                    val emitted = scratch.copyOfRange(0, copy)
                    onSamples(emitted, rate)
                    writeIndex += copy
                }

                val elapsed = (System.nanoTime() - started) / 1e9
                onProgress((elapsed / durationSec).toFloat().coerceIn(0f, 1f))
            }
        } finally {
            runCatching { recorder.stop() }
            runCatching { recorder.release() }
            runCatching { track.stop() }
            runCatching { track.release() }
        }

        AudioCapture(captured.copyOf(writeIndex), rate, aborted)
    }
}