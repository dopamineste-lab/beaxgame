package com.oxarena.ui.result

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oxarena.domain.model.ClientState
import com.oxarena.domain.model.GameStatus
import com.oxarena.ui.components.ParticleBackground
import com.oxarena.ui.components.PrimaryButton
import com.oxarena.ui.theme.Amber
import com.oxarena.ui.theme.MarkO
import com.oxarena.ui.theme.MarkX
import com.oxarena.ui.theme.SurfaceGlass
import com.oxarena.ui.theme.TextMuted
import com.oxarena.ui.theme.TextPrimary
import com.oxarena.ui.theme.TextSecondary

/**
 * Post-match summary: outcome, XP earned (placeholder economy — see roadmap), and
 * the choice to queue straight into another match or return home.
 */
@Composable
fun ResultScreen(
    state: ClientState,
    onNextMatch: () -> Unit,
    onHome: () -> Unit,
) {
    val result = resultOf(state)

    Box(Modifier.fillMaxSize()) {
        ParticleBackground(particleColor = result.accent)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.height(40.dp))

            val transition = rememberInfiniteTransition(label = "result")
            val pulse by transition.animateFloat(
                initialValue = 0.96f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
                label = "pulse",
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = result.title,
                    color = result.accent,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.scale(pulse),
                )
                Text(
                    text = result.subtitle,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // XP / reward card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceGlass)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("XP EARNED", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                Text(
                    "+${result.xp}",
                    color = Amber,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "Ranked progression · coming soon",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Column(Modifier.fillMaxWidth()) {
                PrimaryButton(text = "NEXT MATCH", onClick = onNextMatch)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Home",
                    color = TextSecondary,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color(0x11FFFFFF))
                        .clickable { onHome() }
                        .padding(vertical = 16.dp),
                )
            }
        }
    }
}

private data class MatchResult(val title: String, val subtitle: String, val accent: Color, val xp: Int)

private fun resultOf(state: ClientState): MatchResult {
    val you = state.matchInfo?.yourSymbol
    val snap = state.snapshot
    return when {
        state.opponentLeft -> MatchResult("VICTORY", "Opponent left the match", MarkO, 20)
        snap?.status == GameStatus.DRAW -> MatchResult("DRAW", "Evenly matched", TextPrimary, 15)
        snap?.winner == you -> MatchResult("VICTORY", "Flawless play", MarkO, 25)
        snap?.winner != null -> MatchResult("DEFEAT", "Regroup and go again", MarkX, 10)
        else -> MatchResult("MATCH OVER", "", TextPrimary, 10)
    }
}
