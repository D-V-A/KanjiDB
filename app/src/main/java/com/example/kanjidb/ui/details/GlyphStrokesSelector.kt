package com.example.kanjidb.ui.details

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.kanjidb.R
import com.example.kanjidb.ui.AppIcons

/** One display-mode action: the thumb covers the inactive representation. */
@Composable
internal fun GlyphStrokesSelector(showStrokes: Boolean, onShowStrokes: (Boolean) -> Unit) {
    val glyph = stringResource(R.string.details_glyph)
    val strokes = stringResource(R.string.details_strokes)
    TwoIconSelector(showStrokes, onShowStrokes, AppIcons.Eye, AppIcons.Brush, glyph, strokes,
        modifier = Modifier.fillMaxWidth(), trackWidth = 104.dp, trackHeight = 40.dp,
        inset = 3.dp, leftIconSize = 35.dp, rightIconSize = 29.dp)
}

/** Shared display selector: one tap zone and a sliding opaque cover over the inactive icon. */
@Composable
internal fun TwoIconSelector(
    rightSelected: Boolean, onSelect: (Boolean) -> Unit,
    leftIcon: ImageVector, rightIcon: ImageVector, leftLabel: String, rightLabel: String,
    modifier: Modifier = Modifier, trackWidth: Dp = 52.dp, trackHeight: Dp = 24.dp,
    inset: Dp = 2.dp, leftIconSize: Dp = 20.dp, rightIconSize: Dp = 20.dp
) {
    val current = if (rightSelected) rightLabel else leftLabel
    val next = if (rightSelected) leftLabel else rightLabel
    val colors = MaterialTheme.colorScheme
    val toggle = { onSelect(!rightSelected) }
    BoxWithConstraints(
        modifier = modifier.heightIn(min = 48.dp)
            .clickable(interactionSource = null, indication = null, onClick = toggle)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = "$leftLabel / $rightLabel"
                stateDescription = current
                onClick(label = next) { toggle(); true }
            },
        contentAlignment = Alignment.Center
    ) {
        val width = minOf(trackWidth, maxWidth)
        val thumbSize = trackHeight - inset * 2
        val thumbOffset by animateDpAsState(
            targetValue = if (rightSelected) inset else width - thumbSize - inset,
            animationSpec = tween(durationMillis = 180), label = "Inactive representation cover"
        )
        Box(Modifier.size(width, trackHeight).clip(RoundedCornerShape(trackHeight / 2))
            .background(colors.surfaceContainerHighest)) {
            Box(Modifier.align(Alignment.CenterStart).padding(start = inset).size(thumbSize),
                contentAlignment = Alignment.Center) {
                Icon(leftIcon, contentDescription = null, modifier = Modifier.requiredSize(leftIconSize),
                    tint = if (!rightSelected) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.4f))
            }
            Box(Modifier.align(Alignment.CenterEnd).padding(end = inset).size(thumbSize),
                contentAlignment = Alignment.Center) {
                Icon(rightIcon, contentDescription = null, modifier = Modifier.requiredSize(rightIconSize),
                    tint = if (rightSelected) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.4f))
            }
            Box(Modifier.offset(x = thumbOffset, y = inset).size(thumbSize)
                .background(colors.outlineVariant, CircleShape))
        }
    }
}
