package com.example.kanjidb.data.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KanjiSearchTest {
    @Test fun equivalentScriptsAndRomaji() {
        listOf(
            "yama" to "\u3084\u307e",
            "san" to "\u3055\u3093",
            "gakkou" to "\u304c\u3063\u3053\u3046",
            "shou" to "\u3057\u3087\u3046"
        ).forEach { (romaji, kana) ->
            assertEquals(kana, normalizeReading(romaji))
            assertEquals(kana, normalizeReading(kana))
            assertEquals(kana, normalizeReading(toKatakana(kana)))
        }
    }
    @Test fun handlesNAndDictionaryNotation() {
        assertEquals("\u3053\u3093\u306b\u3061\u306f", normalizeReading("konnichiha"))
        assertEquals("\u3057\u3093\u3088\u3046", normalizeReading("shin'you"))
        assertEquals("\u3093", normalizeReading("nn"))
        assertEquals("\u305f\u3079\u308b", normalizeReading("-\u305f.\u3079\u308b"))
        assertEquals("\u3084\u307e", normalizeReading(" YAMA "))
        assertEquals("\u3084\u307e", normalizeReading("\uff94\uff8f"))
    }
    @Test fun doesNotPartiallyConvertEnglishOrInvalidInput() {
        assertNull(normalizeReading("mount"))
        assertNull(normalizeReading("sh"))
        assertNull(normalizeReading("%"))
        assertNull(normalizeReading(""))
    }
}
