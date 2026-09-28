package com.ruthwik.pulmoacoustic

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt

internal data class ChestZone(
    val index: Int,
    val name: String,
    val description: String,
    val x: Float,
    val y: Float,
)

internal val chestZones = listOf(
    ChestZone(0, "Upper sternum", "Centre upper chest, below the base of the neck.", 0.50f, 0.18f),
    ChestZone(1, "Upper-left chest", "Person's left upper chest.", 0.30f, 0.28f),
    ChestZone(2, "Upper-right chest", "Person's right upper chest.", 0.70f, 0.28f),
    ChestZone(3, "Mid-left chest", "Person's left chest, around the middle.", 0.30f, 0.49f),
    ChestZone(4, "Mid-right chest", "Person's right chest, around the middle.", 0.70f, 0.49f),
    ChestZone(5, "Lower sternum", "Centre lower sternum, above the upper abdomen.", 0.50f, 0.68f),
)

@Composable
internal fun ChestPlacementGuide(
    selected: Int,
    tested: Set<Int>,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "placementPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.78f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val zone = chestZones[selected.coerceIn(chestZones.indices)]
    val outline = MaterialTheme.colorScheme.onSurface
    val subtle = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    OutlinedCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Where to place the phone", fontWeight = FontWeight.SemiBold)
            Text("Left/right mean the person's left/right. Keep the speaker + microphone side facing the chest.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Box(Modifier.fillMaxWidth().height(315.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val cx = w * 0.50f
                    val torso = Path().apply {
                        moveTo(cx, h * 0.06f)
                        cubicTo(w * 0.38f, h * 0.08f, w * 0.27f, h * 0.14f, w * 0.18f, h * 0.24f)
                        lineTo(w * 0.08f, h * 0.38f)
                        cubicTo(w * 0.13f, h * 0.57f, w * 0.19f, h * 0.76f, w * 0.25f, h * 0.96f)
                        lineTo(w * 0.75f, h * 0.96f)
                        cubicTo(w * 0.81f, h * 0.76f, w * 0.87f, h * 0.57f, w * 0.92f, h * 0.38f)
                        lineTo(w * 0.82f, h * 0.24f)
                        cubicTo(w * 0.73f, h * 0.14f, w * 0.62f, h * 0.08f, cx, h * 0.06f)
                        close()
                    }
                    drawPath(torso, color = subtle, style = Stroke(width = 2.4f, join = StrokeJoin.Round))
                    drawLine(color = subtle, start = Offset(cx, h * 0.14f), end = Offset(cx, h * 0.78f), strokeWidth = 1.8f)
                    for (r in 0..4) {
                        val yy = h * (0.22f + r * 0.095f)
                        drawArc(subtle, 195f, 150f, false, Offset(w * 0.24f, yy), Size(w * 0.52f, h * 0.18f), style = Stroke(width = 1.2f))
                    }
                    chestZones.forEach { z ->
                        val p = Offset(w * z.x, h * z.y)
                        val selectedNow = z.index == zone.index
                        if (selectedNow) drawCircle(accent.copy(alpha = 0.15f), (24f * pulse), p)
                        drawCircle(if (selectedNow) accent else outline, if (selectedNow) 12f else 9f, p, style = Stroke(width = 2.8f))
                        drawCircle(if (selectedNow) accent else outline, 3.5f, p)
                    }
                    val target = Offset(w * zone.x, h * zone.y)
                    val phoneLeft = w * 0.08f
                    val phoneTop = h * 0.69f
                    val phoneW = w * 0.15f
                    val phoneH = h * 0.17f
                    drawRoundRect(outline, Offset(phoneLeft, phoneTop), Size(phoneW, phoneH), CornerRadius(10f, 10f), style = Stroke(width = 2.2f))
                    drawLine(color = accent, start = Offset(phoneLeft + phoneW, phoneTop + phoneH * 0.34f), end = target, strokeWidth = 2.0f, cap = StrokeCap.Round)
                }
                Text("PHONE • SPEAKER + MIC → CHEST", modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = RoundedCornerShape(12.dp), color = surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("0" + (zone.index + 1), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Column {
                        Text(zone.name, fontWeight = FontWeight.SemiBold)
                        Text(zone.description, color = subtle, fontSize = 12.sp)
                    }
                }
            }
            Text(tested.size.toString() + "/" + chestZones.size + " positions tested", color = subtle, fontSize = 11.sp)
        }
    }
}