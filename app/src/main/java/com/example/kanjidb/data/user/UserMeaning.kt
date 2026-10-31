package com.example.kanjidb.data.user

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "kanji_meaning_override")
data class UserMeaning(@PrimaryKey val character: String, val preferred: String? = null, val custom: String? = null)

@Dao
abstract class UserMeaningDao {
    @Query("SELECT * FROM kanji_meaning_override")
    abstract fun observeAll(): Flow<List<UserMeaning>>
    @Query("SELECT * FROM kanji_meaning_override WHERE character = :character")
    abstract suspend fun get(character: String): UserMeaning?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun put(row: UserMeaning)
    @Query("DELETE FROM kanji_meaning_override WHERE character = :character")
    abstract suspend fun delete(character: String)
    @Transaction
    open suspend fun setPreferred(character: String, preferred: String?) {
        save((get(character) ?: UserMeaning(character)).copy(preferred = preferred))
    }
    @Transaction
    open suspend fun setCustom(character: String, custom: String?) {
        save((get(character) ?: UserMeaning(character)).copy(custom = custom?.trim()?.takeIf { it.isNotEmpty() }))
    }
    private suspend fun save(row: UserMeaning) {
        if (row.preferred == null && row.custom == null) delete(row.character) else put(row)
    }
}

/** Retain source ordering except for clearly abbreviated glosses. */
fun automaticMeaning(meanings: List<String>): String? =
    meanings.firstOrNull { it.isNotBlank() && !isAbbreviatedMeaning(it) } ?: meanings.firstOrNull()

fun resolveMeaning(automatic: String?, override: UserMeaning?): String? =
    override?.custom?.takeIf { it.isNotBlank() } ?: override?.preferred?.takeIf { it.isNotBlank() } ?: automatic

internal fun isAbbreviatedMeaning(value: String): Boolean = value.trim().let {
    it.length <= 9 && it.endsWith(".") && !it.contains(' ')
}
