package com.ruthwik.pulmoacoustic.model

import kotlin.math.max
import kotlin.math.roundToInt

data class AcousticConfig(
    val sampleRateHz: Int = 48_000,
    val carrierHz: Double = 19_000.0,
    val outputGain: Float = 0.05f,
    val durationSec: Int = 30,
    val targetDistanceCm: Float = 50f,
    val lowHz: Double = 0.08,
    val highHz: Double = 0.80,
)

data class SignalQuality(
    val snrDb: Double,
    val periodicity: Double,
    val motionScore: Double,
    val carrierStability: Double,
    val confidence: Int,
    val valid: Boolean,
    val majorMovementDetected: Boolean = false,
    val estimatorAgreementBpm: Double = 99.0,
)

data class RespiratoryResult(
    val respiratoryRateBpm: Double?,
    val signalQuality: SignalQuality,
    val carrierHz: Double,
    val durationSec: Double,
    val timestampEpochMs: Long,
    val message: String,
) {
    val confidenceText: String
        get() = when {
            signalQuality.confidence >= 90 -> "Very high"
            signalQuality.confidence >= 75 -> "High"
            signalQuality.confidence >= 60 -> "Moderate"
            else -> "Low"
        }
}

data class DeviceCapabilities(
    val inputSampleRateHz: Int,
    val outputSampleRateHz: Int,
    val hasAccelerometer: Boolean,
    val hasGyroscope: Boolean,
    val supports48k: Boolean,
    val likelyHighFrequencyCapable: Boolean,
)

data class ScanPoint(
    val label: String,
    val qualityScore: Double = 0.0,
    val snrDb: Double = 0.0,
    val periodicity: Double = 0.0,
    val motionScore: Double = 0.0,
    val pitchDeg: Double = 0.0,
    val rollDeg: Double = 0.0,
    val yawDeg: Double = 0.0,
    val tested: Boolean = false,
)

fun confidenceFromMetrics(
    snrDb: Double,
    periodicity: Double,
    motionScore: Double,
    carrierStability: Double,
    estimatorAgreementBpm: Double,
    majorMovement: Boolean,
): Int {
    val snrPart = ((snrDb + 4.0) / 24.0).coerceIn(0.0, 1.0)
    val pPart = periodicity.coerceIn(0.0, 1.0)
    val mPart = (1.0 - motionScore).coerceIn(0.0, 1.0)
    val cPart = carrierStability.coerceIn(0.0, 1.0)
    val agreementPart = ((2.5 - estimatorAgreementBpm.coerceAtLeast(0.0)) / 2.5).coerceIn(0.0, 1.0)
    val safetyPenalty = if (majorMovement) 0.35 else 1.0
    val score = 100.0 * safetyPenalty * (
        0.28 * snrPart +
            0.28 * pPart +
            0.18 * mPart +
            0.14 * cPart +
            0.12 * agreementPart
        )
    return max(0, score.roundToInt())
}