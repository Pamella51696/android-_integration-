package com.edag.seethroughapplication.headTracking

import com.edag.seethroughapplication.Constants

/**
 * Turns a [com.edag.seethroughapplication.headTracking.FaceTrackState] into visual zoom/tilt values. Kept as pure
 * functions with the tuning constants in one place, separate from both the
 * face-tracking math (FaceTrackerController) and however the values end up
 * getting applied (HeadTrackedVideo).
 */
object HeadTrackTransform {

    // Amplifies the proximity signal so the same lean-in/lean-out movement
    // produces a bigger, snappier zoom change (bounds stay BASE_SCALE..MAX_SCALE).

    /** proximity 0f (face far) .. 1f (face close) -> render scale. */
    fun zoomScale(proximity: Float): Float {
        val boosted = (proximity * Constants.PROXIMITY_GAIN).coerceIn(0f, 1f)
        return Constants.BASE_SCALE + (Constants.MAX_SCALE - Constants.BASE_SCALE) * boosted
    }

    // ─── Tilt (display only — no longer rotates the screen) ────────────────

    /** roll in degrees (+ = head tilted right) -> readout value shown on screen. */
    fun tiltDegrees(roll: Float): Float =
        (-roll * Constants.ROLL_STRENGTH).coerceIn(-Constants.MAX_TILT_DEG, Constants.MAX_TILT_DEG)

    // ─── Yaw-driven pan ─────────────────────────────────────────────────────
    // Turning your head left/right pans the panorama the same way the compass
    // heading does — this just produces a heading offset (in degrees) that
    // gets added on top of the existing compass heading in PanoramaView.


    /** yaw -1f (turned left) .. 1f (turned right) -> heading offset in degrees. */
    fun yawPanDegrees(yaw: Float): Float =
        (yaw * Constants.YAW_PAN_STRENGTH).coerceIn(-Constants.MAX_YAW_PAN_DEG, Constants.MAX_YAW_PAN_DEG)

    // ─── Roll-correct the raw yaw/pitch face offset ───────────────────────
    // The front camera is bolted to the phone, so its image rotates
    // together with the phone. If the phone itself is rolled (held
    // tilted clockwise/counter-clockwise), a head turn that's really
    // "left/right" in the real world shows up partly as "up/down" in the
    // raw camera-frame yaw/pitch, and vice versa. This rotates the raw
    // (yaw, pitch) face-offset vector by the phone's own roll to recover
    // the real-world left/right component, so head-turn panning stays
    // correct no matter how the phone is rolled in its mount.

    /**
     * yaw/pitch: raw face offset from [com.edag.seethroughapplication.headTracking.FaceTrackState]
     * (-1f..1f each). phoneRollDegrees: PhonePosition's current roll
     * reading (0 = phone upright). Returns a world-space yaw in the same
     * -1f..1f range/meaning as the raw yaw, safe to pass into
     * [yawPanDegrees] as before.
     */
    fun worldYaw(yaw: Float, pitch: Float, phoneRollDegrees: Float): Float {
        val radians = Math.toRadians(phoneRollDegrees.toDouble())
        val cos = kotlin.math.cos(radians).toFloat()
        val sin = kotlin.math.sin(radians).toFloat()
        return (yaw * cos - pitch * sin).coerceIn(-1f, 1f)
    }
}