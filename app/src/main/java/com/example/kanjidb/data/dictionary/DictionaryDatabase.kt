package com.example.kanjidb.data.dictionary

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DictionaryKanji(
    val character: String,
    val strokeCount: Int?,
    val grade: Int?,
    val frequency: Int?,
    val isJoyo: Boolean,
    val meanings: List<String>,
    val onReadings: List<String>,
    val kunReadings: List<String>,
    val words: List<DictionaryWord>
)

data class DictionaryWord(
    val entryId: Long,
    val written: String,
    val reading: String,
    val meanings: List<String>
)

data class DictionaryWordDetails(
    val entryId: Long,
    val written: String,
    val readings: List<String>,
    val meaningGroups: Map<String, List<String>>,
    val alternativeWrittenForms: List<String>,
    val preferredReading: String
)

/** Independent asset dictionary; never creates or writes SQLite tables. */
class DictionaryDatabase(context: Context) {
    private val context = context.applicationContext

    suspend fun getKanji(character: String): DictionaryKanji? = withContext(Dispatchers.IO) {
        SQLiteDatabase.openDatabase(
            dictionaryFile().absolutePath, null, SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            db.rawQuery(
                "SELECT id, character, stroke_count, grade, frequency, joyo FROM kanji WHERE character = ?",
                arrayOf(character)
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@withContext null
                val id = cursor.getLong(0).toString()
                DictionaryKanji(
                    character = cursor.getString(1),
                    strokeCount = cursor.nullableInt(2),
                    grade = cursor.nullableInt(3),
                    frequency = cursor.nullableInt(4),
                    isJoyo = cursor.getInt(5) == 1,
                    meanings = db.strings(
                        "SELECT meaning FROM kanji_meaning WHERE kanji_id = ? AND language = 'en' ORDER BY id",
                        id
                    ),
                    onReadings = db.strings(
                        "SELECT reading FROM kanji_reading WHERE kanji_id = ? AND type = 'on' ORDER BY id",
                        id
                    ),
                    kunReadings = db.strings(
                        "SELECT reading FROM kanji_reading WHERE kanji_id = ? AND type = 'kun' ORDER BY id",
                        id
                    ),
                    words = getCommonWords(db, id).deduplicateCommonWords()
                )
            }
        }
    }

