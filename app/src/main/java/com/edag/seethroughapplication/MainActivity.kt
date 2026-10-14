package com.edag.seethroughapplication

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.edag.seethroughapplication.phonePosition.PhonePosition
import com.edag.seethroughapplication.ui.theme.SeeThroughApplicationTheme
import com.edag.seethroughapplication.videoStreaming.MJPEGRepository
import com.edag.seethroughapplication.headTracking.FaceTrackState
import com.edag.seethroughapplication.headTracking.HeadTrackTransform
import com.edag.seethroughapplication.headTracking.HeadTrackedVideo
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Button
import androidx.compose.material3.TextField
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.edag.seethroughapplication._3DModel.DriverViewScreen
import com.edag.seethroughapplication.curboverlay.CurbOverlayConfig
import com.edag.seethroughapplication.curboverlay.CurbOverlayLayer
import kotlin.text.toFloatOrNull

// Purely a UI-placement concept for the button row - has nothing to
// do with system/Activity orientation, which is locked to "portrait"
// in the manifest so the footage (PanoramaView) frame never rotates
// or resizes. This just reads the same roll value PanoramaView already
// uses to keep the footage level (see currentRoll / coverScale in
// PanoramaView.kt, left untouched), and uses it to figure out which
// edge of the (always-portrait) screen is physically closest to "down"
// in the user's hand.
private enum class DeviceOrientation { PORTRAIT, LANDSCAPE_LEFT, LANDSCAPE_RIGHT }

// Wider "enter" band than "exit" band (hysteresis) so the buttons
// don't flicker back and forth when the roll value sits right around
// the 45/135 degree boundary.
private fun classifyOrientation(
    roll: Float,
    previous: DeviceOrientation
): DeviceOrientation {

    val enterLandscape = 55f
    val exitLandscape = 35f

    return when (previous) {
        DeviceOrientation.PORTRAIT -> when {
            roll in enterLandscape..(180f - enterLandscape) -> DeviceOrientation.LANDSCAPE_LEFT
            roll in -(180f - enterLandscape)..-enterLandscape -> DeviceOrientation.LANDSCAPE_RIGHT
            else -> DeviceOrientation.PORTRAIT
        }
        DeviceOrientation.LANDSCAPE_LEFT -> {
            if (roll in exitLandscape..(180f - exitLandscape)) DeviceOrientation.LANDSCAPE_LEFT
            else DeviceOrientation.PORTRAIT
        }
        DeviceOrientation.LANDSCAPE_RIGHT -> {
            if (roll in -(180f - exitLandscape)..-exitLandscape) DeviceOrientation.LANDSCAPE_RIGHT
            else DeviceOrientation.PORTRAIT
        }
    }
}
private const val PHONE_MAX_PITCH = 360f

private fun uprightRotation(roll: Float): Float {
    var angle = ((-roll) % 360f + 360f) % 360f   // normalize to 0..360
    if (angle > 180f) angle -= 360f              // normalize to -180..180
    return when {
        angle > PHONE_MAX_PITCH -> angle - 180f
        angle < -PHONE_MAX_PITCH -> angle + 180f
        else -> angle
    }
}

class MainActivity : ComponentActivity() {

    lateinit var phonePositionObject: PhonePosition

    companion object {
        var instance: MainActivity? = null

        /** Set to false to disable PR7 curb overlay without removing the package. */
        private const val ENABLE_CURB_OVERLAY = true
    }

    private var disableDialog: AlertDialog? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        phonePositionObject = PhonePosition(this)

        instance = this


        val enableValue = Settings.System.getString(
            contentResolver,
            "app_enable"
        )

        if (enableValue == "false") {
            showDisableDialog()
            return
        }

        Toast.makeText(this, "STARTING SERVICE", Toast.LENGTH_SHORT).show()

        startService(
            Intent(this, AppMonitorService::class.java)
        )

         enableEdgeToEdge()


