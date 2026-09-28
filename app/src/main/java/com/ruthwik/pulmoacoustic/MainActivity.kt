package com.ruthwik.pulmoacoustic

import android.Manifest
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ruthwik.pulmoacoustic.audio.CardiacAudioEngine
import com.ruthwik.pulmoacoustic.model.CardiacReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var engine: CardiacAudioEngine
    private val deviceName = mutableStateOf("Checking headset…")
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { deviceName.value = engine.deviceLabel() }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { deviceName.value = engine.deviceLabel() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = CardiacAudioEngine(this)
        deviceName.value = engine.deviceLabel()
        engine.registerDeviceCallback(deviceCallback)
        setContent { CardioSonicTheme { CardioSonicScreen(engine, deviceName.value) } }
    }

    override fun onDestroy() {
        engine.unregisterDeviceCallback(deviceCallback)
        engine.stop()
        super.onDestroy()
    }
}

@Composable
private fun CardioSonicScreen(engine: CardiacAudioEngine, initialDevice: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var waveform by remember { mutableStateOf(FloatArray(420)) }
    var reading by remember { mutableStateOf(CardiacReading(status = "Ready")) }
    var running by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val ok = startMeasurement(engine, scope, { waveform = it.first; reading = it.second }, { error = it })
            running = ok
        } else error = "Microphone permission was denied."
    }

    LaunchedEffect(running) {
        if (running) while (true) { delay(1000); seconds++ }
    }
    DisposableEffect(Unit) { onDispose { engine.stop() } }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        1f, 1.07f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "scale"
    )

    Surface(color = Color(0xFF070A12), modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF4F73), modifier = Modifier.size((34 * pulse).dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("CardioSonic", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Real-time acoustic cardiac monitor", color = Color(0xFF9DA8BD))
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Headphones, null, tint = Color(0xFF6EE7F9))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Headset input", color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(initialDevice, color = Color(0xFF9DA8BD))
                            }
                            AssistChip(onClick = {}, label = { Text(if (initialDevice.contains("headset", true)) "READY" else "CONNECT") })
                        }
                        Text(
                            "Connect a wired headset with an inline microphone. Place the microphone/chest-contact head over the cardiac area. The earbud speaker is output only; the microphone is the sensing element.",
                            color = Color(0xFFB9C3D5), style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1322)), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("HEART RATE", color = Color(0xFF93A4BF), style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.weight(1f))
                            Text("%02d:%02d".format(seconds / 60, seconds % 60), color = Color(0xFF93A4BF))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(reading.heartRateBpm?.toString() ?: "--", style = MaterialTheme.typography.displayLarge, color = Color.White, fontWeight = FontWeight.Black)
                            Spacer(Modifier.width(8.dp))
                            Text("BPM", color = Color(0xFF93A4BF), fontWeight = FontWeight.Bold)
                        }
                        Text(reading.status, color = Color(0xFF6EE7F9))
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1322)), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, null, tint = Color(0xFF8B9CFF))
                            Spacer(Modifier.width(8.dp))
                            Text("Live filtered phonocardiogram", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(10.dp))
                        WaveformView(waveform, Modifier.fillMaxWidth().height(190.dp))
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("DC removal", color = Color(0xFF8794AB), style = MaterialTheme.typography.labelSmall)
                            Text("HP 20 Hz", color = Color(0xFF8794AB), style = MaterialTheme.typography.labelSmall)
                            Text("LP 120 Hz", color = Color(0xFF8794AB), style = MaterialTheme.typography.labelSmall)
                            Text("50/60 Hz", color = Color(0xFF8794AB), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard("QUALITY", "${reading.signalQuality}%", Modifier.weight(1f))
                    MetricCard("CONF.", "${reading.confidence}%", Modifier.weight(1f))
                    MetricCard("SNR", "${"%.1f".format(reading.snrDb)} dB", Modifier.weight(1f))
                }
            }
            item {
                Button(
                    onClick = {
                        error = null
                        if (running) {
                            engine.stop()
                            running = false
                        } else {
                            seconds = 0
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                permission.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                running = startMeasurement(engine, scope, { waveform = it.first; reading = it.second }, { error = it })
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (running) Color(0xFFB4233F) else Color(0xFF2E5BFF))
                ) {
                    Icon(if (running) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(10.dp))
                    Text(if (running) "STOP MEASUREMENT" else "START MEASUREMENT", fontWeight = FontWeight.Bold)
                }
            }
            item {
                if (error != null) Text(error!!, color = Color(0xFFFF7A90))
                Text("Research prototype • Not a medical diagnostic device", color = Color(0xFF65728A), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun startMeasurement(
    engine: CardiacAudioEngine,
    scope: CoroutineScope,
    update: (Pair<FloatArray, CardiacReading>) -> Unit,
    onError: (String) -> Unit
): Boolean {
    return engine.start(scope) { wave, read -> update(wave to read) }
        .onFailure { onError(it.message ?: "Unable to start measurement") }
        .isSuccess
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, color = Color(0xFF7F8CA4), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WaveformView(samples: FloatArray, modifier: Modifier) {
    Canvas(modifier.background(Color(0xFF060B14), RoundedCornerShape(16.dp))) {
        val mid = size.height / 2f
        val path = Path()
        val n = samples.size.coerceAtLeast(2)
        for (i in samples.indices) {
            val x = i * size.width / (n - 1).toFloat()
            val y = mid - samples[i] * size.height * 0.40f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        for (i in 1..4) drawLine(Color(0x223A4A66), Offset(0f, size.height * i / 5f), Offset(size.width, size.height * i / 5f), 1f)
        drawPath(path, Color(0xFF63E6BE), strokeWidth = 3f)
    }
}

@Composable
private fun CardioSonicTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF2E5BFF), secondary = Color(0xFF63E6BE)), content = content)
}
