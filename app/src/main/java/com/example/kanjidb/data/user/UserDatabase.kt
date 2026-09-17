package com.example.kanjidb.data.user

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [UserKanjiStateEntity::class], version = 2, exportSchema = true)
abstract class UserDatabase : RoomDatabase() {
    abstract fun kanjiStates(): UserKanjiStateDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE kanji_state ADD COLUMN manualPosition INTEGER NOT NULL DEFAULT 0")
                // Preserve the previous default character ordering independently in each state.
                db.execSQL("""
                    UPDATE kanji_state SET manualPosition = (
                        SELECT COUNT(*) FROM kanji_state AS earlier
                        WHERE earlier.state = kanji_state.state AND earlier.character < kanji_state.character
                    )
                """.trimIndent())
            }
        }

        @Volatile private var instance: UserDatabase? = null

        fun getInstance(context: Context): UserDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, UserDatabase::class.java, "user.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
