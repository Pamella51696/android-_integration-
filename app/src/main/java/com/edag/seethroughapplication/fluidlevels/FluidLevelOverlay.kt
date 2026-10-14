package com.edag.seethroughapplication.fluidlevels

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edag.driver_view_3d_model.fluidlevels.FluidInfoCard
import com.google.android.filament.Engine
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.CameraNode
import kotlin.math.abs

// Width of the box holding one symbol + caption. The symbol circle sits at
// the top of it and its centre is the point pinned to the 3D component.
private val BadgeWidth = 96.dp
private val BadgeCircleArea = 64.dp

// Fingertip sized tap target around each symbol, and extra slack around the
// reservoir's own projected outline.
private val MinTapRadius = 44.dp
private val OutlineTapMargin = 10.dp

/** Live screen position of one reservoir. Updated every frame. */
@Stable
private class ReservoirUiState(val definition: FluidReservoir) {
    var entity by mutableIntStateOf(0)
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    var onScreen by mutableStateOf(false)
}

/**
 * Tap-to-learn overlay for the car's fluid reservoirs.
 *
 * Draws a small symbol (with a "Tap to learn" caption) pinned onto each of
 * the three reservoirs of the 3D model - cooling water, engine oil and
 * washer fluid - and shows the current fluid level when one is tapped.
 *
 * Place it directly above the SceneView, filling the same area, e.g. inside
 * the Box that already holds the PanoramaView and the SceneView. It only
 * reads the scene (names, transforms, camera); it never modifies it.
 */
@Composable
fun FluidLevelOverlay(
    engine: Engine,
    cameraNode: CameraNode,
    modelInstance: ModelInstance?,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val minTapRadiusPx = with(density) { MinTapRadius.toPx() }
    val outlineMarginPx = with(density) { OutlineTapMargin.toPx() }
    val offScreenSlackPx = with(density) { 60.dp.toPx() }

    val states = remember { FluidLevelData.reservoirs.map { ReservoirUiState(it) } }
    var selectedKind by remember { mutableStateOf<FluidKind?>(null) }

    // 1. Find the three reservoir parts as soon as the model has loaded.
    LaunchedEffect(modelInstance) {
        val instance = modelInstance ?: return@LaunchedEffect
        for (s in states) {
            s.entity = FluidReservoirLocator.findEntity(instance.asset, s.definition.nodeNameHints)
        }
    }

    // 2. Keep each symbol pinned to its reservoir while the camera moves.
    LaunchedEffect(engine, cameraNode) {
        while (true) {
            withFrameNanos {
                val viewport = cameraNode.view?.viewport
                val viewW = viewport?.width ?: 0
                val viewH = viewport?.height ?: 0

                for (s in states) {
                    val box = FluidReservoirLocator.worldBox(engine, s.entity)
                    val p = box?.let { FluidReservoirLocator.projectCenter(cameraNode, it) }

                    if (p == null || viewW == 0 || viewH == 0) {
                        s.onScreen = false
                    } else {
                        if (abs(p[0] - s.x) > 0.5f) s.x = p[0]
                        if (abs(p[1] - s.y) > 0.5f) s.y = p[1]
                        s.onScreen =
                            p[0] > -offScreenSlackPx && p[0] < viewW + offScreenSlackPx &&
                                    p[1] > -offScreenSlackPx && p[1] < viewH + offScreenSlackPx
                    }
                }
            }
        }
    }

    // 3. Taps: pick the reservoir under the finger (if any).
    val onTap = rememberUpdatedState<(Offset) -> Unit> { tap ->
        var best: ReservoirUiState? = null
        var bestDistance = Float.MAX_VALUE

        for (s in states) {
            if (!s.onScreen) continue

            val rect = FluidReservoirLocator.worldBox(engine, s.entity)
                ?.let { FluidReservoirLocator.projectRect(cameraNode, it) }

            val hit = FluidHitTest.isHit(
                tapX = tap.x,
                tapY = tap.y,
                centerX = s.x,
                centerY = s.y,
                minRadiusPx = minTapRadiusPx,
                rect = rect,
                marginPx = outlineMarginPx
            )

            if (hit) {
                val d = FluidHitTest.distanceSq(tap.x, tap.y, s.x, s.y)
                if (d < bestDistance) {
                    bestDistance = d
                    best = s
                }
            }
        }

        val kind = best?.definition?.kind
        // tap on empty space or on the open reservoir closes the card
        selectedKind = if (kind == null || kind == selectedKind) null else kind
    }

    // Card slide-in
    val cardAppear = remember { Animatable(0f) }
    LaunchedEffect(selectedKind) {
        if (selectedKind != null) {
            cardAppear.snapTo(0f)
            cardAppear.animateTo(1f, animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset -> onTap.value(offset) }
            }
    ) {
        for (s in states) {
            ReservoirBadge(
                state = s,
                selected = selectedKind == s.definition.kind
            )
        }

        val selected = states.firstOrNull { it.definition.kind == selectedKind }?.definition
        if (selected != null) {
            FluidInfoCard(
                reservoir = selected,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .fillMaxWidth()
                    .widthIn(max = 400.dp)
                    .graphicsLayer {
                        alpha = cardAppear.value
                        translationY = (1f - cardAppear.value) * 48f
                    }
            )
        }
    }
}

/** One pinned symbol: glass circle with the symbol, pulsing ring and caption. */
@Composable
private fun ReservoirBadge(
    state: ReservoirUiState,
    selected: Boolean
) {
    val kind = state.definition.kind
    val accent = fluidAccentColor(kind)

    val density = LocalDensity.current
    val halfWidthPx = with(density) { (BadgeWidth / 2).toPx() }
    val anchorYPx = with(density) { (BadgeCircleArea / 2).toPx() }

    val pulse = rememberInfiniteTransition(label = "badgePulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "badgePulseValue"
    )

    Column(
        modifier = Modifier
            .width(BadgeWidth)
            .graphicsLayer {
                translationX = state.x - halfWidthPx
                translationY = state.y - anchorYPx - 250f
                alpha = if (state.onScreen) 1f else 0f
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(BadgeCircleArea),
            contentAlignment = Alignment.Center
        ) {
            // soft pulsing ring
            Canvas(modifier = Modifier.matchParentSize()) {
                val p = pulse.value
                val base = 23.dp.toPx()
                drawCircle(
                    color = accent.copy(alpha = (1f - p) * 0.55f),
                    radius = base + p * 9.dp.toPx(),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color(0xF2152238) else Color(0xD90B1220))
                    .border(if (selected) 3.dp else 2.dp, accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                FluidSymbol(kind = kind, modifier = Modifier.size(26.dp))
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = FluidLevelData.TAP_HINT,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 10.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0x990B1220))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
