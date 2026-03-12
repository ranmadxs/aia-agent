package cl.tomi.aiaagent.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cl.tomi.aiaagent.data.NetworkDeviceItem
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SonarAnimation(
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
    sweepColor: Color = Color(0xFF3B82F6),
    backgroundColor: Color = Color(0xFF0F172A),
    centerColor: Color = Color(0xFF1E293B),
    cachedDevices: List<NetworkDeviceItem> = emptyList()
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sonar")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val centerX = size.toPx() / 2
            val centerY = size.toPx() / 2
            val radius = size.toPx() / 2
            val sweepAngle = 60f

            // Círculos concéntricos de fondo
            for (i in 1..4) {
                val r = radius * (i / 4f)
                drawCircle(
                    color = backgroundColor.copy(alpha = 0.3f),
                    radius = r,
                    center = Offset(centerX, centerY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                )
            }

            // Sweep del sonar (arco que gira 60°)
            rotate(rotation, pivot = Offset(centerX, centerY)) {
                drawArc(
                    color = sweepColor.copy(alpha = 0.6f),
                    startAngle = 0f,
                    sweepAngle = sweepAngle,
                    useCenter = true,
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(size.toPx(), size.toPx())
                )
            }

            // Puntos de sensores en caché (icono de sensor encontrado)
            val pointRadius = radius * 0.75f
            val pointSize = radius * 0.06f
            val glowSize = radius * 0.12f
            val count = cachedDevices.size.coerceAtLeast(1)
            for (i in cachedDevices.indices) {
                val angleDeg = (i.toFloat() / count) * 360f
                val drawScopeAngle = (270f - angleDeg + 360f) % 360f
                val angleRad = drawScopeAngle * (PI / 180f).toFloat()
                val px = centerX + pointRadius * cos(angleRad)
                val py = centerY + pointRadius * sin(angleRad)
                val pointOffset = Offset(px, py)

                val sweepEnd = (rotation + sweepAngle) % 360f
                val sweepStart = rotation
                val inSweep = when {
                    sweepEnd > sweepStart -> drawScopeAngle in sweepStart..sweepEnd
                    else -> drawScopeAngle >= sweepStart || drawScopeAngle < sweepEnd
                }

                if (inSweep) {
                    drawCircle(
                        color = sweepColor.copy(alpha = 0.5f),
                        radius = glowSize,
                        center = pointOffset
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f),
                        radius = glowSize * 0.6f,
                        center = pointOffset
                    )
                }
                drawCircle(
                    color = if (inSweep) Color.White else sweepColor,
                    radius = pointSize,
                    center = pointOffset
                )
            }

            // Centro sólido
            drawCircle(
                color = centerColor,
                radius = radius * 0.12f,
                center = Offset(centerX, centerY)
            )
            drawCircle(
                color = sweepColor.copy(alpha = 0.5f),
                radius = radius * 0.08f,
                center = Offset(centerX, centerY)
            )
        }
    }
}
