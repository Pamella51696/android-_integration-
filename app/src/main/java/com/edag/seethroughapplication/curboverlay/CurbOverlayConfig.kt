package com.edag.seethroughapplication.curboverlay

/**
 * Configuration for the curb overlay adapter. Disabled by default so existing app
 * behaviour is unchanged until the host screen opts in.
 */
data class CurbOverlayConfig(
    val enabled: Boolean = false,
    val signalsUrl: String,
    val clientStaleMs: Long = 3_000L,
    val reconnectInitialDelayMs: Long = 500L,
    val reconnectMaxDelayMs: Long = 15_000L,
    val dangerThresholdM: Double? = null,
    val warningThresholdM: Double? = null,
) {
    companion object {
        const val DEFAULT_SIGNALS_PORT = 9091

        fun forHost(
            host: String,
            port: Int = DEFAULT_SIGNALS_PORT,
            enabled: Boolean = false,
        ): CurbOverlayConfig = CurbOverlayConfig(
            enabled = enabled,
            signalsUrl = "http://$host:$port/api/v1/signals",
        )
    }
}
