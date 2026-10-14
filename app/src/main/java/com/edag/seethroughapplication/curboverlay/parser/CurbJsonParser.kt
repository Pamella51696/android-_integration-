package com.edag.seethroughapplication.curboverlay.parser

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure JSON parser for middleware SSE `curb` events. No networking or UI.
 *
 * Schema follows `CurbModel.snapshot()` in fake_vehicle_ultrasonic_integration:
 * `type`, `seq`, `ts`, `thresholds`, `zones[]`, `vision`.
 */
class CurbJsonParser {

    fun parseEventJson(json: String): MiddlewareCurbEvent? {
        if (json.isBlank()) return null
        return try {
            parseRoot(JSONObject(json))
        } catch (_: Exception) {
            null
        }
    }

    private fun parseRoot(root: JSONObject): MiddlewareCurbEvent? {
        val type = root.optString("type", "")
        if (type.isNotEmpty() && type != "curb") return null

        val thresholds = root.optJSONObject("thresholds")
        val danger = thresholds?.optDoubleNaN("dangerM")
        val warning = thresholds?.optDoubleNaN("warningM")

        val zones = mutableListOf<MiddlewareZoneReading>()
        val zonesArr = root.optJSONArray("zones")
        if (zonesArr != null) {
            for (i in 0 until zonesArr.length()) {
                zonesArr.optJSONObject(i)?.let { zones.add(parseZone(it)) }
            }
        }

        val vision = mutableMapOf<String, MiddlewareVisionReading>()
        val visionObj = root.optJSONObject("vision")
        if (visionObj != null) {
            for (key in listOf("left", "right", "front", "rear")) {
                visionObj.optJSONObject(key)?.let { vision[key] = parseVision(it) }
            }
        }

        return MiddlewareCurbEvent(
            sequence = root.optLong("seq", 0L),
            eventTimestampMs = root.optLong("ts", System.currentTimeMillis()),
            dangerThresholdM = danger,
            warningThresholdM = warning,
            zones = zones,
            visionBySide = vision,
        )
    }

    private fun parseZone(obj: JSONObject): MiddlewareZoneReading {
        val panoObj = obj.optJSONObject("pano")
        val pano = panoObj?.let {
            MiddlewarePanoReading(
                x = it.optInt("x", 0),
                w = it.optInt("w", 0),
                xNorm = it.optDoubleNaN("xNorm") ?: 0.0,
                wNorm = it.optDoubleNaN("wNorm") ?: 0.0,
            )
        }
        return MiddlewareZoneReading(
            id = obj.optStringOrNull("id"),
            positionKey = obj.optStringOrNull("pos")?.ifBlank { obj.optStringOrNull("id") },
            distanceM = obj.optDoubleNaN("distanceM"),
            zoneName = obj.optStringOrNull("zone"),
            source = obj.optStringOrNull("source"),
            colorHex = obj.optStringOrNull("color"),
            pano = pano,
        )
    }

    private fun parseVision(obj: JSONObject): MiddlewareVisionReading =
        MiddlewareVisionReading(
            detected = obj.optBoolean("detected", false),
            distanceM = obj.optDoubleNaN("distanceM"),
            source = obj.optString("source", null),
        )

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key, "")
        return v.ifBlank { null }
    }

    private fun JSONObject.optDoubleNaN(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val v = optDouble(key, Double.NaN)
        return when {
            v.isNaN() || v.isInfinite() -> null
            else -> v
        }
    }
}
