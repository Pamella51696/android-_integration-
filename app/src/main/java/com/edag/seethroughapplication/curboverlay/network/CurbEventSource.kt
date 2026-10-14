package com.edag.seethroughapplication.curboverlay.network

/**
 * Transport-agnostic source of raw middleware curb JSON payloads (SSE `data:` lines).
 */
interface CurbEventSource {
    fun start(listener: CurbEventListener)
    fun stop()
}

fun interface CurbEventListener {
    /** Called on a background thread. */
    fun onCurbJson(json: String)

    fun onConnectionChanged(connected: Boolean) {}
}
