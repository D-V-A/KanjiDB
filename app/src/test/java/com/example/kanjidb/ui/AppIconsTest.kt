package com.example.kanjidb.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconsTest {
    @Test fun everyLazySvgDefinitionParsesWithoutRuntimeErrors() {
        // The build does not instantiate lazy vectors; parse every approved SVG here.
        val icons = listOf(
            AppIcons.TextQuestion, AppIcons.Backspace, AppIcons.Search, AppIcons.Vocabulary, AppIcons.PencilCheck, AppIcons.Eye,
            AppIcons.Brush, AppIcons.Notebook, AppIcons.ListLetters, AppIcons.ListSearch,
            AppIcons.FilterDown, AppIcons.FilterUp, AppIcons.Sheets, AppIcons.TransferVertical,
            AppIcons.SortDescending, AppIcons.SortAscending, AppIcons.CircleChevronLeft,
            AppIcons.FilterOff, AppIcons.CirclePlay, AppIcons.CircleX, AppIcons.InfoSquareRounded, AppIcons.Cross, AppIcons.Check,
            AppIcons.AlertCircle, AppIcons.Rotate, AppIcons.CircleCheck, AppIcons.Refresh,
            AppIcons.RefreshDot, AppIcons.RefreshAlert, AppIcons.Repeat, AppIcons.CornerDownLeftDouble
        )
        icons.forEach { icon -> assertTrue("Empty SVG: " + icon.name, icon.root.size > 0) }
    }
}
