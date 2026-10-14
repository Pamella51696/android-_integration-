package com.edag.seethroughapplication.curboverlay.model

data class CurbZone(
    val side: CurbSide,
    val distanceM: Double?,
    val classification: CurbZoneClassification,
    val source: String?,
    val valid: Boolean,
    val colorHex: String?,
    val eventTimestampMs: Long,
    val receivedTimestampMs: Long,
    val pano: PanoPlacement? = null,
    val sensorId: String? = null,
) {
    val isFresh: Boolean
        get() = valid && classification != CurbZoneClassification.UNKNOWN
}
