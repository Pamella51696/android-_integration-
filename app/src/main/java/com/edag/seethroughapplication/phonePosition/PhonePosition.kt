package com.edag.seethroughapplication.phonePosition

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt
import android.util.Log
import com.edag.seethroughapplication.Constants


data class HeadPose(val yawDeg: Float, val pitchDeg: Float)

class PhonePosition(private val context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE)
                as SensorManager

    // Gyro-based rotation sensor (no magnetometer) - replaces the old
    // TYPE_ROTATION_VECTOR compass sensor so heading no longer depends
    // on magnetic-field readings. No accelerometer is used.
    private val rotationSensor =
        sensorManager.getDefaultSensor(
            Sensor.TYPE_GAME_ROTATION_VECTOR
        )

    private val rotationMatrix = FloatArray(9)


    //--------------------------------------------------
    // Filter state
    //--------------------------------------------------
    private var filteredAzimuth = 0f
    private var initialized = false

    private var filteredRoll = 0f
    private var rollInitialized = false

    //--------------------------------------------------
    // "Front" reference and whether it's been calibrated yet - gyro
    // heading is relative-only, so it needs a reference point. Front
    // is set once automatically on the first reliable reading after
    // start, and after that only changes when calibrateFront() is
    // called (e.g. from the "Set Front" button).
    //--------------------------------------------------
    private var refYaw = 0f
    private var frontCalibrated = false
    private var currentYaw = 0f

    //--------------------------------------------------

    private val _position =
        MutableStateFlow<Map<String, String>>(emptyMap())

    val position: StateFlow<Map<String, String>> =
        _position

    private val _head = MutableStateFlow(HeadPose(0f, 0f))
    val head: StateFlow<HeadPose> = _head

    //--------------------------------------------------

    fun startListening() {

        rotationSensor?.let {

            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_FASTEST
            )
        }
    }

    fun stopListening() {

        sensorManager.unregisterListener(this)
    }

    // Re-calibrates "front" to whatever the phone's current yaw is,
    // shifted by FRONT_OFFSET_DEGREES (called from the "Set Front"
    // button).
    fun calibrateFront() {

        refYaw = normalizeAngle(currentYaw - Constants.FRONT_OFFSET_DEGREES)
        frontCalibrated = true
        initialized = false
    }

    //--------------------------------------------------

    override fun onSensorChanged(
        event: SensorEvent
    ) {

        if (event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            handleRotation(event)
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) {
        // No-op
    }

    private fun handleRotation(event: SensorEvent) {

        //--------------------------------------------------
        // Rotation Vector -> Rotation Matrix
        //--------------------------------------------------

        SensorManager.getRotationMatrixFromVector(
            rotationMatrix,
            event.values
        )

        //--------------------------------------------------
        // Heading and roll, computed directly from the rotation
        // matrix instead of Euler angles (getOrientation()).
        //
        // Euler-angle yaw/pitch/roll go through gimbal lock: as pitch
        // approaches +-90 deg (e.g. tipping the phone from vertical
        // toward flat), yaw and roll bleed into each other and the
        // reported heading jumps/re-locks onto the wrong value. That
        // was the bug - "front" appearing to shift when the phone was
        // rotated from vertical to horizontal.
        //
        // The fix: work directly with the device's axis vectors,
        // expressed in the world frame by the rotation matrix, and
        // measure angles with plain vector projection. That has no
        // gimbal-lock singularity except the true edge case of the
        // device's forward axis pointing straight up/down, which
        // normal handling never reaches.
        //
        // "Forward" is taken to be the device's own Z axis (matches
        // the physical mounting assumption the old compass axis-remap
        // used). "Up" is the device's own Y axis (top of the phone).
        //--------------------------------------------------

        // Device Z axis (forward), expressed in world coordinates -
        // this is column 3 of the rotation matrix.
        val forwardX = rotationMatrix[2]
        val forwardY = rotationMatrix[5]
        val forwardZ = rotationMatrix[8]

        val forwardHorizontalMag =
            sqrt(forwardX * forwardX + forwardY * forwardY)

        // Device Y axis (up), expressed in world coordinates - column
        // 2 of the rotation matrix. Always perpendicular to "forward"
        // (rotation matrix columns are orthonormal), so this stays
        // well-defined at any pitch, including the cases where the
        // heading calculation below is gated out.
        val upX = rotationMatrix[6]
        val upY = rotationMatrix[7]

        // Heading: bearing of the forward axis's projection onto the
        // horizontal plane. Independent of pitch AND roll.
        val yaw =
            Math.toDegrees(
                atan2(forwardX.toDouble(), forwardY.toDouble())
            ).toFloat()

        // How far forward's projection has tipped off the horizontal
        // plane - used only as the reliability gate below (same role
        // the old Euler pitch played), and as an informational reading.
        val pitch =
            Math.toDegrees(
                atan2(forwardZ.toDouble(), forwardHorizontalMag.toDouble())
            ).toFloat()
        if (System.currentTimeMillis() % 500 < 20) {
            Log.d("PHONE_PITCH", "Pitch = $pitch")
        }

        // Roll: how far the device's "up" is rotated away from true
        // world-up, around the forward axis. This is what changes when
        // you twist the phone clockwise/counter-clockwise in your hand
        // (screen roll) - used to counter-rotate the panorama view and
        // to correct head-tracking's left/right reading below.
        val roll =
            Math.toDegrees(
                atan2(upX.toDouble(), upY.toDouble())
            ).toFloat()

        currentYaw = normalizeAngle(yaw)

        //--------------------------------------------------
        // Ignore the true edge case: forward axis pointing close to
        // straight up/down (e.g. phone lying flat on a table).
        //--------------------------------------------------

        if (abs(pitch) > Constants.PHONE_MAX_PITCH) {
            return
        }

        //--------------------------------------------------
        // First reliable reading after start - calibrate "front"
        // once automatically so the app isn't stuck waiting. From
        // here on, "front" only changes via calibrateFront().
        //--------------------------------------------------

        if (!frontCalibrated) {
            refYaw = normalizeAngle(currentYaw - Constants.FRONT_OFFSET_DEGREES)
            frontCalibrated = true
        }

        //--------------------------------------------------
        // Heading relative to the calibrated "front" reference
        //--------------------------------------------------

        val azimuth = normalizeAngle(currentYaw - refYaw)

        //--------------------------------------------------
        // Initialize / low-pass filter the heading
        //--------------------------------------------------

        if (!initialized) {

            filteredAzimuth = azimuth
            initialized = true

        } else {

            val delta =
                shortestAngleDiff(
                    filteredAzimuth,
                    azimuth
                )

            if (abs(delta) >=
                Constants.PHONE_DEAD_BAND
            ) {

                filteredAzimuth =
                    normalizeAngle(
                        filteredAzimuth +
                                delta * Constants.PHONE_HEADING_ALPHA
                    )
            }
        }

        //--------------------------------------------------
        // Initialize / low-pass filter the roll (same alpha as
        // heading - keeps the panorama's counter-rotation smooth
        // instead of jittering with every sample).
        //--------------------------------------------------

        if (!rollInitialized) {

            filteredRoll = roll
            rollInitialized = true

        } else {

            val rollDelta =
                shortestAngleDiff(
                    filteredRoll,
                    roll
                )

            if (abs(rollDelta) >=
                Constants.PHONE_DEAD_BAND
            ) {

                filteredRoll =
                    normalizeAngleSigned(
                        filteredRoll +
                                rollDelta * Constants.PHONE_HEADING_ALPHA
                    )
            }
        }

        _head.value = HeadPose(
            // filteredAzimuth is 0..360 relative to the calibrated front; convert to -180..180
            yawDeg = shortestAngleDiff(0f, filteredAzimuth - Constants.FRONT_OFFSET_DEGREES),
            // the Z axis tips opposite to the look direction, so invert
            pitchDeg = -pitch
        )

        Log.d("HEAD", "offset=${Constants.FRONT_OFFSET_DEGREES} yaw=${_head.value.yawDeg}")

        _position.value = mapOf(
            "phoneHeading" to
                    filteredAzimuth.roundToInt()
                        .toString(),

            "direction" to
                    getDirection(filteredAzimuth),

            "pitch" to
                    pitch.roundToInt()
                        .toString(),

            "roll" to
                    filteredRoll.roundToInt()
                        .toString()
        )
    }

    private fun normalizeAngle(
        angle: Float
    ): Float {

        return ((angle % 360f) + 360f) % 360f
    }

    // Same idea as normalizeAngle, but keeps the result in -180..180
    // instead of 0..360 - the natural range for a roll value (0 =
    // upright, +/-180 = upside down), rather than an always-positive
    // heading.
    private fun normalizeAngleSigned(
        angle: Float
    ): Float {

        var a = angle % 360f

        if (a > 180f) a -= 360f
        if (a < -180f) a += 360f

        return a
    }

    private fun shortestAngleDiff(
        current: Float,
        target: Float
    ): Float {

        var diff = target - current

        while (diff > 180f)
            diff -= 360f

        while (diff < -180f)
            diff += 360f

        return diff
    }

    private fun getDirection(
        azimuth: Float
    ): String {

        return when {

            azimuth >= 337.5f ||
                    azimuth < 22.5f ->
                "FRONT"

            azimuth < 67.5f ->
                "FRONT-RIGHT"

            azimuth < 112.5f ->
                "RIGHT"

            azimuth < 157.5f ->
                "BACK-RIGHT"

            azimuth < 202.5f ->
                "BACK"

            azimuth < 247.5f ->
                "BACK-LEFT"

            azimuth < 292.5f ->
                "LEFT"

            else ->
                "FRONT-LEFT"
        }
    }
}