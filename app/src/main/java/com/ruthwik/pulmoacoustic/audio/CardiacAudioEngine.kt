package com.ruthwik.pulmoacoustic.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.ruthwik.pulmoacoustic.model.CardiacReading
import com.ruthwik.pulmoacoustic.signal.CardiacSignalProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CardiacAudioEngine(private val context: Context) {
    companion object { const val SAMPLE_RATE = 16000 }
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var recorder: AudioRecord? = null
    private var job: Job? = null
    private var callback: ((FloatArray, CardiacReading) -> Unit)? = null
    private var processor = CardiacSignalProcessor(SAMPLE_RATE)
    private var currentDevice: AudioDeviceInfo? = null

    fun detectInput(): AudioDeviceInfo? = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull {
        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
        it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
        (android.os.Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
    }

    fun deviceLabel(): String = currentDevice?.productName?.toString()?.ifBlank { null }
        ?: detectInput()?.productName?.toString()?.ifBlank { null }
        ?: "No compatible headset microphone"

    fun start(scope: CoroutineScope, onFrame: (FloatArray, CardiacReading) -> Unit): Result<Unit> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            return Result.failure(SecurityException("Microphone permission is required"))
        currentDevice = detectInput() ?: return Result.failure(IllegalStateException("Connect a headset with a microphone"))
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return Result.failure(IllegalStateException("Audio input is not supported"))
        val bufferSize = maxOf(min * 2, SAMPLE_RATE / 5)
        recorder = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.UNPROCESSED)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(bufferSize).build().also { it.preferredDevice = currentDevice }
        } catch (_: Exception) {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(bufferSize).build().also { it.preferredDevice = currentDevice }
        }
        processor = CardiacSignalProcessor(SAMPLE_RATE)
        callback = onFrame
        recorder?.startRecording()
        if (recorder?.recordingState != AudioRecord.RECORDSTATE_RECORDING) return Result.failure(IllegalStateException("Could not start microphone"))
        job = scope.launch(Dispatchers.IO) {
            val buf = ShortArray(SAMPLE_RATE / 50)
            while (isActive) {
                val n = recorder?.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING) ?: -1
                if (n > 0) {
                    val p = processor.process(if (n == buf.size) buf else buf.copyOf(n))
                    callback?.invoke(p.waveform, p.reading)
                }
            }
        }
        return Result.success(Unit)
    }

    fun stop() {
        job?.cancel(); job = null
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release(); recorder = null
    }

    fun registerDeviceCallback(callback: AudioDeviceCallback) = audioManager.registerAudioDeviceCallback(callback, null)
    fun unregisterDeviceCallback(callback: AudioDeviceCallback) = audioManager.unregisterAudioDeviceCallback(callback)
}
