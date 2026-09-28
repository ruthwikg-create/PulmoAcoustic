package com.ruthwik.pulmoacoustic.model

data class CardiacReading(
    val heartRateBpm: Int? = null,
    val confidence: Int = 0,
    val signalQuality: Int = 0,
    val snrDb: Double = 0.0,
    val beatCount: Int = 0,
    val sampleRate: Int = 16000,
    val inputDevice: String = "Unknown",
    val elapsedMs: Long = 0L,
    val status: String = "Ready"
)

data class ProcessedFrame(
    val waveform: FloatArray,
    val envelope: FloatArray,
    val reading: CardiacReading
)
