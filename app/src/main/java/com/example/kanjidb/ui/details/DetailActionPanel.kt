package com.example.kanjidb.ui.details

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kanjidb.R
import com.example.kanjidb.ui.FloatingActionPanel

private val returnToOriginIcon = ImageVector.Builder(
    name = "CornerDownLeftDouble", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f
).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(19f, 5f); verticalLineTo(11f)
        arcTo(3f, 3f, 0f, false, true, 16f, 14f); horizontalLineTo(9f)
        moveTo(13f, 10f); lineTo(9f, 14f); lineTo(13f, 18f)
        moveTo(8f, 10f); lineTo(4f, 14f); lineTo(8f, 18f)
    }
}.build()

/** Both detail screens share the same return control, surface and action geometry. */
@Composable
internal fun DetailActionPanel(
    onReturnToOrigin: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit = {}
) {
    FloatingActionPanel(modifier) {
        OutlinedIconButton(onClick = onReturnToOrigin) {
            Icon(returnToOriginIcon, contentDescription = stringResource(R.string.details_return_to_origin))
        }
        content()
    }
}
