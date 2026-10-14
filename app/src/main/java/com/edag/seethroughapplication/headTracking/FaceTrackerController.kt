package com.edag.seethroughapplication.headTracking

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.edag.seethroughapplication.Constants
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Smoothed head-tracking signal, front-camera driven.
 *  - yaw: -1f (turned left) .. 1f (turned right), raw horizontal face
 *    offset in the camera image
 *  - pitch: -1f (face high in frame) .. 1f (face low in frame), raw
 *    vertical face offset in the camera image. Combined with yaw and
 *    the phone's own roll (see HeadTrackTransform.worldYaw) to recover
 *    which way the user's head actually turned in the real world, even
 *    when the phone itself is held rolled/rotated.
 *  - proximity: 0f (face far from screen) .. 1f (face close to screen)
 *  - roll: head tilt, in degrees (+ = tilted right)
 */
data class FaceTrackState(
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val proximity: Float = 0f,
    val roll: Float = 0f
)

/**
 * Self-contained front-camera + ML Kit face tracker. Doesn't know anything
 * about panoramas, compass headings, or video — it only produces a
 * [FaceTrackState] stream. Start/stop are tied to a [LifecycleOwner] so
 * CameraX handles binding/unbinding safely on its own.
 */
class FaceTrackerController(context: Context) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)

    private var isRunning = false

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .enableTracking()
            .build()
    )

    private var cameraProvider: ProcessCameraProvider? = null

    private val _state = MutableStateFlow(FaceTrackState())
    val state: StateFlow<FaceTrackState> = _state

    // ─── Smoothing (exponential moving average) ────────────────────────
    private val ALPHA = 0.2f
    private var smoothYaw = 0f
    private var smoothPitch = 0f
    private var smoothProximity = 0f
    private var smoothRoll = 0f

    // Face-width-fraction range used to map "how close the face is" to 0f..1f.
    private val NEAR_FRACTION = 0.65f
    private val FAR_FRACTION = 0.08f

    // ID of the face currently locked onto (from ML Kit's per-face
    // tracking, not position/size). Null = no lock yet / lock lost.
    private var lockedTrackingId: Int? = null

    fun start(lifecycleOwner: LifecycleOwner) {

        isRunning = true
        val future = ProcessCameraProvider.getInstance(appContext)

        future.addListener({

            if (!isRunning) {
                return@addListener
            }

            val provider = future.get()
            cameraProvider = provider

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(mainExecutor) { imageProxy -> analyze(imageProxy) }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    analysis
                )
                Log.d(Constants.TAG, "Front camera bound OK for head tracking")
            } catch (e: Exception) {
                Log.e(Constants.TAG, "Failed to bind front camera for head tracking", e)
            }

        }, mainExecutor)
    }

    fun stop() {
        isRunning = false
        cameraProvider?.unbindAll()
        cameraProvider = null

        smoothYaw = 0f
        smoothPitch = 0f
        smoothProximity = 0f
        smoothRoll = 0f
        lockedTrackingId = null

        _state.value = FaceTrackState()
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyze(imageProxy: ImageProxy) {

        val mediaImage = imageProxy.image

        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        detector.process(image)
            .addOnSuccessListener { faces ->

                if (!isRunning) {
                    return@addOnSuccessListener
                }

                if (faces.isEmpty()) {
                    // Nobody in frame at all - clear the lock so
                    // whoever appears next starts a fresh lock.
                    lockedTrackingId = null
                    Log.d(Constants.TAG, "No face detected in this frame")
                    return@addOnSuccessListener
                }

                // Keep following the currently-locked face by its
                // tracking ID if it's still present, no matter its
                // size/position relative to any other face this frame.
                val lockedFace =
                    lockedTrackingId?.let { id -> faces.firstOrNull { it.trackingId == id } }

                val face = lockedFace ?: run {
                    // No lock yet, or the locked person left the frame -
                    // (re)acquire a lock on whoever is currently most
                    // prominent (largest/closest face) and stick with
                    // them until they, in turn, leave.
                    val newLock = faces.maxByOrNull { it.boundingBox.width() }
                    lockedTrackingId = newLock?.trackingId
                    newLock
                }

                if (face == null) {
                    return@addOnSuccessListener
                }

                val bounds = face.boundingBox
                val imgW = image.width.toFloat()
                val imgH = image.height.toFloat()

                val faceWidthFraction = bounds.width() / imgW
                val proximity =
                    ((faceWidthFraction - FAR_FRACTION) / (NEAR_FRACTION - FAR_FRACTION))
                        .coerceIn(0f, 1f)

                val faceCenterX = bounds.exactCenterX() / imgW
                val yaw = ((faceCenterX - 0.5f) * 2f).coerceIn(-1f, 1f)

                val faceCenterY = bounds.exactCenterY() / imgH
                val pitch = ((faceCenterY - 0.5f) * 2f).coerceIn(-1f, 1f)

                val roll = face.headEulerAngleZ

                smoothYaw += (yaw - smoothYaw) * ALPHA
                smoothPitch += (pitch - smoothPitch) * ALPHA
                smoothProximity += (proximity - smoothProximity) * ALPHA
                smoothRoll += (roll - smoothRoll) * ALPHA

                _state.value = FaceTrackState(
                    yaw = smoothYaw,
                    pitch = smoothPitch,
                    proximity = smoothProximity,
                    roll = smoothRoll
                )
            }
            .addOnFailureListener { e ->
                Log.e(Constants.TAG, "Face detection failed", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
