package com.edag.seethroughapplication.fluidlevels

/**
 * Pure tap hit-testing maths (no Android / Compose dependencies).
 *
 * The reservoirs are small on screen, so a tap counts as a hit when it lands
 * either close to the symbol (a fingertip-sized circle) or inside the
 * reservoir's own projected outline (enlarged by a small margin).
 */
internal object FluidHitTest {

    fun distanceSq(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return dx * dx + dy * dy
    }

    /**
     * @param rect projected outline of the reservoir as minX, minY, maxX, maxY (or null)
     */
    fun isHit(
        tapX: Float,
        tapY: Float,
        centerX: Float,
        centerY: Float,
        minRadiusPx: Float,
        rect: FloatArray?,
        marginPx: Float
    ): Boolean {
        if (distanceSq(tapX, tapY, centerX, centerY) <= minRadiusPx * minRadiusPx) return true

        if (rect != null && rect.size >= 4) {
            return tapX >= rect[0] - marginPx && tapX <= rect[2] + marginPx &&
                    tapY >= rect[1] - marginPx && tapY <= rect[3] + marginPx
        }
        return false
    }
}
