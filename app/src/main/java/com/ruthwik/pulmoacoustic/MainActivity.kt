package com.ruthwik.pulmoacoustic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ruthwik.pulmoacoustic.audio.AcousticEngine
import com.ruthwik.pulmoacoustic.model.*
import com.ruthwik.pulmoacoustic.sensors.DeviceSensors
import com.ruthwik.pulmoacoustic.signal.RespiratorySignalProcessor
import com.ruthwik.pulmoacoustic.storage.MeasurementStore
import com.ruthwik.pulmoacoustic.ui.theme.PulmoTheme
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private enum class Screen(val label: String) {
    HOME("Home"), MEASURE("Measure"), HISTORY("History"), RESEARCH("Research"), SETTINGS("Settings"), GUIDE("Guide"), SCAN("Scan")
}

private data class DatasetInfo(
    val name: String,
    val purpose: String,
    val role: String,
    val access: String,
    val url: String
)

private val datasets = listOf(
    DatasetInfo("BIDMC PPG & Respiration", "Impedance respiration, PPG and manual breath annotations.", "Ground truth", "Open", "https://physionet.org/content/bidmc/1.0.0/"),
    DatasetInfo("BIDMC 32-second RR", "7,949 short RR windows derived from BIDMC.", "Benchmark", "Open", "https://zenodo.org/records/4001463"),
    DatasetInfo("BreathMY v2", "2,550 clean respiratory audio signals plus controlled and environmental noise variants.", "Audio / auxiliary", "CC BY-NC 4.0", "https://zenodo.org/records/18964276"),
    DatasetInfo("Apnea-ECG", "Sleep-apnea signals with respiration annotations.", "Sleep / apnea", "Open", "https://physionet.org/content/apnea-ecg/1.0.0/"),
    DatasetInfo("Sleep-EDF Expanded", "Whole-night PSG including respiratory channels.", "Sleep / respiration", "Open", "https://physionet.org/content/sleep-edfx/1.0.0/"),
    DatasetInfo("MIT-BIH Polysomnographic", "Multi-channel sleep recordings with apnea and respiration annotations.", "Sleep / apnea", "ODC-By", "https://physionet.org/content/slpdb/1.0.0/"),
    DatasetInfo("CAP Sleep Database", "108 polysomnographic recordings with airflow, thoracic/abdominal effort and SaO2.", "Sleep / respiration", "Open terms", "https://physionet.org/content/capslpdb/1.0.0/"),
    DatasetInfo("Sleep Heart Health Study PSG", "Large overnight PSG reference data for sleep and respiratory analysis.", "Sleep / respiration", "Source terms", "https://physionet.org/content/shhpsgdb/1.0.0/"),
    DatasetInfo("CEBSDB", "ECG, respiration and seismocardiography from 20 volunteers.", "Reference physiology", "Open", "https://physionet.org/content/cebsdb/1.0.0/"),
    DatasetInfo("Aeration Respiratory + HR", "Pressure, flow, aeration and heart-rate data from controlled trials.", "Ground truth", "Open", "https://physionet.org/content/respiratory-heartrate-dataset/1.0.0/"),
    DatasetInfo("PEEP Respiratory Dataset", "Pressure, flow, volume, thoraco-abdominal circumference and EIT.", "Pulmonary mechanics", "Open", "https://physionet.org/content/respiratory-dataset/1.0.0/"),
    DatasetInfo("Thoraco-Abdominal CPAP", "Dynamic chest/abdomen circumference with pressure and flow.", "Chest motion", "Open", "https://physionet.org/content/pressure-flow-circum-cpap/1.0.0/"),
    DatasetInfo("CPAP Canterbury", "Controlled respiratory pressure/flow data.", "Pulmonary mechanics", "Open", "https://physionet.org/content/cpap-data-canterbury/1.0.1/"),
    DatasetInfo("Simulated Obstructive Disease", "Controlled obstructive-respiratory pressure/flow signals.", "Obstructive model", "Open", "https://physionet.org/content/simulated-obstructive-disease/1.0.0/"),
    DatasetInfo("Respiratory Oximetry Apnoea 2026", "Airway pressure, flow and pulse-oximetry during simulated apnea.", "Apnea / reference", "Open", "https://physionet.org/content/respiratory-oximetry-apnoea/1.0.0/"),
    DatasetInfo("Comprehensive PSG Sleep", "PSG with respiratory effort, apnoea, desaturation and cardiac events.", "Sleep / events", "Open", "https://physionet.org/content/cps-dataset-sleep/1.0.0/"),
    DatasetInfo("MMWave Breathing + Heart", "Breathing and heart waveform data from non-contact sensing.", "Auxiliary non-contact", "Zenodo", "https://zenodo.org/records/7086410"),
    DatasetInfo("MIMIC-III Waveform Matched Subset", "Large critical-care waveform set with respiratory/cardiorespiratory signals.", "Auxiliary / ICU", "Credentialed", "https://physionet.org/content/mimic3wdb-matched/1.0.0/"),
    DatasetInfo("PhysioNet Respiratory Topic Index", "Discovery hub for additional current respiratory resources.", "Discovery hub", "Directory", "https://physionet.org/content/?topic=respiratory")
)

