package com.example

import com.example.data.repository.NbaIntelRepository
import com.example.domain.model.NbaAvailabilityBucket
import com.example.domain.model.NbaNewsCategory
import com.example.domain.model.NbaTeamWeekSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NbaIntelRepositoryTest {

    private val repo = NbaIntelRepository()

    @Test
    fun parseNbaNewsJson_classifiesArticlesAndAttachesPickImpact() {
        val sampleJson = """
            {
              "articles": [
                {
                  "id": "101",
                  "headline": "Star Guard ruled out with ankle sprain for Friday matchup",
                  "description": "Team expects secondary guards to shoulder extra minutes while starter recovers.",
                  "published": "2026-11-18T08:00:00Z",
                  "categories": [{"type": "team", "description": "Boston Celtics"}],
                  "links": {"web": {"href": "https://www.espn.com/nba/story/_/id/101"}}
                },
                {
                  "id": "102",
                  "headline": "High-tempo offense pushes six-game winning streak",
                  "description": "Up-tempo pace and three-point volume continue to lift game totals.",
                  "published": "2026-11-18T07:30:00Z",
                  "categories": []
                }
              ]
            }
        """.trimIndent()

        val articles = repo.parseNbaNewsJson(sampleJson)
        assertEquals(2, articles.size)
        assertEquals(NbaNewsCategory.INJURY_UPDATE, articles[0].category)
        assertTrue(articles[0].pickImpactNote.contains("Pick Edge"))
        assertEquals(listOf("Boston Celtics"), articles[0].teamTags)
        assertEquals(NbaNewsCategory.PICK_EDGE, articles[1].category)
    }

    @Test
    fun parseNbaWeeklyInjuries_groupsBucketsAndAttachesSchedule() {
        val sampleInjuries = """
            {
              "injuries": [
                {
                  "team": {"abbreviation": "BOS", "displayName": "Boston Celtics"},
                  "injuries": [
                    {
                      "status": "Out",
                      "shortComment": "Knee soreness, targeting late week return.",
                      "athlete": {
                        "id": "555",
                        "displayName": "Center One",
                        "position": {"abbreviation": "C"}
                      }
                    },
                    {
                      "status": "Questionable",
                      "shortComment": "GTD with illness.",
                      "athlete": {
                        "id": "556",
                        "displayName": "Guard Two",
                        "position": {"abbreviation": "G"}
                      }
                    },
                    {
                      "status": "Probable",
                      "shortComment": "Expected to play.",
                      "athlete": {
                        "id": "557",
                        "displayName": "Forward Three",
                        "position": {"abbreviation": "F"}
                      }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val schedules = mapOf(
            "BOS" to NbaTeamWeekSchedule(
                teamId = "2",
                teamAbbr = "BOS",
                teamName = "Boston Celtics",
                logoUrl = null,
                gamesThisWeek = 4,
                hasBackToBack = true,
                nextOpponentDisplay = "@ CLE • Tonight",
                highestTotalLine = 231.5,
                avgTotalLine = 228.0
            )
        )

        val statuses = repo.parseNbaWeeklyInjuries(sampleInjuries, schedules)
        assertEquals(3, statuses.size)
        assertEquals(NbaAvailabilityBucket.WONT_PLAY, statuses[0].bucket)
        assertEquals(4, statuses[0].gamesThisWeek)
        assertTrue(statuses[0].isBackToBackTeam)
        assertEquals(NbaAvailabilityBucket.QUESTIONABLE, statuses[1].bucket)
        assertEquals(NbaAvailabilityBucket.PLAYING_THIS_WEEK, statuses[2].bucket)
    }

    @Test
    fun parseNbaStandingsJson_extractsConferenceRows() {
        val sampleStandings = """
            {
              "children": [
                {
                  "shortName": "Eastern Conference",
                  "standings": {
                    "entries": [
                      {
                        "team": {"abbreviation": "BOS", "displayName": "Boston Celtics"},
                        "stats": [
                          {"name": "wins", "displayValue": "12", "value": 12},
                          {"name": "losses", "displayValue": "3", "value": 3},
                          {"name": "winPercent", "displayValue": ".800"},
                          {"name": "streak", "displayValue": "W4"},
                          {"name": "Last Ten Games", "displayValue": "8-2"},
                          {"name": "differential", "displayValue": "+9.2"}
                        ]
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val rows = repo.parseNbaStandingsJson(sampleStandings)
        assertEquals(1, rows.size)
        assertEquals("Eastern", rows[0].conference)
        assertEquals("BOS", rows[0].teamAbbr)
        assertEquals(12, rows[0].wins)
        assertEquals(3, rows[0].losses)
        assertEquals("W4", rows[0].streakDisplay)
        assertEquals("8-2", rows[0].lastTenDisplay)
        assertEquals("+9.2", rows[0].pointDiffDisplay)
    }
}
