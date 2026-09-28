package com.ruthwik.pulmoacoustic.signal

import com.ruthwik.pulmoacoustic.model.CardiacReading
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class CardiacSignalProcessor(private val sampleRate: Int = 16000) {
    private val low = Biquad.lowPass(sampleRate.toDouble(), 120.0, 0.707)
    private val high = Biquad.highPass(sampleRate.toDouble(), 20.0, 0.707)
    private val notch50 = Biquad.notch(sampleRate.toDouble(), 50.0, 18.0)
    private val notch60 = Biquad.notch(sampleRate.toDouble(), 60.0, 18.0)
    private var dc = 0.0
    private var noiseFloor = 0.002
    private val recentPeaks = ArrayDeque<Long>()
    private var sampleCounter = 0L
    private var lastBeatSample = Long.MIN_VALUE

    fun process(input: ShortArray, use50HzNotch: Boolean = true): Processed {
        val x = FloatArray(input.size)
        var energy = 0.0
        for (i in input.indices) {
            val s = input[i] / 32768f
            dc += 0.002 * (s - dc)
            val hp0 = s - dc
            val y1 = high.process(hp0.toDouble()).toFloat()
            val y2 = if (use50HzNotch) notch50.process(y1.toDouble()) else notch60.process(y1.toDouble())
            val y3 = low.process(y2).toFloat()
            x[i] = y3
            energy += y3 * y3
        }

        val rms = sqrt(energy / max(1, x.size))
        noiseFloor = 0.995 * noiseFloor + 0.005 * min(rms, noiseFloor * 2.0 + 0.0005)
        val gate = max(noiseFloor * 1.8, 0.0008)

        val envelope = FloatArray(x.size)
        var smooth = 0.0
        for (i in x.indices) {
            smooth = 0.92 * smooth + 0.08 * abs(x[i]).toDouble()
            envelope[i] = if (smooth < gate) 0f else smooth.toFloat()
        }

        val shannon = FloatArray(x.size)
        for (i in x.indices) {
            val a = min(0.999999, abs(x[i]).toDouble())
            shannon[i] = (-a * a * ln(a * a + 1e-12)).toFloat()
        }
        val finalEnv = FloatArray(x.size) { i -> (0.65 * envelope[i] + 0.35 * shannon[i]).toFloat() }

        detectBeats(finalEnv)
        val bpm = estimateBpm()
        val snr = 20.0 * kotlin.math.log10((rms + 1e-9) / (noiseFloor + 1e-9))
        val regularity = beatRegularity()
        val quality = ((snr.coerceIn(0.0, 30.0) / 30.0) * 70.0 + regularity * 30.0).toInt().coerceIn(0, 100)

        sampleCounter += input.size
        return Processed(
            waveform = normalize(x),
            envelope = normalize(finalEnv),
            reading = CardiacReading(
                heartRateBpm = bpm,
                confidence = (quality * 0.92).toInt().coerceIn(0, 99),
                signalQuality = quality,
                snrDb = snr,
                beatCount = recentPeaks.size,
                sampleRate = sampleRate,
                elapsedMs = sampleCounter * 1000L / sampleRate,
                status = when {
                    quality < 25 -> "Weak signal — reposition sensor"
                    bpm == null -> "Listening for cardiac cycles…"
                    else -> "Signal locked"
                }
            )
        )
    }

    private fun detectBeats(env: FloatArray) {
        if (env.size < 5) return
        val mean = env.average()
        val threshold = max(mean * 1.8, env.maxOrNull()?.times(0.28) ?: 0.0)
        val refractory = (sampleRate * 0.30).toLong()
        for (i in 2 until env.size - 2) {
            val global = sampleCounter + i
            if (global - lastBeatSample < refractory) continue
            val v = env[i]
            if (v > threshold && v >= env[i - 1] && v >= env[i + 1] && v >= env[i - 2] && v >= env[i + 2]) {
                recentPeaks.addLast(global)
                lastBeatSample = global
            }
        }
        while (recentPeaks.size > 12) recentPeaks.removeFirst()
        while (recentPeaks.isNotEmpty() && sampleCounter - recentPeaks.first() > sampleRate * 15L) recentPeaks.removeFirst()
    }

    private fun estimateBpm(): Int? {
        if (recentPeaks.size < 3) return null
        val intervals = recentPeaks.zipWithNext { a, b -> b - a }.filter { it.toDouble() in sampleRate * 0.35..sampleRate * 1.8 }
        if (intervals.size < 2) return null
        val sorted = intervals.sorted()
        val median = sorted[sorted.size / 2].toDouble()
        return (60.0 * sampleRate / median).toInt().coerceIn(35, 220)
    }

    private fun beatRegularity(): Double {
        if (recentPeaks.size < 4) return 0.0
        val rr = recentPeaks.zipWithNext { a, b -> (b - a).toDouble() }
        val mean = rr.average()
        if (mean <= 0) return 0.0
        val sd = sqrt(rr.sumOf { (it - mean) * (it - mean) } / rr.size)
        return (1.0 - (sd / mean).coerceIn(0.0, 1.0)).coerceIn(0.0, 1.0)
    }

    private fun normalize(a: FloatArray): FloatArray {
        val peak = a.maxOfOrNull { abs(it) } ?: 1f
        val scale = if (peak < 1e-6f) 1f else 1f / peak
        return FloatArray(a.size) { (a[it] * scale).coerceIn(-1f, 1f) }
    }

    data class Processed(val waveform: FloatArray, val envelope: FloatArray, val reading: CardiacReading)

    private class Biquad(
        private val b0: Double, private val b1: Double, private val b2: Double,
        private val a1: Double, private val a2: Double
    ) {
        private var x1=0.0; private var x2=0.0; private var y1=0.0; private var y2=0.0
        fun process(x: Double): Double {
            val y=b0*x+b1*x1+b2*x2-a1*y1-a2*y2
            x2=x1; x1=x; y2=y1; y1=y
            return y
        }
        companion object {
            fun lowPass(fs: Double, f: Double, q: Double): Biquad {
                val w=2*Math.PI*f/fs; val c=kotlin.math.cos(w); val s=kotlin.math.sin(w); val a=s/(2*q); val a0=1+a
                return Biquad((1-c)/2/a0,(1-c)/a0,(1-c)/2/a0,-2*c/a0,(1-a)/a0)
            }
            fun highPass(fs: Double, f: Double, q: Double): Biquad {
                val w=2*Math.PI*f/fs; val c=kotlin.math.cos(w); val s=kotlin.math.sin(w); val a=s/(2*q); val a0=1+a
                return Biquad((1+c)/2/a0,-(1+c)/a0,(1+c)/2/a0,-2*c/a0,(1-a)/a0)
            }
            fun notch(fs: Double, f: Double, q: Double): Biquad {
                val w=2*Math.PI*f/fs; val c=kotlin.math.cos(w); val s=kotlin.math.sin(w); val a=s/(2*q); val a0=1+a
                return Biquad(1/a0,-2*c/a0,1/a0,-2*c/a0,(1-a)/a0)
            }
        }
    }
}
