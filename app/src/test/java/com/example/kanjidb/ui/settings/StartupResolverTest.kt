package com.example.kanjidb.ui.settings

import com.example.kanjidb.data.user.*
import org.junit.Assert.*
import org.junit.Test

class StartupResolverTest {
    private val missing = String(Character.toChars(0x20000))
    private val dictionary = setOf("日", "月")
    private fun resolve(data: FullBackupData, screen: StartupScreen = StartupScreen.AUTOMATIC,
        lastTab: MyKanjiTab = MyKanjiTab.MY_LISTS) = resolveStartup(screen, data.characters, dictionary, lastTab)
    private fun listData(vararg characters: String) = FullBackupData(emptyList(),
        listOf(BackupList(1, "A list", 0, characters.mapIndexed { index, character -> BackupMember(character, index.toLong()) })))

    @Test fun automaticEmptyDatabaseForcesGroupsOverRememberedTab() {
        assertEquals(StartupTarget("my_kanji", MyKanjiTab.KANJI_GROUPS), resolve(FullBackupData(emptyList(), emptyList())))
    }
    @Test fun emptyCustomListIsNotATrainableUserPool() {
        assertEquals("my_kanji", resolve(listData()).route)
        assertEquals(MyKanjiTab.KANJI_GROUPS, resolve(listData()).myKanjiTab)
    }
    @Test fun missingStatesAndMembershipsForceGroups() {
        val data = listData(missing).copy(states = listOf(BackupState(missing, "KNOWN", 0)))
        assertEquals(StartupTarget("my_kanji", MyKanjiTab.KANJI_GROUPS), resolve(data))
    }
    @Test fun learningAndKnownEachAllowAutomaticTraining() {
        for (state in listOf("LEARNING", "KNOWN")) {
            val data = FullBackupData(listOf(BackupState("日", state, 0)), emptyList())
            assertEquals("training", resolve(data).route)
        }
    }
    @Test fun validListOnlyMemberAllowsTrainingEvenWithMissingMembers() {
        assertEquals("training", resolve(listData(missing, "月")).route)
    }
    @Test fun explicitSearchNeverDependsOnUserData() {
        assertEquals("search", resolve(listData(), StartupScreen.SEARCH).route)
        assertEquals("search", resolve(listData("日"), StartupScreen.SEARCH).route)
    }
    @Test fun explicitMyKanjiRetainsLastTabEvenWithoutTrainableData() {
        for (tab in MyKanjiTab.entries) {
            assertEquals(StartupTarget("my_kanji", tab), resolve(listData(), StartupScreen.MY_KANJI, tab))
        }
    }
    @Test fun explicitTrainingHasSameTrainabilityAndGroupsFallback() {
        assertEquals("training", resolve(listData("日"), StartupScreen.TRAINING).route)
        assertEquals(StartupTarget("my_kanji", MyKanjiTab.KANJI_GROUPS), resolve(listData(missing), StartupScreen.TRAINING))
    }
}
