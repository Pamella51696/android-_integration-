package com.edag.seethroughapplication.curboverlay.model

/**
 * Zone labels aligned with middleware [com.middleware.panorama.wp5.Zone].
 */
enum class CurbZoneClassification(val level: Int) {
    NONE(0),
    SAFE(1),
    WARNING(2),
    DANGER(3),
    UNKNOWN(-1),
    ;

    companion object {
        fun fromMiddlewareName(raw: String?): CurbZoneClassification {
            if (raw.isNullOrBlank()) return UNKNOWN
            return runCatching { valueOf(raw.trim().uppercase()) }.getOrDefault(UNKNOWN)
        }

        fun worst(a: CurbZoneClassification, b: CurbZoneClassification): CurbZoneClassification =
            if (a.level >= b.level) a else b
    }
}
