package com.example.kanjidb.data.dictionary

import android.database.sqlite.SQLiteDatabase

data class KanjiStroke(val index: Int, val pathData: String)

internal fun loadKanjiStrokes(db: SQLiteDatabase, kanjiId: String): List<KanjiStroke> {
    // Standalone development fixtures/older dictionaries can lack optional enrichment.
    val exists=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='kanji_stroke'",null)
        .use { it.moveToFirst() }
    if (!exists) return emptyList()
    return db.rawQuery("SELECT stroke_index,path_data FROM kanji_stroke WHERE kanji_id=? ORDER BY stroke_index",
        arrayOf(kanjiId)).use { c -> buildList {
        while(c.moveToNext()) add(KanjiStroke(c.getInt(0),c.getString(1)))
    } }
}
