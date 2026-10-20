package com.example.kanjidb.ui.settings

import com.example.kanjidb.ui.training.availableTrainingPool

internal data class StartupTarget(val route: String, val myKanjiTab: MyKanjiTab)

/** A fresh root only. A missing/empty user pool forces Groups over any remembered tab. */
internal fun resolveStartup(
    preference: StartupScreen, userCharacters: List<String>, available: Set<String>, lastTab: MyKanjiTab
): StartupTarget {
    val hasTrainableUserKanji = availableTrainingPool(userCharacters, available).isNotEmpty()
    return when (preference) {
        StartupScreen.SEARCH -> StartupTarget("search", lastTab)
        StartupScreen.MY_KANJI -> StartupTarget("my_kanji", lastTab)
        StartupScreen.AUTOMATIC, StartupScreen.TRAINING ->
            if (hasTrainableUserKanji) StartupTarget("training", lastTab)
            else StartupTarget("my_kanji", MyKanjiTab.KANJI_GROUPS)
    }
}
