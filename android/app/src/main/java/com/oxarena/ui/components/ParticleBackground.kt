package com.oxarena.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.oxarena.ui.theme.Background
import com.oxarena.ui.theme.BackgroundElevated
import com.oxarena.ui.theme.Violet
import kotlin.math.abs
import kotlin.math.sin

/**
 * A GPU-cheap animated particle field over a vertical gradient. Particle positions
 * are derived deterministically from their index (a fractional-sine hash), so the
 * whole field is driven by a single animated phase value — no per-frame allocation
 * and no jank. Used behind the home and searching screens.
 */
@Composable
fun ParticleBackground(
    modifier: Modifier = Modifier,
    particleColor: Color = Violet,
    particleCount: Int = 46,
) {
    val transition = rememberInfiniteTransition(label = "particles")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "phase",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(BackgroundElevated, Background),
                startY = 0f,
                endY = size.height,
            ),
        )

        for (i in 0 until particleCount) {
            val hx = hash(i * 1.37f)
            val hSpeed = 0.35f + hash(i * 2.11f) * 0.9f
            val hPhase = hash(i * 3.73f)
            val hSize = 1.5f + hash(i * 4.19f) * 3.5f

            // Drift upward, wrapping; add a gentle horizontal sway.
            val progress = (phase * hSpeed + hPhase) % 1f
            val y = (1f - progress) * size.height
            val sway = sin((phase + hPhase) * 6.2831853f) * 18f
            val x = hx * size.width + sway

            // Fade in the middle of travel, out at the edges.
            val alpha = (1f - abs(progress - 0.5f) * 2f).coerceIn(0f, 1f) * 0.5f
            drawCircle(
                color = particleColor.copy(alpha = alpha),
                radius = hSize,
                center = Offset(x, y),
            )
        }
    }
}

/** Deterministic pseudo-random in [0,1) from a float seed (fractional sine hash). */
private fun hash(seed: Float): Float {
    val v = sin(seed * 12.9898f) * 43758.547f
    return v - kotlin.math.floor(v)
}