private data class NavItem(val screen: Screen, val icon: ImageVector)

private val navItems = listOf(
    NavItem(Screen.HOME, Icons.Filled.Home),
    NavItem(Screen.MEASURE, Icons.Filled.Assessment),
    NavItem(Screen.HISTORY, Icons.Filled.History),
    NavItem(Screen.RESEARCH, Icons.Filled.Dataset),
    NavItem(Screen.SETTINGS, Icons.Filled.Settings)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PulmoTheme(darkTheme = isSystemInDarkTheme()) { PulmoApp() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PulmoApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { AcousticEngine() }
    val processor = remember { RespiratorySignalProcessor() }
    val sensors = remember { DeviceSensors(context) }
    val store = remember { MeasurementStore(context) }
    val prefs = remember { context.getSharedPreferences("pulmo_settings", 0) }
    val deviceKey = remember { android.os.Build.MANUFACTURER + ":" + android.os.Build.MODEL }

    var screen by remember { mutableStateOf(Screen.HOME) }
    var hasMic by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var carrier by remember { mutableStateOf(prefs.getFloat("carrier_hz", 19000f).toDouble()) }
    var gain by remember { mutableStateOf(prefs.getFloat("gain", 0.04f)) }
    var calibrated by remember { mutableStateOf(prefs.getString("calibrated_device", "") == deviceKey) }
    var autoOptimize by remember { mutableStateOf(prefs.getBoolean("auto_optimize", true)) }
    var researchCapture by remember { mutableStateOf(prefs.getBoolean("research_capture", false)) }
    var durationSec by remember { mutableStateOf(prefs.getInt("duration_sec", 45).coerceIn(30, 60)) }
    var bestPosition by remember { mutableStateOf("Not scanned") }
    var bestOrientation by remember { mutableStateOf("Not scanned") }
    var status by remember { mutableStateOf("Ready for setup") }
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
        status = if (it) "Microphone ready" else "Microphone permission is required"
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
            status = "Calibrating this phone..."
            var best = carrier
            var bestStrength = -1.0
            val candidates = listOf(18000.0, 19000.0, 20000.0)
            for (index in candidates.indices) {
                val f = candidates[index]
                val cap = engine.capture(2, f, gain) { p -> progress = (index + p) / candidates.size.toFloat() }
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
            status = "Calibration complete • " + best.roundToInt() + " Hz selected"
        } catch (t: Throwable) {
            status = "Calibration failed • " + (t.message ?: "audio error")
        } finally {
            busy = false
        }
    }

    suspend fun scan(label: String) {
        if (!requestMic()) return
        busy = true
        try {
            sensors.beginMeasurementSession()
            status = "Testing " + label + " • stay still"
            progress = 0f
            val cap = engine.capture(12, carrier, gain) { progress = it }
            val motion = sensors.getMeasurementMotionScore()
            val a = processor.analyze(cap.samples, cap.sampleRateHz, carrier, motion, sensors.hasMajorMovement())
            val score = ((a.snrDb + 4.0) / 24.0).coerceIn(0.0, 1.0) * 45.0 + a.periodicity * 35.0 + a.confidence * 0.20
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
                "Pitch " + it.pitchDeg.roundToInt() + "° • Roll " + it.rollDeg.roundToInt() + "° • Yaw " + it.yawDeg.roundToInt() + "°"
            } ?: "Not scanned"
            status = "Best location • " + bestPosition
        } catch (t: Throwable) {
            status = "Scan failed • " + (t.message ?: "audio error")
        } finally {
            busy = false
        }
    }

    suspend fun measure() {
        if (!requestMic()) return
        if (autoOptimize && !calibrated) {
            screen = Screen.SETTINGS
            calibrate()
            if (!calibrated) return
        }
        if (autoOptimize && scanResults.isEmpty()) {
            screen = Screen.SCAN
            status = "Complete the guided chest scan first."
            return
        }
        busy = true
        try {
            result = null
            status = "Measuring • stay still and breathe normally"
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
                com.ruthwik.pulmoacoustic.storage.WavWriter.writeMonoPcm16(java.io.File(dir, "capture_" + r.timestampEpochMs + ".wav"), cap.samples, cap.sampleRateHz)
            }
            result = r
            if (a.valid) {
                store.save(r)
                history.add(0, r)
                while (history.size > 30) history.removeLast()
            }
            progress = 1f
            status = if (a.valid) "Measurement accepted" else "No trustworthy result • repeat"
        } catch (t: Throwable) {
            status = "Measurement failed • " + (t.message ?: "audio error")
        } finally {
            busy = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(
                                if (isSystemInDarkTheme()) R.drawable.ic_pulmo_logo_white else R.drawable.ic_pulmo_logo
                            ),
                            contentDescription = "PulmoAcoustic",
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Pulmo", fontWeight = FontWeight.SemiBold)
                        Text("Acoustic", fontWeight = FontWeight.Normal)
                    }
                },
                actions = {
                    IconButton(onClick = { screen = Screen.GUIDE }) {
                        Icon(Icons.Filled.HelpOutline, contentDescription = "Guide")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (screen != Screen.GUIDE && screen != Screen.SCAN) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    navItems.forEach { item ->
                        NavigationBarItem(
                            selected = screen == item.screen,
                            onClick = { screen = item.screen },
                            icon = { Icon(item.icon, contentDescription = item.screen.label) },
                            label = { Text(item.screen.label, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                Screen.HOME -> HomeScreen(history, calibrated, bestPosition, hasMic, onPrimary = { screen = Screen.MEASURE }, onCalibrate = { scope.launch { calibrate() } }, onScan = { screen = Screen.SCAN }, onGuide = { screen = Screen.GUIDE })
                Screen.MEASURE -> MeasureScreen(progress, busy, status, bestPosition, bestOrientation, durationSec, result, onMeasure = { scope.launch { measure() } }, onScan = { screen = Screen.SCAN })
                Screen.HISTORY -> HistoryScreen(history)
                Screen.RESEARCH -> ResearchScreen(context)
                Screen.SETTINGS -> SettingsScreen(autoOptimize, researchCapture, durationSec, carrier, gain, calibrated, onAuto = { autoOptimize = it; prefs.edit().putBoolean("auto_optimize", it).apply() }, onResearchCapture = { researchCapture = it; prefs.edit().putBoolean("research_capture", it).apply() }, onDuration = { durationSec = it; prefs.edit().putInt("duration_sec", it).apply() }, onCarrier = { carrier = it; prefs.edit().putFloat("carrier_hz", it.toFloat()).apply() }, onGain = { gain = it; prefs.edit().putFloat("gain", it).apply() }, onCalibrate = { scope.launch { calibrate() } })
                Screen.GUIDE -> GuideScreen { screen = Screen.HOME }
                Screen.SCAN -> ScanScreen(calibrated, carrier, bestPosition, scanPoints, selectedPoint, scanResults, busy, progress, status, onCalibrate = { scope.launch { calibrate() } }, onPoint = { selectedPoint = it }, onTest = { scope.launch { scan(scanPoints[selectedPoint]) } }, onBack = { screen = Screen.HOME })
            }
        }
    }
}

@Composable
private fun HomeScreen(
    history: List<RespiratoryResult>,
    calibrated: Boolean,
    bestPosition: String,
    hasMic: Boolean,
    onPrimary: () -> Unit,
    onCalibrate: () -> Unit,
    onScan: () -> Unit,
    onGuide: () -> Unit
) {
    val last = history.firstOrNull()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("Contactless respiratory monitoring", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Phone-only sensing using your built-in speaker + microphone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Research measurement", fontWeight = FontWeight.Medium)
                    }
                    Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground), shape = RoundedCornerShape(16.dp)) {
                        Text("Start measurement", fontWeight = FontWeight.SemiBold)
                    }
                    Text("Automatic calibration • chest scan • motion rejection • confidence gate", color = MaterialTheme.colorScheme.background.copy(alpha = 0.70f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text("Setup status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item { StatusCard("Microphone", if (hasMic) "Ready" else "Permission required", Icons.Filled.Mic, hasMic) }
        item { StatusCard("Phone calibration", if (calibrated) "Complete" else "Not calibrated", Icons.Filled.Tune, calibrated) }
        item { StatusCard("Chest position", if (bestPosition != "Not scanned") bestPosition else "Scan required", Icons.Filled.LocationOn, bestPosition != "Not scanned") }
        item {
            OutlinedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Adaptive setup", fontWeight = FontWeight.SemiBold)
                    }
                    Text("The app searches safe acoustic settings and ranks chest positions by signal quality before measuring.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = onCalibrate, label = { Text("Calibrate") })
                        AssistChip(onClick = onScan, label = { Text("Chest scan") })
                    }
                }
            }
        }
        if (last != null) {
            item { Text("Latest accepted measurement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item { ResultSummaryCard(last) }
        }
        item {
            OutlinedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Best-practice setup", fontWeight = FontWeight.SemiBold)
                    }
                    Text("Quiet room • seated • bare upper chest for the initial protocol • phone unobstructed • stay still.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onGuide) { Text("Open full guide") }
                }
            }
        }
        item {
            Text("Research prototype • not a diagnostic device", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatusCard(title: String, value: String, icon: ImageVector, good: Boolean) {
    OutlinedCard {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Medium)
                    Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
            AssistChip(onClick = {}, label = { Text(if (good) "Ready" else "Action") }, leadingIcon = { Icon(if (good) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber, contentDescription = null, modifier = Modifier.size(18.dp)) })
        }
    }
}

@Composable
private fun ResultSummaryCard(result: RespiratoryResult) {
    Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Respiratory rate", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(result.respiratoryRateBpm?.let { String.format(Locale.US, "%.1f", it) } ?: "—", fontSize = 38.sp, fontWeight = FontWeight.SemiBold)
            Text("breaths / minute")
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricCell("Confidence", result.signalQuality.confidence.toString() + "%")
                MetricCell("SNR", String.format(Locale.US, "%.1f dB", result.signalQuality.snrDb))
                MetricCell("Motion", String.format(Locale.US, "%.2f", result.signalQuality.motionScore))
            }
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MeasureScreen(
    progress: Float,
    busy: Boolean,
    status: String,
    bestPosition: String,
    bestOrientation: String,
    durationSec: Int,
    result: RespiratoryResult?,
    onMeasure: () -> Unit,
    onScan: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("Measurement", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("One clean session is better than a forced number.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
                        CircularProgressIndicator(progress = { if (busy) progress else 1f }, modifier = Modifier.fillMaxSize(), strokeWidth = 8.dp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (busy) (progress * durationSec).roundToInt().toString() + " s" else "Ready", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (busy) "acquiring" else durationSec.toString() + " s", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(status, textAlign = TextAlign.Center)
                    Button(onClick = onMeasure, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (busy) "Measuring..." else "Start measurement")
                    }
                    OutlinedButton(onClick = onScan, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Tune, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Review chest position")
                    }
                }
            }
        }
        item { Text("Current setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item { SetupSummaryCard(bestPosition, bestOrientation, durationSec) }
        result?.let {
            item { Text("Latest session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item { ResultSummaryCard(it) }
        }
    }
}

@Composable
private fun SetupSummaryCard(bestPosition: String, bestOrientation: String, durationSec: Int) {
    OutlinedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Best chest location", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(bestPosition, fontWeight = FontWeight.Medium)
            Text("Phone orientation", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(bestOrientation, fontWeight = FontWeight.Medium)
            Text("Target distance", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("About 40–60 cm • standardized research setup")
            Text("Duration", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(durationSec.toString() + " seconds", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ScanScreen(
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
    onTest: () -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                Column {
                    Text("Guided chest scan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("Find the cleanest acoustic response.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            OutlinedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = {}, label = { Text(if (calibrated) "Calibrated" else "Calibrate first") })
                        AssistChip(onClick = {}, label = { Text(carrier.roundToInt().toString() + " Hz") })
                    }
                    Text("Best location: " + bestPosition, fontWeight = FontWeight.SemiBold)
                    Text("Move to a point, hold steady, then test. Repositioning motion is not treated as the point's respiratory signal.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onCalibrate, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Tune, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Calibrate device")
                    }
                }
            }
        }
        item { Text("Chest points", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        items(points.indices.toList()) { i ->
            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, border = if (selected == i) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text((i + 1).toString(), fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(points[i], fontWeight = FontWeight.Medium)
                        val tested = results.firstOrNull { it.label == points[i] }
                        Text(
                            tested?.let {
                                "Quality " + it.qualityScore.roundToInt() + "/100 • SNR " + String.format(Locale.US, "%.1f", it.snrDb) + " dB"
                            } ?: "Not tested yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    if (selected == i) Icon(Icons.Filled.RadioButtonChecked, contentDescription = "Selected")
                }
            }
        }
        item {
            Button(onClick = onTest, enabled = !busy && calibrated, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Text(if (busy) "Testing selected point..." else "I'm ready — test selected point")
            }
        }
        item {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HistoryScreen(history: List<RespiratoryResult>) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Accepted local measurements only.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (history.isEmpty()) {
            item { EmptyState(Icons.Filled.History, "No accepted measurements yet", "Complete calibration, chest scanning and one clean measurement to start building your baseline.") }
        } else {
            items(history) { r ->
                Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.respiratoryRateBpm?.let { String.format(Locale.US, "%.1f breaths/min", it) } ?: "Rejected", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Text(java.util.Date(r.timestampEpochMs).toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(r.signalQuality.confidence.toString() + "%")
                            Text("confidence", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResearchScreen(context: android.content.Context) {
    var filter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Ground truth", "Sleep", "Audio", "Mechanics", "Auxiliary")
    val filtered = when (filter) {
        "Ground truth" -> datasets.filter { it.role.contains("Ground", true) || it.role.contains("Reference", true) }
        "Sleep" -> datasets.filter { it.role.contains("Sleep", true) || it.role.contains("Apnea", true) }
        "Audio" -> datasets.filter { it.role.contains("Audio", true) }
        "Mechanics" -> datasets.filter { it.role.contains("Mechanics", true) || it.role.contains("Pulmonary", true) || it.role.contains("Chest", true) }
        "Auxiliary" -> datasets.filter { it.role.contains("Auxiliary", true) }
        else -> datasets
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("Research data", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(datasets.size.toString() + " curated sources • reference physiology + audio + non-contact data", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEach { item -> FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item) }) }
            }
        }
        item {
            OutlinedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Biotech, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Accuracy strategy", fontWeight = FontWeight.SemiBold)
                    }
                    Text("Public datasets supply physiology, labels and robustness cases. Final phone-acoustic accuracy still requires paired recordings from this app against a reference respiratory signal.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(filtered) { d ->
            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        AssistChip(onClick = {}, label = { Text(d.access) })
                    }
                    Text(d.purpose, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Use: " + d.role, fontSize = 12.sp)
                    OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(d.url))) }) { Text("Open source") }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
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
    onCalibrate: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Automatic optimization should remain on for normal use.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { SettingToggleCard(Icons.Filled.AutoAwesome, "Automatic optimization", "Select a strong supported acoustic configuration.", autoOptimize, onAuto) }
        item {
            OutlinedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Measurement duration", fontWeight = FontWeight.SemiBold)
                    Text(durationSec.toString() + " seconds", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = durationSec.toFloat(), onValueChange = { onDuration(it.roundToInt().coerceIn(30, 60)) }, valueRange = 30f..60f, steps = 5)
                }
            }
        }
        item { SettingToggleCard(Icons.Filled.Biotech, "Research raw audio", "Save the raw capture locally for supervised validation.", researchCapture, onResearchCapture) }
        item {
            OutlinedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Device calibration", fontWeight = FontWeight.SemiBold)
                    Text(if (calibrated) "Calibrated for this phone" else "Calibration required", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onCalibrate, modifier = Modifier.fillMaxWidth()) { Text("Run calibration again") }
                }
            }
        }
        item {
            OutlinedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Advanced research controls", fontWeight = FontWeight.SemiBold)
                    Text("Carrier " + carrier.roundToInt() + " Hz", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = carrier.toFloat(), onValueChange = { onCarrier((it / 100f).roundToInt() * 100.0) }, valueRange = 18000f..20000f, steps = 19)
                    Text("Output gain " + String.format(Locale.US, "%.3f", gain), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = gain, onValueChange = onGain, valueRange = 0.015f..0.08f, steps = 12)
                    Text("Manual controls are bounded and intended for supervised research only.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
        item {
            OutlinedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("How the measurement works", fontWeight = FontWeight.SemiBold)
                    }
                    Text("Controlled acoustic carrier → chest reflection → microphone → I/Q demodulation → three RR estimators → confidence gate.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SettingToggleCard(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    OutlinedCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun GuideScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text("How to get the best result", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GuideStep("01", "Quiet setup", "Sit upright in a quiet room. Use bare upper chest for the initial research protocol and keep the phone unobstructed.")
            GuideStep("02", "Calibrate the phone", "Run calibration after changing phones. The app tests a bounded 18–20 kHz range.")
            GuideStep("03", "Scan the chest", "Move to each guided chest point, hold steady, then run the point test. The app ranks locations by signal quality.")
            GuideStep("04", "Measure", "Use the best point and saved orientation. Keep roughly 40–60 cm away and remain still and silent.")
            GuideStep("05", "Trust the gate", "The app compares spectral, autocorrelation and time-domain estimates. Poor agreement or high motion can reject the result.")
            GuideStep("06", "Validate", "Compare accepted phone measurements against a reference respiratory device before making accuracy claims.")
            OutlinedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Important", fontWeight = FontWeight.SemiBold)
                    Text("PulmoAcoustic is a research prototype and not a diagnostic device or replacement for medical-grade equipment.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun GuideStep(number: String, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.background) {
            Text(number, modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, body: String) {
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp))
            Text(title, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
