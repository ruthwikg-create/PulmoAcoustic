package com.ruthwik.pulmoacoustic.signal

import com.ruthwik.pulmoacoustic.model.*
import kotlin.math.*

data class SignalAnalysis(
    val rrBpm: Double?,
    val snrDb: Double,
    val periodicity: Double,
    val carrierStability: Double,
    val estimatorAgreementBpm: Double,
    val confidence: Int,
    val valid: Boolean,
)

class RespiratorySignalProcessor {

    fun analyze(
        samples: ShortArray,
        sampleRate: Int,
        carrierHz: Double,
        motionScore: Double,
        majorMovement: Boolean,
    ): SignalAnalysis {
        if (samples.size < sampleRate * 10) {
            return SignalAnalysis(null, -99.0, 0.0, 0.0, 99.0, 0, false)
        }

        val carrierCycles = samplesPerCarrierPeriod(sampleRate, carrierHz).coerceAtLeast(1)
        val block = (carrierCycles * 10).coerceAtMost(samples.size / 10).coerceAtLeast(carrierCycles)
        val blockRate = sampleRate.toDouble() / block
        val count = samples.size / block

        val phase = DoubleArray(count)
        val amplitude = DoubleArray(count)

        var previousPhase = 0.0
        var phaseOffset = 0.0

        for (b in 0 until count) {
            var iAcc = 0.0
            var qAcc = 0.0
            var p = 0.0
            val step = 2.0 * PI * carrierHz / sampleRate
            for (j in 0 until block) {
                val x = samples[b * block + j].toDouble() / Short.MAX_VALUE
                iAcc += x * cos(p)
                qAcc += x * sin(p)
                p += step
                if (p > 2.0 * PI) p -= 2.0 * PI
            }
            val i = iAcc / block
            val q = qAcc / block
            amplitude[b] = hypot(i, q)
            var current = atan2(q, i)
            var delta = current - previousPhase
            while (delta > PI) { current -= 2.0 * PI; delta -= 2.0 * PI }
            while (delta < -PI) { current += 2.0 * PI; delta += 2.0 * PI }
            phaseOffset += current - previousPhase
            phase[b] = phaseOffset
            previousPhase = current
        }

        val meanAmp = amplitude.average().coerceAtLeast(1e-9)
        val ampStd = sqrt(amplitude.map { (it - meanAmp).pow(2) }.average())
        val carrierStability = (1.0 - (ampStd / meanAmp).coerceIn(0.0, 1.0))

        val detrended = detrend(phase)
        val resampled = resampleLinear(detrended, blockRate, 20.0)
        val rrRangeHz = 0.08..0.80

        val spectral = dominantFrequency(resampled, 20.0, rrRangeHz)
        val autocorr = autocorrelationPeak(resampled, 20.0, rrRangeHz)
        val peak = peakFrequency(resampled, 20.0, rrRangeHz)
        val estimates = listOfNotNull(spectral, autocorr, peak)
        val agreement = if (estimates.size >= 2) {
            (estimates.maxOrNull()!! - estimates.minOrNull()!!) * 60.0
        } else 99.0

        val rrHz = when {
            estimates.isEmpty() -> null
            estimates.size == 1 -> estimates.first()
            else -> estimates.sorted().let { sorted ->
                val median = sorted[sorted.lastIndex / 2]
                0.55 * median + 0.25 * (spectral ?: median) + 0.20 * (autocorr ?: median)
            }
        }

        val rr = rrHz?.times(60.0)
        val periodicity = if (autocorr != null) {
            autocorrelationStrength(resampled, 20.0, autocorr)
        } else 0.0

        val snrDb = if (rrHz != null) {
            respiratorySnrDb(resampled, 20.0, rrHz)
        } else -99.0

        val confidence = confidenceFromMetrics(
            snrDb = snrDb,
            periodicity = periodicity,
            motionScore = motionScore,
            carrierStability = carrierStability,
            estimatorAgreementBpm = agreement,
            majorMovement = majorMovement,
        )

        val valid = rr != null &&
            rr in 6.0..45.0 &&
            confidence >= 65 &&
            !majorMovement &&
            agreement <= 4.0

        return SignalAnalysis(
            rrBpm = if (valid) rr else null,
            snrDb = snrDb,
            periodicity = periodicity,
            carrierStability = carrierStability,
            estimatorAgreementBpm = agreement,
            confidence = confidence,
            valid = valid,
        )
    }

    fun carrierStrength(samples: ShortArray, sampleRate: Int, carrierHz: Double): Double {
        if (samples.isEmpty()) return 0.0
        val n = min(samples.size, sampleRate * 2)
        var iAcc = 0.0
        var qAcc = 0.0
        val step = 2.0 * PI * carrierHz / sampleRate
        var phase = 0.0
        for (i in 0 until n) {
            val x = samples[i].toDouble() / Short.MAX_VALUE
            iAcc += x * cos(phase)
            qAcc += x * sin(phase)
            phase += step
            if (phase > 2.0 * PI) phase -= 2.0 * PI
        }
        return hypot(iAcc / n, qAcc / n)
    }

    private fun samplesPerCarrierPeriod(sampleRate: Int, carrierHz: Double): Int {
        val carrierInt = carrierHz.roundToInt().coerceAtLeast(1)
        var a = sampleRate
        var b = carrierInt
        while (b != 0) {
            val tmp = a % b
            a = b
            b = tmp
        }
        return sampleRate / a.coerceAtLeast(1)
    }

