package com.pixelplay.dotsboxes.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelplay.dotsboxes.domain.model.GameState
import com.pixelplay.dotsboxes.domain.model.PlayerType
import com.pixelplay.dotsboxes.presentation.theme.Player1Blue
import com.pixelplay.dotsboxes.presentation.theme.Player2Orange

@Composable
fun ScoreBoard(
    state: GameState,
    isAiThinking: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerCard(
            name       = state.p1Name,
            score      = state.p1Score,
            color      = Player1Blue,
            isActive   = !state.isGameOver && state.currentPlayer == PlayerType.ONE && !isAiThinking,
            modifier   = Modifier.weight(1f)
        )

        // Centre divider with total boxes info
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                "vs",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
            )
            Text(
                "${state.p1Score + state.p2Score}/${state.totalBoxes}",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(0.35f)
            )
        }

        PlayerCard(
            name       = state.p2Name,
            score      = state.p2Score,
            color      = Player2Orange,
            isActive   = !state.isGameOver && state.currentPlayer == PlayerType.TWO,
            modifier   = Modifier.weight(1f),
            alignEnd   = true
        )
    }
}

@Composable
private fun PlayerCard(
    name: String,
    score: Int,
    color: Color,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false
) {
    val bgAlpha by animateColorAsState(
        targetValue = if (isActive) color.copy(alpha = 0.18f) else Color.Transparent,
        animationSpec = tween(300),
        label = "bg"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgAlpha)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start
        ) {
            if (!alignEnd) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(color, androidx.compose.foundation.shape.CircleShape)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                color = if (isActive) color else MaterialTheme.colorScheme.onSurface.copy(0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (alignEnd) {
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(8.dp)
                        .background(color, androidx.compose.foundation.shape.CircleShape)
                )
            }
        }

        Text(
            "$score",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color      = color
            )
        )
    }
}
