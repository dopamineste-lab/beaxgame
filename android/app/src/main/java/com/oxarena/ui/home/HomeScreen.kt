package com.oxarena.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oxarena.ui.components.ParticleBackground
import com.oxarena.ui.components.PrimaryButton
import com.oxarena.ui.theme.MarkO
import com.oxarena.ui.theme.MarkX
import com.oxarena.ui.theme.SurfaceGlass
import com.oxarena.ui.theme.TextMuted
import com.oxarena.ui.theme.TextSecondary

/**
 * Home. No login, no menus to wade through — one prominent Play button that drops
 * the player straight into worldwide matchmaking.
 */
@Composable
fun HomeScreen(playerId: String?, onPlay: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        ParticleBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.height(24.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = MarkX)) { append("O") }
                        withStyle(SpanStyle(color = MarkO)) { append("X") }
                    },
                    fontSize = 88.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "ARENA",
                    color = TextSecondary,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Real-time · Worldwide · Instant",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            ModeChip()

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                PrimaryButton(text = "PLAY", onClick = onPlay)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = playerId?.let { "You · ${it.take(8)}" } ?: "Connecting…",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun ModeChip() {
    Box(
        modifier = Modifier
            .background(SurfaceGlass, RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = "CLASSIC MODE",
            color = TextSecondary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
