package com.oxarena.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.oxarena.domain.model.GameSnapshot
import com.oxarena.domain.model.PlayerSymbol
import com.oxarena.ui.theme.Amber
import com.oxarena.ui.theme.MarkO
import com.oxarena.ui.theme.MarkX
import com.oxarena.ui.theme.SurfaceGlass
import com.oxarena.ui.theme.SurfaceStroke

/**
 * The authoritative board. Renders [snapshot] exactly; taps on empty cells are
 * only forwarded when [enabled] (it's the local player's turn and the game is
 * live). Marks animate in, the last move pulses, and the winning line glows.
 */
@Composable
fun GameBoard(
    snapshot: GameSnapshot,
    enabled: Boolean,
    onCellTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val size = snapshot.size
    val winning = snapshot.winningLine.toHashSet()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceGlass)
            .border(1.dp, SurfaceStroke, RoundedCornerShape(24.dp))
            .padding(10.dp),
    ) {
        for (row in 0 until size) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                for (col in 0 until size) {
                    val index = row * size + col
                    Cell(
                        symbol = snapshot.board[index],
                        isWinning = index in winning,
                        isLastMove = snapshot.lastMove == index,
                        enabled = enabled && snapshot.board[index] == null,
                        onTap = { onCellTap(index) },
                        modifier = Modifier.weight(1f).aspectRatio(1f).padding(6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Cell(
    symbol: PlayerSymbol?,
    isWinning: Boolean,
    isLastMove: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appear = remember(symbol) { Animatable(if (symbol == null) 0f else 0f) }
    LaunchedEffect(symbol) {
        if (symbol != null) appear.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
    }

    val borderColor = when {
        isWinning -> Amber
        isLastMove -> SurfaceStroke
        else -> SurfaceStroke
    }
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isWinning) Amber.copy(alpha = 0.12f) else Color(0x0DFFFFFF))
            .border(if (isWinning) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(interaction, null, enabled = enabled) { onTap() },
    ) {
        if (symbol != null) {
            val color = if (symbol == PlayerSymbol.X) MarkX else MarkO
            Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val p = appear.value
                val stroke = Stroke(width = size.minDimension * 0.12f, cap = StrokeCap.Round)
                if (symbol == PlayerSymbol.X) {
                    val s = size.minDimension
                    val o = (size.minDimension) * (1f - p) * 0.5f
                    drawLine(color, Offset(o, o), Offset(s - o, s - o), stroke.width, StrokeCap.Round)
                    drawLine(color, Offset(s - o, o), Offset(o, s - o), stroke.width, StrokeCap.Round)
                } else {
                    val d = size.minDimension * (0.5f + 0.5f * p)
                    val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = 360f * p,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(d, d),
                        style = stroke,
                    )
                }
            }
        }
    }
}
