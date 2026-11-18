package com.example

import com.example.domain.math.QuantCorrelationEngine
import com.example.domain.model.League
import com.example.domain.model.MarketLeg
import com.example.domain.model.MarketType
import com.example.domain.model.ParlaySizePreference
import com.example.domain.model.QuantFilterPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun defaultPreferences_buildThreePickMultiSimParlays() {
        val prefs = QuantFilterPreferences()
        assertEquals(ParlaySizePreference.THREE_LEGS, prefs.parlaySizePreference)

        val legs = listOf(
            MarketLeg(
                id = "nba_1_ml",
                eventId = "g1",
                league = League.NBA,
                matchupShort = "BOS @ LAL",
                homeTeamName = "Los Angeles Lakers",
                awayTeamName = "Boston Celtics",
                homeLogoUrl = null,
                awayLogoUrl = null,
                startTimeDisplay = "7:00 PM",
                marketType = MarketType.MONEYLINE,
                selectionLabel = "LAL Moneyline",
                sideTag = "HOME",
                lineValue = null,
                offeredAmericanOdds = -135,
                offeredDecimalOdds = 1.74,
                marketImpliedProb = 0.57,
                fairTrueProb = 0.61,
                evPercent = 0.06,
                sharpBookLabel = "Multi-Book Devigged",
                isFavoriteSide = true,
                expectedTotal = 228.5,
                expectedHomeMargin = 4.0
            ),
            MarketLeg(
                id = "nba_1_tt",
                eventId = "g1",
                league = League.NBA,
                matchupShort = "BOS @ LAL",
                homeTeamName = "Los Angeles Lakers",
                awayTeamName = "Boston Celtics",
                homeLogoUrl = null,
                awayLogoUrl = null,
                startTimeDisplay = "7:00 PM",
                marketType = MarketType.TEAM_TOTAL_OVER,
                selectionLabel = "LAL Team Over 114.5",
                sideTag = "HOME",
                lineValue = 114.5,
                offeredAmericanOdds = -110,
                offeredDecimalOdds = 1.91,
                marketImpliedProb = 0.52,
                fairTrueProb = 0.57,
                evPercent = 0.09,
                sharpBookLabel = "Multi-Book Devigged",
                isFavoriteSide = true,
                expectedTotal = 228.5,
                expectedHomeMargin = 4.0
            ),
            MarketLeg(
                id = "nba_1_prop",
                eventId = "g1",
                league = League.NBA,
                matchupShort = "BOS @ LAL",
                homeTeamName = "Los Angeles Lakers",
                awayTeamName = "Boston Celtics",
                homeLogoUrl = null,
                awayLogoUrl = null,
                startTimeDisplay = "7:00 PM",
                marketType = MarketType.PLAYER_PROP_MORE,
                selectionLabel = "LeBron James MORE 25.5 Pts",
                sideTag = "HOME",
                lineValue = 25.5,
                offeredAmericanOdds = -112,
                offeredDecimalOdds = 1.89,
                marketImpliedProb = 0.53,
                fairTrueProb = 0.58,
                evPercent = 0.096,
                sharpBookLabel = "ESPN + Pace Model",
                isFavoriteSide = true,
                expectedTotal = 228.5,
                expectedHomeMargin = 4.0
            ),
            MarketLeg(
                id = "nba_2_over",
                eventId = "g2",
                league = League.NBA,
                matchupShort = "DEN @ PHX",
                homeTeamName = "Phoenix Suns",
                awayTeamName = "Denver Nuggets",
                homeLogoUrl = null,
                awayLogoUrl = null,
                startTimeDisplay = "9:30 PM",
                marketType = MarketType.TOTAL_OVER,
                selectionLabel = "DEN/PHX Over 231.5",
                sideTag = "OVER",
                lineValue = 231.5,
                offeredAmericanOdds = -108,
                offeredDecimalOdds = 1.93,
                marketImpliedProb = 0.52,
                fairTrueProb = 0.56,
                evPercent = 0.08,
                sharpBookLabel = "Multi-Book Devigged",
                isFavoriteSide = false,
                expectedTotal = 231.5,
                expectedHomeMargin = -1.5
            )
        )

        val parlays = QuantCorrelationEngine.buildTopCorrelatedParlays(legs, prefs)
        assertTrue("Expected at least 1 ranked parlay", parlays.isNotEmpty())
        val top = parlays.first()
        assertEquals(3, top.legs.size)
        assertEquals(25_000, top.simConsensusCount)
        assertTrue(top.gaussianCopulaProb in 0.05..0.95)
        assertTrue(top.studentTCopulaProb in 0.05..0.95)
        assertTrue(top.paceStressSimProb in 0.05..0.95)
        assertTrue(top.correlatedProb > top.independentProb)
    }
}
