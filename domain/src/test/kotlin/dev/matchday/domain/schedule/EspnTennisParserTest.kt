package dev.matchday.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EspnTennisParserTest {

    private val json = """
        {
          "count": 2,
          "items": [
            {
              "id": "186982",
              "date": "2026-10-21T10:30Z",
              "type": {"text": "Men's Singles", "type": "singles"},
              "venue": {"address": {"summary": "Brussels, Belgium"}},
              "competitors": [
                {"id": "11457", "name": "Joel Josef Schwaerzler", "order": 1},
                {"id": "14601", "name": "Federico Cina", "order": 2}
              ]
            },
            {
              "id": "186983",
              "date": "2026-10-21T12:00Z",
              "competitors": []
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `individual tennis matches become schedule events`() {
        val events = EspnTennisParser.parse(json, "440-2026", "BNP Paribas Fortis European Open", "ATP")
        assertEquals(1, events.size)
        val event = events.single()
        assertEquals("tennis", event.sportId)
        assertEquals("ATP", event.competitionName)
        assertEquals("Joel Josef Schwaerzler vs Federico Cina", event.title)
        assertEquals(2, event.participants.size)
        assertEquals(ParticipantKind.ATHLETE, event.participants[0].kind)
        assertEquals("BNP Paribas Fortis European Open · Brussels, Belgium", event.venue)
        assertTrue(event.startMs > 0)
    }

    @Test
    fun `malformed tennis payload is safe`() {
        assertTrue(EspnTennisParser.parse("", "1", "Open", "ATP").isEmpty())
        assertTrue(EspnTennisParser.parse("{}", "1", "Open", "ATP").isEmpty())
    }
}
