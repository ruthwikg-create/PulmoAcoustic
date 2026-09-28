package com.ruthwik.pulmoacoustic.signal

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class LiveSnapshot(
    val trace: List<Float>,
    val rrBpm: Double?,
    val confidence: Int,
    val snrDb: Double,
    val periodicity: Double,
    val elapsedSec: Double,
    val stage: String,
    val motionScore: Double,
) {
    companion object {
        fun empty() = LiveSnapshot(emptyList(), null, 0, -99.0, 0.0, 0.0, "Ready", 0.0)
    }
}

class LiveRespiratoryTracker(
    private val processor: RespiratorySignalProcessor,
) {
    private val lock = Any()
    private val raw = ArrayList<Short>()
    private val trace = ArrayList<Float>()
    private var lastPhase = 0.0
    private var unwrappedPhase = 0.0
    private var phaseInitialized = false
    private var sampleRate = 48_000
    private var carrierHz = 19_000.0
    private var startedAtNs = 0L
    private var lastEstimateAtNs = 0L
    private var lastSnapshot = LiveSnapshot.empty()

    fun reset(rate: Int, carrier: Double) {
        synchronized(lock) {
            raw.clear()
            trace.clear()
            lastPhase = 0.0
            unwrappedPhase = 0.0
            phaseInitialized = false
            sampleRate = rate
            carrierHz = carrier
            startedAtNs = System.nanoTime()
            lastEstimateAtNs = 0L
            lastSnapshot = LiveSnapshot.empty()
        }
    }

    fun ingest(samples: ShortArray, rate: Int) {
        synchronized(lock) {
            if (startedAtNs == 0L) {
                sampleRate = rate
                startedAtNs = System.nanoTime()
            } else {
                sampleRate = rate
            }
            raw.addAll(samples.asList())
            val keep = rate * 15
            if (raw.size > keep) {
                raw.subList(0, raw.size - keep).clear()
            }

            val block = blockSize(rate, carrierHz)
            if (block <= 0) return
            var offset = 0
            while (offset + block <= samples.size) {
                var iAcc = 0.0
                var qAcc = 0.0
                var phase = 0.0
                val step = 2.0 * PI * carrierHz / rate
                for (j in 0 until block) {
                    val x = samples[offset + j].toDouble() / Short.MAX_VALUE
                    iAcc += x * cos(phase)
                    qAcc += x * sin(phase)
                    phase += step
                    if (phase > 2.0 * PI) phase -= 2.0 * PI
                }
                val current = atan2(qAcc, iAcc)
                if (!phaseInitialized) {
                    lastPhase = current
                    phaseInitialized = true
                } else {
                    var delta = current - lastPhase
                    while (delta > PI) delta -= 2.0 * PI
                    while (delta < -PI) delta += 2.0 * PI
                    unwrappedPhase += delta
                    lastPhase = current
                }
                trace.add(unwrappedPhase.toFloat())
                offset += block
            }
            if (trace.size > 220) trace.subList(0, trace.size - 220).clear()
        }
    }

    fun updateAnalysis(
        motionScore: Double,
        majorMovement: Boolean,
    ) {
        synchronized(lock) {
            if (raw.size < sampleRate * 6) {
                val elapsed = (System.nanoTime() - startedAtNs).coerceAtLeast(0L) / 1e9
                lastSnapshot = LiveSnapshot(
                    trace = normalizedTrace(),
                    rrBpm = null,
                    confidence = 0,
                    snrDb = -99.0,
                    periodicity = 0.0,
                    elapsedSec = elapsed,
                    stage = if (majorMovement) "Movement detected" else "Acquiring signal",
                    motionScore = motionScore
                )
                return
            }
            val now = System.nanoTime()
            if (now - lastEstimateAtNs < 1_500_000_000L) {
                lastSnapshot = lastSnapshot.copy(
                    trace = normalizedTrace(),
                    elapsedSec = (now - startedAtNs).coerceAtLeast(0L) / 1e9,
                    stage = if (majorMovement) "Movement detected" else "Acquiring signal",
                    motionScore = motionScore
                )
                return
            }
            lastEstimateAtNs = now

            val snapshot = raw.takeLast(min(raw.size, sampleRate * 12)).toShortArray()
            val analysis = processor.analyze(snapshot, sampleRate, carrierHz, motionScore, majorMovement)
            lastSnapshot = LiveSnapshot(
                trace = normalizedTrace(),
                rrBpm = analysis.rrBpm,
                confidence = analysis.confidence,
                snrDb = analysis.snrDb,
                periodicity = analysis.periodicity,
                elapsedSec = (now - startedAtNs).coerceAtLeast(0L) / 1e9,
                stage = when {
                    majorMovement -> "Movement detected"
                    analysis.rrBpm != null -> "Respiration detected"
                    else -> "Analyzing signal"
                },
                motionScore = motionScore
            )
        }
    }

    fun snapshot(): LiveSnapshot = synchronized(lock) { lastSnapshot.copy(trace = normalizedTrace()) }

    private fun normalizedTrace(): List<Float> {
        if (trace.isEmpty()) return emptyList()
        val values = trace.takeLast(180)
        val mean = values.average()
        val maxDev = values.maxOf { kotlin.math.abs(it - mean) }.coerceAtLeast(1e-6)
        return values.map { ((it - mean) / maxDev).toFloat() }
    }

    private fun blockSize(rate: Int, carrier: Double): Int {
        val c = carrier.roundToInt().coerceAtLeast(1)
        var a = rate
        var b = c
        while (b != 0) {
            val t = a % b
            a = b
            b = t
        }
        return (rate / a.coerceAtLeast(1)) * 10
    }
}
