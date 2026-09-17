package com.example.kanjidb.data.dictionary

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
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
    val words: List<DictionaryWord>,
    val jlpt: Int? = null
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

    /** One bulk query: all JLPT rows for progress, with the shared discovery gate for eligibility. */
    internal suspend fun getRecommendationKanji(): List<RecommendationKanji> = withContext(Dispatchers.IO) {
        SQLiteDatabase.openDatabase(
            dictionaryFile().absolutePath, null, SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            db.rawQuery("""
                SELECT k.character, j.level, k.frequency,
                    CASE WHEN $DISCOVERY_KANJI_GATE THEN 1 ELSE 0 END,
                    COALESCE((SELECT meaning FROM kanji_meaning
                        WHERE kanji_id = k.id AND language = 'en' ORDER BY id LIMIT 1), '')
                FROM kanji k JOIN jlpt_kanji j ON j.kanji_id = k.id
                WHERE j.level BETWEEN 1 AND 5 ORDER BY k.character
            """.trimIndent(), null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        coroutineContext.ensureActive()
                        add(RecommendationKanji(KanjiSummary(cursor.getString(0), cursor.getString(4)),
                            cursor.getInt(1), cursor.nullableInt(2), cursor.getInt(3) == 1))
                    }
                }
            }
        }
    }

    suspend fun getExploreKanji(): List<KanjiSummary> = withContext(Dispatchers.IO) {
        SQLiteDatabase.openDatabase(
            dictionaryFile().absolutePath, null, SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            db.rawQuery(EXPLORE_KANJI_SQL, null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        coroutineContext.ensureActive()
                        add(KanjiSummary(cursor.getString(0), cursor.getString(1)))
                    }
                }
            }
        }
    }

    suspend fun searchKanji(query: String, limit: Int): KanjiSearchPage = withContext(Dispatchers.IO) {
        require(limit in 1 until Int.MAX_VALUE)
        val text = query.trim().lowercase(Locale.ROOT)
        if (text.isEmpty()) return@withContext KanjiSearchPage(emptyList(), false)
        val reading = normalizeReading(text).orEmpty()
        SQLiteDatabase.openDatabase(
            dictionaryFile().absolutePath, null, SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            val args = arrayOf(text, reading, toKatakana(reading), (limit + 1).toString())
            val rows = db.rawQuery(SEARCH_KANJI_SQL, args).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        coroutineContext.ensureActive()
                        add(KanjiSummary(cursor.getString(0), cursor.getString(1)))
                    }
                }
            }
            KanjiSearchPage(rows.take(limit), rows.size > limit)
        }
    }

    suspend fun searchWords(query: String, limit: Int): WordSearchPage = withContext(Dispatchers.IO) {
        require(limit in 1 until Int.MAX_VALUE)
        val text = query.trim().lowercase(Locale.ROOT)
        if (text.isEmpty()) return@withContext WordSearchPage(emptyList(), false)
        val reading = normalizeReading(text).orEmpty()
        val rows = queryWordSummaries(SEARCH_WORDS_SQL,
            arrayOf(text, reading, toKatakana(reading), (limit + 1).toString()))
        WordSearchPage(rows.take(limit), rows.size > limit)
    }

    suspend fun getExploreWords(): List<DictionaryWord> = withContext(Dispatchers.IO) {
        queryWordSummaries(EXPLORE_WORDS_SQL, emptyArray())
    }

    private suspend fun queryWordSummaries(sql: String, args: Array<String>): List<DictionaryWord> =
        SQLiteDatabase.openDatabase(
            dictionaryFile().absolutePath, null, SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            db.rawQuery(sql, args).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        coroutineContext.ensureActive()
                        add(DictionaryWord(cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                            if (cursor.isNull(3)) emptyList() else listOf(cursor.getString(3))))
                    }
                }
            }
        }

    /** Two bulk queries, independent of the number of kanji; no per-card detail/word lookups. */
    suspend fun getKanjiGroups(): List<KanjiGroupEntry> = withContext(Dispatchers.IO) {
        SQLiteDatabase.openDatabase(
            dictionaryFile().absolutePath, null, SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            val kun = mutableMapOf<Long, String>()
            val on = mutableMapOf<Long, String>()
            db.rawQuery("SELECT kanji_id, type, reading FROM kanji_reading ORDER BY id", null).use { cursor ->
                while (cursor.moveToNext()) {
                    coroutineContext.ensureActive()
                    val readings = if (cursor.getString(1) == "kun") kun else on
                    readings.putIfAbsent(cursor.getLong(0), cursor.getString(2))
                }
            }
            db.rawQuery("""
                SELECT k.id, k.character, k.grade, k.frequency, k.stroke_count, k.joyo, j.level
                FROM kanji k LEFT JOIN jlpt_kanji j ON j.kanji_id = k.id
                ORDER BY k.character
            """.trimIndent(), null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        coroutineContext.ensureActive()
                        val id = cursor.getLong(0)
                        add(KanjiGroupEntry(
                            character = cursor.getString(1), reading = kun[id] ?: on[id],
                            grade = cursor.nullableInt(2), frequency = cursor.nullableInt(3),
                            strokeCount = cursor.nullableInt(4), isJoyo = cursor.getInt(5) == 1,
                            jlpt = cursor.nullableInt(6)
                        ))
                    }
                }
            }
        }
    }

    /** Only card readings: no meanings or related words are loaded. Missing entries map to null. */
    suspend fun getCardReadings(characters: List<String>): Map<String, String?> =
        withContext(Dispatchers.IO) {
            if (characters.isEmpty()) return@withContext emptyMap()
            val result = characters.associateWith<String, String?> { null }.toMutableMap()
            SQLiteDatabase.openDatabase(
                dictionaryFile().absolutePath, null, SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                characters.distinct().chunked(900).forEach { chunk ->
                    coroutineContext.ensureActive()
                    val placeholders = chunk.joinToString(",") { "?" }
                    db.rawQuery("""
                        SELECT k.character, COALESCE(
                            (SELECT reading FROM kanji_reading WHERE kanji_id = k.id AND type = 'kun' ORDER BY id LIMIT 1),
                            (SELECT reading FROM kanji_reading WHERE kanji_id = k.id AND type = 'on' ORDER BY id LIMIT 1)
                        ) FROM kanji k WHERE k.character IN ($placeholders)
                    """.trimIndent(), chunk.toTypedArray()).use { cursor ->
                        while (cursor.moveToNext()) {
                            result[cursor.getString(0)] = if (cursor.isNull(1)) null else cursor.getString(1)
                        }
                    }
                }
            }
            result
        }

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
                val wordSection = getKanjiWords(db, id)
                // Older installed dictionary copies may predate the optional JLPT table.
                val hasJlpt = db.rawQuery(
                    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'jlpt_kanji'", null
                ).use { it.moveToFirst() }
                val jlpt = if (hasJlpt) db.rawQuery(
                    "SELECT level FROM jlpt_kanji WHERE kanji_id = ?", arrayOf(id)
                ).use { if (it.moveToFirst()) it.getInt(0) else null } else null
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
                    words = wordSection.deduplicateCommonWords(),
                    jlpt = jlpt
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
                val group = kanjiId?.let { getKanjiWords(db, it).groupCommonWords() }
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

    // Common forms retain their preferred reading and precede all remaining forms.
    private fun getKanjiWords(db: SQLiteDatabase, kanjiId: String): List<DictionaryWord> =
        mergeKanjiWords(getWords(db, kanjiId, commonOnly = true),
            getWords(db, kanjiId, commonOnly = false))

    private fun getWords(db: SQLiteDatabase, kanjiId: String, commonOnly: Boolean): List<DictionaryWord> =
        db.rawQuery(WORDS_SQL, arrayOf(kanjiId, if (commonOnly) "1" else "0",
            if (commonOnly) "1" else "0")).use { cursor ->
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
        const val WORDS_SQL = """
            SELECT wf.id, wf.entry_id, wf.written, wf.reading
            FROM (
                SELECT f.entry_id, f.written
                FROM word_kanji wk
                JOIN word_form f ON f.id = wk.word_form_id
                WHERE wk.kanji_id = ? AND (? = '0' OR f.common = 1)
                GROUP BY f.entry_id, f.written
            ) grouped
            JOIN word_form wf ON wf.id = (
                SELECT preferred.id FROM word_form preferred
                WHERE preferred.entry_id = grouped.entry_id
                  AND preferred.written = grouped.written
                  AND (? = '0' OR preferred.common = 1)
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

internal fun mergeKanjiWords(common: List<DictionaryWord>, all: List<DictionaryWord>): List<DictionaryWord> =
    (common + all).distinctBy { it.entryId to it.written }
