package com.example.kanjidb.data.user

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [UserKanjiStateEntity::class, CustomListEntity::class, CustomListKanjiEntity::class], version = 3, exportSchema = true)
abstract class UserDatabase : RoomDatabase() {
    abstract fun kanjiStates(): UserKanjiStateDao
    abstract fun customLists(): CustomListDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS custom_list (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, manualIndex INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS custom_list_kanji (listId INTEGER NOT NULL, character TEXT NOT NULL, manualIndex INTEGER NOT NULL, PRIMARY KEY(listId, character), FOREIGN KEY(listId) REFERENCES custom_list(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_custom_list_kanji_listId ON custom_list_kanji (listId)")
            }
        }

        @Volatile private var instance: UserDatabase? = null

        fun getInstance(context: Context): UserDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, UserDatabase::class.java, "user.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
