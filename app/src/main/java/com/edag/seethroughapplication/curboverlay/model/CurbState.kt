package com.edag.seethroughapplication.curboverlay.model

/**
 * Normalized four-side curb view for the overlay.
 */
data class CurbState(
    val front: CurbZone,
    val rear: CurbZone,
    val left: CurbZone,
    val right: CurbZone,
    val eventTimestampMs: Long,
    val receivedTimestampMs: Long,
    val sequence: Long,
    val thresholdsDangerM: Double?,
    val thresholdsWarningM: Double?,
    val streamConnected: Boolean = false,
) {
    fun zone(side: CurbSide): CurbZone = when (side) {
        CurbSide.FRONT -> front
        CurbSide.REAR -> rear
        CurbSide.LEFT -> left
        CurbSide.RIGHT -> right
    }

    companion object {
        fun empty(nowMs: Long = System.currentTimeMillis()): CurbState {
            fun blank(side: CurbSide) = CurbZone(
                side = side,
                distanceM = null,
                classification = CurbZoneClassification.UNKNOWN,
                source = null,
                valid = false,
                colorHex = null,
                eventTimestampMs = nowMs,
                receivedTimestampMs = nowMs,
            )
            return CurbState(
                front = blank(CurbSide.FRONT),
                rear = blank(CurbSide.REAR),
                left = blank(CurbSide.LEFT),
                right = blank(CurbSide.RIGHT),
                eventTimestampMs = nowMs,
                receivedTimestampMs = nowMs,
                sequence = 0L,
                thresholdsDangerM = null,
                thresholdsWarningM = null,
                streamConnected = false,
            )
        }
    }
}
