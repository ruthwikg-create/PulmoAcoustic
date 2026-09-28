package com.ruthwik.pulmoacoustic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ruthwik.pulmoacoustic.audio.AcousticEngine
import com.ruthwik.pulmoacoustic.model.*
import com.ruthwik.pulmoacoustic.sensors.DeviceSensors
import com.ruthwik.pulmoacoustic.signal.RespiratorySignalProcessor
import com.ruthwik.pulmoacoustic.storage.MeasurementStore
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class DatasetInfo(val name: String, val purpose: String, val url: String)

private val datasets = listOf(
    DatasetInfo("BIDMC PPG & Respiration", "Reference respiration and manual breath annotations", "https://physionet.org/content/bidmc/1.0.0/"),
    DatasetInfo("Apnea-ECG", "Sleep-apnea and respiration research", "https://physionet.org/content/apnea-ecg/1.0.0/"),
    DatasetInfo("Sleep-EDF Expanded", "Whole-night PSG including respiration", "https://physionet.org/content/sleep-edfx/1.0.0/"),
    DatasetInfo("CEBSDB", "ECG, respiration and seismocardiography", "https://physionet.org/content/cebsdb/1.0.0/"),
    DatasetInfo("Aeration Respiratory + HR", "Reference respiratory pressure/flow measurements", "https://physionet.org/content/respiratory-heartrate-dataset/1.0.0/"),
    DatasetInfo("Simulated Obstructive Disease", "Obstructive/COPD-style respiratory modelling", "https://physionet.org/content/simulated-obstructive-disease/1.0.0/"),
    DatasetInfo("Respiratory Oximetry Apnoea 2026", "Recent simulated apnea reference data", "https://physionet.org/content/respiratory-oximetry-apnoea/1.0.0/"),
    DatasetInfo("Sleep Heart Health Study PSG", "Large overnight PSG/respiratory reference set", "https://physionet.org/content/shhpsgdb/1.0.0/"),
    DatasetInfo("PEEP Respiratory Dataset", "80 adults; pressure, flow and thoraco-abdominal reference signals", "https://physionet.org/content/respiratory-dataset/1.0.0/"),
    DatasetInfo("Thoraco-Abdominal Circumference + CPAP", "Chest/abdominal motion with pressure and flow", "https://physionet.org/content/pressure-flow-circum-cpap/1.0.0/"),
    DatasetInfo("CPAP Canterbury", "Controlled breathing pressure/flow reference data", "https://physionet.org/content/cpap-data-canterbury/1.0.1/"),
    DatasetInfo("Preterm Cardio-Respiratory Signals", "Respiratory reference signals for later edge-case research", "https://physionet.org/content/?topic=respiratory")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PulmoApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulmoApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { AcousticEngine() }
    val processor = remember { RespiratorySignalProcessor() }
    val sensors = remember { DeviceSensors(context) }
    val store = remember { MeasurementStore(context) }
    val prefs = remember { context.getSharedPreferences("pulmo_settings", 0) }
    val deviceKey = remember { android.os.Build.MANUFACTURER + ":" + android.os.Build.MODEL }

    var tab by remember { mutableStateOf(0) }
    var hasMic by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var carrier by remember { mutableStateOf(prefs.getFloat("carrier_hz", 19_000f).toDouble()) }
    var gain by remember { mutableStateOf(prefs.getFloat("gain", 0.04f)) }
    var calibrated by remember { mutableStateOf(prefs.getString("calibrated_device", "") == deviceKey) }
    var autoOptimize by remember { mutableStateOf(prefs.getBoolean("auto_optimize", true)) }
    var researchCapture by remember { mutableStateOf(prefs.getBoolean("research_capture", false)) }
    var durationSec by remember { mutableStateOf(prefs.getInt("duration_sec", 45).coerceIn(30, 60)) }
    var bestPosition by remember { mutableStateOf("Not scanned") }
    var bestOrientation by remember { mutableStateOf("Not scanned") }
    var status by remember { mutableStateOf("Ready") }
    var progress by remember { mutableStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RespiratoryResult?>(null) }
    val history = remember { mutableStateListOf<RespiratoryResult>() }
    val scanResults = remember { mutableStateListOf<ScanPoint>() }
    val scanPoints = remember {
        listOf("Upper sternum", "Upper-left chest", "Upper-right chest", "Mid-left chest", "Mid-right chest", "Lower sternum")
    }
    var selectedPoint by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        sensors.start()
        history.addAll(store.loadNewest())
        onDispose { sensors.stop() }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasMic = it
        status = if (it) "Microphone permission granted" else "Microphone permission denied"
    }

    fun requestMic(): Boolean {
        if (hasMic) return true
        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        return false
    }

    suspend fun calibrate() {
        if (!requestMic()) return
        busy = true
        try {
            status = "Testing safe carrier frequencies..."
            var best = carrier
            var bestStrength = -1.0
            val candidates = listOf(18_000.0, 19_000.0, 20_000.0)
            for (index in candidates.indices) {
                val f = candidates[index]
                val cap = engine.capture(2, f, gain) { p ->
                    progress = (index + p) / candidates.size.toFloat()
                }
                val strength = processor.carrierStrength(cap.samples, cap.sampleRateHz, f)
                if (strength > bestStrength) {
                    bestStrength = strength
                    best = f
                }
            }
            carrier = best
            calibrated = true
            prefs.edit().putFloat("carrier_hz", best.toFloat()).putFloat("gain", gain).putString("calibrated_device", deviceKey).apply()
            progress = 1f
            status = "Calibration complete. Selected carrier " + best.roundToInt() + " Hz"
        } catch (t: Throwable) {
            status = "Calibration failed on this phone: " + (t.message ?: "audio error")
        } finally {
            busy = false
        }
    }
    suspend fun scan(label: String) {
        if (!requestMic()) return
        busy = true
        try {
            sensors.beginMeasurementSession()
            status = "Hold still at " + label
            progress = 0f
            val cap = engine.capture(12, carrier, gain) { progress = it }
            val motion = sensors.getMeasurementMotionScore()
            val a = processor.analyze(cap.samples, cap.sampleRateHz, carrier, motion, sensors.hasMajorMovement())
            val score = (
                ((a.snrDb + 4.0) / 24.0).coerceIn(0.0, 1.0) * 45.0 +
                a.periodicity * 35.0 +
                a.confidence * 0.20
            )
            val point = ScanPoint(
                label = label,
                qualityScore = score,
                snrDb = a.snrDb,
                periodicity = a.periodicity,
                motionScore = motion,
                pitchDeg = sensors.pitchDegrees(),
                rollDeg = sensors.rollDegrees(),
                yawDeg = sensors.yawDegrees(),
                tested = true
            )
            scanResults.removeAll { it.label == label }
            scanResults.add(point)
            val best = scanResults.maxByOrNull { it.qualityScore }
            bestPosition = best?.label ?: label
            bestOrientation = best?.let {
                "Pitch " + it.pitchDeg.roundToInt() + "°, Roll " + it.rollDeg.roundToInt() + "°, Yaw " + it.yawDeg.roundToInt() + "°"
            } ?: "Not scanned"
            status = "Best position: " + bestPosition
        } catch (t: Throwable) {
            status = "Scan failed: " + (t.message ?: "audio error")
        } finally {
            busy = false
        }
    }
    suspend fun measure() {
        if (!requestMic()) return
        if (autoOptimize && !calibrated) {
            calibrate()
            if (!calibrated) return
        }
        if (autoOptimize && scanResults.isEmpty()) {
            status = "Run the guided chest scan first for best placement."
            tab = 1
            return
        }
        busy = true
        try {
            result = null
            status = "Measuring for " + durationSec + " seconds. Stay still and silent."
            progress = 0f
            sensors.beginMeasurementSession()
            val cap = engine.capture(durationSec, carrier, gain) { progress = it }
            val motion = sensors.getMeasurementMotionScore()
            val a = processor.analyze(cap.samples, cap.sampleRateHz, carrier, motion, sensors.hasMajorMovement())
            val quality = SignalQuality(
                snrDb = a.snrDb,
                periodicity = a.periodicity,
                motionScore = motion,
                carrierStability = a.carrierStability,
                confidence = a.confidence,
                valid = a.valid,
                majorMovementDetected = sensors.hasMajorMovement(),
                estimatorAgreementBpm = a.estimatorAgreementBpm
            )
            val r = RespiratoryResult(
                respiratoryRateBpm = a.rrBpm,
                signalQuality = quality,
                carrierHz = carrier,
                durationSec = durationSec.toDouble(),
                timestampEpochMs = System.currentTimeMillis(),
                message = if (a.valid) "Accepted" else "Rejected: signal not trustworthy"
            )
            if (researchCapture) {
                val dir = java.io.File(context.filesDir, "research-captures")
                com.ruthwik.pulmoacoustic.storage.WavWriter.writeMonoPcm16(
                    java.io.File(dir, "capture_" + r.timestampEpochMs + ".wav"),
                    cap.samples,
                    cap.sampleRateHz
                )
            }
            result = r
            if (a.valid) {
                store.save(r)
                history.add(0, r)
                while (history.size > 30) history.removeLast()
            }
            progress = 1f
            status = if (a.valid) "Measurement accepted" else "No trustworthy result; repeat"
        } catch (t: Throwable) {
            status = "Measurement failed: " + (t.message ?: "audio error")
        } finally {
            busy = false
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("PulmoAcoustic") }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                listOf("Guide", "Scan", "Measure", "History", "Data", "Settings").forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
                }
            }
            when (tab) {
                0 -> Guide()
                1 -> ScanPage(
                    calibrated, carrier, bestPosition, scanPoints, selectedPoint, scanResults,
                    busy, progress, status,
                    onCalibrate = { scope.launch { calibrate() } },
                    onPoint = { selectedPoint = it },
                    onTest = { scope.launch { scan(scanPoints[selectedPoint]) } }
                )
                2 -> MeasurePage(progress, busy, status, bestPosition, bestOrientation, durationSec, result) {
                    scope.launch { measure() }
                }
                3 -> HistoryPage(history)
                4 -> DataPage(context)
                else -> SettingsPage(autoOptimize, researchCapture, durationSec, carrier, gain, calibrated,
                    onAuto = { autoOptimize = it; prefs.edit().putBoolean("auto_optimize", it).apply() },
                    onResearchCapture = { researchCapture = it; prefs.edit().putBoolean("research_capture", it).apply() },
                    onDuration = { durationSec = it; prefs.edit().putInt("duration_sec", it).apply() },
                    onCarrier = { carrier = it; prefs.edit().putFloat("carrier_hz", it.toFloat()).apply() },
                    onGain = { gain = it; prefs.edit().putFloat("gain", it).apply() })
            }
        }
    }
}

