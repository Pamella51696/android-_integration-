package com.edag.seethroughapplication._3DModel


import android.opengl.Matrix
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.gltfio.FilamentAsset
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.viewinterop.AndroidView
import com.edag.seethroughapplication.Constants
import com.edag.seethroughapplication.fluidlevels.FluidLevelOverlay
import com.edag.seethroughapplication.phonePosition.PhonePosition
import com.edag.seethroughapplication.videoStreaming.PanoramaView
import com.google.android.filament.Material
import io.github.sceneview.rememberRenderer
import io.github.sceneview.rememberScene
import io.github.sceneview.rememberView
import kotlin.math.tan


// Approximate physical length of the real car.
// Calibrate this against the actual GLB model before relying on distances.
private const val METERS_PER_MODEL_UNIT = 4.3f

@Composable
fun DriverViewScreen(phonePositionObject: PhonePosition) {

    val context = LocalContext.current

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraNode = rememberCameraNode(engine)
    val modelInstance = rememberModelInstance(modelLoader, "volkswagen_golf_r_transparant.glb")     // volkswagen_golf_r_transparant
    // var viewSize by remember { mutableStateOf(IntSize.Zero) }

    // Head state
    var eye by remember { mutableStateOf<Position?>(null) }   // fixed driver's eye position
    var yawDeg by remember { mutableFloatStateOf(0f) }        // + = look left, - = look right
    var pitchDeg by remember { mutableFloatStateOf(0f) }      // + = look up

    // Phone yaw that counts as "looking straight ahead" (null = take the next reading)
    var yawOrigin by remember { mutableStateOf<Float?>(null) }
    var lastYaw by remember { mutableFloatStateOf(0f) }

    val forwardZ = 1f   // same value that worked for you before (flip to -1f if needed)

    // PanoramaView
    val panorama = remember { PanoramaView(context, Constants.CAR_HEADING) }

    val view = rememberView(engine)
    val renderer = rememberRenderer(engine)
    val scene = rememberScene(engine)


    LaunchedEffect(phonePositionObject) {
        phonePositionObject.head.collect { h ->
            lastYaw = h.yawDeg

            // First reading (or after Recenter) becomes the straight-ahead reference.
            // Set Constants.YAW_FROM_COMPASS = true only if h.yawDeg is a real compass heading.
            val origin = yawOrigin ?: (
                    if (Constants.YAW_FROM_COMPASS) Constants.CAR_HEADING else h.yawDeg
                    ).also { yawOrigin = it }

            val rel = shortestAngle(h.yawDeg - origin)   // -180..180, + = turned right

            yawDeg = -rel                                // 3D model, + = look left
            pitchDeg = h.pitchDeg.coerceIn(-45f, 45f)

            panorama.updateHeading(Constants.CAR_HEADING + rel)
            panorama.updatePitch(pitchDeg)
        }
    }



    // 1. Find the wheel once and place the eye
    LaunchedEffect(modelInstance) {
        val instance = modelInstance ?: return@LaunchedEffect
        delay(500.milliseconds)

        val entity = findWheelEntityAuto(engine, instance.asset)
        if (entity == 0) {
            Log.e("DriverView", "Steering wheel NOT found")
            return@LaunchedEffect
        }
        val wheel = worldCenter(engine, entity)

        val unit = 1f / 4.3f
        val eyePos = Position(
            wheel.x,
            wheel.y + 0.25f * unit,
            wheel.z - forwardZ * 0.45f * unit
        )
        cameraNode.position = eyePos
        eye = eyePos

        logCarWorldBox(
            engine = engine,
            asset = instance.asset
        )
    }

    // 2. Re-aim the camera whenever the head angles change
    LaunchedEffect(eye, yawDeg, pitchDeg) {
        val e = eye ?: return@LaunchedEffect
        val yaw = Math.toRadians(yawDeg.toDouble())
        val pitch = Math.toRadians(pitchDeg.toDouble())
        val dir = Position(
            x = (forwardZ * sin(yaw) * cos(pitch)).toFloat(),
            y = sin(pitch).toFloat(),
            z = (forwardZ * cos(yaw) * cos(pitch)).toFloat()
        )
        cameraNode.position = e   // keep the eye fixed
        cameraNode.lookAt(Position(e.x + dir.x, e.y + dir.y, e.z + dir.z))
    }

    LaunchedEffect(cameraNode) {
        cameraNode.near = 0.005f      // small near plane so the dashboard doesn't clip
        cameraNode.far = 20f
        // cameraNode.focalLength = 15.6 // ≈ 75° vertical FOV (see note below)
        cameraNode.focalLength = 12.0 / tan(Math.toRadians(Constants.MODEL_VERTICAL_FOV_DEG / 2.0))
    }

    LaunchedEffect(view, renderer, scene) {
        view.blendMode = com.google.android.filament.View.BlendMode.TRANSLUCENT

        val options = com.google.android.filament.Renderer.ClearOptions()
        options.clear = true
        options.clearColor = doubleArrayOf(0.0, 0.0, 0.0, 0.0)   // transparent
        renderer.clearOptions = options

        scene.skybox = null
    }

    // 3. Drag = move your eyes
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
    ) {

        // Bottom layer: live footage
        AndroidView(
            factory = { panorama },
            modifier = Modifier.fillMaxSize()
        )

        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            scene = scene,
            cameraNode = cameraNode,
            cameraManipulator = null,
            isOpaque = false,
        ) {
            modelInstance?.let { ModelNode(modelInstance = it, scaleToUnits = 1.0f) }
        }
        // Top layer: tap-to-learn symbols on the fluid reservoirs (fluidlevels package)
        FluidLevelOverlay(
            engine = engine,
            cameraNode = cameraNode,
            modelInstance = modelInstance
        )
    }

    DisposableEffect(phonePositionObject) {
        phonePositionObject.startListening()
        onDispose { phonePositionObject.stopListening() }
    }
}