    private fun peakFrequency(x: DoubleArray, fs: Double, range: ClosedFloatingPointRange<Double>): Double? {
        if (x.size < 3) return null
        val mean = x.average()
        val std = sqrt(x.map { (it - mean).pow(2) }.average()).coerceAtLeast(1e-9)
        val threshold = mean + 0.35 * std
        val minDistance = ceil(fs / range.endInclusive).toInt().coerceAtLeast(1)
        val peaks = ArrayList<Int>()
        var last = -minDistance
        for (i in 1 until x.lastIndex) {
            if (i - last < minDistance) continue
            if (x[i] > x[i - 1] && x[i] >= x[i + 1] && x[i] >= threshold) {
                peaks += i
                last = i
            }
        }
        if (peaks.size < 2) return null
        val intervals = peaks.zipWithNext { a, b -> b - a }.filter { it > 0 }.sorted()
        if (intervals.isEmpty()) return null
        val median = intervals[intervals.lastIndex / 2].toDouble()
        val f = fs / median
        return if (f in range) f else null
    }

    private fun detrend(x: DoubleArray): DoubleArray {
        val n = x.size
        if (n < 2) return x.copyOf()
        var sx = 0.0
        var sy = 0.0
        var sxx = 0.0
        var sxy = 0.0
        for (i in 0 until n) {
            val xi = i.toDouble()
            sx += xi
            sy += x[i]
            sxx += xi * xi
            sxy += xi * x[i]
        }
        val denom = n * sxx - sx * sx
        val slope = if (abs(denom) > 1e-9) (n * sxy - sx * sy) / denom else 0.0
        val intercept = (sy - slope * sx) / n
        return DoubleArray(n) { x[it] - (intercept + slope * it) }
    }

    private fun resampleLinear(x: DoubleArray, inRate: Double, outRate: Double): DoubleArray {
        val outN = max(1, floor(x.size * outRate / inRate).toInt())
        return DoubleArray(outN) { k ->
            val pos = k * inRate / outRate
            val i0 = floor(pos).toInt().coerceIn(0, x.lastIndex)
            val i1 = min(i0 + 1, x.lastIndex)
            val f = pos - i0
            x[i0] * (1.0 - f) + x[i1] * f
        }
    }

    private fun dominantFrequency(x: DoubleArray, fs: Double, range: ClosedFloatingPointRange<Double>): Double? {
        val minF = range.start
        val maxF = range.endInclusive
        var bestF: Double? = null
        var bestP = Double.NEGATIVE_INFINITY
        val span = maxF - minF
        val steps = 180
        for (s in 0..steps) {
            val f = minF + span * s / steps
            val w = 2.0 * PI * f / fs
            var c = 0.0
            var q = 0.0
            for (i in x.indices) {
                c += x[i] * cos(w * i)
                q += x[i] * sin(w * i)
            }
            val p = c * c + q * q
            if (p > bestP) { bestP = p; bestF = f }
        }
        return bestF
    }

    private fun autocorrelationPeak(x: DoubleArray, fs: Double, range: ClosedFloatingPointRange<Double>): Double? {
        val minLag = ceil(fs / range.endInclusive).toInt()
        val maxLag = floor(fs / range.start).toInt().coerceAtMost(x.size / 2)
        if (maxLag <= minLag) return null

        var bestLag = -1
        var best = Double.NEGATIVE_INFINITY
        for (lag in minLag..maxLag) {
            var sum = 0.0
            var e1 = 0.0
            var e2 = 0.0
            for (i in 0 until (x.size - lag)) {
                val a = x[i]
                val b = x[i + lag]
                sum += a * b
                e1 += a * a
                e2 += b * b
            }
            val corr = sum / sqrt((e1 * e2).coerceAtLeast(1e-12))
            if (corr > best) { best = corr; bestLag = lag }
        }
        return if (bestLag > 0) fs / bestLag else null
    }

    private fun autocorrelationStrength(x: DoubleArray, fs: Double, freq: Double): Double {
        val lag = (fs / freq).roundToInt()
        if (lag <= 0 || lag >= x.size) return 0.0
        var sum = 0.0
        var e1 = 0.0
        var e2 = 0.0
        for (i in 0 until x.size - lag) {
            val a = x[i]
            val b = x[i + lag]
            sum += a * b
            e1 += a * a
            e2 += b * b
        }
        return (sum / sqrt((e1 * e2).coerceAtLeast(1e-12))).coerceIn(0.0, 1.0)
    }

    private fun respiratorySnrDb(x: DoubleArray, fs: Double, f: Double): Double {
        val signal = powerAt(x, fs, f)
        val noiseFreqs = doubleArrayOf(0.10, 0.17, 0.27, 0.37, 0.47, 0.67)
            .filter { abs(it - f) > 0.05 }
        val noise = noiseFreqs.map { powerAt(x, fs, it) }.average().coerceAtLeast(1e-12)
        return 10.0 * log10((signal / noise).coerceAtLeast(1e-12))
    }

    private fun powerAt(x: DoubleArray, fs: Double, f: Double): Double {
        val w = 2.0 * PI * f / fs
        var c = 0.0
        var q = 0.0
        for (i in x.indices) {
            c += x[i] * cos(w * i)
            q += x[i] * sin(w * i)
        }
        return (c * c + q * q) / x.size.coerceAtLeast(1)
    }
}