@Composable
private fun Guide() {
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("User Guide", style = MaterialTheme.typography.headlineSmall) }
        item { Text("1. Sit upright in a quiet room. For the research prototype, use bare upper-chest skin.") }
        item { Text("2. Allow microphone permission.") }
        item { Text("3. Run Calibration once for the phone.") }
        item { Text("4. Run the Chest Scan. Move the phone to each suggested location; only test after you are steady.") }
        item { Text("5. Use the highest-scoring location. The app records the phone orientation during the best test.") }
        item { Text("6. During measurement keep the phone about 40–60 cm away, keep still, and do not talk.") }
        item { Text("7. Minor motion lowers confidence. Major motion rejects the measurement.") }
        item { Text("8. A result is shown only when multiple signal estimators agree and the quality gate passes.") }
        item { Text("9. This is a research prototype, not a medical device or replacement for clinical respiratory equipment.") }
        item { Text("Working principle", style = MaterialTheme.typography.titleLarge) }
        item { Text("Speaker -> controlled high-frequency acoustic signal -> chest reflection -> microphone -> I/Q demodulation -> respiratory-band analysis -> spectral + autocorrelation agreement -> confidence gate -> respiratory rate.") }
    }
}

@Composable
private fun ScanPage(
    calibrated: Boolean,
    carrier: Double,
    bestPosition: String,
    points: List<String>,
    selected: Int,
    results: List<ScanPoint>,
    busy: Boolean,
    progress: Float,
    status: String,
    onCalibrate: () -> Unit,
    onPoint: (Int) -> Unit,
    onTest: () -> Unit
) {
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Adaptive Chest Scan", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Calibration: " + if (calibrated) "complete" else "required") }
        item { Text("Carrier: " + carrier.roundToInt() + " Hz") }
        item { Button(onClick = onCalibrate, enabled = !busy) { Text("Calibrate device") } }
        item { Text("Best position: " + bestPosition) }
        items(points.indices.toList()) { i ->
            OutlinedButton(onClick = { onPoint(i) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text((if (i == selected) "▶ " else "") + points[i])
            }
        }
        item { Button(onClick = onTest, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("I'm ready - test selected spot") } }
        item { LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()) }
        item { Text(status) }
        items(results) { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text(p.label)
                    Text("Quality " + p.qualityScore.roundToInt() + "/100; SNR " + "%.1f".format(p.snrDb) + " dB; periodicity " + "%.2f".format(p.periodicity))
                }
            }
        }
    }
}

