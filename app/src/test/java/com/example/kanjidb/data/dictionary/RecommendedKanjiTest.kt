package com.example.kanjidb.data.dictionary

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
import kotlin.math.sqrt

class RecommendedKanjiTest {
    private fun progress(vararg values: Double) = jlptOrder.mapIndexed { i, level -> level to (values.getOrNull(i) ?: 0.0) }.toMap()
    private fun rows(level: Int, count: Int) = (0 until count).map {
        RecommendationKanji(KanjiSummary("$level:$it", "Meaning"), level, if (it % 3 == 0) null else it + 1, true)
    }
    private fun chars(rows: List<RecommendationKanji>) = rows.map { it.character }.toSet()

    @Test fun openingIsChainedAndThresholdInclusive() {
        assertEquals(listOf(5), openLevels(progress()))
        assertEquals(listOf(5), openLevels(progress(0.2499, 0.0, 0.0, 0.0, 1.0)))
        assertEquals(listOf(5, 4), openLevels(progress(0.25)))
        assertEquals(listOf(5, 4, 3), openLevels(progress(0.6, 0.3, 0.05, 0.9, 1.0)))
    }
    @Test fun effectiveWeightsUseCorrectedPreviousRecursively() {
        assertEquals(listOf(0.6, 0.3, 0.05), effectiveWeights(progress(0.6, 0.3, 0.05)).values.toList())
        val weights = effectiveWeights(progress(0.3, 0.6, 0.25)).values.toList()
        listOf(0.3, 0.24, 0.192, 0.0).zip(weights).forEach { (a,b) -> assertEquals(a,b,1e-12) }
        assertTrue(weights.zipWithNext().all { (a,b) -> a >= b })
    }
    @Test fun progressIncludesBothOwnedStatesAndIgnoresNonJlptAndQuality() {
        val rows = rows(5, 100).mapIndexed { i, row -> row.copy(eligible = i >= 10) } + rows(1,100)
        // The Room adapter supplies the union of Learning and Known character keys.
        val owned = chars(rows.take(25)) + "no-jlpt" + "1:0"
        val p = jlptProgress(rows, owned)
        assertEquals(0.25,p.getValue(5),0.0)
        assertEquals(0.01,p.getValue(1),0.0)
        assertEquals(listOf(5,4),openLevels(p))
    }
    @Test fun quotasRoundExactlyAndRedistributeShortage() {
        assertEquals(mapOf(5 to 31,4 to 16,3 to 3), recommendationQuotas(mapOf(5 to 0.6,4 to 0.3,3 to 0.05),mapOf(5 to 100,4 to 100,3 to 100),50))
        val q = recommendationQuotas(mapOf(5 to 0.9,4 to 0.1),mapOf(5 to 11,4 to 100),50)
        assertEquals(mapOf(5 to 11,4 to 39),q)
        assertEquals(50,q.values.sum())
        assertTrue(q.values.all { it >= 0 })
        assertEquals(7,recommendationQuotas(mapOf(5 to 0.0),mapOf(5 to 7),50).values.sum())
    }
    @Test fun quotaPropertiesAcrossCapacitiesAndWeights() {
        val random = Random(12)
        repeat(500) {
            val capacity = jlptOrder.associateWith { random.nextInt(101) }
            val weights = jlptOrder.associateWith { random.nextDouble() }
            val q = recommendationQuotas(weights,capacity,50)
            assertEquals(minOf(50,capacity.values.sum()),q.values.sum())
            assertTrue(q.all { (level,n) -> n in 0..capacity.getValue(level) })
            assertTrue(capacity.filterValues { it <= 10 }.all { (level,n) -> q[level] == n })
        }
    }
    @Test fun bootstrapQualityExclusionAndUniqueness() {
        val all = rows(5,100) + rows(4,100)
        val session = RecommendedKanjiSession(all + all,Random(1))
        session.update(emptySet())
        assertEquals(50,session.pool.size)
        assertTrue(session.pool.all { it.level == 5 })
        val bad = all.take(10).map { it.copy(eligible=false) }
        val owned = chars(all.subList(10,30))
        val filtered = RecommendedKanjiSession(bad + all.drop(10),Random(2))
        filtered.update(owned)
        assertEquals(50,chars(filtered.pool).size)
        assertTrue(filtered.pool.all { it.eligible && it.character !in owned && it.character !in chars(bad) })
    }
    @Test fun tailsAreMandatoryButElevenIsNotATail() {
        val q = recommendationQuotas(mapOf(5 to 0.01,4 to 0.99),mapOf(5 to 10,4 to 100),50)
        assertEquals(10,q[5])
        assertTrue(recommendationQuotas(mapOf(5 to 0.01,4 to 0.99),mapOf(5 to 11,4 to 100),50).getValue(5) < 11)
        val all = rows(5,100)+rows(4,100)+rows(3,100)
        val owned = chars(all.take(93)) + chars(all.filter { it.level==4 }.take(70))
        val session = RecommendedKanjiSession(all,Random(3))
        session.update(owned)
        assertTrue(session.pool.containsAll(all.filter { it.level==5 && it.character !in owned }))
        assertEquals(50,session.pool.size)
    }
    @Test fun frequencyDirectionNullAndEqualRanks() {
        assertEquals(1.0,normalizedFrequency(1,1,100),0.0)
        assertEquals(0.05,normalizedFrequency(100,1,100),1e-12)
        assertEquals(0.05,normalizedFrequency(null,1,100),0.0)
        assertEquals(1.0,normalizedFrequency(4,4,4),0.0)
        assertEquals(sqrt(0.05),frequencyWeight(null,1,100),0.0)
        assertTrue(frequencyWeight(1,1,100)>frequencyWeight(100,1,100))
        assertTrue(frequencyWeight(100,1,100)>0)
    }
    @Test fun weightedSamplingIsSeededUniqueAndRareCanWin() {
        val all = rows(5,40)
        assertEquals(frequencySample(all,20,Random(42)),frequencySample(all,20,Random(42)))
        assertEquals(40,chars(frequencySample(all+all,100,Random(2))).size)
        val winners = (0..1000).flatMap { frequencySample(all,1,Random(it)) }
        assertEquals(chars(all),chars(winners))
    }
    @Test fun threeScreensAvoidTenHistoryAndOlderCardsBecomeEligible() {
        val s = RecommendedKanjiSession(rows(5,100),Random(5)); s.update(emptySet())
        val a=chars(s.visible); s.refresh(); val b=chars(s.visible); s.refresh(); val c=chars(s.visible)
        assertTrue(a.intersect(b).isEmpty()); assertTrue(a.intersect(c).isEmpty()); assertTrue(b.intersect(c).isEmpty())
        assertEquals(b+c,s.recent.toSet()); assertEquals(10,s.recent.size)
        val later = mutableSetOf<String>()
        repeat(30) { s.refresh(); later.addAll(chars(s.visible)) }
        assertTrue(later.intersect(a).isNotEmpty())
    }
    @Test fun tinyPoolFallbackNeverDuplicates() {
        for (size in 0..14) {
            val s=RecommendedKanjiSession(rows(5,size),Random(size)); s.update(emptySet())
            repeat(5) {
                s.refresh(); assertEquals(minOf(5,size),s.visible.size)
                assertEquals(s.visible.size,chars(s.visible).size); assertTrue(s.recent.size<=10)
            }
            s.visible.firstOrNull()?.let { s.returnedFromDetails(it.character) }
            assertEquals(minOf(5,size),s.visible.size)
        }
    }
    @Test fun updatesRetainUnaffectedAndUseNewProgressWithoutReinsertion() {
        val all=rows(5,100)+rows(4,100)
        val s=RecommendedKanjiSession(all,Random(8)); s.update(emptySet())
        val before=s.pool
        // Bulk ownership opens N4 and removes many pool members at once.
        val owned=chars(before.take(30)) + chars(all.filter { it.level == 4 }.take(20))
        s.update(owned)
        assertTrue(s.pool.containsAll(before.drop(30)))
        assertEquals(50,chars(s.pool).size)
        assertTrue(chars(s.pool).intersect(owned).isEmpty())
        assertTrue(s.pool.any { it.level==4 })
        val updated=s.pool
        s.update(owned) // Learning <-> Known leaves the union unchanged.
        assertEquals(updated,s.pool)
        s.update(emptySet()) // Remove -> NONE does not insert.
        assertEquals(updated,s.pool)
    }
    @Test fun detailsReturnReplacesOnlyOpenedSlotAndRecordsOutgoing() {
        val s=RecommendedKanjiSession(rows(5,100),Random(10));s.update(emptySet())
        val before=s.visible; val opened=before[2].character; val pool=s.pool
        s.returnedFromDetails(opened)
        assertNotEquals(opened,s.visible[2].character)
        listOf(0,1,3,4).forEach { assertEquals(before[it],s.visible[it]) }
        assertEquals(pool,s.pool);assertEquals(5,chars(s.visible).size)
        assertTrue(opened in s.recent);assertTrue(s.recent.size<=10)
    }
    @Test fun roomReplacesVisibleOnceAndBackDoesNotReplaceAgain() {
        val s=RecommendedKanjiSession(rows(5,100),Random(11));s.update(emptySet())
        val before=s.visible;val opened=before[2].character
        s.update(setOf(opened))
        val updated=s.visible
        assertFalse(opened in chars(updated));assertEquals(5,chars(updated).size)
        listOf(0,1,3,4).forEach { assertEquals(before[it],updated[it]) }
        s.returnedFromDetails(opened);assertEquals(updated,s.visible)
        s.returnedFromDetails("not-a-recommendation");assertEquals(updated,s.visible)
    }
    @Test fun bulkVisibleRemovalPreservesUnaffectedSlotsAndHandlesExhaustion() {
        val s=RecommendedKanjiSession(rows(5,100),Random(20));s.update(emptySet())
        val before=s.visible
        val owned=setOf(before[0].character,before[3].character,before[4].character)
        val retained=s.pool.filterNot { it.character in owned }
        s.update(owned)
        assertTrue(s.pool.containsAll(retained))
        assertEquals(50,s.pool.size)
        assertEquals(before[1],s.visible[1]);assertEquals(before[2],s.visible[2])
        assertTrue(chars(s.visible).intersect(owned).isEmpty())
        assertEquals(5,chars(s.visible).size)
        s.update(chars(rows(5,100)))
        assertTrue(s.pool.isEmpty());assertTrue(s.visible.isEmpty())
        s.update(emptySet());assertTrue(s.pool.isEmpty())
    }
    @Test fun unopenedLevelsWithPriorKnowledgeOnlyAffectReplacementsWhenChainOpens() {
        val all=rows(5,100)+rows(4,100)+rows(1,100)
        val existingKnowledge=chars(all.filter { it.level == 4 }.take(20)) + chars(all.filter { it.level == 1 }.take(90))
        val s=RecommendedKanjiSession(all,Random(21));s.update(existingKnowledge)
        assertTrue(s.pool.all { it.level==5 })
        // Own exactly 25 N5, including only one pool member: only that slot may adapt.
        val removed=s.pool.first()
        val extra=all.filter { it.level==5 && it.character !in chars(s.pool) }.take(24)
        val retained=s.pool.drop(1)
        s.update(existingKnowledge+chars(extra)+removed.character)
        assertTrue(s.pool.containsAll(retained));assertEquals(50,s.pool.size)
        assertEquals(4,s.pool.last().level)
        assertFalse(s.pool.any { it.level==1 })
    }
    @Test fun exploreAndRecommendedShareGateWithoutFilteringSearch() {
        assertTrue(EXPLORE_KANJI_SQL.contains(DISCOVERY_KANJI_GATE))
        assertFalse(SEARCH_KANJI_SQL.contains(DISCOVERY_KANJI_GATE))
        assertTrue(DISCOVERY_KANJI_GATE.contains("m.language = 'en'"))
        assertTrue(DISCOVERY_KANJI_GATE.contains("r.type IN ('on', 'kun')"))
        assertTrue(DISCOVERY_KANJI_GATE.contains("k.joyo = 1 OR k.frequency IS NOT NULL"))
        assertTrue(DISCOVERY_KANJI_GATE.contains("JOIN word_form"))
    }
}
