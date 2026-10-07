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
import com.example.kanjidb.R
import com.example.kanjidb.ui.AppIcons

/** One display-mode action: the thumb covers the inactive representation. */
@Composable
internal fun GlyphStrokesSelector(showStrokes: Boolean, onShowStrokes: (Boolean) -> Unit) {
    val glyph = stringResource(R.string.details_glyph)
    val strokes = stringResource(R.string.details_strokes)
    val current = if (showStrokes) strokes else glyph
    val next = if (showStrokes) glyph else strokes
    val colors = MaterialTheme.colorScheme
    val toggle = { onShowStrokes(!showStrokes) }
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(interactionSource = null, indication = null, onClick = toggle)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = "$glyph / $strokes"
                stateDescription = current
                onClick(label = next) { toggle(); true }
            },
        contentAlignment = Alignment.Center
    ) {
        val trackWidth = minOf(104.dp, maxWidth)
        val thumbOffset by animateDpAsState(
            targetValue = if (showStrokes) 3.dp else trackWidth - 37.dp,
            animationSpec = tween(durationMillis = 180), label = "Inactive representation cover"
        )
        Box(Modifier.size(trackWidth, 40.dp).clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerHighest)) {
            Box(Modifier.align(Alignment.CenterStart).padding(start = 3.dp).size(34.dp),
                contentAlignment = Alignment.Center) {
                Icon(AppIcons.Eye, contentDescription = null, modifier = Modifier.requiredSize(35.dp),
                    tint = if (!showStrokes) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.4f))
            }
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 3.dp).size(34.dp),
                contentAlignment = Alignment.Center) {
                Icon(AppIcons.Brush, contentDescription = null, modifier = Modifier.size(29.dp),
                    tint = if (showStrokes) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.4f))
            }
            Box(Modifier.offset(x = thumbOffset, y = 3.dp).size(34.dp)
                .background(colors.outlineVariant, CircleShape))
        }
    }
}
