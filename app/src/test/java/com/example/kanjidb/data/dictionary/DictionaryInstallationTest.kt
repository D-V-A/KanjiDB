package com.example.kanjidb.data.dictionary

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DictionaryInstallationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun decisionsCoverMissingLegacyCurrentUpgradeAndDowngrade() {
        assertTrue(shouldInstallDictionary(null, 1))
        assertTrue(shouldInstallDictionary(0, 1))
        assertFalse(shouldInstallDictionary(1, 1))
        assertTrue(shouldInstallDictionary(1, 2))
        assertFalse(shouldInstallDictionary(2, 1))
    }

    @Test fun malformedVersionsFail() {
        for (value in listOf("", "0", "-1", "one", "1.0", "2147483648")) {
            assertTrue(runCatching { parseDictionaryVersion(value) }.isFailure)
        }
        assertEquals(1, parseDictionaryVersion("1\n"))
    }

    @Test fun failuresPreserveOldFileAndCleanTemporaryArtifacts() {
        for (failure in listOf("copy", "validation", "install")) {
            val target = folder.newFile("$failure.db").apply { writeText("old") }
            val result = runCatching {
                replaceDictionary(target, 1,
                    copy = { it.writeText("new"); if (failure == "copy") error("copy failed") },
                    validate = { if (failure == "validation") 0 else 1 },
                    install = { _, _ -> error("install failed") })
            }
            assertTrue(result.isFailure)
            assertEquals("old", target.readText())
            assertFalse(folder.root.listFiles()!!.any { it.name.startsWith("dictionary-") })
        }
    }

    @Test fun successfulReplacementPublishesOnlyValidatedCopy() {
        val target = folder.newFile("dictionary.db").apply { writeText("old") }
        replaceDictionary(target, 2, copy = { it.writeText("new") }, validate = {
            assertEquals("old", target.readText())
            assertEquals("new", it.readText())
            2
        })
        assertEquals("new", target.readText())
        assertEquals(listOf(target.name), folder.root.listFiles()!!.map(File::getName))
    }
}