    suspend fun getWord(entryId: Long, written: String, sourceKanji: String): DictionaryWordDetails? =
        withContext(Dispatchers.IO) {
            SQLiteDatabase.openDatabase(
                dictionaryFile().absolutePath, null, SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                val args = arrayOf(entryId.toString(), written)
                val kanjiId = db.strings("SELECT id FROM kanji WHERE character = ?", sourceKanji)
                    .firstOrNull()
                val group = kanjiId?.let { getCommonWords(db, it).groupCommonWords() }
                    ?.firstOrNull { forms ->
                        forms.any { it.entryId == entryId && it.written == written }
                    }
                val writtenForms = group?.map { it.written }?.toSet() ?: setOf(written)
                val readings = db.rawQuery(WORD_READINGS_SQL, arrayOf(entryId.toString())).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            if (cursor.getString(1) in writtenForms) add(cursor.getString(0))
                        }
                    }.distinct()
                }
                if (readings.isEmpty()) return@withContext null
                val groups = linkedMapOf<String, MutableList<String>>()
                db.rawQuery(WORD_MEANINGS_SQL, args).use { cursor ->
                    while (cursor.moveToNext()) {
                        groups.getOrPut(cursor.getString(0)) { mutableListOf() }
                            .add(cursor.getString(1))
                    }
                }
                DictionaryWordDetails(
                    entryId, written, readings, groups,
                    alternativeWrittenForms = writtenForms.filter { it != written },
                    preferredReading = group?.first()?.reading ?: readings.first()
                )
            }
        }

    private fun getCommonWords(db: SQLiteDatabase, kanjiId: String): List<DictionaryWord> =
        db.rawQuery(COMMON_WORDS_SQL, arrayOf(kanjiId)).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(DictionaryWord(
                        entryId = cursor.getLong(1),
                        written = cursor.getString(2),
                        reading = cursor.getString(3),
                        meanings = db.strings(
                            "SELECT meaning FROM word_meaning WHERE word_form_id = ? AND language = 'en' ORDER BY sense_index, id",
                            cursor.getLong(0).toString()
                        ).distinct()
                    ))
                }
            }
        }

    private fun dictionaryFile(): File = synchronized(copyLock) {
        // Exclude the reproducible asset copy from Android backups.
        val target = File(context.noBackupFilesDir, "dictionary.db")
        if (!target.exists()) {
            val temporary = File(context.noBackupFilesDir, "dictionary.db.tmp")
            try {
                context.assets.open("dictionary.db").use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
                check(temporary.renameTo(target)) { "Cannot install dictionary asset" }
            } finally {
                temporary.delete()
            }
        }
        target
    }

    private fun SQLiteDatabase.strings(sql: String, id: String): List<String> =
        rawQuery(sql, arrayOf(id)).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun Cursor.nullableInt(index: Int): Int? =
        if (isNull(index)) null else getInt(index)

    private companion object {
        val copyLock = Any()

        const val WORD_READINGS_SQL = """
            SELECT reading, written FROM word_form
            WHERE entry_id = ?
            ORDER BY reading_priority DESC, reading_order ASC, id ASC
        """

        // The same gloss is often repeated across valid reading forms.
        const val WORD_MEANINGS_SQL = """
            SELECT wm.language, TRIM(wm.meaning) AS meaning
            FROM word_form wf
            JOIN word_meaning wm ON wm.word_form_id = wf.id
            WHERE wf.entry_id = ? AND wf.written = ?
            GROUP BY wm.language, TRIM(wm.meaning)
            ORDER BY wm.language, MIN(wm.sense_index), MIN(wm.id)
        """

        // GROUP BY also removes repeated occurrences of the kanji in one written form.
        // Correlated selection works on SDK 26 without SQLite window functions.
        const val COMMON_WORDS_SQL = """
            SELECT wf.id, wf.entry_id, wf.written, wf.reading
            FROM (
                SELECT f.entry_id, f.written
                FROM word_kanji wk
                JOIN word_form f ON f.id = wk.word_form_id
                WHERE wk.kanji_id = ? AND f.common = 1
                GROUP BY f.entry_id, f.written
            ) grouped
            JOIN word_form wf ON wf.id = (
                SELECT preferred.id FROM word_form preferred
                WHERE preferred.entry_id = grouped.entry_id
                  AND preferred.written = grouped.written
                  AND preferred.common = 1
                ORDER BY preferred.reading_priority DESC, preferred.reading_order ASC, preferred.id ASC
                LIMIT 1
            )
            ORDER BY wf.written, wf.entry_id
        """
    }
}

/** Keeps the first equivalent form in the existing Common words order. */
internal fun List<DictionaryWord>.deduplicateCommonWords(): List<DictionaryWord> =
    groupCommonWords().map { it.first() }

/** Retains group members without changing representative selection or order. */
internal fun List<DictionaryWord>.groupCommonWords(): List<List<DictionaryWord>> {
    val groups = mutableListOf<MutableList<DictionaryWord>>()
    val representatives = mutableMapOf<Pair<Long, String>, MutableList<Pair<Set<String>, Int>>>()
    for (word in this) {
        val meanings = word.meanings.map { it.trim().lowercase(Locale.ROOT) }.toSet()
        val previous = representatives.getOrPut(word.entryId to word.reading) { mutableListOf() }
        // Compare only with retained forms: similarity is not transitive.
        val match = previous.firstOrNull { (other, _) ->
            val largerSize = maxOf(meanings.size, other.size)
            largerSize > 0 && meanings.intersect(other).size.toDouble() / largerSize >= 0.75
        }
        if (match == null) {
            previous.add(meanings to groups.size)
            groups.add(mutableListOf(word))
        } else {
            groups[match.second].add(word)
        }
    }
    return groups
}
