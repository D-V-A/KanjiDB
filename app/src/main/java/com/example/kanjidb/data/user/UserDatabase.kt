package com.example.kanjidb.data.user

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [UserKanjiStateEntity::class], version = 1, exportSchema = true)
abstract class UserDatabase : RoomDatabase() {
    abstract fun kanjiStates(): UserKanjiStateDao

    companion object {
        @Volatile private var instance: UserDatabase? = null

        fun getInstance(context: Context): UserDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, UserDatabase::class.java, "user.db"
            ).build().also { instance = it }
        }
    }
}
