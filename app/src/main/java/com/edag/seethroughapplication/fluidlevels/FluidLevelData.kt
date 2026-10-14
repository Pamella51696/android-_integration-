package com.edag.seethroughapplication.fluidlevels

/**
 * Data for the tap-to-learn fluid reservoir feature.
 *
 * This whole package (fluidlevels) is self-contained: nothing else in the
 * app depends on it except a single call to [FluidLevelOverlay] from
 * DriverViewScreen. To change a displayed level, edit the values below.
 */

enum class FluidKind {
    COOLING_WATER,
    OIL,
    WASHER
}

enum class FluidStatus {
    OK,
    LOW,
    CRITICAL
}

data class FluidReservoir(
    val kind: FluidKind,
    /** Name shown on the info card. */
    val title: String,
    /** One-line description shown on the info card. */
    val description: String,
    /**
     * Lower-case fragments matched against the 3D model's node names
     * (the glb contains the nodes "cooling_water_Reservoir_Tank",
     * "Oil_Reservoir" and "Washer_Reservoir_Tank.001").
     */
    val nodeNameHints: List<String>,
    /** Current fluid level, 0..100. */
    val levelPercent: Int
)

object FluidLevelData {

    // Current levels (percent).
    const val COOLING_WATER_PERCENT = 87
    const val OIL_PERCENT = 95
    const val WASHER_PERCENT = 85

    // Small phrase shown under every symbol.
    const val TAP_HINT = "Tap to learn"

    // Below these levels the card switches to a warning state.
    const val LOW_BELOW_PERCENT = 40
    const val CRITICAL_BELOW_PERCENT = 20

    val reservoirs: List<FluidReservoir> = listOf(
        FluidReservoir(
            kind = FluidKind.COOLING_WATER,
            title = "Cooling water",
            description = "Engine coolant expansion tank",
            nodeNameHints = listOf("cooling_water_reservoir", "cooling_water", "coolant"),
            levelPercent = COOLING_WATER_PERCENT
        ),
        FluidReservoir(
            kind = FluidKind.OIL,
            title = "Engine oil",
            description = "Engine oil reservoir",
            nodeNameHints = listOf("oil_reservoir", "oil_res"),
            levelPercent = OIL_PERCENT
        ),
        FluidReservoir(
            kind = FluidKind.WASHER,
            title = "Washer fluid",
            description = "Windscreen washer reservoir",
            nodeNameHints = listOf("washer_reservoir", "washer"),
            levelPercent = WASHER_PERCENT
        )
    )

    fun statusOf(levelPercent: Int): FluidStatus = when {
        levelPercent < CRITICAL_BELOW_PERCENT -> FluidStatus.CRITICAL
        levelPercent < LOW_BELOW_PERCENT -> FluidStatus.LOW
        else -> FluidStatus.OK
    }
}
