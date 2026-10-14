package com.edag.seethroughapplication.curboverlay

import com.edag.seethroughapplication.curboverlay.mapper.CurbZoneMapper
import com.edag.seethroughapplication.curboverlay.model.CurbSide
import com.edag.seethroughapplication.curboverlay.model.CurbZoneClassification
import com.edag.seethroughapplication.curboverlay.parser.CurbJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurbZoneMapperTest {

    private val parser = CurbJsonParser()
    private val mapper = CurbZoneMapper()

    @Test
    fun mapsFourSidesFromMiddlewareFixture() {
        val event = parser.parseEventJson(readFixture("curb_middleware_v2_sample.json"))!!
        val state = mapper.mapToState(
            event = event,
            receivedMs = event.eventTimestampMs,
            config = CurbOverlayConfig.forHost("localhost", enabled = true),
            streamConnected = true,
        )
        assertEquals(CurbZoneClassification.DANGER, state.front.classification)
        assertEquals(0.42, state.front.distanceM!!, 0.001)
        assertEquals(CurbZoneClassification.SAFE, state.rear.classification)
        assertEquals(CurbZoneClassification.WARNING, state.left.classification)
        assertEquals(CurbZoneClassification.UNKNOWN, state.right.classification)
        assertNull(state.right.distanceM)
    }

    @Test
    fun subSensorIdsMapToVehicleSides() {
        assertEquals(CurbSide.FRONT, mapper.resolveSide("FRONT_CENTER"))
        assertEquals(CurbSide.REAR, mapper.resolveSide("REAR_LEFT"))
        assertEquals(CurbSide.LEFT, mapper.resolveSide("LEFT_FRONT"))
        assertEquals(CurbSide.RIGHT, mapper.resolveSide("RIGHT_REAR"))
    }

    @Test
    fun staleStateGreysOutMeasurements() {
        val event = parser.parseEventJson(readFixture("curb_middleware_v2_sample.json"))!!
        val fresh = mapper.mapToState(
            event,
            receivedMs = 1_000L,
            config = CurbOverlayConfig.forHost("localhost", enabled = true),
            streamConnected = true,
        )
        val stale = mapper.applyClientStale(fresh, nowMs = 5_000L, staleMs = 3_000L)
        assertEquals(CurbZoneClassification.UNKNOWN, stale.front.classification)
        assertNull(stale.front.distanceM)
    }

    private fun readFixture(name: String): String =
        this::class.java.classLoader!!.getResource(name)!!.readText()
}