@Composable
private fun MeasurePage(
    progress: Float,
    busy: Boolean,
    status: String,
    bestPosition: String,
    bestOrientation: String,
    durationSec: Int,
    result: RespiratoryResult?,
    onMeasure: () -> Unit
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Respiratory Measurement", style = MaterialTheme.typography.headlineSmall)
        Text("Best location: " + bestPosition)
        Text("Saved phone orientation: " + bestOrientation)
        Text("Duration: " + durationSec + " s • Target distance: about 40–60 cm")
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Button(onClick = onMeasure, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Measuring..." else "Start 30-second measurement")
        }
        Text(status)
        result?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Respiratory rate", style = MaterialTheme.typography.titleLarge)
                    Text(it.respiratoryRateBpm?.let { value -> "%.1f breaths/min".format(value) } ?: "No trustworthy result")
                    Text("Confidence " + it.signalQuality.confidence + "% (" + it.confidenceText + ")")
                    Text("SNR " + "%.1f".format(it.signalQuality.snrDb) + " dB")
                    Text("Periodicty " + "%.2f".format(it.signalQuality.periodicity))
                    Text("Motion " + "%.2f".format(it.signalQuality.motionScore))
                    Text("Estimator agreement " + "%.2f".format(it.signalQuality.estimatorAgreementBpm) + " bpm")
                }
            }
        }
    }
}

