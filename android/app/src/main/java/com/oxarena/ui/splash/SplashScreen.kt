package com.oxarena.ui.splash

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oxarena.ui.components.ParticleBackground
import com.oxarena.ui.theme.MarkO
import com.oxarena.ui.theme.MarkX
import com.oxarena.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Animated splash. Holds for a brief, deliberate beat (brand moment) while the
 * anonymous session connects in the background, then hands off to Home.
 */
@Composable
fun SplashScreen(connected: Boolean, onReady: () -> Unit) {
    var navigated by remember { mutableStateOf(false) }
    LaunchedEffect(connected) {
        delay(1500) // minimum on-screen time for polish
        if (!navigated) {
            navigated = true
            onReady()
        }
    }

    val transition = rememberInfiniteTransition(label = "splash")
    val pulse by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ParticleBackground()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                text = buildLogo(),
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.scale(pulse),
            )
            Text(
                text = "ARENA",
                color = TextSecondary,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun buildLogo() = androidx.compose.ui.text.buildAnnotatedString {
    pushStyle(androidx.compose.ui.text.SpanStyle(color = MarkX))
    append("O")
    pop()
    pushStyle(androidx.compose.ui.text.SpanStyle(color = MarkO))
    append("X")
    pop()
}
