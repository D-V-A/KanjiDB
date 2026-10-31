package com.example.kanjidb.data.user

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.example.kanjidb.ui.LearningState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class UserMeaningTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Test fun resolverUsesCustomThenPreferredThenAutomatic() {
        assertEquals("Personal", resolveMeaning("Auto", UserMeaning("\u5b89", "Cheap", "Personal")))
        assertEquals("Cheap", resolveMeaning("Auto", UserMeaning("\u5b89", "Cheap")))
        assertEquals("Auto", resolveMeaning("Auto", null))
        assertNull(resolveMeaning(null, null))
        assertEquals("Auto", resolveMeaning("Auto", UserMeaning("\u5b89", " ", "")))
    }
    @Test fun automaticPreservesOrderExceptObviousAbbreviations() {
        assertEquals("institution", automaticMeaning(listOf("Inst.", "institution", "temple")))
        assertEquals("Relax", automaticMeaning(listOf("Relax", "Cheap")))
        assertEquals("Don", automaticMeaning(listOf("Don", "Wear")))
        assertEquals("Inst.", automaticMeaning(listOf("Inst.")))
        assertEquals("A full sentence.", automaticMeaning(listOf("A full sentence.", "next")))
        assertNull(automaticMeaning(emptyList()))
    }
    @Test fun summarySqlAndFullDetailsChooseTheSameAutomaticMeaning() {
        android.database.sqlite.SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE kanji_meaning (id INTEGER PRIMARY KEY, meaning TEXT)")
            listOf("Inst.", "institution", "temple").forEachIndexed { index, text ->
                db.execSQL("INSERT INTO kanji_meaning VALUES (?, ?)", arrayOf<Any>(index, text))
            }
            db.rawQuery("SELECT meaning FROM kanji_meaning ORDER BY ${com.example.kanjidb.data.dictionary.KANJI_PRIMARY_MEANING_ORDER} LIMIT 1", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(automaticMeaning(listOf("Inst.", "institution", "temple")), it.getString(0))
            }
        }
    }
    @Test fun editingAndDeletingPreserveIndependentFieldsAndBackup() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.meanings()
            dao.setPreferred("\u5b89", "cheap")
            dao.setCustom("\u5b89", "  My meaning  ")
            assertEquals("My meaning", resolveMeaning("relax", dao.get("\u5b89")))
            assertTrue(db.backup().observeHasData().first())
            assertTrue(db.backup().snapshot().collectionCharacters.isEmpty())
            val snapshot = db.backup().snapshot()
            val encoded = UserBackupFormat.encode(UserBackup("v0.10.3", 65, "2026-10-31T12:00:00Z", snapshot))
            val decoded = UserBackupFormat.decode(encoded).data
            assertEquals(snapshot, decoded)
            db.backup().replace(FullBackupData(emptyList(), emptyList()))
            assertNull(dao.get("\u5b89"))
            db.backup().replace(decoded)
            assertEquals(UserMeaning("\u5b89", "cheap", "My meaning"), dao.get("\u5b89"))
            dao.setCustom("\u5b89", null)
            assertEquals("cheap", resolveMeaning("relax", dao.get("\u5b89")))
            dao.setPreferred("\u5b89", null)
            assertNull(dao.get("\u5b89"))
            assertEquals("relax", resolveMeaning("relax", dao.get("\u5b89")))
            assertFalse(db.backup().observeHasData().first())
        } finally { db.close() }
    }
    @Test fun oldBackupWithoutMeaningsImportsAndReplacesOverrides() = runBlocking {
        val legacy = """{"formatVersion":1,"backupType":"full","appVersionName":"v0.10.2","appVersionCode":64,"exportedAt":"2026-10-31T12:00:00Z","data":{"states":[{"character":"\u5b89","state":"KNOWN","manualPosition":8}],"lists":[]}}"""
        val data = UserBackupFormat.decode(legacy).data
        assertTrue(data.meanings.isEmpty())
        val db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.meanings().setCustom("\u5b89", "Personal")
            db.backup().replace(data)
            assertNull(db.meanings().get("\u5b89"))
            assertEquals(LearningState.KNOWN, db.kanjiStates().getState("\u5b89"))
        } finally { db.close() }
    }
    @Test fun malformedOverridesRejectedBeforeReplacement() {
        fun file(records: String) = """{"formatVersion":2,"backupType":"full","appVersionName":"v0.10.3","appVersionCode":65,"exportedAt":"2026-10-31T12:00:00Z","data":{"states":[],"lists":[],"meanings":$records}}"""
        for (records in listOf("""[{"character":"\u5b89","preferred":null,"custom":" "}]""",
            """[{"character":"\u5b89","preferred":null,"custom":null}]""",
            """[{"character":"\u5b89","preferred":5,"custom":null}]""",
            """[{"character":"\u5b89","preferred":"cheap","custom":null},{"character":"\u5b89","preferred":"safe","custom":null}]""")) {
            assertThrows(BackupException::class.java) { UserBackupFormat.decode(file(records)) }
        }
    }
    @Test fun migrationFromV3PreservesAllOldTablesAndAllowsNewData() = runBlocking {
        val name = "meaning-migration-" + System.nanoTime()
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE kanji_state (character TEXT NOT NULL PRIMARY KEY, state TEXT NOT NULL, manualPosition INTEGER NOT NULL DEFAULT 0)")
                    UserDatabase.MIGRATION_2_3.migrate(db)
                    db.execSQL("INSERT INTO kanji_state VALUES ('\u5b89', 'LEARNING', 7), ('\u7740', 'KNOWN', 3)")
                    db.execSQL("INSERT INTO custom_list VALUES (4, 'Personal list', 5)")
                    db.execSQL("INSERT INTO custom_list_kanji VALUES (4, '\u5b89', 9)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        helper.writableDatabase
        helper.close()
        val db = Room.databaseBuilder(context, UserDatabase::class.java, name).allowMainThreadQueries()
            .addMigrations(UserDatabase.MIGRATION_3_4).build()
        try {
            assertEquals(7L, db.kanjiStates().getAll().first { it.character == "\u5b89" }.manualPosition)
            assertEquals(LearningState.KNOWN, db.kanjiStates().getState("\u7740"))
            val lists = db.customLists().getLists()
            assertEquals("Personal list", lists.single().list.name)
            assertEquals(9L, lists.single().memberships.single().manualIndex)
            db.meanings().setPreferred("\u5b89", "cheap")
            db.meanings().setCustom("\u7740", "wear")
            assertEquals("cheap", db.meanings().get("\u5b89")?.preferred)
            assertEquals("wear", db.meanings().get("\u7740")?.custom)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
