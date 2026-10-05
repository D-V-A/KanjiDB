package com.example.kanjidb.data.user

import org.junit.Assert.*
import org.junit.Test

class CustomListNameTest {
    private val existing = listOf(CustomListEntity(1, "My List", 0))
    @Test fun twoCodePointsValid() { assertNull(customListNameError("😀日", emptyList())) }
    @Test fun oneCodePointInvalid() { assertNotNull(customListNameError("😀", emptyList())) }
    @Test fun over32Invalid() { assertNotNull(customListNameError("😀".repeat(33), emptyList())) }
    @Test fun exactly32Valid() { assertNull(customListNameError("😀".repeat(32), emptyList())) }
    @Test fun whitespaceOnlyInvalid() { assertNotNull(customListNameError(" \t\n", emptyList())) }
    @Test fun duplicateTrimInvalid() { assertNotNull(customListNameError(" My List ", existing)) }
    @Test fun duplicateCaseInvalid() { assertNotNull(customListNameError(" MY LIST ", existing)) }
    @Test fun originalPreserved() {
        val name = " My List "
        val draft = CustomListDraft(emptyList(), listOf("日"))
        assertNull(draft.create(name))
        assertEquals(name, draft.targets.single().name)
    }
    @Test fun renameDoesNotConflictWithItself() { assertNull(customListNameError("My List ", existing, 1)) }
    @Test fun renameStillConflictsWithAnotherList() { assertNotNull(customListNameError("my list", existing, 2)) }
    @Test fun sqlSyntaxIsData() { assertNull(customListNameError("x'); DROP TABLE kanji_state;--", emptyList())) }
}
