package com.example.kanjidb.ui.details

import android.graphics.PathMeasure
import android.database.sqlite.SQLiteDatabase
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StrokeGeometryTest {
    @Test fun progressiveSegmentFollowsStartToEndAndClampsProgress() {
        val stroke=StrokeGeometry("M10 20L30 20")
        assertEquals(20f,stroke.length,0.001f)
        val half=PathMeasure(stroke.segment(0.5f),false)
        assertEquals(10f,half.length,0.001f)
        val point=FloatArray(2)
        assertTrue(half.getPosTan(half.length,point,null))
        assertArrayEquals(floatArrayOf(20f,20f),point,0.001f)
        assertEquals(0f,PathMeasure(stroke.segment(-1f),false).length,0.001f)
        assertEquals(20f,PathMeasure(stroke.segment(2f),false).length,0.001f)
    }
    @Test fun allBundledStrokePathsAreRenderableContinuousAndNonzero() {
        val file=listOf(File("src/main/assets/dictionary.db"),File("app/src/main/assets/dictionary.db")).first { it.exists() }
        SQLiteDatabase.openDatabase(file.canonicalPath,null,SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT path_data FROM kanji_stroke",null).use { c ->
                var count=0
                while(c.moveToNext()) {
                    val geometry=StrokeGeometry(c.getString(0))
                    assertTrue(geometry.length>0f)
                    val measure=PathMeasure(geometry.path,false)
                    assertFalse("Multiple contours in bundled stroke",measure.nextContour())
                    count++
                }
                assertTrue("Bundled dictionary must contain real KanjiVG strokes",count>0)
            }
        }
    }
}
