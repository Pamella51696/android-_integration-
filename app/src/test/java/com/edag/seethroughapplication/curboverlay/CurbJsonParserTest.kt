package com.edag.seethroughapplication.curboverlay

import com.edag.seethroughapplication.curboverlay.parser.CurbJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CurbJsonParserTest {

    private val parser = CurbJsonParser()

    @Test
    fun parsesMiddlewareV2Fixture() {
        val json = readFixture("curb_middleware_v2_sample.json")
        val event = parser.parseEventJson(json)
        assertNotNull(event)
        assertEquals(42L, event!!.sequence)
        assertEquals(0.5, event.dangerThresholdM!!, 0.001)
        assertEquals(3, event.zones.size)
        assertEquals("FRONT", event.zones[0].positionKey)
        assertEquals(0.42, event.zones[0].distanceM!!, 0.001)
    }

    @Test
    fun malformedJsonReturnsNull() {
        assertNull(parser.parseEventJson("{not json"))
        assertNull(parser.parseEventJson(""))
    }

    @Test
    fun wrongTypeIsIgnored() {
        assertNull(parser.parseEventJson("""{"type":"steering","seq":1}"""))
    }

    private fun readFixture(name: String): String =
        this::class.java.classLoader!!.getResource(name)!!.readText()
}
