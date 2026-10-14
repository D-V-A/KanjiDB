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
    val jlpt: Int? = null,
    val strokes: List<KanjiStroke> = emptyList()
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
    val primaryReading: String,
    val constituentKanji: List<KanjiSummary>,
    val glosses: List<WordGloss> = emptyList(),
    val readingMeaningGroups: List<ReadingMeaningGroup> = emptyList(),
    val otherEntries: List<DictionaryWord> = emptyList()
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
        val prepared = prepareWordSearchQuery(query)
        val text = prepared.text
        if (text.isEmpty()) return@withContext WordSearchPage(emptyList(), false)
        val reading = prepared.reading
        SQLiteDatabase.openDatabase(
            dictionaryFile().absolutePath, null, SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            val rows = searchWordResults(db, text, reading)
            WordSearchPage(rows.take(limit), rows.size > limit)
        }
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

    suspend fun getKanji(character: String, includeWords: Boolean = true): DictionaryKanji? = withContext(Dispatchers.IO) {
        SQLiteDatabase.openDatabase(
            dictionaryFile().absolutePath, null, SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            db.rawQuery(
                "SELECT id, character, stroke_count, grade, frequency, joyo FROM kanji WHERE character = ?",
                arrayOf(character)
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@withContext null
                val id = cursor.getLong(0).toString()
                // Older installed dictionary copies may predate the optional JLPT table.
                val hasJlpt = db.rawQuery(
                    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'jlpt_kanji'", null
                ).use { it.moveToFirst() }
                val wordSection = if (includeWords) getRelatedWords(db, id, character, hasJlpt) else emptyList()
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
                    words = wordSection,
                    jlpt = jlpt,
                    strokes = loadKanjiStrokes(db, id)
                )
            }
        }
    }

    suspend fun getWord(entryId: Long, written: String): DictionaryWordDetails? = withContext(Dispatchers.IO) {
        SQLiteDatabase.openDatabase(dictionaryFile().absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            loadWordDetails(db, entryId, written)
        }
    }

    internal suspend fun getWordTrainingCandidates(pool: List<String>): List<WordTrainingCandidate> = withContext(Dispatchers.IO) {
        require(pool.isNotEmpty())
        SQLiteDatabase.openDatabase(dictionaryFile().absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            pool.distinct().chunked(900).flatMap { chunk ->
                val selected = wordTrainingFormsSql(chunk.size)
                val hasJlpt = db.rawQuery("SELECT 1 FROM sqlite_master WHERE name='jlpt_kanji'", null).use { it.moveToFirst() }
                loadWordCandidates(db, selected, chunk.toTypedArray(), hasJlpt).map {
                    WordTrainingCandidate(it, it.word.meanings.firstOrNull())
                }
            }.distinctBy { Triple(it.word.entryId, it.word.written, it.word.reading) }
        }
    }

    /** Compact Results metadata, loaded in batches rather than reopening the dictionary per card. */
    internal suspend fun getKanjiSummaries(characters: List<String>): Map<String, KanjiSummary> =
        withContext(Dispatchers.IO) {
            val result = linkedMapOf<String, KanjiSummary>()
            if (characters.isEmpty()) return@withContext result
            SQLiteDatabase.openDatabase(dictionaryFile().absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                characters.distinct().chunked(900).forEach { chunk ->
                    val placeholders = chunk.joinToString(",") { "?" }
                    db.rawQuery("""
                        SELECT k.character, (SELECT meaning FROM kanji_meaning
                            WHERE kanji_id=k.id AND language='en' ORDER BY id LIMIT 1)
                        FROM kanji k WHERE k.character IN ($placeholders)
                    """.trimIndent(), chunk.toTypedArray()).use { cursor ->
                        while (cursor.moveToNext()) {
                            coroutineContext.ensureActive()
                            result[cursor.getString(0)] = KanjiSummary(cursor.getString(0),
                                if (cursor.isNull(1)) "" else cursor.getString(1))
                        }
                    }
                }
            }
            result
        }

    /** Three bulk reads; no per-word/per-kanji lookups, user data or premature limit. */
    private suspend fun getRelatedWords(
        db: SQLiteDatabase, kanjiId: String, character: String, hasJlpt: Boolean
    ): List<DictionaryWord> {
        return RelatedWordsRanker.rank(loadWordCandidates(db, WORDS_SQL, arrayOf(kanjiId, "0"), hasJlpt), character)
    }

    /** The selection SQL supplies valid primary forms; the same feature/gloss reads serve both callers. */
    private suspend fun loadWordCandidates(
        db: SQLiteDatabase, selectedSql: String, args: Array<String>, hasJlpt: Boolean
    ): List<RelatedWordCandidate> {
        val meanings = mutableMapOf<Long, MutableList<String>>()
        db.rawQuery(candidateMeaningsSql(selectedSql, primaryReadingOnly = true), args).use { cursor ->
            while (cursor.moveToNext()) {
                coroutineContext.ensureActive()
                meanings.getOrPut(cursor.getLong(0)) { mutableListOf() }.add(cursor.getString(1))
            }
        }
        val kanji = mutableMapOf<Long, MutableList<RelatedWordKanji>>()
        db.rawQuery(candidateKanjiSql(selectedSql, hasJlpt), args).use { cursor ->
            while (cursor.moveToNext()) {
                coroutineContext.ensureActive()
                kanji.getOrPut(cursor.getLong(0)) { mutableListOf() }.add(
                    RelatedWordKanji(cursor.getString(1), cursor.nullableInt(2), cursor.nullableInt(3))
                )
            }
        }
        val candidates = db.rawQuery(candidateFeaturesSql(selectedSql), args).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    coroutineContext.ensureActive()
                    val formId = cursor.getLong(0)
                    add(RelatedWordCandidate(
                        word = DictionaryWord(cursor.getLong(1), cursor.getString(2), cursor.getString(3),
                            meanings[formId].orEmpty().distinct()),
                        common = cursor.getInt(4) == 1,
                        maxReadingPriority = cursor.getInt(5),
                        primaryReadingCommon = cursor.getInt(6) == 1,
                        primaryReadingOrder = cursor.getInt(7),
                        kanji = kanji[formId].orEmpty(),
                        writtenOrder = cursor.getInt(8),
                        writtenCommon = cursor.getInt(9) == 1
                    ))
                }
            }
        }
        return candidates
    }

    private fun dictionaryFile(): File = DictionaryInstallation.file(context)

    suspend fun getDatabaseVersion(): Int = withContext(Dispatchers.IO) {
        DictionaryInstallation.readVersion(dictionaryFile())
    }

    private fun SQLiteDatabase.strings(sql: String, id: String): List<String> =
        rawQuery(sql, arrayOf(id)).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun Cursor.nullableInt(index: Int): Int? =
        if (isNull(index)) null else getInt(index)

}
