package com.edag.seethroughapplication.curboverlay.network

/**
 * Fake event source for unit tests and manual demos without a live middleware server.
 */
class InMemoryCurbEventSource(
    private val payloads: List<String> = emptyList(),
) : CurbEventSource {

    private var listener: CurbEventListener? = null

    override fun start(listener: CurbEventListener) {
        this.listener = listener
        listener.onConnectionChanged(true)
        for (payload in payloads) {
            listener.onCurbJson(payload)
        }
    }

    override fun stop() {
        listener?.onConnectionChanged(false)
        listener = null
    }

    fun push(json: String) {
        listener?.onCurbJson(json)
    }
}
