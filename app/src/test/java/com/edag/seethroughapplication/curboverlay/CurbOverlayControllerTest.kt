package com.edag.seethroughapplication.curboverlay

import com.edag.seethroughapplication.curboverlay.controller.CurbOverlayController
import com.edag.seethroughapplication.curboverlay.model.CurbZoneClassification
import com.edag.seethroughapplication.curboverlay.network.InMemoryCurbEventSource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CurbOverlayControllerTest {

    @Test
    fun controllerUpdatesStateFromFakeSource() {
        val json = this::class.java.classLoader!!
            .getResource("curb_middleware_v2_sample.json")!!
            .readText()
        val source = InMemoryCurbEventSource(listOf(json))
        val latch = CountDownLatch(1)
        var danger = CurbZoneClassification.UNKNOWN

        val controller = CurbOverlayController(
            config = CurbOverlayConfig.forHost("localhost", enabled = true),
            eventSource = source,
            mainExecutor = { it.run() },
        )
        controller.onStateChanged = {
            danger = it.front.classification
            latch.countDown()
        }
        controller.start()
        latch.await(2, TimeUnit.SECONDS)
        controller.stop()

        assertEquals(CurbZoneClassification.DANGER, danger)
    }
}
