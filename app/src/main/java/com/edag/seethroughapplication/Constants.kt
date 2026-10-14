package com.edag.seethroughapplication

import com.edag.seethroughapplication.videoStreaming.PanoSegment

object Constants{

    // MJPEGRepository Constants
    const val BASE_SERVER_URL = "http://10.44.26.147:9090/stitch"
    const val CONNECTION_TIME_OUT = 5000
    const val PANORAMA_VIEW_HEADING_ALPHA = 0.08f
    const val PANORAMA_VIEW_CROP_ALPHA = 0.12f
    const val PANORAMA_VIEW_FEATHER_WIDTH_PX = 140
    const val PANORAMA_VIEW_SEAM_CONTEXT_PX = 40
    const val PANORAMA_VIEW_HORIZONTAL_FOV = 90f
    const val PANORAMA_VIEW_COVERAGE_DEG = 279f
    const val PANORAMA_VIEW_START_DEG = 0f

    // Debugging log tag
    const val TAG = "SeeThroughApplication"

    // HeadTrackerTransform constants
    const val BASE_SCALE = 1.00f
    const val MAX_SCALE = 1.6f
    const val PROXIMITY_GAIN = 1.4f
    const val ROLL_STRENGTH = 0.4f
    const val MAX_TILT_DEG = 15f
    const val YAW_PAN_STRENGTH = 60f
    const val MAX_YAW_PAN_DEG = 60f

    // PhonePosition Constants
    // Filtering Parameters
    val PHONE_HEADING_ALPHA = 0.05f
    val PHONE_DEAD_BAND = 1.5f
    val PHONE_MAX_PITCH = 75f

    // How far (in degrees) to shift the calibrated "front" reference
    // away from the phone's actual facing direction when Set Front is
    // tapped. E.g. 45f means "front" is set 45 degrees clockwise from
    // wherever the phone is pointing at calibration time.
    val FRONT_OFFSET_DEGREES = 250f

    // MainActivity
    const val DISABLED_MESSAGE = "The functionality is currently disabled because the vehicle is not in the parked state."

    // Video Streaming Screen
    val CAR_HEADING: Float = 90F

    // PanoramaView new Constants
    const val MODEL_VERTICAL_FOV_DEG = 75f      // must match the 3D camera, see section 4
    const val PANORAMA_HORIZON_FRAC = 0.5f      // horizon height in the image (0 = top, 1 = bottom)
    const val PANORAMA_DEBUG = true
    const val PANORAMA_TURN_SIGN = 1f           // set to -1f if turning right moves the footage the wrong way
    const val PANORAMA_YAW_OFFSET_DEG = 0f      // shifts all footage at once
    const val PANORAMA_VERTICAL_FOV_DEG = 90f     // vertical angle the footage is stretched over
    const val PANORAMA_SRC_TOP_FRAC = 0f        // first usable image row (0 = top of the bitmap)
    const val PANORAMA_SRC_BOTTOM_FRAC = 1f     // last usable image row (1 = bottom of the bitmap)
    const val PANORAMA_AUTO_FILL = false     // true: footage is always tall enough for the normal view

    const val PANORAMA_PITCH_MARGIN_DEG = 5f




    // image fraction start/end  ->  direction start/end (0 = front, 90 = right, 180 = back, 270 = left)
    val PANORAMA_SEGMENTS = listOf(
        PanoSegment(0.00f, 0.25f, -45f, 45f),   // image slot 1: front
        PanoSegment(0.25f, 0.50f,  45f, 135f),   // image slot 2: right
        PanoSegment(0.50f, 0.75f, 135f, 225f),   // image slot 3: back
        PanoSegment(0.75f, 1.00f, 225f, 315f),   // image slot 4: left
    )

    // DriverViewScreen
    const val YAW_FROM_COMPASS = false
}