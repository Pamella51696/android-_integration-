package com.edag.seethroughapplication.curboverlay.controller

import android.os.Handler
import android.os.Looper
import com.edag.seethroughapplication.curboverlay.CurbOverlayConfig
import com.edag.seethroughapplication.curboverlay.mapper.CurbZoneMapper
import com.edag.seethroughapplication.curboverlay.model.CurbState
import com.edag.seethroughapplication.curboverlay.network.CurbEventListener
import com.edag.seethroughapplication.curboverlay.network.CurbEventSource
import com.edag.seethroughapplication.curboverlay.network.SseCurbEventSource
import com.edag.seethroughapplication.curboverlay.parser.CurbJsonParser

/**
 * Coordinates parsing, freshness and UI-thread state delivery. Independent of steering / wheel alignment.
 */
class CurbOverlayController(
    private val config: CurbOverlayConfig,
    private val eventSource: CurbEventSource = SseCurbEventSource(config),
    private val parser: CurbJsonParser = CurbJsonParser(),
    private val mapper: CurbZoneMapper = CurbZoneMapper(),
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val mainExecutor: ((Runnable) -> Unit)? = null,
) {

    private val dispatch: (Runnable) -> Unit =
        mainExecutor ?: { runnable -> mainHandler.post(runnable) }

    @Volatile
    private var latestState: CurbState = CurbState.empty()

    private var started = false
    private var staleRunnable: Runnable? = null

    var onStateChanged: ((CurbState) -> Unit)? = null

    fun start() {
        if (!config.enabled || started) return
        started = true
        scheduleStaleCheck()
        eventSource.start(
            object : CurbEventListener {
                override fun onCurbJson(json: String) {
                    val received = System.currentTimeMillis()
                    val event = parser.parseEventJson(json) ?: return
                    val mapped = mapper.mapToState(
                        event = event,
                        receivedMs = received,
                        config = config,
                        streamConnected = true,
                    )
                    publish(mapped)
                }

                override fun onConnectionChanged(connected: Boolean) {
                    if (!connected && started) {
                        publish(
                            latestState.copy(
                                streamConnected = false,
                                receivedTimestampMs = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
            },
        )
    }

    fun stop() {
        if (!started) return
        started = false
        staleRunnable?.let { mainHandler.removeCallbacks(it) } // stale timer uses Handler only
        staleRunnable = null
        eventSource.stop()
        publish(CurbState.empty())
    }

    fun currentState(): CurbState = latestState

    private fun scheduleStaleCheck() {
        staleRunnable?.let { mainHandler.removeCallbacks(it) } // stale timer uses Handler only
        val runnable = object : Runnable {
            override fun run() {
                if (!started) return
                val stale = mapper.applyClientStale(
                    latestState,
                    System.currentTimeMillis(),
                    config.clientStaleMs,
                )
                if (stale != latestState) publish(stale)
                mainHandler.postDelayed(this, config.clientStaleMs / 2)
            }
        }
        staleRunnable = runnable
        mainHandler.postDelayed(runnable, config.clientStaleMs / 2)
    }

    private fun publish(state: CurbState) {
        latestState = state
        dispatch { onStateChanged?.invoke(state) }
    }
}
