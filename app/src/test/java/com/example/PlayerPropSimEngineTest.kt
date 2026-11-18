package com.example

import com.example.domain.math.PlayerPropSimEngine
import com.example.domain.model.PropSimScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPropSimEngineTest {

    @Test
    fun simulatePropOutcomes_altLineSensitivityAndDistributionAreValid() {
        val recentPoints = listOf(22.0, 26.0, 19.0, 28.0, 24.0, 31.0, 25.0, 23.0, 29.0, 27.0)

        val lowerAlt = PlayerPropSimEngine.simulatePropOutcomes(
            statLabel = "Points",
            historicalValues = recentPoints,
            lineValue = 20.5,
            upcomingGame = null,
            playerTeamAbbr = "LAL",
            matchupInjuries = emptyList(),
            scenario = PropSimScenario.BASELINE,
            runId = 1
        )
        val higherAlt = PlayerPropSimEngine.simulatePropOutcomes(
            statLabel = "Points",
            historicalValues = recentPoints,
            lineValue = 29.5,
            upcomingGame = null,
            playerTeamAbbr = "LAL",
            matchupInjuries = emptyList(),
            scenario = PropSimScenario.BASELINE,
            runId = 1
        )

        assertEquals(10_000, lowerAlt.trialCount)
        assertEquals(1.0, lowerAlt.moreProb + lowerAlt.lessProb, 0.02)
        assertTrue(
            "Expected MORE probability to drop when alt line rises (${lowerAlt.moreProb} vs ${higherAlt.moreProb})",
            lowerAlt.moreProb > higherAlt.moreProb
        )
        assertTrue(lowerAlt.floorP10 <= lowerAlt.simMedian)
        assertTrue(lowerAlt.simMedian <= lowerAlt.ceilingP90)
        assertEquals(9, lowerAlt.histogram.size)
        assertEquals(10_000, lowerAlt.histogram.sumOf { it.count })
    }

    @Test
    fun simulatePropOutcomes_scenariosShiftMeanInExpectedDirection() {
        val recentAssists = listOf(6.0, 8.0, 7.0, 9.0, 7.0, 8.0, 6.0, 8.0)

        val fastPace = PlayerPropSimEngine.simulatePropOutcomes(
            statLabel = "Assists",
            historicalValues = recentAssists,
            lineValue = 7.5,
            upcomingGame = null,
            playerTeamAbbr = "IND",
            matchupInjuries = emptyList(),
            scenario = PropSimScenario.FAST_PACE,
            runId = 0
        )
        val slowGrind = PlayerPropSimEngine.simulatePropOutcomes(
            statLabel = "Assists",
            historicalValues = recentAssists,
            lineValue = 7.5,
            upcomingGame = null,
            playerTeamAbbr = "IND",
            matchupInjuries = emptyList(),
            scenario = PropSimScenario.SLOW_GRIND,
            runId = 0
        )
        val highUsage = PlayerPropSimEngine.simulatePropOutcomes(
            statLabel = "Assists",
            historicalValues = recentAssists,
            lineValue = 7.5,
            upcomingGame = null,
            playerTeamAbbr = "IND",
            matchupInjuries = emptyList(),
            scenario = PropSimScenario.HIGH_USAGE,
            runId = 0
        )

        assertTrue(fastPace.simMean > slowGrind.simMean)
        assertTrue(highUsage.simMean > fastPace.simMean)
        assertTrue(highUsage.moreProb > slowGrind.moreProb)
    }

    @Test
    fun buildAltLinePresets_returnsSortedDistinctHalfPointLines() {
        val presets = PlayerPropSimEngine.buildAltLinePresets(baseLine = 24.5, statLabel = "Points")
        assertTrue(presets.size >= 4)
        assertEquals(presets.sorted(), presets)
        assertEquals(presets.distinct(), presets)
        assertTrue(presets.all { it >= 0.5 && (it * 2.0) % 2.0 == 1.0 })
        assertTrue(24.5 in presets)
    }
}
