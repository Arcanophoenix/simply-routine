package com.simplyroutine.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

data class TourStep(val title: String, val body: String)

private val TOUR_STEPS = listOf(
    TourStep("Week view", "Swipe left or right to navigate between weeks."),
    TourStep("Date header", "Tap the date range to jump to any month quickly."),
    TourStep("Timetable grid", "Tap any blank slot to create a new event. This can be turned off in Settings."),
    TourStep("Add event", "Alternatively, you can tap this button to create a new event."),
    TourStep("Add to home screen", "Tap here to pin the Simply Routine widget to your home screen."),
    TourStep("Settings", "Tap here to customise day range, alerts, time format, and more."),
)

class TourState {
    var active by mutableStateOf(false)
    var step by mutableStateOf(0)

    var pagerBounds by mutableStateOf(Rect.Zero)
    var titleBounds by mutableStateOf(Rect.Zero)
    var canvasBounds by mutableStateOf(Rect.Zero)
    var fabBounds by mutableStateOf(Rect.Zero)
    var widgetBtnBounds by mutableStateOf(Rect.Zero)
    var settingsBtnBounds by mutableStateOf(Rect.Zero)

    fun start() { step = 0; active = true }
    fun next() { if (step < TOUR_STEPS.lastIndex) step++ else finish() }
    fun finish() { active = false }

    val currentSpotlight: Rect
        get() = when (step) {
            0 -> pagerBounds
            1 -> titleBounds
            2 -> canvasBounds
            3 -> fabBounds
            4 -> widgetBtnBounds
            5 -> settingsBtnBounds
            else -> Rect.Zero
        }
}

@Composable
fun TourOverlay(
    state: TourState,
    onFinish: () -> Unit,
) {
    if (!state.active) return

    val density = LocalDensity.current
    val config = LocalConfiguration.current
    // Smaller padding for icon-sized targets (FAB, widget btn, settings btn)
    val paddingDp = if (state.step >= 3) 6.dp else 12.dp
    val paddingPx = with(density) { paddingDp.toPx() }
    val screenWidthPx  = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
    val raw = state.currentSpotlight
    val padded = Rect(
        left   = (raw.left   - paddingPx).coerceAtLeast(0f),
        top    = (raw.top    - paddingPx).coerceAtLeast(0f),
        right  = (raw.right  + paddingPx).coerceAtMost(screenWidthPx),
        bottom = (raw.bottom + paddingPx).coerceAtMost(screenHeightPx),
    )

    val animatedRect by animateRectAsState(
        targetValue = padded,
        animationSpec = tween(durationMillis = 350),
        label = "spotlight",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Final).changes.forEach {
                            if (!it.isConsumed) it.consume()
                        }
                    }
                }
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(Color.Black.copy(alpha = 0.62f))
            drawIntoCanvas { canvas ->
                canvas.drawRoundRect(
                    left    = animatedRect.left,
                    top     = animatedRect.top,
                    right   = animatedRect.right,
                    bottom  = animatedRect.bottom,
                    radiusX = 16f,
                    radiusY = 16f,
                    paint   = Paint().apply {
                        blendMode = BlendMode.Clear
                        color = Color.Transparent
                    },
                )
            }
        }

        val step = TOUR_STEPS[state.step]

        // Keep card centre in the middle 50% of screen (25%–75%), nudging away from the spotlight.
        val spotlightCentreY = (animatedRect.top + animatedRect.bottom) / 2f
        val spotlightFraction = (spotlightCentreY / screenHeightPx).coerceIn(0f, 1f)
        val cardCentreFraction = (0.5f + (spotlightFraction - 0.5f) * 0.4f).coerceIn(0.25f, 0.75f)
        val cardOffsetDp = with(density) { (screenHeightPx * (cardCentreFraction - 0.5f)).toDp() }

        val animatedCardOffset by animateDpAsState(
            targetValue = cardOffsetDp,
            animationSpec = tween(durationMillis = 350),
            label = "cardOffset",
        )

        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = animatedCardOffset)
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "${state.step + 1} / ${TOUR_STEPS.size}  •  ${step.title}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(text = step.body, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { state.finish(); onFinish() }) {
                        Text("Skip")
                    }
                    Button(onClick = {
                        if (state.step == TOUR_STEPS.lastIndex) { state.finish(); onFinish() }
                        else state.next()
                    }) {
                        Text(if (state.step == TOUR_STEPS.lastIndex) "Done" else "Next")
                    }
                }
            }
        }
    }
}
