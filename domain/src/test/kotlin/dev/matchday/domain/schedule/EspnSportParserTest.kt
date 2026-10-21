package dev.matchday.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EspnSportParserTest {

    @Test
    fun `f1 race weekend becomes session events`() {
        val json = """
            {
              "events": [{
                "id": "600057446",
                "date": "2026-10-23T17:30Z",
                "endDate": "2026-10-25T20:00Z",
                "name": "MSC Cruises United States Grand Prix",
                "shortName": "MSC Cruises United States GP",
                "circuit": {"fullName": "Circuit of the Americas"},
                "competitions": [
                  {"id": "1", "date": "2026-10-23T17:30Z", "type": {"abbreviation": "FP1"}, "competitors": []},
                  {"id": "5", "date": "2026-10-25T19:00Z", "type": {"abbreviation": "RACE"}, "competitors": []}
                ]
              }]
            }
        """.trimIndent()

        val events = EspnSportParser.parse(json, "motorsport", "Formula 1", ParticipantKind.DRIVER)
        assertEquals(2, events.size)
        assertEquals("motorsport", events[0].sportId)
        assertEquals("Formula 1", events[0].competitionName)
        assertTrue(events[0].title.contains("FP1"))
        assertEquals("Circuit of the Americas", events[0].venue)
        assertTrue(events[0].participants.isEmpty())
        assertTrue(events[1].title.contains("RACE"))
    }

    @Test
    fun `tennis tournament keeps its multi-day end date`() {
        val json = """
            {
              "events": [{
                "id": "440-2026",
                "date": "2026-10-17T04:00Z",
                "endDate": "2026-10-26T03:59Z",
                "name": "BNP Paribas Fortis European Open",
                "competitions": []
              }]
            }
        """.trimIndent()

        val event = EspnSportParser.parse(json, "tennis", "ATP", ParticipantKind.ATHLETE).single()
        assertEquals("tennis", event.sportId)
        assertEquals("ATP", event.competitionName)
        assertEquals("BNP Paribas Fortis European Open", event.title)
        assertNotNull(event.endMs)
        assertTrue(event.endMs!! > event.startMs)
        assertTrue(event.participants.isEmpty())
    }

    @Test
    fun `ufc card becomes fight events`() {
        val json = """
            {
              "events": [{
                "id": "600061267",
                "date": "2026-10-24T14:00Z",
                "name": "UFC 333: Volkanovski vs. Evloev",
                "shortName": "UFC 333",
                "competitions": [
                  {
                    "id": "401911632",
                    "date": "2026-10-24T18:00Z",
                    "competitors": [
                      {"id": "3949584", "type": "athlete", "athlete": {"id": "3949584", "displayName": "Alexander Volkanovski", "shortName": "A. Volkanovski"}},
                      {"id": "4029275", "type": "athlete", "athlete": {"id": "4029275", "displayName": "Movsar Evloev", "shortName": "M. Evloev"}}
                    ]
                  },
                  {
                    "id": "401911633",
                    "date": "2026-10-24T18:00Z",
                    "competitors": [
                      {"id": "1", "athlete": {"id": "1", "displayName": "Petr Yan"}},
                      {"id": "2", "athlete": {"id": "2", "displayName": "Merab Dvalishvili"}}
                    ]
                  }
                ]
              }]
            }
        """.trimIndent()

        val events = EspnSportParser.parse(json, "fighting", "UFC", ParticipantKind.FIGHTER)
        assertEquals(2, events.size)
        assertEquals("UFC 333", events[0].competitionName)
        assertEquals(2, events[0].participants.size)
        assertEquals("Alexander Volkanovski vs Movsar Evloev", events[0].title)
        assertEquals(ParticipantKind.FIGHTER, events[0].participants[0].kind)
    }

    @Test
    fun `nfl keeps home and away order`() {
        val json = """
            {
              "events": [{
                "id": "401873010",
                "date": "2026-10-23T00:15Z",
                "name": "New England Patriots at Chicago Bears",
                "competitions": [{
                  "id": "401873010",
                  "competitors": [
                    {"homeAway": "home", "team": {"id": "3", "displayName": "Chicago Bears", "abbreviation": "CHI"}},
                    {"homeAway": "away", "team": {"id": "17", "displayName": "New England Patriots", "abbreviation": "NE"}}
                  ]
                }]
              }]
            }
        """.trimIndent()

        val event = EspnSportParser.parse(json, "american-football", "NFL", ParticipantKind.TEAM).single()
        assertEquals("american-football", event.sportId)
        assertEquals("NFL", event.competitionName)
        assertEquals("Chicago Bears vs New England Patriots", event.title)
        assertEquals(2, event.participants.size)
    }

    @Test
    fun `soccer league id maps to a readable competition`() {
        assertEquals("English Premier League", EspnSoccerLeagues.nameFor("700"))
        assertEquals("UEFA Champions League", EspnSoccerLeagues.nameFor("775"))
        assertEquals("Mexican Liga BBVA MX", EspnSoccerLeagues.nameFor("760"))
    }

    @Test
    fun `query includes multi-day events that started before the window`() {
        val event = ScheduleEvent(
            id = "tennis",
            providerId = "espn",
            sportId = "tennis",
            competitionId = null,
            competitionName = "ATP",
            title = "Open",
            participants = emptyList(),
            startMs = 1_000L,
            endMs = 10_000L,
        )
        val query = ScheduleQuery(fromMs = 5_000L, toMs = 20_000L)
        assertTrue(query.matches(event))
    }
}
