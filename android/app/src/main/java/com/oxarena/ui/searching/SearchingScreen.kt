package com.oxarena.ui.searching

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.oxarena.ui.components.ParticleBackground
import com.oxarena.ui.components.PrimaryButton
import com.oxarena.ui.theme.Danger
import com.oxarena.ui.theme.TextMuted
import com.oxarena.ui.theme.TextPrimary
import com.oxarena.ui.theme.TextSecondary
import com.oxarena.ui.theme.Violet
import kotlinx.coroutines.delay

/**
 * Matchmaking screen — an animated radar sweep, a live elapsed timer, and a cancel
 * affordance. If the queue times out server-side, offers a retry.
 */
@Composable
fun SearchingScreen(
    timedOut: Boolean,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(timedOut) {
        if (!timedOut) {
            elapsed = 0
            while (true) {
                delay(1000)
                elapsed += 1
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        ParticleBackground(particleColor = Violet)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            RadarSweep()
            Spacer(Modifier.height(40.dp))

            if (timedOut) {
                Text("No opponent found", color = TextPrimary, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "The queue timed out. Try again?",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(32.dp))
                PrimaryButton(text = "SEARCH AGAIN", onClick = onRetry)
            } else {
                Text("Searching worldwide…", color = TextPrimary, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Finding an opponent · ${elapsed}s",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "Auto region & latency optimization",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        if (!timedOut) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(28.dp)
                    .align(Alignment.BottomCenter),
            ) {
                PrimaryButton(
                    text = "CANCEL",
                    onClick = onCancel,
                    gradient = listOf(Danger.copy(alpha = 0.85f), Danger.copy(alpha = 0.6f)),
                )
            }
        }
    }
}

@Composable
private fun RadarSweep() {
    val transition = rememberInfiniteTransition(label = "radar")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "angle",
    )
    val ripple by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "ripple",
    )

    Canvas(modifier = Modifier.size(180.dp)) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        // Concentric rings
        for (i in 1..3) {
            drawCircle(
                color = Violet.copy(alpha = 0.18f),
                radius = radius * i / 3f,
                center = center,
                style = Stroke(width = 2f),
            )
        }
        // Expanding ripple
        drawCircle(
            color = Violet.copy(alpha = (1f - ripple) * 0.5f),
            radius = radius * ripple,
            center = center,
            style = Stroke(width = 3f),
        )
        // Sweeping line
        val rad = Math.toRadians(angle.toDouble())
        drawLine(
            color = Violet,
            start = center,
            end = Offset(
                center.x + (radius * kotlin.math.cos(rad)).toFloat(),
                center.y + (radius * kotlin.math.sin(rad)).toFloat(),
            ),
            strokeWidth = 3f,
        )
    }
}
