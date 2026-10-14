package com.edag.seethroughapplication.curboverlay.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edag.seethroughapplication.curboverlay.model.CurbState
import com.edag.seethroughapplication.curboverlay.model.CurbZone
import com.edag.seethroughapplication.curboverlay.model.CurbZoneClassification

@Composable
fun CurbOverlayRenderer(
    state: CurbState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        SideBadge(
            zone = state.front,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
        )
        SideBadge(
            zone = state.rear,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp),
        )
        SideBadge(
            zone = state.left,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp),
        )
        SideBadge(
            zone = state.right,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
        )
        Text(
            text = "Vehicle",
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun SideBadge(zone: CurbZone, modifier: Modifier = Modifier) {
    val color = zoneColor(zone)
    Column(
        modifier = modifier
            .size(width = 108.dp, height = 56.dp)
            .background(color.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
            .border(1.dp, color.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = zone.side.name,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
        )
        Text(
            text = distanceLabel(zone),
            color = Color.White,
            fontSize = 12.sp,
        )
        Text(
            text = zone.classification.name,
            color = color,
            fontSize = 10.sp,
        )
    }
}

private fun distanceLabel(zone: CurbZone): String {
    val d = zone.distanceM
    return when {
        d == null || !zone.valid -> "—"
        else -> String.format("%.2f m", d)
    }
}

private fun zoneColor(zone: CurbZone): Color {
    zone.colorHex?.let { return parseHexColor(it) }
    return when (zone.classification) {
        CurbZoneClassification.DANGER -> Color(0xFFFF3B30)
        CurbZoneClassification.WARNING -> Color(0xFFFFCC00)
        CurbZoneClassification.SAFE -> Color(0xFF34C759)
        CurbZoneClassification.NONE -> Color(0xFF9E9E9E)
        CurbZoneClassification.UNKNOWN -> Color(0xFF9E9E9E)
    }
}

private fun parseHexColor(hex: String): Color {
    val cleaned = hex.removePrefix("#")
    val value = cleaned.toLongOrNull(16) ?: return Color.Gray
    return when (cleaned.length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(value)
        else -> Color.Gray
    }
}