@Composable
private fun HistoryPage(history: List<RespiratoryResult>) {
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Local History", style = MaterialTheme.typography.headlineSmall) }
        items(history) { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text(r.respiratoryRateBpm?.let { "%.1f breaths/min".format(it) } ?: "Rejected")
                    Text("Confidence " + r.signalQuality.confidence + "%")
                    Text(java.util.Date(r.timestampEpochMs).toString())
                }
            }
        }
    }
}

@Composable
private fun DataPage(context: android.content.Context) {
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Research Dataset Hub", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Official dataset links are provided for algorithm research. Public physiological data do not replace the paired phone-acoustic dataset needed for final validation.") }
        items(datasets) { d ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text(d.name, style = MaterialTheme.typography.titleMedium)
                    Text(d.purpose)
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(d.url)))
                    }) { Text("Open official source") }
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(
    autoOptimize: Boolean,
    researchCapture: Boolean,
    durationSec: Int,
    carrier: Double,
    gain: Float,
    calibrated: Boolean,
    onAuto: (Boolean) -> Unit,
    onResearchCapture: (Boolean) -> Unit,
    onDuration: (Int) -> Unit,
    onCarrier: (Double) -> Unit,
    onGain: (Float) -> Unit,
) {
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineSmall) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Automatic optimization")
                Switch(checked = autoOptimize, onCheckedChange = onAuto)
            }
        }
        item { Text("Measurement duration: " + durationSec + " seconds") }
        item {
            Slider(
                value = durationSec.toFloat(),
                onValueChange = { onDuration(it.roundToInt().coerceIn(30, 60)) },
                valueRange = 30f..60f,
                steps = 5
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Research raw WAV capture")
                Switch(checked = researchCapture, onCheckedChange = onResearchCapture)
            }
        }
        item { Text("Carrier: " + carrier.roundToInt() + " Hz") }
        item {
            Slider(
                value = carrier.toFloat(),
                onValueChange = { onCarrier((it / 100f).roundToInt() * 100.0) },
                valueRange = 18_000f..20_000f,
                steps = 19
            )
        }
        item { Text("Output gain: " + "%.3f".format(gain) + " (bounded app range)") }
        item {
            Slider(value = gain, onValueChange = onGain, valueRange = 0.015f..0.08f, steps = 12)
        }
        item { Text("Calibration status: " + if (calibrated) "complete for this phone" else "not completed") }
        item { Text("Keep automatic optimization enabled for normal use. Manual carrier/gain controls are for supervised research only.") }
    }
}
