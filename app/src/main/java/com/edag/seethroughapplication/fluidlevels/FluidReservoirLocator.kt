package com.edag.seethroughapplication.fluidlevels

import android.opengl.Matrix
import com.google.android.filament.Engine
import com.google.android.filament.gltfio.FilamentAsset
import io.github.sceneview.math.Position
import io.github.sceneview.node.CameraNode
import io.github.sceneview.utils.worldToScreen
import com.google.android.filament.Box as FilamentBox

/**
 * Finds the reservoir parts inside the loaded 3D model and projects them
 * onto the screen, using the same Filament calls DriverView already uses
 * to find the steering wheel. Read-only: nothing here changes the scene.
 */
internal object FluidReservoirLocator {

    /** Axis aligned world box: minX, minY, minZ, maxX, maxY, maxZ. */
    class WorldBox(val values: FloatArray) {
        val centerX get() = (values[0] + values[3]) / 2f
        val centerY get() = (values[1] + values[4]) / 2f
        val centerZ get() = (values[2] + values[5]) / 2f
    }

    /** Entity id of the first model node whose name contains one of [hints] (0 = none). */
    fun findEntity(asset: FilamentAsset, hints: List<String>): Int {
        for (hint in hints) {
            for (e in asset.entities) {
                val name = asset.getName(e) ?: continue
                if (name.lowercase().contains(hint.lowercase())) return e
            }
        }
        return 0
    }

    /** World space box of a renderable entity, or null if it isn't ready/renderable yet. */
    fun worldBox(engine: Engine, entity: Int): WorldBox? {
        if (entity == 0) return null

        val tm = engine.transformManager
        val rm = engine.renderableManager

        val ri = rm.getInstance(entity)
        if (ri == 0) return null

        val ti = tm.getInstance(entity)
        if (ti == 0) return null

        val world = FloatArray(16)
        tm.getWorldTransform(ti, world)

        val box = FilamentBox().also { rm.getAxisAlignedBoundingBox(ri, it) }
        val c = box.center
        val h = box.halfExtent

        val mn = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        val mx = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        val out = FloatArray(4)

        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) for (sz in intArrayOf(-1, 1)) {
            val p = floatArrayOf(c[0] + sx * h[0], c[1] + sy * h[1], c[2] + sz * h[2], 1f)
            Matrix.multiplyMV(out, 0, world, 0, p, 0)
            for (i in 0..2) {
                mn[i] = minOf(mn[i], out[i])
                mx[i] = maxOf(mx[i], out[i])
            }
        }

        return WorldBox(floatArrayOf(mn[0], mn[1], mn[2], mx[0], mx[1], mx[2]))
    }

    /**
     * Screen position (pixels, relative to the 3D view) of the centre of the box,
     * or null when it is behind the camera / the camera isn't attached yet.
     */
    fun projectCenter(cameraNode: CameraNode, box: WorldBox): FloatArray? {
        val view = cameraNode.view ?: return null
        val p = view.worldToScreen(Position(box.centerX, box.centerY, box.centerZ)) ?: return null
        return floatArrayOf(p.x, p.y)
    }

    /**
     * Screen rectangle (pixels) covering the visible corners of the box:
     * minX, minY, maxX, maxY. Null when no corner can be projected.
     */
    fun projectRect(cameraNode: CameraNode, box: WorldBox): FloatArray? {
        val view = cameraNode.view ?: return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var any = false

        val v = box.values
        for (ix in intArrayOf(0, 3)) for (iy in intArrayOf(1, 4)) for (iz in intArrayOf(2, 5)) {
            val p = view.worldToScreen(Position(v[ix], v[iy], v[iz])) ?: continue
            any = true
            minX = minOf(minX, p.x)
            minY = minOf(minY, p.y)
            maxX = maxOf(maxX, p.x)
            maxY = maxOf(maxY, p.y)
        }

        return if (any) floatArrayOf(minX, minY, maxX, maxY) else null
    }
}
