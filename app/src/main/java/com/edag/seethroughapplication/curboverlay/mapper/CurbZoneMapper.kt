package com.edag.seethroughapplication.curboverlay.mapper

import com.edag.seethroughapplication.curboverlay.CurbOverlayConfig
import com.edag.seethroughapplication.curboverlay.model.CurbSide
import com.edag.seethroughapplication.curboverlay.model.CurbState
import com.edag.seethroughapplication.curboverlay.model.CurbZone
import com.edag.seethroughapplication.curboverlay.model.CurbZoneClassification
import com.edag.seethroughapplication.curboverlay.model.PanoPlacement
import com.edag.seethroughapplication.curboverlay.parser.MiddlewareCurbEvent
import com.edag.seethroughapplication.curboverlay.parser.MiddlewareZoneReading
import java.util.Locale

/**
 * Maps middleware readings to the four vehicle sides shown on Android.
 *
 * Aggregation when several sensors map to one side: keep the **closest** distance
 * and the **worst** zone level (DANGER over WARNING over SAFE), matching parking safety.
 */
class CurbZoneMapper {

    fun mapToState(
        event: MiddlewareCurbEvent,
        receivedMs: Long,
        config: CurbOverlayConfig,
        streamConnected: Boolean,
    ): CurbState {
        val danger = event.dangerThresholdM ?: config.dangerThresholdM
        val warning = event.warningThresholdM ?: config.warningThresholdM

        val buckets = CurbSide.entries.associateWith { mutableListOf<MiddlewareZoneReading>() }.toMutableMap()
        for (zone in event.zones) {
            val side = resolveSide(zone.positionKey ?: zone.id) ?: continue
            buckets.getValue(side).add(zone)
        }

        fun buildSide(side: CurbSide): CurbZone {
            val readings = buckets.getValue(side)
            val picked = pickReading(readings)
            val visionKey = side.name.lowercase(Locale.US)
            val vision = event.visionBySide[visionKey]

            if (picked == null) {
                if (vision != null && !vision.detected) {
                    return unknownZone(side, event.eventTimestampMs, receivedMs, valid = false)
                }
                return unknownZone(side, event.eventTimestampMs, receivedMs, valid = false)
            }

            val distance = picked.distanceM?.takeIf { it >= 0 }
            val classification = when {
                picked.zoneName != null ->
                    CurbZoneClassification.fromMiddlewareName(picked.zoneName)
                distance != null && danger != null && warning != null ->
                    classifyFromDistance(distance, danger, warning)
                else -> CurbZoneClassification.UNKNOWN
            }

            return CurbZone(
                side = side,
                distanceM = distance,
                classification = classification,
                source = picked.source,
                valid = distance != null && classification != CurbZoneClassification.UNKNOWN,
                colorHex = picked.colorHex,
                eventTimestampMs = event.eventTimestampMs,
                receivedTimestampMs = receivedMs,
                pano = picked.pano?.let {
                    PanoPlacement(it.x, it.w, it.xNorm, it.wNorm)
                },
                sensorId = picked.id ?: picked.positionKey,
            )
        }

        return CurbState(
            front = buildSide(CurbSide.FRONT),
            rear = buildSide(CurbSide.REAR),
            left = buildSide(CurbSide.LEFT),
            right = buildSide(CurbSide.RIGHT),
            eventTimestampMs = event.eventTimestampMs,
            receivedTimestampMs = receivedMs,
            sequence = event.sequence,
            thresholdsDangerM = danger,
            thresholdsWarningM = warning,
            streamConnected = streamConnected,
        )
    }

    fun applyClientStale(state: CurbState, nowMs: Long, staleMs: Long): CurbState {
        if (nowMs - state.receivedTimestampMs <= staleMs) return state
        fun grey(zone: CurbZone) = zone.copy(
            valid = false,
            classification = CurbZoneClassification.UNKNOWN,
            distanceM = null,
        )
        return state.copy(
            front = grey(state.front),
            rear = grey(state.rear),
            left = grey(state.left),
            right = grey(state.right),
        )
    }

    private fun pickReading(readings: List<MiddlewareZoneReading>): MiddlewareZoneReading? {
        if (readings.isEmpty()) return null
        return readings.minWith(
            compareBy<MiddlewareZoneReading> { it.distanceM ?: Double.MAX_VALUE }
                .thenByDescending { CurbZoneClassification.fromMiddlewareName(it.zoneName).level },
        )
    }

    private fun classifyFromDistance(
        distanceM: Double,
        dangerM: Double,
        warningM: Double,
    ): CurbZoneClassification = when {
        distanceM <= dangerM -> CurbZoneClassification.DANGER
        distanceM <= warningM -> CurbZoneClassification.WARNING
        else -> CurbZoneClassification.SAFE
    }

    private fun unknownZone(
        side: CurbSide,
        eventTs: Long,
        receivedMs: Long,
        valid: Boolean,
    ) = CurbZone(
        side = side,
        distanceM = null,
        classification = CurbZoneClassification.UNKNOWN,
        source = null,
        valid = valid,
        colorHex = null,
        eventTimestampMs = eventTs,
        receivedTimestampMs = receivedMs,
    )

    /**
     * Supports middleware `pos` values (LEFT, FRONT, …) and ultrasonic-style IDs.
     */
    fun resolveSide(positionOrId: String?): CurbSide? {
        if (positionOrId.isNullOrBlank()) return null
        val key = positionOrId.trim().uppercase(Locale.US)
        return when {
            key in setOf("FRONT", "CURB_FRONT") -> CurbSide.FRONT
            key in setOf("REAR", "CURB_REAR") -> CurbSide.REAR
            key in setOf("LEFT", "CURB_LEFT") -> CurbSide.LEFT
            key in setOf("RIGHT", "CURB_RIGHT") -> CurbSide.RIGHT
            key.startsWith("FRONT_") -> CurbSide.FRONT
            key.startsWith("REAR_") -> CurbSide.REAR
            key.startsWith("LEFT_") -> CurbSide.LEFT
            key.startsWith("RIGHT_") -> CurbSide.RIGHT
            else -> null
        }
    }
}