        setContent {
            SeeThroughApplicationTheme {
                Scaffold { innerPadding ->
                    // NEW: wraps the existing panorama screen with
                    // front-camera head-tracked zoom (headTracking
                    // module). VideoStreamScreen/PanoramaView below are
                    // untouched. Head yaw is reported back via onHeadTrack
                    // and fed into the panorama's existing compass-heading
                    // scroll, instead of rotating the screen.

                    var headTrack by remember { mutableStateOf(FaceTrackState()) }
                    var resetTrigger by remember { mutableStateOf(0) }
                    var isHeadTrackingEnabled by remember {
                        mutableStateOf(false)
                    }
                    var isIPValueAvailable by remember { mutableStateOf(false) }
                    var middlewareHost by remember { mutableStateOf("") }
                    val position by phonePositionObject.position.collectAsState()
                    val phoneRoll = position["roll"]?.toFloatOrNull() ?: 0f

                    // Physical landscape/portrait state derived only from
                    // roll - used solely to reposition the buttons below.
                    // The Activity itself is locked to portrait
                    // (AndroidManifest), so PanoramaView's canvas size
                    // never changes and the footage is never rotated by
                    // the system; it only stays level via the existing
                    // currentRoll counter-rotation already in
                    // PanoramaView.kt.
                    var deviceOrientation by remember {
                        mutableStateOf(DeviceOrientation.PORTRAIT)
                    }
                    deviceOrientation = classifyOrientation(phoneRoll, deviceOrientation)

                    Box(
                        // modifier = Modifier.padding(innerPadding).fillMaxSize()
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .clipToBounds()
                    ) {

                        if(isIPValueAvailable){
                            HeadTrackedVideo(
                                enabled = isHeadTrackingEnabled,
                                onHeadTrack = { headTrack = it }
                            ) {
//                                VideoStreamScreen(
//                                    modifier = Modifier.fillMaxSize(),
//                                    phonePosition = phonePositionObject,
//                                    headYawPanDegrees = HeadTrackTransform.yawPanDegrees(
//                                        HeadTrackTransform.worldYaw(
//                                            headTrack.yaw,
//                                            headTrack.pitch,
//                                            phoneRoll
//                                        )
//                                    ),
//                                    resetTrigger = resetTrigger
//                                )
                                // NEW: rotates panorama + 3D model + touch components + info card
                                // opposite to the phone's physical orientation.
                                OrientationAdaptiveContainer(phonePosition = phonePositionObject) {
                                    DriverViewScreen(
                                        phonePositionObject = phonePositionObject
                                    )
                                }

                            }

                            if (ENABLE_CURB_OVERLAY && middlewareHost.isNotBlank()) {
                                CurbOverlayLayer(
                                    config = CurbOverlayConfig.forHost(
                                        host = middlewareHost,
                                        enabled = true,
                                    ),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            // "Set Front" button row. In portrait it sits at
                            // the bottom like before. When the user holds
                            // the phone in landscape, it shifts to whichever
                            // edge is now physically "down" and rotates 90
                            // degrees so it still reads as a normal
                            // horizontal row to the user - the footage
                            // itself never moves or rotates, only this row
                            // does.

                            val buttonAlignment = Alignment.BottomCenter

                            val buttonRotationDegrees = -uprightRotation(phoneRoll)
                            Column(
                                modifier = Modifier
                                    .align(buttonAlignment)
                                    .graphicsLayer {
                                        rotationZ = buttonRotationDegrees
                                    }
                                    .padding(24.dp),
                            ) {

                                Button(
                                    onClick = {
                                        phonePositionObject.calibrateFront()
                                        resetTrigger++
                                    }
                                ) {
                                    Text("Set Front")
                                }

                                Spacer(Modifier.width(10.dp))

                                Button(
                                    onClick = {
                                        isHeadTrackingEnabled = !isHeadTrackingEnabled
                                    }
                                ) {
                                    Text(
                                        if (isHeadTrackingEnabled) {
                                            "Head Tracking: ON"
                                        } else {
                                            "Head Tracking: OFF"
                                        }
                                    )
                                }
                            }
                        }else{
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ){
                                var ipAddress by remember { mutableStateOf("") }
                                TextField(
                                    value = ipAddress,
                                    modifier = Modifier.padding(10.dp),
                                    label = { Text(text = "Enter IP Address: ") },
                                    placeholder = {
                                        Text(text="Ex.: 10.44.26.45", color = Color.LightGray)
                                    },
                                    onValueChange = {
                                        ipAddress = it.trim()
                                    }
                                )

                                Button(
                                    onClick = {
                                        middlewareHost = ipAddress
                                        isIPValueAvailable = true
                                        MJPEGRepository.start(ipAddress)
                                    }
                                ) {
                                    Text(text="Get Footage")
                                }
                            }
                        }

                    }


                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        phonePositionObject.startListening()
    }

    override fun onPause() {
        super.onPause()
        phonePositionObject.stopListening()
    }

    fun showDisableDialog() {
        if (disableDialog?.isShowing == true) return

        disableDialog = AlertDialog.Builder(this)
            .setTitle("Application Disabled")
            .setMessage(Constants.DISABLED_MESSAGE)
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                disableDialog = null
                finish()
            }
            .show()
    }

    fun restoreApp() {
        runOnUiThread {
            disableDialog?.dismiss()
            disableDialog = null

            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        super.onDestroy()
    }
}