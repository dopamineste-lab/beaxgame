package com.oxarena.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oxarena.domain.model.ClientState
import com.oxarena.domain.model.GameStatus
import com.oxarena.domain.model.PlayerSymbol
import com.oxarena.ui.components.GameBoard
import com.oxarena.ui.components.PrimaryButton
import com.oxarena.ui.theme.Background
import com.oxarena.ui.theme.MarkO
import com.oxarena.ui.theme.MarkX
import com.oxarena.ui.theme.SurfaceGlass
import com.oxarena.ui.theme.TextMuted
import com.oxarena.ui.theme.TextPrimary
import com.oxarena.ui.theme.TextSecondary

/**
 * The live game. Renders the authoritative [ClientState] and forwards cell taps.
 * Turn ownership, enablement, and the end-of-game overlay are all derived from the
 * server state — the client never decides the outcome itself.
 */
@Composable
fun GameScreen(
    state: ClientState,
    onCellTap: (Int) -> Unit,
    onLeave: () -> Unit,
    onContinue: () -> Unit,
) {
    val snapshot = state.snapshot
    val info = state.matchInfo
    if (snapshot == null || info == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading…", color = TextSecondary)
        }
        return
    }

    val you = info.yourSymbol
    val yourTurn = snapshot.turn == you && snapshot.status == GameStatus.ACTIVE
    val gameOver = snapshot.status.isOver || state.opponentLeft

    Box(Modifier.fillMaxSize().background(Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top bar: turn indicator + leave
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TurnIndicator(you = you, yourTurn = yourTurn, active = snapshot.status == GameStatus.ACTIVE)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SurfaceGlass)
                        .clickable { onLeave() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", color = TextSecondary, fontSize = 18.sp)
                }
            }

            GameBoard(
                snapshot = snapshot,
                enabled = yourTurn && !gameOver,
                onCellTap = onCellTap,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            Text(
                text = "You are ${you.wire} · Classic",
                color = TextMuted,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        AnimatedVisibility(visible = gameOver, enter = fadeIn(), exit = fadeOut()) {
            GameOverOverlay(
                outcome = outcomeFor(state),
                onContinue = onContinue,
            )
        }
    }
}

@Composable
private fun TurnIndicator(you: PlayerSymbol, yourTurn: Boolean, active: Boolean) {
    val color = if (you == PlayerSymbol.X) MarkX else MarkO
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (yourTurn && active) color else TextMuted),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = when {
                !active -> "Game over"
                yourTurn -> "Your turn"
                else -> "Opponent's turn"
            },
            color = if (yourTurn && active) TextPrimary else TextSecondary,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun GameOverOverlay(outcome: Outcome, onContinue: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC07070E)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = outcome.title,
                color = outcome.color,
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = outcome.subtitle,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp, bottom = 40.dp),
            )
            Box(modifier = Modifier.clip(RoundedCornerShape(30.dp))) {
                PrimaryButton(text = "CONTINUE", onClick = onContinue)
            }
        }
    }
}

private data class Outcome(val title: String, val subtitle: String, val color: Color)

private fun outcomeFor(state: ClientState): Outcome {
    val you = state.matchInfo?.yourSymbol
    val snap = state.snapshot
    return when {
        state.opponentLeft -> Outcome("YOU WIN", "Opponent left the match", MarkO)
        snap?.status == GameStatus.DRAW -> Outcome("DRAW", "A perfectly matched game", TextPrimary)
        snap?.winner == you -> Outcome("YOU WIN", "Well played!", MarkO)
        snap?.winner != null -> Outcome("DEFEAT", "Better luck next round", MarkX)
        else -> Outcome("GAME OVER", "", TextPrimary)
    }
}