private fun worldCenter(engine: Engine, entity: Int): Position {
    val tm = engine.transformManager
    val world = FloatArray(16)
    tm.getWorldTransform(tm.getInstance(entity), world)

    // Reuse worldBox so the center is correct even when the node is rotated/scaled
    val b = worldBox(engine, entity)
        ?: return Position(world[12], world[13], world[14])   // no mesh: fall back to node origin

    return Position(
        (b[0] + b[3]) / 2f,
        (b[1] + b[4]) / 2f,
        (b[2] + b[5]) / 2f
    )
}

private fun shortestAngle(d: Float): Float {
    var x = ((d % 360f) + 360f) % 360f
    if (x > 180f) x -= 360f
    return x
}

private fun worldBox(engine: Engine, entity: Int): FloatArray? {
    val tm = engine.transformManager
    val rm = engine.renderableManager
    val ri = rm.getInstance(entity)
    if (ri == 0) return null

    val world = FloatArray(16)
    tm.getWorldTransform(tm.getInstance(entity), world)

    val box = Box().also { rm.getAxisAlignedBoundingBox(ri, it) }
    val c = box.center
    val h = box.halfExtent

    val mn = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
    val mx = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
    val out = FloatArray(4)
    for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) for (sz in intArrayOf(-1, 1)) {
        val p = floatArrayOf(c[0] + sx * h[0], c[1] + sy * h[1], c[2] + sz * h[2], 1f)
        Matrix.multiplyMV(out, 0, world, 0, p, 0)
        for (i in 0..2) { mn[i] = minOf(mn[i], out[i]); mx[i] = maxOf(mx[i], out[i]) }
    }
    return floatArrayOf(mn[0], mn[1], mn[2], mx[0], mx[1], mx[2])
}

private fun findWheelEntityAuto(engine: Engine, asset: FilamentAsset): Int {
    // 1. Collect world boxes of all renderables and the overall car box
    val boxes = HashMap<Int, FloatArray>()
    val car = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
        -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
    for (e in asset.entities) {
        val b = worldBox(engine, e) ?: continue
        boxes[e] = b
        for (i in 0..2) { car[i] = minOf(car[i], b[i]); car[i + 3] = maxOf(car[i + 3], b[i + 3]) }
    }
    if (boxes.isEmpty()) return 0

    val sizeX = car[3] - car[0]
    val sizeY = car[4] - car[1]
    val sizeZ = car[5] - car[2]
    val carLength = maxOf(sizeX, sizeZ)            // assumes Y is up
    val unit = carLength / 4.3f                    // 1.0 if the car is in meters

    // 2. Score every candidate
    var best = 0
    var bestScore = 0
    val positives = listOf("steer", "steering", "_sw_", "sw_", "volant", "lenkrad")
    val negatives = listOf("tire", "tyre", "rim", "brake", "caliper", "hub", "spoke_")

    for ((e, b) in boxes) {
        val name = (asset.getName(e) ?: "").lowercase()
        var score = 0

        // Name hints
        if (positives.any { name.contains(it) }) score += 5
        if (negatives.any { name.contains(it) }) score -= 5

        // Shape: roughly 0.30-0.45 m across in two axes (a ring), and thin in the third
        val dims = floatArrayOf(b[3] - b[0], b[4] - b[1], b[5] - b[2]).map { it / unit }.sorted()
        if (dims[2] in 0.28f..0.45f && dims[1] in 0.20f..0.45f && dims[0] < 0.20f) score += 3

        // Position: inside the car's footprint, mid-height, off the center line
        val cy = (b[1] + b[4]) / 2f
        val relY = (cy - car[1]) / sizeY
        if (relY in 0.40f..0.75f) score += 1
        val cx = (b[0] + b[3]) / 2f
        val offCenter = Math.abs(cx - (car[0] + car[3]) / 2f) / sizeX
        if (offCenter in 0.08f..0.35f) score += 1

        if (score > 0) Log.d("DriverView", "candidate score=$score name=${asset.getName(e)}")
        if (score > bestScore) { bestScore = score; best = e }
    }

    Log.d("DriverView", "best wheel = ${asset.getName(best)} (score $bestScore)")
    return if (bestScore >= 4) best else 0
}


private fun logCarWorldBox(
    engine: Engine,
    asset: FilamentAsset
) {
    val car = floatArrayOf(
        Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
        -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE
    )

    var count = 0

    for (entity in asset.entities) {
        val box = worldBox(engine, entity) ?: continue

        count++

        for (i in 0..2) {
            car[i] = minOf(car[i], box[i])
            car[i + 3] = maxOf(car[i + 3], box[i + 3])
        }
    }

    if (count == 0) {
        Log.e("CarBounds", "No renderable entities found")
        return
    }

    val width = car[3] - car[0]
    val height = car[4] - car[1]
    val length = car[5] - car[2]

    Log.d("CarBounds", "Entity count: $count")
    Log.d("CarBounds", "Min X/Y/Z: ${car[0]}, ${car[1]}, ${car[2]}")
    Log.d("CarBounds", "Max X/Y/Z: ${car[3]}, ${car[4]}, ${car[5]}")
    Log.d("CarBounds", "Width X: $width")
    Log.d("CarBounds", "Height Y: $height")
    Log.d("CarBounds", "Length Z: $length")
}
