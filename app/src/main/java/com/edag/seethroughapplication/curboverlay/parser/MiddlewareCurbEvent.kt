package com.edag.seethroughapplication.curboverlay.parser

/**
 * Parsed middleware curb payload (WP5 `type: curb`, v2) before side normalization.
 */
data class MiddlewareCurbEvent(
    val sequence: Long,
    val eventTimestampMs: Long,
    val dangerThresholdM: Double?,
    val warningThresholdM: Double?,
    val zones: List<MiddlewareZoneReading>,
    val visionBySide: Map<String, MiddlewareVisionReading>,
)

data class MiddlewareZoneReading(
    val id: String?,
    val positionKey: String?,
    val distanceM: Double?,
    val zoneName: String?,
    val source: String?,
    val colorHex: String?,
    val pano: MiddlewarePanoReading?,
)

data class MiddlewareVisionReading(
    val detected: Boolean,
    val distanceM: Double?,
    val source: String?,
)

data class MiddlewarePanoReading(
    val x: Int,
    val w: Int,
    val xNorm: Double,
    val wNorm: Double,
)
