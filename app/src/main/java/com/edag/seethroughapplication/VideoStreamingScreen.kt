package com.edag.seethroughapplication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.edag.seethroughapplication.phonePosition.PhonePosition
import com.edag.seethroughapplication.videoStreaming.PanoramaView

@Composable
fun VideoStreamScreen(
    modifier: Modifier = Modifier,
    phonePosition: PhonePosition,
    headYawPanDegrees: Float = 0f,
    resetTrigger: Int = 0
) {


    val position by phonePosition.position.collectAsState()

    val phoneHeading =
        position["phoneHeading"]?.toFloatOrNull() ?: 0f

    val phoneRoll =
        position["roll"]?.toFloatOrNull() ?: 0f
    val phonePitch =
        position["pitch"]?.toFloatOrNull() ?: 0f

    // Holds the live PanoramaView instance so the "Set Front" button
    // (up in MainActivity) can tell it to snap back to center.
    val panoramaViewRef = remember { mutableStateOf<PanoramaView?>(null) }

    LaunchedEffect(resetTrigger) {
        panoramaViewRef.value?.resetView()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PanoramaView(context, Constants.CAR_HEADING).also {
                    panoramaViewRef.value = it
                }
            },
            update = {view ->
                // Head yaw is folded straight into the same heading value
                // the gyro drives, so it scrolls through the exact same
                // smoothing/wrap-around logic in PanoramaView.
                view.updateHeading(phoneHeading + headYawPanDegrees)

                // Keeps the panorama level no matter how the phone is
                // physically rolled in the driver's hand/mount.
                view.updateRoll(phoneRoll)
                view.updatePitch(phonePitch)
            }
        )
    }
}
