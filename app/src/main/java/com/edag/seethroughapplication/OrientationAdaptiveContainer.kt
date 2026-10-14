package com.edag.seethroughapplication

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.edag.seethroughapplication.phonePosition.PhonePosition
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * NEW (additive) - makes everything placed inside it (panorama footage, 3D model,
 * touch components and info card) follow the physical orientation of the phone.
 *
 * The Activity stays locked to portrait, so the system never rotates/resizes anything.
 * Instead the phone's roll (same sensor value already published by PhonePosition) is
 * snapped to the nearest 0 / 90 / 180 / -90 degrees and the content is rotated the
 * OPPOSITE way, so the user always sees an upright view:
 *   - phone turned left  -> content turned right
 *   - phone turned right -> content turned left
 *   - phone upside down  -> content turned 180 degrees
 *
 * For the 90 degree cases the content is laid out with width/height swapped, so it
 * still fills the whole screen after being rotated. Touches are rotated by Compose
 * together with the content, so tapping the 3D components still works.
 */
@Composable
fun OrientationAdaptiveContainer(
    phonePosition: PhonePosition,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val position by phonePosition.position.collectAsState()
    val roll = position["roll"]?.toFloatOrNull() ?: 0f

    // Snapped orientation in degrees: 0, 90, 180 or -90.
    var snapped by remember { mutableIntStateOf(0) }

    // Hysteresis: only switch once the phone is clearly (>55 deg) away from the
    // current snapped angle, so the view doesn't flicker near the 45 deg borders.
    if (abs(signedDiff(roll, snapped.toFloat())) > SWITCH_THRESHOLD_DEG) {
        snapped = nearestQuadrant(roll)
    }

    val swapSides = snapped == 90 || snapped == -90

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val w = constraints.maxWidth
                    val h = constraints.maxHeight
                    val childConstraints =
                        if (swapSides) Constraints.fixed(h, w) else Constraints.fixed(w, h)
                    val placeable = measurable.measure(childConstraints)
                    layout(w, h) {
                        placeable.placeRelative(
                            (w - placeable.width) / 2,
                            (h - placeable.height) / 2
                        )
                    }
                }
                .graphicsLayer {
                    // Same sign convention the existing button row already uses
                    // (rotationZ = roll): rotates opposite to the phone.
                    rotationZ = snapped.toFloat()
                }
        ) {
            content()
        }
    }
}

private const val SWITCH_THRESHOLD_DEG = 55f

private fun signedDiff(a: Float, b: Float): Float {
    var d = (a - b) % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

private fun nearestQuadrant(roll: Float): Int {
    val q = ((roll / 90f).roundToInt() * 90)
    val n = ((q % 360) + 360) % 360
    return when (n) {
        90 -> 90
        180 -> 180
        270 -> -90
        else -> 0
    }
}
