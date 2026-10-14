package com.example.kanjidb.ui.details

import android.graphics.Path
import android.graphics.PathMeasure
import androidx.core.graphics.PathParser
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kanjidb.R
import com.example.kanjidb.data.dictionary.KanjiStroke
import kotlin.math.floor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

/** Geometry is interpreted once per selected kanji, never during an animation frame. */
internal class StrokeGeometry(pathData: String) {
    val path: Path = checkNotNull(PathParser.createPathFromPathData(pathData))
    private val measure = PathMeasure(path,false)
    val length = measure.length
    init { require(length.isFinite() && length > 0f) { "Invalid stroke geometry" } }
    fun segment(fraction: Float): Path = Path().also {
        measure.getSegment(0f,length*fraction.coerceIn(0f,1f),it,true)
    }
}

@Composable
internal fun StrokeOrderView(strokes: List<KanjiStroke>, modifier: Modifier = Modifier) {
    val paths=remember(strokes) { strokes.map { StrokeGeometry(it.pathData) } }
    val progress=remember(strokes) { Animatable(0f) }
    var replay by remember(strokes) { mutableIntStateOf(0) }
    LaunchedEffect(strokes,replay) {
        while (currentCoroutineContext().isActive) {
            progress.snapTo(0f)
            progress.animateTo(paths.size.toFloat(),tween(paths.size*550,easing=LinearEasing))
            delay(2_000)
        }
    }
    val colors=MaterialTheme.colorScheme
    val replayLabel=stringResource(R.string.details_stroke_replay)
    Box(modifier.clickable(role=Role.Button,onClickLabel=replayLabel) { replay++ }) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val scale=minOf(size.width,size.height)/109f
            withTransform({
                translate((size.width-109f*scale)/2f,(size.height-109f*scale)/2f)
                scale(scale,scale,pivot=androidx.compose.ui.geometry.Offset.Zero)
            }) {
                val completed=floor(progress.value).toInt()
                paths.forEachIndexed { index,geometry ->
                    if(index<completed) drawPath(geometry.path.asComposePath(),colors.onSurface,
                        style=Stroke(3f,cap=androidx.compose.ui.graphics.StrokeCap.Round,
                            join=androidx.compose.ui.graphics.StrokeJoin.Round))
                    else if(index==completed) drawPath(geometry.segment(progress.value-completed).asComposePath(),colors.primary,
                        style=Stroke(3f,cap=androidx.compose.ui.graphics.StrokeCap.Round,
                            join=androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }
        }
        Text(stringResource(R.string.details_stroke_progress,
            minOf(floor(progress.value).toInt()+1,paths.size),paths.size),
            Modifier.align(Alignment.BottomEnd).padding(4.dp),style=MaterialTheme.typography.labelSmall)
    }
}
