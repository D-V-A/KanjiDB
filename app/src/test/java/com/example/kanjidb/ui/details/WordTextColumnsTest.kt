package com.example.kanjidb.ui.details

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WordTextColumnsTest {
    private val style=TextStyle(fontSize=16.sp)
    private fun measurer(fontScale: Float=1f)=TextMeasurer(
        defaultFontFamilyResolver=createFontFamilyResolver(RuntimeEnvironment.getApplication()),
        defaultDensity=Density(1f,fontScale),defaultLayoutDirection=LayoutDirection.Ltr)

    @Test fun shortReadingsAndMeaningsKeepTwoColumnsAtNormalAnd110Percent() {
        for(scale in listOf(1f,1.1f)) {
            val m=measurer(scale)
            assertEquals(2,wordTextColumnCount(listOf("ひ","にち"),400,12,m){ _,_->style })
            assertEquals(2,wordTextColumnCount(listOf("day","sun"),400,12,m){ _,_->style })
        }
    }
    @Test fun oneLongItemSwitchesTheWholeReadingsOrMeaningsBlock() {
        val m=measurer()
        assertEquals(1,wordTextColumnCount(listOf("ひ","とてもながいよみかたとてもながいよみかた"),160,12,m){ _,_->style })
        assertEquals(1,wordTextColumnCount(listOf("sun","a very long additional English meaning that wraps"),240,12,m){ _,_->style })
    }
    @Test fun actualFontScaleChangesTheDecisionAtTheFitBoundary() {
        val text="Sunday reading"
        val normal=measurer()
        val width=normal.measure(AnnotatedString(text),style,softWrap=false).size.width
        assertEquals(2,wordTextColumnCount(listOf(text),width*2+12,12,normal){ _,_->style })
        assertEquals(1,wordTextColumnCount(listOf(text),width*2+12,12,measurer(1.1f)){ _,_->style })
    }
    @Test fun boldHighlightIsMeasuredWithTheSameStyleAsRendering() {
        val m=measurer()
        val bold=style.copy(fontWeight=FontWeight.Bold)
        val text="bold highlighted meaning"
        val width=m.measure(AnnotatedString(text),bold,softWrap=false).size.width
        assertEquals(2,wordTextColumnCount(listOf(text),width*2+12,12,m){ _,_->bold })
        assertEquals(1,wordTextColumnCount(listOf(text),(width-1)*2+12,12,m){ _,_->bold })
    }
    @Test fun ExplicitNewlineAndTooNarrowWidthUseOneColumn() {
        val m=measurer()
        assertEquals(1,wordTextColumnCount(listOf("day\nsun"),400,12,m){ _,_->style })
        assertEquals(1,wordTextColumnCount(listOf("day"),10,12,m){ _,_->style })
    }
}
