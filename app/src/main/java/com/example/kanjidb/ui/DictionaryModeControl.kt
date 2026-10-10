package com.example.kanjidb.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanjidb.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.floor

/** Reuse the shared vector's paths; the mode switch needs a thinner display stroke. */
private fun searchRefreshIcon(): ImageVector {
    val source = AppIcons.RefreshMain
    return ImageVector.Builder(
        name = "SearchRefresh",
        defaultWidth = source.defaultWidth,
        defaultHeight = source.defaultHeight,
        viewportWidth = source.viewportWidth,
        viewportHeight = source.viewportHeight
    ).apply {
        for (node in source.root) {
            val path = node as VectorPath
            addPath(
                pathData = path.pathData,
                pathFillType = path.pathFillType,
                fill = path.fill,
                fillAlpha = path.fillAlpha,
                stroke = path.stroke,
                strokeAlpha = path.strokeAlpha,
                strokeLineWidth = path.strokeLineWidth * (32f / 62.1f),
                strokeLineCap = path.strokeLineCap,
                strokeLineJoin = path.strokeLineJoin,
                strokeLineMiter = path.strokeLineMiter
            )
        }
    }.build()
}

/** Shared Search/Results mode switch; the caller owns the selected presentation. */
@Composable
internal fun DictionaryModeControl(wordMode: Boolean, arrowColor: Color, showLabel: Boolean = false, showSymbol: Boolean = true, enabled: Boolean = true, onSwitch: () -> Boolean) {
    val rotation = remember { Animatable(45f) }
    val scope = rememberCoroutineScope()
    var rotationRunning by remember { mutableStateOf(false) }
    var targetRotation by remember { mutableStateOf(45f) }
    var symbolJob by remember { mutableStateOf<Job?>(null) }
    val symbolOpacity = remember { Animatable(1f) }
    val actionDescription = if (showLabel) {
        if (wordMode) "Words results. Switch to Kanji" else "Kanji results. Switch to Words"
    } else stringResource(if (wordMode) R.string.search_mode_words else R.string.search_mode_kanji)

    val labelStyle = MaterialTheme.typography.headlineMedium
    val textMeasurer = rememberTextMeasurer()
    val labelWidthPx = if (showLabel) maxOf(
        textMeasurer.measure(AnnotatedString("Kanji"), labelStyle, maxLines = 1).size.width,
        textMeasurer.measure(AnnotatedString("Words"), labelStyle, maxLines = 1).size.width
    ) else 0
    val controlWidth = with(LocalDensity.current) { labelWidthPx.toDp() } +
        (if (showSymbol) 64.4.dp else AppActionIconSize) + 6.dp

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (showLabel) 6.dp else 0.dp),
        modifier = (if (showLabel) Modifier.width(controlWidth).heightIn(min = 48.dp) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                enabled = enabled,
                onClick = {
                    onSwitch()
                    // Every tap changes mode, even when the rotation queue is full.
                    if (showSymbol) {
                        symbolJob?.cancel()
                        symbolJob = scope.launch {
                            symbolOpacity.animateTo(0f, tween(130))
                            symbolOpacity.animateTo(1f, tween(130))
                        }
                    }
                    if (!rotationRunning) {
                        rotationRunning = true
                        targetRotation += 180f
                        scope.launch {
                            var endpoint = rotation.value + 180f
                            do {
                                rotation.animateTo(endpoint, tween(260, easing = FastOutSlowInEasing))
                                endpoint += 180f
                            } while (rotation.value < targetRotation)
                            // Normalize both logical angles on the same discrete grid.
                            val normalizedAngle = 45f + (targetRotation - 45f) % 360f
                            rotation.snapTo(normalizedAngle)
                            targetRotation = normalizedAngle
                            rotationRunning = false
                        }
                    } else {
                        // The actual angle determines the active segment, not the distant target.
                        val segment = floor((rotation.value - 45f) / 180f)
                        val maximumTarget = 45f + (segment + 2f) * 180f
                        if (targetRotation < maximumTarget) targetRotation += 180f
                    }
                }
            )
            .semantics { contentDescription = actionDescription }
    ) {
        Box(Modifier.size(if (showSymbol) 64.4.dp else AppActionIconSize), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (showSymbol) remember { searchRefreshIcon() } else AppIcons.RefreshMain,
                contentDescription = null,
                tint = arrowColor,
                // Keep the existing icon slot and control geometry; enlarge only the drawing.
                modifier = (if (showSymbol) Modifier.size(62.1.dp) else Modifier.requiredSize(30.dp)).graphicsLayer { rotationZ = 90f - rotation.value }
            )
            if (showSymbol) Text(
                text = if (wordMode) "\u8A9E" else "\u5B57",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = if (wordMode) 24.15.sp else 25.875.sp,
                    lineHeight = 27.6.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.offset(y = (-1).dp).clearAndSetSemantics {}.graphicsLayer {
                    alpha = symbolOpacity.value
                    scaleX = 0.85f + 0.15f * symbolOpacity.value
                    scaleY = scaleX
                }
            )
        }
        if (showLabel) Text(if (wordMode) "Words" else "Kanji",
            style = labelStyle, maxLines = 1, modifier = Modifier.clearAndSetSemantics {})
    }
}

