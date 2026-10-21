package dev.matchday.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TheSportsDbParserTest {

    private fun json(name: String): String =
        checkNotNull(javaClass.getResource("/schedule/$name")) { "Missing test resource $name" }.readText()

    private fun instant(value: String): Long = Instant.parse(value).toEpochMilli()

    private fun query(from: String, to: String, vararg sports: String): ScheduleQuery =
        ScheduleQuery(
            fromMs = instant(from),
            toMs = instant(to),
            sportIds = sports.toSet(),
        )

    @Test
    fun `football event maps provider fields without scores`() {
        val events = TheSportsDbParser.parseEvents(json("football.json"))

        assertEquals(2, events.size)
        val event = events.first()
        assertEquals("1001", event.id)
        assertEquals("thesportsdb", event.providerId)
        assertEquals("football", event.sportId)
        assertEquals("4328", event.competitionId)
        assertEquals("English Premier League", event.competitionName)
        assertEquals("Arsenal vs Tottenham Hotspur", event.title)
        assertEquals(instant("2024-08-19T19:00:00Z"), event.startMs)
        assertNull(event.endMs)
        assertEquals("Emirates Stadium", event.venue)
        assertEquals("Sky Sports Main Event", event.broadcaster)
        assertEquals(ScheduleStatus.SCHEDULED, event.status)
        assertEquals(2, event.participants.size)
        assertEquals(listOf("Arsenal", "Tottenham Hotspur"), event.participants.map { it.name })
        assertTrue(event.participants.all { it.kind == ParticipantKind.TEAM })
        assertEquals("https://example.test/arsenal.png", event.participants[0].logoUrl)
        assertEquals("https://example.test/spurs.png", event.participants[1].logoUrl)
    }

    @Test
    fun `timestamp without offset is treated as UTC`() {
        val event = TheSportsDbParser.parseEvents(json("football.json")).first()

        assertEquals(Instant.parse("2024-08-19T19:00:00Z").toEpochMilli(), event.startMs)
        assertEquals(
            TheSportsDbParser.parseStartMillis("2024-08-19T19:00:00", null, null),
            event.startMs,
        )
    }

    @Test
    fun `tennis players become athletes`() {
        val event = TheSportsDbParser.parseEvents(json("tennis.json")).single()

        assertEquals("tennis", event.sportId)
        assertEquals(ScheduleStatus.SCHEDULED, event.status)
        assertEquals(2, event.participants.size)
        assertTrue(event.participants.all { it.kind == ParticipantKind.ATHLETE })
        assertEquals(listOf("A. Moreau", "J. Weber"), event.participants.map { it.name })
        assertTrue(event.participants.all { it.logoUrl == null })
    }

    @Test
    fun `racing and cycling with no teams use one event participant`() {
        val events = TheSportsDbParser.parseEvents(json("racing.json"))

        assertEquals(2, events.size)
        val race = events[0]
        assertEquals("motorsport", race.sportId)
        assertEquals("Monaco Grand Prix", race.title)
        assertEquals(1, race.participants.size)
        assertEquals("Monaco Grand Prix", race.participants.single().name)
        assertEquals(ParticipantKind.OTHER, race.participants.single().kind)

        val stage = events[1]
        assertEquals("cycling", stage.sportId)
        assertEquals("Tour of the North Stage 4", stage.title)
        assertEquals(1, stage.participants.size)
        assertEquals(ParticipantKind.OTHER, stage.participants.single().kind)
        assertEquals(instant("2024-08-25T12:15:00Z"), stage.startMs)
    }

    @Test
    fun `missing badges parse as null logos`() {
        val event = TheSportsDbParser.parseEvents(json("missing_badges.json")).single()

        assertEquals(2, event.participants.size)
        assertTrue(event.participants.all { it.logoUrl == null })
        assertTrue(event.participants.all { it.portraitUrl == null })
        assertTrue(event.participants.all { it.countryCode == null })
    }

    @Test
    fun `null and missing events produce an empty schedule`() {
        assertTrue(TheSportsDbParser.parseEvents(json("null_events.json")).isEmpty())
        assertTrue(TheSportsDbParser.parseEvents("{}").isEmpty())
        assertTrue(TheSportsDbParser.parseEvents("""{"events": []}""").isEmpty())
        assertTrue(TheSportsDbParser.parseEvents("").isEmpty())
    }

    @Test
    fun `postponed and cancelled events are kept and mapped`() {
        val events = TheSportsDbParser.parseEvents(json("postponed.json"))

        assertEquals(3, events.size)
        assertEquals(
            listOf(
                ScheduleStatus.POSTPONED,
                ScheduleStatus.CANCELLED,
                ScheduleStatus.POSTPONED,
            ),
            events.map { it.status },
        )

        val withinWindow = TheSportsDbParser.parseEvents(
            json("postponed.json"),
            query("2024-08-23T22:00:00Z", "2024-08-24T20:00:00Z"),
        )
        assertEquals(listOf("5001", "5002", "5003"), withinWindow.map { it.id })
    }

    @Test
    fun `query filters by inclusive window and sport`() {
        val football = json("football.json")

        assertEquals(
            listOf("1001"),
            TheSportsDbParser.parseEvents(
                football,
                query("2024-08-19T19:00:00Z", "2024-08-19T19:00:00Z"),
            ).map { it.id },
        )
        assertEquals(
            listOf("1001", "1002"),
            TheSportsDbParser.parseEvents(
                football,
                query("2024-08-19T19:00:00Z", "2024-08-19T21:30:00Z"),
            ).map { it.id },
        )
        assertTrue(
            TheSportsDbParser.parseEvents(
                football,
                query("2024-08-19T00:00:00Z", "2024-08-20T00:00:00Z", "tennis"),
            ).isEmpty(),
        )
        assertEquals(
            listOf("1001", "1002"),
            TheSportsDbParser.parseEvents(
                football,
                query("2024-08-19T00:00:00Z", "2024-08-20T00:00:00Z", "football"),
            ).map { it.id },
        )
    }

    @Test
    fun `malformed timestamps fall back to date and time or are skipped`() {
        val events = TheSportsDbParser.parseEvents(json("malformed_timestamps.json"))

        assertEquals(listOf("6001", "6002"), events.map { it.id })
        assertEquals(instant("2024-08-21T19:45:00Z"), events[0].startMs)
        assertEquals(instant("2024-08-22T00:00:00Z"), events[1].startMs)
    }

    @Test
    fun `sport names normalize to registry ids`() {
        val expected = mapOf(
            "Soccer" to "football",
            "Football" to "football",
            "Tennis" to "tennis",
            "Motorsport" to "motorsport",
            "Racing" to "motorsport",
            "Cycling" to "cycling",
            "Fighting" to "fighting",
            "Boxing" to "fighting",
            "MMA" to "fighting",
            "Olympic Sports" to "olympic",
            "Basketball" to "basketball",
            "Cricket" to "cricket",
            "Rugby" to "rugby",
            "American Football" to "american-football",
            "NFL" to "american-football",
            "NCAA Football" to "american-football",
            "Ice Hockey" to "ice-hockey",
            "Golf" to "golf",
            "Chess" to "other",
        )

        expected.forEach { (raw, sportId) ->
            assertEquals(raw, sportId, TheSportsDbParser.normalizeSport(raw))
        }
        assertEquals("other", TheSportsDbParser.normalizeSport(null))
    }

    @Test
    fun `schedule models expose no score or result fields`() {
        val names = ScheduleEvent::class.java.declaredFields.map { it.name.lowercase() } +
            Participant::class.java.declaredFields.map { it.name.lowercase() } +
            Artwork::class.java.declaredFields.map { it.name.lowercase() }

        assertTrue(names.none { "score" in it || "result" in it })
    }

    @Test
    fun `events without a title fall back to the matchup`() {
        val events = TheSportsDbParser.parseEvents(
            """
            {
              "events": [
                {
                  "idEvent": "7001",
                  "strSport": "Soccer",
                  "strTimestamp": "2024-08-19T18:00:00",
                  "strHomeTeam": "Alpha",
                  "strAwayTeam": "Beta"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("Alpha vs Beta", events.single().title)
    }
}
