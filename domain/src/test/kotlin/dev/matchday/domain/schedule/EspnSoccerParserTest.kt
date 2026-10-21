package dev.matchday.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EspnSoccerParserTest {

    private val json = """
        {
          "events": [
            {
              "id": "401915395",
              "date": "2026-10-21T16:45Z",
              "name": "Manchester United at Como",
              "competitions": [
                {
                  "venue": {"fullName": "Giuseppe Sinigaglia"},
                  "competitors": [
                    {
                      "homeAway": "home",
                      "team": {
                        "id": "2572",
                        "displayName": "Como",
                        "abbreviation": "COM",
                        "logo": "https://a.espncdn.com/i/teamlogos/soccer/500/2572.png"
                      }
                    },
                    {
                      "homeAway": "away",
                      "team": {
                        "id": "360",
                        "displayName": "Manchester United",
                        "abbreviation": "MUN",
                        "logo": "https://a.espncdn.com/i/teamlogos/soccer/500/360.png"
                      }
                    }
                  ],
                  "notes": [{"headline": "UEFA Champions League"}],
                  "broadcasts": [{"names": ["Paramount+", "TNT Sports"]}],
                  "status": {"type": {"name": "STATUS_SCHEDULED"}}
                }
              ]
            },
            {
              "id": "401915548",
              "date": "2026-10-22T19:00Z",
              "competitions": [
                {
                  "competitors": [
                    {"homeAway": "home", "team": {"displayName": "Lille"}},
                    {"homeAway": "away", "team": {"displayName": "Galatasaray"}}
                  ],
                  "status": {"type": {"name": "STATUS_POSTPONED"}}
                }
              ]
            },
            {
              "id": "broken",
              "date": "2026-10-22T20:00Z",
              "competitions": [{"competitors": []}]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses football schedule without scores`() {
        val events = EspnSoccerParser.parse(json)
        assertEquals(2, events.size)
        val match = events.first()
        assertEquals("football", match.sportId)
        assertEquals("espn-soccer", match.providerId)
        assertEquals("Como vs Manchester United", match.title)
        assertEquals(ScheduleStatus.SCHEDULED, match.status)
        assertEquals("UEFA Champions League", match.competitionName)
        assertEquals("Giuseppe Sinigaglia", match.venue)
        assertEquals("Paramount+, TNT Sports", match.broadcaster)
        assertEquals(2, match.participants.size)
        assertEquals("Como", match.participants[0].name)
        assertEquals("https://a.espncdn.com/i/teamlogos/soccer/500/360.png", match.participants[1].logoUrl)
        assertTrue(match.startMs > 0)
        assertNull(match.endMs)
    }

    @Test
    fun `keeps postponed state and drops malformed events`() {
        val events = EspnSoccerParser.parse(json)
        assertEquals(ScheduleStatus.POSTPONED, events[1].status)
        assertEquals("Lille vs Galatasaray", events[1].title)
    }

    @Test
    fun `empty payload is safe`() {
        assertTrue(EspnSoccerParser.parse("").isEmpty())
        assertTrue(EspnSoccerParser.parse("{}").isEmpty())
        assertTrue(EspnSoccerParser.parse("not json").isEmpty())
    }
}
