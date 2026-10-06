package com.example.kanjidb.data.dictionary

/** Conservative numeral+counter recognition shared by dictionary and training flows. */
internal object CounterConstructions {
    // Exact suffixes only. Calendar/time/age counters have too many lexical ambiguities for v1.
    private val counters = setOf("本", "人", "個", "枚", "匹", "台", "冊", "回", "階")
    private val digits = "一二三四五六七八九"
    // Deliberately recognize only canonical kanji numerals 1..99, not arbitrary digit strings.
    // In particular 万人 (everybody) and 千六本 (a lexicalized term) must not collapse.
    private val kanjiNumerals = buildSet {
        digits.forEach { add(it.toString()) }
        for (tens in 1..9) {
            val prefix = (if (tens == 1) "" else digits[tens - 1].toString()) + "十"
            add(prefix)
            digits.forEach { add(prefix + it) }
        }
    }

    fun family(written: String): String? {
        val counter = counters.firstOrNull { written.endsWith(it) } ?: return null
        val number = written.dropLast(counter.length)
        val decimal = number.isNotEmpty() && number.all { it in '0'..'9' || it in '０'..'９' }
        return if (decimal || number in kanjiNumerals) "#$counter" else null
    }

    fun numeralKanji(written: String): Set<String> =
        if (family(written) == null) emptySet() else written.dropLast(1).map { it.toString() }
            .filter { it.single() in digits || it == "\u5341" }.toSet()
}
