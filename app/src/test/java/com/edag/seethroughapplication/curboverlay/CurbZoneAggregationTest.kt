package com.edag.seethroughapplication.curboverlay

import com.edag.seethroughapplication.curboverlay.mapper.CurbZoneMapper
import com.edag.seethroughapplication.curboverlay.model.CurbZoneClassification
import com.edag.seethroughapplication.curboverlay.parser.MiddlewareCurbEvent
import com.edag.seethroughapplication.curboverlay.parser.MiddlewareZoneReading
import org.junit.Assert.assertEquals
import org.junit.Test

class CurbZoneAggregationTest {

    private val mapper = CurbZoneMapper()

    @Test
    fun multipleFrontSensorsPickClosestDistance() {
        val event = MiddlewareCurbEvent(
            sequence = 1,
            eventTimestampMs = 1000L,
            dangerThresholdM = 0.5,
            warningThresholdM = 1.0,
            zones = listOf(
                MiddlewareZoneReading("FRONT_LEFT", "FRONT_LEFT", 1.2, "SAFE", "ULTRASONIC", "#34C759", null),
                MiddlewareZoneReading("FRONT_RIGHT", "FRONT_RIGHT", 0.35, "DANGER", "ULTRASONIC", "#FF3B30", null),
            ),
            visionBySide = emptyMap(),
        )
        val state = mapper.mapToState(
            event,
            receivedMs = 1000L,
            config = CurbOverlayConfig.forHost("localhost", enabled = true),
            streamConnected = true,
        )
        assertEquals(0.35, state.front.distanceM!!, 0.001)
        assertEquals(CurbZoneClassification.DANGER, state.front.classification)
    }
}
