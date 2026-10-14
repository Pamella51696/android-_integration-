package com.edag.seethroughapplication.curboverlay.network

import android.util.Log
import com.edag.seethroughapplication.Constants
import com.edag.seethroughapplication.curboverlay.CurbOverlayConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.math.min

/**
 * Subscribes to middleware `/api/v1/signals` and forwards `event: curb` payloads.
 * Uses [HttpURLConnection], consistent with [com.edag.seethroughapplication.videoStreaming.MJPEGRepository].
 */
class SseCurbEventSource(
    private val config: CurbOverlayConfig,
) : CurbEventSource {

    @Volatile
    private var running = false

    private var worker: Thread? = null

    override fun start(listener: CurbEventListener) {
        if (running) return
        running = true
        worker = Thread {
            var delayMs = config.reconnectInitialDelayMs
            while (running) {
                try {
                    openStream(listener)
                    delayMs = config.reconnectInitialDelayMs
                } catch (e: Exception) {
                    Log.w(Constants.TAG, "Curb SSE disconnected: ${e.message}")
                    listener.onConnectionChanged(false)
                    if (!running) break
                    try {
                        Thread.sleep(delayMs)
                    } catch (_: InterruptedException) {
                        break
                    }
                    delayMs = min(delayMs * 2, config.reconnectMaxDelayMs)
                }
            }
        }.apply {
            name = "CurbSseEventSource"
            start()
        }
    }

    override fun stop() {
        running = false
        worker?.interrupt()
        worker = null
    }

    private fun openStream(listener: CurbEventListener) {
        val url = URL(config.signalsUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "text/event-stream")
            connectTimeout = Constants.CONNECTION_TIME_OUT
            readTimeout = 0
        }

        connection.connect()
        listener.onConnectionChanged(true)

        BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
            var eventName: String? = null
            val dataLines = mutableListOf<String>()
            while (running) {
                val line = reader.readLine() ?: break
                when {
                    line.startsWith(":") -> Unit
                    line.isEmpty() -> {
                        if (eventName == "curb" && dataLines.isNotEmpty()) {
                            listener.onCurbJson(dataLines.joinToString("\n"))
                        }
                        eventName = null
                        dataLines.clear()
                    }
                    line.startsWith("event:") -> eventName = line.substring(6).trim()
                    line.startsWith("data:") -> dataLines.add(line.substring(5).trim())
                }
            }
        }
        connection.disconnect()
        listener.onConnectionChanged(false)
    }
}
