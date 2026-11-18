package com.example

import com.example.domain.math.OddsMath
import com.example.domain.math.QuantCorrelationEngine
import com.example.domain.model.DevigMethod
import com.example.domain.model.League
import com.example.domain.model.MarketLeg
import com.example.domain.model.MarketType
import com.example.domain.model.QuantFilterPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantCorrelationEngineTest {

    @Test
    fun devigTwoWay_removesOverroundAndSumsToOne() {
        val p1 = OddsMath.americanToImpliedProb(-110)
        val p2 = OddsMath.americanToImpliedProb(-110)
        val (fair1, fair2) = OddsMath.devigTwoWay(p1, p2, DevigMethod.POWER)
        assertEquals(1.0, fair1 + fair2, 1e-4)
        assertEquals(0.5, fair1, 1e-3)
    }

    @Test
    fun bivariateNormalJointProb_increasesWithPositiveCorrelation() {
        val pA = 0.65
        val pB = 0.60
        val indep = OddsMath.bivariateNormalJointProb(pA, pB, 0.0)
        val corr = OddsMath.bivariateNormalJointProb(pA, pB, 0.58)
        assertEquals(pA * pB, indep, 1e-3)
        assertTrue("Expected correlated joint prob > independent", corr > indep + 0.08)
    }

    @Test
    fun evaluatePairCorrelation_rejectsOpposingSidesInSameGame() {
        val homeMl = sampleLeg("g1_h", "g1", League.NBA, MarketType.MONEYLINE, "BOS ML", "HOME", 0.68, -190, true)
        val awayMl = sampleLeg("g1_a", "g1", League.NBA, MarketType.MONEYLINE, "MIL ML", "AWAY", 0.32, 170, false)
        assertNull(QuantCorrelationEngine.evaluatePairCorrelation(homeMl, awayMl))
    }

    @Test
    fun buildTopCorrelatedParlays_surfacesHighestProbabilityPositiveCorrelation() {
        val legs = listOf(
            sampleLeg("g1_h", "g1", League.NBA, MarketType.MONEYLINE, "BOS ML", "HOME", 0.72, -230, true),
            sampleLeg("g1_tt", "g1", League.NBA, MarketType.TEAM_TOTAL_OVER, "BOS TT Ov 114.5", "HOME", 0.68, -185, true),
            sampleLeg("g1_ov", "g1", League.NBA, MarketType.TOTAL_OVER, "Over 224.5", "OVER", 0.55, -108, false),
            sampleLeg("g2_a", "g2", League.NFL, MarketType.ALT_SPREAD, "KC +2.5 (Alt)", "AWAY", 0.69, -200, true),
            sampleLeg("g2_tt", "g2", League.NFL, MarketType.TEAM_TOTAL_OVER, "KC TT Ov 23.5", "AWAY", 0.66, -170, true)
        )

        val ranked = QuantCorrelationEngine.buildTopCorrelatedParlays(
            legs = legs,
            prefs = QuantFilterPreferences(minHitProbability = 0.35, minCorrelationRho = 0.25)
        )

        assertTrue("Should generate at least 2 top correlated parlays", ranked.size >= 2)
        val topOne = ranked.first()
        assertEquals(1, topOne.rank)
        assertEquals(3, topOne.legs.size)
        assertTrue(topOne.correlatedProb > topOne.independentProb)
        assertTrue(topOne.avgRho >= 0.45)
    }

    @Test
    fun buildTopCorrelatedParlays_defaultsToThreePickParlaysEvenForMixedSports() {
        val legs = listOf(
            sampleLeg("g1_p1", "g1", League.NBA, MarketType.PLAYER_PROP_MORE, "Star A MORE 24.5 Points", "HOME", 0.59, -125, true),
            sampleLeg("g2_p1", "g2", League.NHL, MarketType.PLAYER_PROP_MORE, "Star B MORE 3.5 Shots", "AWAY", 0.58, -120, false),
            sampleLeg("g3_p1", "g3", League.MLB, MarketType.PLAYER_PROP_MORE, "Pitcher C MORE 5.5 Strikeouts", "HOME", 0.57, -115, true),
            sampleLeg("g4_p1", "g4", League.NBA, MarketType.TOTAL_OVER, "Over 228.5", "OVER", 0.56, -110, false)
        )

        val ranked = QuantCorrelationEngine.buildTopCorrelatedParlays(
            legs = legs,
            prefs = QuantFilterPreferences()
        )

        assertTrue("Should surface top 3-pick parlays across sports", ranked.size >= 3)
        assertTrue("All parlays should be 3-pick style", ranked.all { it.legs.size == 3 })
    }

    private fun sampleLeg(
        id: String,
        eventId: String,
        league: League,
        type: MarketType,
        label: String,
        side: String,
        fairProb: Double,
        americanOdds: Int,
        isFav: Boolean
    ): MarketLeg {
        val dec = OddsMath.americanToDecimal(americanOdds)
        return MarketLeg(
            id = id,
            eventId = eventId,
            league = league,
            matchupShort = "AWY @ HOM",
            homeTeamName = "Home Team",
            awayTeamName = "Away Team",
            homeLogoUrl = null,
            awayLogoUrl = null,
            startTimeDisplay = "7:30 PM",
            marketType = type,
            selectionLabel = label,
            sideTag = side,
            lineValue = null,
            offeredAmericanOdds = americanOdds,
            offeredDecimalOdds = dec,
            marketImpliedProb = OddsMath.americanToImpliedProb(americanOdds),
            fairTrueProb = fairProb,
            evPercent = OddsMath.expectedValue(fairProb, dec),
            sharpBookLabel = "Test Sharp",
            isFavoriteSide = isFav,
            expectedTotal = 224.5,
            expectedHomeMargin = -5.5
        )
    }
}
