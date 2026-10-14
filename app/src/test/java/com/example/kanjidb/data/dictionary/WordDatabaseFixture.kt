package com.example.kanjidb.data.dictionary

import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Exercises the actual v2 DDL and queries, with explicit source applicability fixtures. */
internal class WordDatabaseFixture(val db: SQLiteDatabase) {
    init {
        db.execSQL("CREATE TABLE kanji (id INTEGER PRIMARY KEY,character TEXT UNIQUE,frequency INTEGER)")
        db.execSQL("CREATE TABLE kanji_meaning (id INTEGER PRIMARY KEY,kanji_id INTEGER,language TEXT,meaning TEXT)")
        val schema=listOf(File("tools/dictionary_word_schema.sql"),File("../tools/dictionary_word_schema.sql"))
            .first { it.exists() }.readText()
        schema.split(';').filter { it.isNotBlank() }.forEach { db.execSQL(it) }
        db.setForeignKeyConstraintsEnabled(true)
    }

    fun form(id: Int,entry: Long,written: String,reading: String,order: Int=0,common: Boolean=false,priority: Int=0,writtenOrder: Int=0) {
        db.execSQL("INSERT OR IGNORE INTO word_entry VALUES (?)",arrayOf<Any>(entry))
        db.execSQL("INSERT OR IGNORE INTO word_written(text) VALUES (?)",arrayOf<Any>(written))
        val wid=db.rawQuery("SELECT id FROM word_written WHERE text=?",arrayOf<String>(written)).use { it.moveToFirst(); it.getLong(0) }
        db.execSQL("INSERT OR IGNORE INTO word_entry_written VALUES (?,?,?,?)",arrayOf<Any>(entry,wid,writtenOrder,if(common) 1 else 0))
        db.execSQL("INSERT OR IGNORE INTO word_reading(entry_id,text,reading_order,common,reading_priority) VALUES (?,?,?,?,?)",
            arrayOf<Any>(entry,reading,order,if(common) 1 else 0,priority))
        val rid=db.rawQuery("SELECT id FROM word_reading WHERE entry_id=? AND reading_order=?",arrayOf<String>(entry.toString(),order.toString()))
            .use { it.moveToFirst(); it.getLong(0) }
        db.execSQL("INSERT INTO word_entry_written_reading VALUES (?,?,?,?)",arrayOf<Any>(id,entry,wid,rid))
    }

    fun meaning(form: Int,index: Int,text: String): Long {
        val eid=db.rawQuery("SELECT entry_id FROM word_entry_written_reading WHERE id=?",arrayOf<String>(form.toString()))
            .use { it.moveToFirst(); it.getLong(0) }
        db.execSQL("INSERT OR IGNORE INTO word_sense(entry_id,sense_index) VALUES (?,?)",arrayOf<Any>(eid,index))
        val sid=db.rawQuery("SELECT id FROM word_sense WHERE entry_id=? AND sense_index=?",arrayOf<String>(eid.toString(),index.toString()))
            .use { it.moveToFirst(); it.getLong(0) }
        val count=db.rawQuery("SELECT COUNT(*) FROM word_meaning WHERE sense_id=?",arrayOf<String>(sid.toString())).use { it.moveToFirst(); it.getInt(0) }
        db.execSQL("INSERT OR IGNORE INTO word_meaning(sense_id,language,meaning,gloss_order) VALUES (?,'en',?,?)",arrayOf<Any>(sid,text,count))
        db.execSQL("INSERT OR IGNORE INTO word_sense_form VALUES (?,?,?)",arrayOf<Any>(eid,sid,form))
        return db.rawQuery("SELECT id FROM word_meaning WHERE sense_id=? AND meaning=?",arrayOf<String>(sid.toString(),text)).use { it.moveToFirst(); it.getLong(0) }
    }

    fun kanji(written: String,character: String,position: Int): Long {
        db.execSQL("INSERT OR IGNORE INTO kanji(character,frequency) VALUES (?,1)",arrayOf<Any>(character))
        val kid=db.rawQuery("SELECT id FROM kanji WHERE character=?",arrayOf<String>(character)).use { it.moveToFirst(); it.getLong(0) }
        val wid=db.rawQuery("SELECT id FROM word_written WHERE text=?",arrayOf<String>(written)).use { it.moveToFirst(); it.getLong(0) }
        db.execSQL("INSERT OR IGNORE INTO word_written_kanji VALUES (?,?,?)",arrayOf<Any>(wid,kid,position))
        return kid
    }
}
