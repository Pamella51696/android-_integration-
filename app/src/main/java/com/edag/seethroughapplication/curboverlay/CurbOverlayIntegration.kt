package com.edag.seethroughapplication.curboverlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.edag.seethroughapplication.curboverlay.controller.CurbOverlayController
import com.edag.seethroughapplication.curboverlay.model.CurbState
import com.edag.seethroughapplication.curboverlay.renderer.CurbOverlayRenderer

/**
 * Public integration surface: attach this composable above the existing camera / driver view.
 * When [CurbOverlayConfig.enabled] is false, this is a no-op (no networking, no UI).
 */
@Composable
fun CurbOverlayLayer(
    config: CurbOverlayConfig,
    modifier: Modifier = Modifier,
) {
    if (!config.enabled) return

    var state by remember { mutableStateOf(CurbState.empty()) }
    val controller = remember(config) {
        CurbOverlayController(config)
    }

    DisposableEffect(controller) {
        controller.onStateChanged = { state = it }
        controller.start()
        onDispose { controller.stop() }
    }

    CurbOverlayRenderer(state = state, modifier = modifier)
}

/**
 * Factory for hosts that prefer constructing the controller outside Compose (tests, custom wiring).
 */
object CurbOverlayIntegration {
    fun createController(config: CurbOverlayConfig): CurbOverlayController =
        CurbOverlayController(config)
}
