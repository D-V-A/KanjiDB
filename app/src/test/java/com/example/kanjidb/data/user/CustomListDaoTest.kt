package com.example.kanjidb.data.user

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.example.kanjidb.ui.LearningState
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real generated Room DAO and SQLite migrations run on the JVM, without a device or emulator. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CustomListDaoTest {
    private lateinit var db: UserDatabase
    private lateinit var dao: CustomListDao
    private val context: Context get() = RuntimeEnvironment.getApplication()
    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java).allowMainThreadQueries().build()
        dao = db.customLists()
    }
    @After fun close() { db.close() }
    private suspend fun create(name: String = "Difficult") = dao.create(name)
    private suspend fun withKanji(vararg characters: String): Long = create().also {
        dao.setMembership(it, characters.toList(), true)
    }
    private suspend fun draft(selected: List<String> = listOf("日", "月", "火")) = CustomListDraft(dao.getLists(), selected)

    @Test fun createList() = runBlocking {
        val id = create()
        assertEquals(CustomListEntity(id, "Difficult", 0), dao.getLists().single().list)
    }
    @Test fun renameListAndPreserveExactName() = runBlocking {
        val id = create(); dao.rename(id, " New name ")
        assertEquals(" New name ", dao.getLists().single().list.name)
    }
    @Test fun deleteListCascadesOnlyItsMemberships() = runBlocking {
        val id = withKanji("日"); val other = create("Other")
        dao.setMembership(other, listOf("日"), true); dao.delete(id)
        assertTrue(dao.getMemberships(id).isEmpty())
        assertEquals(listOf("日"), dao.getLists().single().characters)
    }
    @Test fun membershipAddRemove() = runBlocking {
        val id = withKanji("日", "月"); dao.setMembership(id, listOf("日"), false)
        assertEquals(listOf("月"), dao.getLists().single().characters)
    }
    @Test fun sameKanjiInMultipleLists() = runBlocking {
        val first = withKanji("日"); val second = create("Second")
        dao.setMembership(second, listOf("日"), true)
        assertEquals(setOf(first, second), dao.getLists().filter { "日" in it.characters }.map { it.list.id }.toSet())
    }
    @Test fun duplicateMembershipPreventionAndIdempotentOrder() = runBlocking {
        val id = withKanji("日", "月"); val original = dao.getMemberships(id)
        dao.setMembership(id, listOf("日", "日"), true)
        assertEquals(original, dao.getMemberships(id))
        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL("INSERT OR IGNORE INTO custom_list_kanji VALUES (?, ?, ?)", arrayOf<Any>(id, "日", 99))
        assertEquals(original, dao.getMemberships(id))
    }
    @Test fun manualListOrderDescending() = runBlocking {
        val first = create("First"); val second = create("Second"); val third = create("Third")
        assertEquals(listOf(third, second, first), dao.getLists().map { it.list.id })
    }
    @Test fun manualKanjiOrderAppendsAndIsIndependentPerList() = runBlocking {
        val first = withKanji("月", "日"); val second = create("Second")
        dao.setMembership(second, listOf("日", "月"), true)
        dao.setMembership(first, listOf("月", "火"), true)
        assertEquals(listOf("月", "日", "火"), dao.getMemberships(first).map { it.character })
        assertEquals(listOf("日", "月"), dao.getMemberships(second).map { it.character })
    }
    @Test fun newListGetsMaximumPlusOneAfterDeletion() = runBlocking {
        val first = create("First"); create("Second"); dao.delete(first)
        val third = create("Third")
        assertEquals(2L, dao.getLists().first { it.list.id == third }.list.manualIndex)
    }
    @Test fun deleteDoesNotAlterLearningKnown() = runBlocking {
        db.kanjiStates().setState(listOf("日"), LearningState.LEARNING)
        db.kanjiStates().setState(listOf("月"), LearningState.KNOWN)
        val before = db.kanjiStates().getAll()
        dao.delete(withKanji("日", "月"))
        assertEquals(before.toSet(), db.kanjiStates().getAll().toSet())
    }
    @Test fun noneToChecked() = runBlocking {
        create(); val draft = draft()
        assertEquals(ListMembershipState.UNCHECKED, draft.state(0)); draft.tap(0)
        assertEquals(ListMembershipState.CHECKED, draft.state(0))
    }
    @Test fun partialToChecked() = runBlocking {
        withKanji("日"); val draft = draft()
        assertEquals(ListMembershipState.PARTIAL, draft.state(0)); draft.tap(0)
        assertEquals(ListMembershipState.CHECKED, draft.state(0))
    }
    @Test fun checkedToUnchecked() = runBlocking {
        withKanji("日", "月", "火"); val draft = draft(); draft.tap(0)
        assertEquals(ListMembershipState.UNCHECKED, draft.state(0))
    }
    @Test fun applyAddsAll() = runBlocking {
        val id = withKanji("日"); val draft = draft(); draft.tap(0)
        dao.applyChanges(draft.selected, draft.targets)
        assertEquals(listOf("日", "月", "火"), dao.getMemberships(id).map { it.character })
    }
    @Test fun applyRemovesAllButNotUnselected() = runBlocking {
        val id = withKanji("日", "月", "火", "水"); val draft = draft(); draft.tap(0)
        dao.applyChanges(draft.selected, draft.targets)
        assertEquals(listOf("水"), dao.getMemberships(id).map { it.character })
    }
    @Test fun cancelLeavesRoomUnchanged() = runBlocking {
        withKanji("日"); val original = dao.getLists(); val draft = draft(); draft.tap(0)
        assertEquals(original, dao.getLists())
    }
    @Test fun untouchedPartialStatePreservedOnApply() = runBlocking {
        withKanji("日"); val original = dao.getLists(); val draft = draft()
        dao.applyChanges(draft.selected, draft.targets)
        assertEquals(original, dao.getLists())
    }
    @Test fun stagedListNotPersistedBeforeMainApply() = runBlocking {
        val draft = draft(); assertNull(draft.create("New list"))
        assertEquals(ListMembershipState.CHECKED, draft.state(0))
        assertTrue(dao.getLists().isEmpty())
    }
    @Test fun mainCancelDiscardsStagedList() = runBlocking {
        var staged: CustomListDraft? = draft(); staged!!.create("New list"); staged = null
        assertNull(staged); assertTrue(dao.getLists().isEmpty())
    }
    @Test fun mainApplyCreatesStagedListAndMemberships() = runBlocking {
        val draft = draft(); draft.create("New list"); dao.applyChanges(draft.selected, draft.targets)
        assertEquals(listOf("日", "月", "火"), dao.getLists().single().characters)
    }
    @Test fun multipleStagedListsUseDisplayedManualOrder() = runBlocking {
        val draft = draft(); draft.create("First"); draft.create("Second")
        dao.applyChanges(draft.selected, draft.targets)
        assertEquals(listOf("Second", "First"), dao.getLists().map { it.list.name })
        assertEquals(listOf(1L, 0L), dao.getLists().map { it.list.manualIndex })
    }
    @Test fun coherentApplyCanAddRemoveAndLeavePartial() = runBlocking {
        val partial = withKanji("日"); val all = create("All"); dao.setMembership(all, listOf("日", "月", "火"), true)
        val none = create("None"); val draft = draft()
        draft.tap(0); draft.tap(1)
        dao.applyChanges(draft.selected, draft.targets)
        assertEquals(listOf("日"), dao.getMemberships(partial).map { it.character })
        assertTrue(dao.getMemberships(all).isEmpty())
        assertEquals(listOf("日", "月", "火"), dao.getMemberships(none).map { it.character })
    }
    @Test fun applyFailureRollsBackStagedCreationAndMemberships() = runBlocking {
        withKanji("日"); val original = dao.getLists()
        try {
            dao.applyChanges(listOf("月"), listOf(CustomListTarget(null, "New list", true), CustomListTarget(999, "Missing", true)))
            fail("Missing list must reject the transaction")
        } catch (_: IllegalArgumentException) {}
        assertEquals(original, dao.getLists())
    }
    @Test fun reorderCancelDoesNotChangeDatabase() = runBlocking {
        create("First"); create("Second"); val original = dao.getLists()
        val preview = original.map { it.list.id }.reversed()
        assertNotEquals(original.map { it.list.id }, preview)
        assertEquals(original, dao.getLists())
    }
    @Test fun reorderApplyPersistsNormalizedDescendingIndices() = runBlocking {
        create("First"); create("Second"); create("Third")
        val original = dao.getLists().map { it.list.id }; val after = original.reversed()
        assertTrue(dao.reorderLists(original, after))
        assertEquals(after, dao.getLists().map { it.list.id })
        assertEquals(listOf(2L, 1L, 0L), dao.getLists().map { it.list.manualIndex })
    }
    @Test fun kanjiReorderPersistsNormalizedAscendingIndices() = runBlocking {
        val id = withKanji("日", "月", "火")
        assertTrue(dao.reorderKanji(id, listOf("日", "月", "火"), listOf("火", "日", "月")))
        assertEquals(listOf("火", "日", "月"), dao.getMemberships(id).map { it.character })
        assertEquals(listOf(0L, 1L, 2L), dao.getMemberships(id).map { it.manualIndex })
    }
    @Test fun staleAndInvalidReordersDoNotWrite() = runBlocking {
        val id = withKanji("日", "月"); val before = dao.getLists()
        assertFalse(dao.reorderKanji(id, listOf("日", "月"), listOf("日", "日")))
        assertFalse(dao.reorderKanji(id, listOf("月", "日"), listOf("日", "月")))
        assertFalse(dao.reorderLists(listOf(id), listOf(id, id)))
        assertEquals(before, dao.getLists())
    }
    @Test fun namesUseBoundParametersAndRejectDuplicates() = runBlocking {
        val name = "x'); DROP TABLE kanji_state;--"
        val id = create(name); assertEquals(name, dao.getLists().single().list.name)
        db.kanjiStates().setState(listOf("日"), LearningState.LEARNING)
        assertEquals(LearningState.LEARNING, db.kanjiStates().getState("日"))
        try { create(" " + name.uppercase() + " "); fail("Duplicate") } catch (_: IllegalArgumentException) {}
        dao.rename(id, name)
    }
    @Test fun membershipsAndIndependentOrdersSurviveDatabaseReopen() = runBlocking {
        val name = "custom-list-reopen-" + System.nanoTime()
        fun openFile() = Room.databaseBuilder(context, UserDatabase::class.java, name).allowMainThreadQueries().build()
        var file = openFile()
        try {
            val first = file.customLists().create("First")
            val second = file.customLists().create("Second")
            file.customLists().setMembership(first, listOf("日", "月"), true)
            file.customLists().setMembership(second, listOf("月", "日"), true)
            file.customLists().reorderLists(listOf(second, first), listOf(first, second))
            file.customLists().reorderKanji(first, listOf("日", "月"), listOf("月", "日"))
            val snapshot = file.customLists().getLists()
            file.close()
            file = openFile()
            assertEquals(snapshot, file.customLists().getLists())
            assertEquals(listOf(first, second), file.customLists().getLists().map { it.list.id })
            assertEquals(listOf("月", "日"), file.customLists().getMemberships(first).map { it.character })
        } finally { file.close(); context.deleteDatabase(name) }
    }

    @Test fun stagedUncheckedListCommitsAsEmpty() = runBlocking {
        val draft = draft(); draft.create("Empty list"); draft.tap(0)
        dao.applyChanges(draft.selected, draft.targets)
        assertTrue(dao.getLists().single().memberships.isEmpty())
    }

    @Test fun stagedCreationChecksOtherStagedNamesForDuplicates() = runBlocking {
        val draft = draft(); assertNull(draft.create(" New list "))
        assertNotNull(draft.create("new LIST"))
        assertEquals(1, draft.targets.size)
        assertTrue(dao.getLists().isEmpty())
    }

    @Test fun membershipChangesLeaveLearningKnownUntouched() = runBlocking {
        db.kanjiStates().setState(listOf("日"), LearningState.LEARNING)
        db.kanjiStates().setState(listOf("月"), LearningState.KNOWN)
        val original = db.kanjiStates().getAll()
        val id = create(); dao.setMembership(id, listOf("日", "月"), true)
        dao.setMembership(id, listOf("日"), false)
        assertEquals(original, db.kanjiStates().getAll())
    }

    @Test fun migrationPreservesLearningKnownAndManualOrder() = runBlocking { migrationCheck(2) }
    @Test fun newTablesWorkAfterMigration() = runBlocking { migrationCheck(2) }
    @Test fun chainedMigrationFromVersionOneWorks() = runBlocking { migrationCheck(1) }

    private suspend fun migrationCheck(version: Int) {
        val name = "custom-list-migration-" + System.nanoTime()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(sqlite: SupportSQLiteDatabase) {
                        val position = if (version == 2) ", manualPosition INTEGER NOT NULL DEFAULT 0" else ""
                        sqlite.execSQL("CREATE TABLE kanji_state (character TEXT NOT NULL PRIMARY KEY, state TEXT NOT NULL" + position + ")")
                        sqlite.execSQL(if (version == 2) "INSERT INTO kanji_state VALUES ('日', 'LEARNING', 7), ('月', 'KNOWN', 4)"
                            else "INSERT INTO kanji_state VALUES ('日', 'LEARNING'), ('月', 'KNOWN')")
                    }
                    override fun onUpgrade(sqlite: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                }).build()
        )
        helper.writableDatabase
        helper.close()
        val migrated = Room.databaseBuilder(context, UserDatabase::class.java, name)
            .allowMainThreadQueries().addMigrations(UserDatabase.MIGRATION_1_2, UserDatabase.MIGRATION_2_3).build()
        try {
            val old = migrated.kanjiStates().getAll().associateBy { it.character }
            assertEquals(LearningState.LEARNING, old.getValue("日").state)
            assertEquals(LearningState.KNOWN, old.getValue("月").state)
            assertEquals(if (version == 2) 7L else 0L, old.getValue("日").manualPosition)
            assertEquals(if (version == 2) 4L else 0L, old.getValue("月").manualPosition)
            assertEquals(3, migrated.openHelper.writableDatabase.version)
            val lists = migrated.customLists()
            val id = lists.create("Migrated list")
            lists.setMembership(id, listOf("日", "月"), true)
            lists.rename(id, "Renamed list")
            assertEquals(listOf("日", "月"), lists.getLists().single().characters)
            lists.delete(id)
            assertTrue(lists.getMemberships(id).isEmpty())
            assertEquals(old, migrated.kanjiStates().getAll().associateBy { it.character })
        } finally { migrated.close(); context.deleteDatabase(name) }
    }
}
