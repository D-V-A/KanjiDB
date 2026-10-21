package com.example.kanjidb.data.user

import androidx.room.Room
import com.example.kanjidb.ui.LearningState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class UserBackupTest {
    private val missing = String(Character.toChars(0x20000))
    private fun backup() = UserBackup("v0.10.0", 58, "2026-10-20T12:00:00Z", FullBackupData(
        listOf(BackupState("日", "LEARNING", 7), BackupState(missing, "KNOWN", 2)),
        listOf(BackupList(8, "Mixed list", 6, listOf(BackupMember(missing, 9), BackupMember("日", 12))),
            BackupList(3, "Empty list", 1, emptyList()))))
    private fun json() = UserBackupFormat.encode(backup())
    private fun rejected(text: String) {
        assertThrows(BackupException::class.java) { UserBackupFormat.decode(text) }
    }

    @Test fun fullRoundtripPreservesUnicodeStatesEmptyListsIdsAndOrdering() {
        assertEquals(backup(), UserBackupFormat.decode(json()))
        val result = UserBackupFormat.decode(json())
        assertEquals(listOf(missing), result.data.missingCharacters(setOf("日")))
        assertEquals(missing, result.data.lists.first().members.first().character)
        assertEquals(1, result.data.states.last().character.codePointCount(0, missing.length))
        assertTrue(result.data.hasData)
    }

    @Test fun fullV1DispatchUsesFormatRatherThanAppVersion() {
        val root = JSONObject(json()).put("appVersionName", "other-app-version").put("appVersionCode", 999)
        assertEquals(backup().data, UserBackupFormat.decode(root.toString()).data)
    }
    @Test fun unsupportedVersionRejected() = rejected(JSONObject(json()).put("formatVersion", 2).toString())
    @Test fun importCopyDistinguishesInvalidStructureFromUnsupportedFormat() {
        for (text in listOf("{", "{}", JSONObject(json()).put("data", JSONObject()).toString())) {
            val error = assertThrows(BackupException::class.java) { UserBackupFormat.decode(text) }
            assertEquals("This data file seems corrupted or invalid. Please check its internal structure.", error.message)
        }
        val error = assertThrows(BackupException::class.java) {
            UserBackupFormat.decode(JSONObject(json()).put("formatVersion", 2).toString())
        }
        assertEquals("This data file uses an unsupported format version.", error.message)
    }
    @Test fun unsupportedTypeRejected() = rejected(JSONObject(json()).put("backupType", "custom-list").toString())
    @Test fun fileExtensionIsRequiredIndependentOfMime() {
        UserBackupFormat.validateFileName("Backup.kdb")
        UserBackupFormat.validateFileName("Backup.KDB")
        for (name in listOf(null, "Backup.json", "Backup.kdb.json", "Backup")) {
            assertThrows(BackupException::class.java) { UserBackupFormat.validateFileName(name) }
        }
    }
    @Test fun requiredFieldsRejected() {
        for (key in listOf("formatVersion", "backupType", "appVersionName", "appVersionCode", "exportedAt", "data")) {
            rejected(JSONObject(json()).apply { remove(key) }.toString())
        }
    }
    @Test fun emptyAndMalformedJsonRejected() {
        listOf("", " ", "{", "null", "[]", "{foo:1}", json() + "{}",
            json().replaceFirst("\"formatVersion\": 1", "\"formatVersion\": 1, \"formatVersion\": 1"))
            .forEach(::rejected)
    }
    @Test fun invalidTypesAndPayloadRejected() {
        rejected(JSONObject(json()).put("formatVersion", "1").toString())
        rejected(JSONObject(json()).put("formatVersion", 1.5).toString())
        rejected(JSONObject(json()).put("exportedAt", "yesterday").toString())
        rejected(JSONObject(json()).put("data", JSONObject()).toString())
        rejected(json().replace("LEARNING", "NONE"))
        rejected(json().replace("\"manualPosition\": 7", "\"manualPosition\": -1"))
    }
    @Test fun duplicateStatesListsMembershipsAndInvalidNamesRejected() {
        val original = backup()
        listOf(
            original.copy(data = original.data.copy(states = original.data.states + original.data.states.first())),
            original.copy(data = original.data.copy(lists = original.data.lists + original.data.lists.first())),
            original.copy(data = original.data.copy(lists = listOf(original.data.lists.first().let {
                it.copy(members = it.members + it.members.first())
            }))),
            original.copy(data = original.data.copy(lists = listOf(original.data.lists.first().copy(name = " "))))
        ).forEach { rejected(UserBackupFormat.encode(it)) }
    }
    @Test fun invalidUnicodeAndMultipleCharactersRejected() {
        for (character in listOf("", "日月", "abc", "\uD840", "\n", " ")) {
            val invalid = backup().copy(data = FullBackupData(listOf(BackupState(character, "KNOWN", 0)), emptyList()))
            rejected(UserBackupFormat.encode(invalid))
        }
    }
    @Test fun newerCjkExtensionsDoNotDependOnPlatformUnicodeRecognition() {
        val character = String(Character.toChars(0x31350))
        val backup = backup().copy(data = FullBackupData(listOf(BackupState(character, "KNOWN", 0)), emptyList()))
        assertEquals(backup, UserBackupFormat.decode(UserBackupFormat.encode(backup)))
    }
    @Test fun anyPersistentDataEnablesExportIncludingEmptyListAndMissingOnlyState() {
        assertFalse(FullBackupData(emptyList(), emptyList()).hasData)
        assertTrue(FullBackupData(listOf(BackupState(missing, "KNOWN", 0)), emptyList()).hasData)
        assertTrue(FullBackupData(emptyList(), listOf(BackupList(1, "Empty", 0, emptyList()))).hasData)
    }

    @Test fun actualRoomReplacementSnapshotAndObserversPreserveAllData() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), UserDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.backup()
            assertFalse(dao.observeHasData().first())
            val oldList = db.customLists().create("Old list")
            db.customLists().setMembership(oldList, listOf("月"), true)
            db.kanjiStates().setState(listOf("月"), LearningState.KNOWN)
            val incoming = UserBackupFormat.decode(json()).data
            dao.replace(incoming)
            assertTrue(dao.observeHasData().first())
            assertEquals(incoming.states.toSet(), dao.snapshot().states.toSet())
            assertEquals(incoming.lists, dao.snapshot().lists)
            assertEquals(setOf("日", missing), db.kanjiStates().observeAll().first().map { it.character }.toSet())
            assertEquals(listOf(missing, "日"), db.customLists().observeLists().first().first().characters)
            assertEquals(LearningState.KNOWN, db.kanjiStates().getState(missing))
            db.customLists().setMembership(3, listOf(missing), true)
            assertEquals(listOf(missing), db.customLists().getMemberships(3).map { it.character })
            assertTrue(db.customLists().create("Created after import") > 8)
            dao.replace(FullBackupData(emptyList(), emptyList()))
            assertFalse(dao.observeHasData().first())
        } finally { db.close() }
    }

    @Test fun roomTransactionFailureRollsBackDeletionAndPartialInserts() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), UserDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.backup()
            dao.replace(backup().data)
            val before = dao.snapshot()
            // A database constraint fails after the delete and the first insertion.
            val invalid = backup().data.copy(lists = listOf(backup().data.lists.first(), backup().data.lists.first()))
            assertTrue(runCatching { dao.replace(invalid) }.isFailure)
            assertEquals(before, dao.snapshot())
        } finally { db.close() }
    }
}
