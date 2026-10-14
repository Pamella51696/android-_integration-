package com.edag.seethroughapplication.fluidlevels

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser

/**
 * Line-art symbols for the three reservoirs, drawn natively (no image
 * assets). Every symbol lives on a 24 x 24 grid; the path data below is
 * plain SVG path syntax.
 */

private class SymbolPart(val pathData: String, val filled: Boolean)

private val CoolingWaterParts = listOf(
    // tank body
    SymbolPart("M8.5 7.5H15.5A2.5 2.5 0 0 1 18 10V18.5A2.5 2.5 0 0 1 15.5 21H8.5A2.5 2.5 0 0 1 6 18.5V10A2.5 2.5 0 0 1 8.5 7.5Z", false),
    // neck and cap
    SymbolPart("M9.5 7.5V4.8H14.5V7.5", false),
    // liquid wave
    SymbolPart("M6.4 14.2Q8 12.4 9.6 14.2T12.8 14.2T16 14.2T17.6 14.2", false),
    // bubbles
    SymbolPart("M10 18.1a0.7 0.7 0 1 0 1.4 0a0.7 0.7 0 1 0-1.4 0Z", false),
    SymbolPart("M13.2 17a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0-1 0Z", false)
)

private val OilParts = listOf(
    // can body
    SymbolPart("M10 9.5H16A2 2 0 0 1 18 11.5V16.5A2 2 0 0 1 16 18.5H10A2 2 0 0 1 8 16.5V11.5A2 2 0 0 1 10 9.5Z", false),
    // filler cap
    SymbolPart("M11 9.5V7.3H15V9.5", false),
    // spout
    SymbolPart("M8 12.3L3.2 15.6", false),
    // handle
    SymbolPart("M18 11.8C21.6 11.4 21.6 16.6 18 16.2", false),
    // oil drop
    SymbolPart("M3.1 17.6C3.9 18.6 4.5 19.3 4.5 20A1.4 1.4 0 0 1 1.7 20C1.7 19.3 2.3 18.6 3.1 17.6Z", true)
)

private val WasherParts = listOf(
    // windscreen
    SymbolPart("M3.5 19L6.2 7Q12 4.4 17.8 7L20.5 19Z", false),
    // wiper
    SymbolPart("M17.2 16.4L8 9.6", false),
    // washer drops
    SymbolPart("M7.2 12.5C8.28 13.76 9 14.57 9 15.56A1.8 1.8 0 0 1 5.4 15.56C5.4 14.57 6.12 13.76 7.2 12.5Z", true),
    SymbolPart("M12.4 12.5C13.24 13.48 13.8 14.11 13.8 14.88A1.4 1.4 0 0 1 11 14.88C11 14.11 11.56 13.48 12.4 12.5Z", true)
)

private fun partsOf(kind: FluidKind): List<SymbolPart> = when (kind) {
    FluidKind.COOLING_WATER -> CoolingWaterParts
    FluidKind.OIL -> OilParts
    FluidKind.WASHER -> WasherParts
}

/** Accent colour used for a reservoir's symbol, ring and gauge. */
fun fluidAccentColor(kind: FluidKind): Color = when (kind) {
    FluidKind.COOLING_WATER -> Color(0xFF4FC3F7)
    FluidKind.OIL -> Color(0xFFFFB74D)
    FluidKind.WASHER -> Color(0xFF4DD0E1)
}

@Composable
fun FluidSymbol(
    kind: FluidKind,
    modifier: Modifier = Modifier,
    tint: Color = fluidAccentColor(kind)
) {
    // Parse the SVG path strings once per symbol.
    val paths = remember(kind) {
        partsOf(kind).map { part ->
            Pair(PathParser().parsePathString(part.pathData).toPath(), part.filled)
        }
    }

    Canvas(modifier = modifier) {
        val s = size.minDimension / 24f
        scale(scale = s, pivot = Offset.Zero) {
            for ((path, filled) in paths) {
                if (filled) {
                    drawPath(path = path, color = tint, style = Fill)
                } else {
                    drawPath(
                        path = path,
                        color = tint,
                        style = Stroke(
                            width = 1.7f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
    }
}
