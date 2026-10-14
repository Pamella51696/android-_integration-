package com.edag.seethroughapplication.headTracking

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner


/**
 * Drop-in wrapper: put any existing screen inside this and it gets
 * head-tracked zoom applied on top, with zero changes required to that
 * screen. Handles the CAMERA permission request and the
 * [com.edag.seethroughapplication.headTracking.FaceTrackerController] lifecycle internally. Head tilt is shown as a
 * small corner readout rather than rotating the screen. Pass [onHeadTrack]
 * if the wrapped content wants to react to yaw itself (e.g. panning a
 * panorama the same way it reacts to compass heading).
 *
 * Usage:
 *   HeadTrackedVideo(
 *       modifier = Modifier.padding(innerPadding),
 *       onHeadTrack = { headTrack = it }
 *   ) {
 *       VideoStreamScreen(Modifier.fillMaxSize(), phonePositionObject)
 *   }
 */
@Composable
fun HeadTrackedVideo(
    modifier: Modifier = Modifier,
    enabled: Boolean = false,
    onHeadTrack: (FaceTrackState) -> Unit = {},
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(enabled, hasCameraPermission) {
        if (enabled && !hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val controller = remember { FaceTrackerController(context) }

    DisposableEffect(
        enabled,
        hasCameraPermission,
        lifecycleOwner
    ) {

        val observer =
            LifecycleEventObserver { _, event ->

                when (event) {

                    Lifecycle.Event.ON_RESUME -> {
                        if (enabled && hasCameraPermission) {
                            controller.start(lifecycleOwner)
                        }
                    }

                    Lifecycle.Event.ON_PAUSE -> {
                        controller.stop()
                    }

                    else -> {}
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)

        // Important:
        // If enabled changes while the Activity is already resumed,
        // start/stop immediately.
        if (
            enabled &&
            hasCameraPermission &&
            lifecycleOwner.lifecycle.currentState
                .isAtLeast(Lifecycle.State.RESUMED)
        ) {
            controller.start(lifecycleOwner)
        } else if (!enabled) {
            controller.stop()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.stop()
        }
    }

    val faceTrack by controller.state.collectAsState()

    val effectiveFaceTrack =
        if (enabled) {
            faceTrack
        } else {
            FaceTrackState()
        }

    LaunchedEffect(effectiveFaceTrack) {
        onHeadTrack(effectiveFaceTrack)
    }

    val scale =
        HeadTrackTransform.zoomScale(
            effectiveFaceTrack.proximity
        )
    val tiltDeg =
        HeadTrackTransform.tiltDegrees(
            effectiveFaceTrack.roll
        )

    // Tilting your head no longer rotates the whole screen (that felt
    // wrong) — the panorama itself stays level and just zooms with
    // proximity. We still surface the tilt reading as a small corner
    // label so it's visible while the view scrolls.
    Box(modifier = modifier.fillMaxSize()) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            content()
        }

        BasicText(
            text = "Tilt: ${"%.0f".format(tiltDeg)}°",
            style = TextStyle(
                color = Color.White,
                fontSize = 14.sp
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        )
    }
}
