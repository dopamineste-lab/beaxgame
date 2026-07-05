package com.oxarena.ui.matchfound

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oxarena.domain.model.MatchInfo
import com.oxarena.domain.model.PlayerSymbol
import com.oxarena.ui.components.ParticleBackground
import com.oxarena.ui.theme.MarkO
import com.oxarena.ui.theme.MarkX
import com.oxarena.ui.theme.SurfaceGlass
import com.oxarena.ui.theme.TextMuted
import com.oxarena.ui.theme.TextPrimary
import com.oxarena.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * "Opponent found" reveal followed by a 3-2-1 countdown, then hands off to the
 * game. (A voice-channel connect step slots in here on the roadmap.)
 */
@Composable
fun MatchFoundScreen(matchInfo: MatchInfo, onCountdownComplete: () -> Unit) {
    var count by remember { mutableIntStateOf(3) }

    LaunchedEffect(matchInfo.matchId) {
        delay(800) // reveal beat
        while (count > 0) {
            delay(750)
            count -= 1
        }
        onCountdownComplete()
    }

    val you = matchInfo.yourSymbol
    val opponent = if (you == PlayerSymbol.X) PlayerSymbol.O else PlayerSymbol.X

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ParticleBackground()
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("OPPONENT FOUND", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(28.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                SymbolBadge(you, "YOU")
                Text(
                    "VS",
                    color = TextMuted,
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                SymbolBadge(opponent, "RIVAL")
            }

            Spacer(Modifier.height(48.dp))

            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    (scaleIn(tween(250)) + fadeIn()) togetherWith fadeOut(tween(200))
                },
                label = "countdown",
            ) { c ->
                Text(
                    text = if (c > 0) "$c" else "GO",
                    color = TextPrimary,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Black,
                )
            }

            Text(
                "Voice channel · coming soon",
                color = TextMuted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

@Composable
private fun SymbolBadge(symbol: PlayerSymbol, label: String) {
    val color = if (symbol == PlayerSymbol.X) MarkX else MarkO
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(SurfaceGlass),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol.wire, color = color, fontSize = 44.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(10.dp))
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
    }
}
