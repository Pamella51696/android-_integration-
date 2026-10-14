package com.edag.driver_view_3d_model.fluidlevels

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edag.seethroughapplication.fluidlevels.FluidLevelData
import com.edag.seethroughapplication.fluidlevels.FluidReservoir
import com.edag.seethroughapplication.fluidlevels.FluidStatus
import com.edag.seethroughapplication.fluidlevels.FluidSymbol
import com.edag.seethroughapplication.fluidlevels.fluidAccentColor
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private val CardBackground = Color(0xF2101826)
private val MutedText = Color(0xFF9AA7BC)

private fun statusColor(status: FluidStatus): Color = when (status) {
    FluidStatus.OK -> Color(0xFF3DDC84)
    FluidStatus.LOW -> Color(0xFFFFB300)
    FluidStatus.CRITICAL -> Color(0xFFFF5252)
}

private fun statusText(status: FluidStatus): String = when (status) {
    FluidStatus.OK -> "Level OK"
    FluidStatus.LOW -> "Getting low"
    FluidStatus.CRITICAL -> "Refill needed"
}

/**
 * Card shown after tapping a reservoir: animated tank gauge, the current
 * level in percent, and a status chip.
 */
@Composable
internal fun FluidInfoCard(
    reservoir: FluidReservoir,
    modifier: Modifier = Modifier
) {
    val accent = fluidAccentColor(reservoir.kind)
    val status = FluidLevelData.statusOf(reservoir.levelPercent)
    val target = (reservoir.levelPercent / 100f).coerceIn(0f, 1f)

    // Fills up from empty to the current level each time the card opens.
    val progress = remember(reservoir.kind) { Animatable(0f) }
    LaunchedEffect(reservoir.kind) {
        progress.animateTo(target, animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    val shownPercent = (progress.value * 100f).roundToInt()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(CardBackground)
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TankGauge(
            fraction = progress.value,
            color = accent,
            modifier = Modifier
                .width(62.dp)
                .height(108.dp)
        )

        Spacer(modifier = Modifier.width(18.dp))

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                FluidSymbol(kind = reservoir.kind, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = reservoir.title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = reservoir.description,
                color = MutedText,
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "$shownPercent",
                    color = accent,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "%",
                    color = accent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 2.dp, bottom = 9.dp)
                )
            }

            Text(
                text = "Current level",
                color = MutedText,
                fontSize = 12.sp
            )

            Text(
                text = statusText(status),
                color = statusColor(status),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(statusColor(status).copy(alpha = 0.16f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )

            Text(
                text = "Tap anywhere to close",
                color = MutedText.copy(alpha = 0.8f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** Vertical tank with a gently moving liquid surface. */
@Composable
private fun TankGauge(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "tankWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "tankWavePhase"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val corner = w * 0.28f

        val tank = Path().apply {
            addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(corner, corner)))
        }

        // empty tank
        drawPath(path = tank, color = Color.White.copy(alpha = 0.07f))

        // liquid
        val levelY = h * (1f - fraction.coerceIn(0f, 1f))
        clipPath(tank) {
            val amplitude = if (fraction <= 0.01f) 0f else h * 0.022f
            val liquid = Path().apply {
                moveTo(0f, h)
                lineTo(0f, levelY)
                var x = 0f
                while (x <= w) {
                    val y = levelY + amplitude * sin((x / w) * 2.0 * PI * 1.4 + phase).toFloat()
                    lineTo(x, y)
                    x += 2f
                }
                lineTo(w, h)
                close()
            }
            drawPath(
                path = liquid,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.5f)),
                    startY = levelY,
                    endY = h
                )
            )

            // scale marks at 25 / 50 / 75 %
            for (mark in listOf(0.25f, 0.5f, 0.75f)) {
                val y = h * (1f - mark)
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(w * 0.68f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.5f
                )
            }
        }

        // outline
        drawPath(
            path = tank,
            color = color.copy(alpha = 0.9f),
            style = Stroke(width = 2.5f)
        )
    }
}
