package dev.matchday.domain.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FixtureMatcherTest {

    private val start = 1_700_000_000_000L

    private fun arsenalSpurs(
        startMs: Long = start,
        broadcasters: List<String> = emptyList(),
    ) = MatchEvent(
        id = "fixture-arsenal-spurs",
        title = "Arsenal vs Tottenham Hotspur",
        sportId = "football",
        competitionName = "Premier League",
        participants = listOf(
            MatchParticipant(
                name = "Arsenal",
                shortName = "ARS",
                aliases = listOf("Arsenal FC"),
                kind = ParticipantKind.TEAM,
            ),
            MatchParticipant(
                name = "Tottenham Hotspur",
                shortName = "Spurs",
                aliases = listOf("Tottenham", "TOT"),
                kind = ParticipantKind.TEAM,
            ),
        ),
        startMs = startMs,
        endMs = startMs + 2L * 60L * 60L * 1000L,
        broadcasterNames = broadcasters,
    )

    private fun channel(
        id: String,
        name: String,
        language: String? = null,
        qualityRank: Int = 0,
        sourceId: Long = 0L,
        epgChannelId: String? = null,
        nowTitle: String? = null,
        nextTitle: String? = null,
    ) = ChannelCandidate(
        id = id,
        rawName = name,
        epgChannelId = epgChannelId,
        language = language,
        qualityRank = qualityRank,
        sourceId = sourceId,
        nowTitle = nowTitle,
        nextTitle = nextTitle,
    )

    private fun programme(
        channelId: String,
        title: String,
        startMs: Long = start - 60L * 60L * 1000L,
        stopMs: Long = start + 2L * 60L * 60L * 1000L,
    ) = ProgrammeCandidate(channelId, title, startMs, stopMs)

    @Test
    fun `both teams in channel name are high confidence and auto-play`() {
        val result = FixtureMatcher.match(
            event = arsenalSpurs(),
            channels = listOf(channel("c1", "PPV 011 [Arsenal vs Tottenham] HD")),
        )

        val best = result.best
        assertNotNull(best)
        assertTrue(best!!.confidence >= FixtureMatcher.AUTO_PLAY_THRESHOLD)
        assertTrue(best.autoPlayEligible)
        assertTrue(result.hasAutoPlay)
        assertTrue(best.reasons.contains("both teams in channel name"))
    }

    @Test
    fun `one team only is displayed but never auto-played`() {
        val result = FixtureMatcher.match(
            event = arsenalSpurs(),
            channels = listOf(channel("c1", "Arsenal TV HD")),
        )

        val best = result.best
        assertNotNull(best)
        assertTrue(best!!.confidence >= FixtureMatcher.DISPLAY_THRESHOLD)
        assertTrue(best.confidence < FixtureMatcher.AUTO_PLAY_THRESHOLD)
        assertFalse(best.autoPlayEligible)
        assertTrue(best.reasons.contains("one team in channel name"))
    }

    @Test
    fun `EPG title confirms event on a generic channel`() {
        val result = FixtureMatcher.match(
            event = arsenalSpurs(),
            channels = listOf(channel("c1", "Sky Sports 1 HD")),
            programmes = listOf(programme("c1", "Arsenal vs Tottenham")),
        )

        val best = result.best
        assertNotNull(best)
        assertTrue(best!!.confidence >= FixtureMatcher.AUTO_PLAY_THRESHOLD)
        assertTrue(best.autoPlayEligible)
        assertTrue(best.reasons.contains("EPG title matches event"))
    }

    @Test
    fun `accented and alias names resolve to the same fixture`() {
        val event = MatchEvent(
            id = "fixture-bayern-dortmund",
            title = "Bayern Munich vs Borussia Dortmund",
            sportId = "football",
            competitionName = "Bundesliga",
            participants = listOf(
                MatchParticipant("Bayern München", shortName = "Bayern", aliases = listOf("Bayern Munich")),
                MatchParticipant("Borussia Dortmund", shortName = "Dortmund", aliases = listOf("BVB")),
            ),
            startMs = start,
        )

        val result = FixtureMatcher.match(
            event = event,
            channels = listOf(channel("c1", "Bayern Munich - Dortmund HD")),
        )

        val best = result.best
        assertNotNull(best)
        assertTrue(best!!.confidence >= FixtureMatcher.AUTO_PLAY_THRESHOLD)
        assertTrue(best.autoPlayEligible)
        assertTrue(best.reasons.contains("both teams in channel name"))
    }

    @Test
    fun `racing event title match is display only because it has no participants`() {
        val event = MatchEvent(
            id = "fixture-racing",
            title = "Formula 1 British Grand Prix",
            sportId = "motorsport",
            competitionName = "Formula 1",
            participants = emptyList(),
            startMs = start,
            broadcasterNames = listOf("Sky Sports F1"),
        )

        val result = FixtureMatcher.match(
            event = event,
            channels = listOf(channel("c1", "Sky Sports F1: British Grand Prix HD")),
        )

        val best = result.best
        assertNotNull(best)
        assertTrue(best!!.confidence >= FixtureMatcher.DISPLAY_THRESHOLD)
        assertFalse(best.autoPlayEligible)
        assertFalse(result.hasAutoPlay)
        assertTrue(best.reasons.contains("event title match"))
    }

    @Test
    fun `Sky Sports 1 does not match Sky Sports 2`() {
        val result = FixtureMatcher.match(
            event = arsenalSpurs(broadcasters = listOf("Sky Sports 1")),
            channels = listOf(
                channel("c1", "Sky Sports 1 HD"),
                channel("c2", "Sky Sports 2 HD"),
            ),
        )

        assertEquals(listOf("c1"), result.matches.map(StreamMatch::channelId))
        assertTrue(result.matches.single().reasons.contains("broadcaster match"))
        assertFalse(result.matches.single().autoPlayEligible)
        assertNull(result.matches.firstOrNull { it.channelId == "c2" })
    }

    @Test
    fun `outside the time window a high confidence match cannot auto-play`() {
        val result = FixtureMatcher.match(
            event = arsenalSpurs(),
            channels = listOf(channel("c1", "Arsenal vs Tottenham HD")),
            nowMs = start + 4L * 60L * 60L * 1000L,
        )

        val best = result.best
        assertNotNull(best)
        assertTrue(best!!.confidence >= FixtureMatcher.AUTO_PLAY_THRESHOLD)
        assertFalse(best.autoPlayEligible)
        assertTrue(best.reasons.contains("outside time window"))
    }

    @Test
    fun `language preference orders equal-confidence matches`() {
        val result = FixtureMatcher.match(
            event = arsenalSpurs(),
            channels = listOf(
                channel("c-de", "Arsenal vs Tottenham", language = "DE"),
                channel("c-ar", "Arsenal vs Tottenham", language = "AR"),
                channel("c-fr", "Arsenal vs Tottenham", language = "FR"),
                channel("c-en", "Arsenal vs Tottenham", language = "EN"),
            ),
            preferredLanguages = listOf("EN", "FR", "AR", "DE"),
        )

        assertEquals(listOf("c-en", "c-fr", "c-ar", "c-de"), result.matches.map(StreamMatch::channelId))
    }

    @Test
    fun `empty candidates produce an empty result`() {
        val result = FixtureMatcher.match(arsenalSpurs(), emptyList())

        assertTrue(result.matches.isEmpty())
        assertNull(result.best)
        assertFalse(result.hasAutoPlay)
    }

    @Test
    fun `one generic programme token is not enough to confirm an event`() {
        val result = FixtureMatcher.match(
            event = arsenalSpurs(),
            channels = listOf(channel("c1", "Sky Sports 1 HD")),
            programmes = listOf(programme("c1", "Arsenal")),
        )

        assertTrue(result.matches.isEmpty())
    }

    @Test
    fun `EPG row outside the fixture window does not confirm a generic channel`() {
        val result = FixtureMatcher.match(
            event = arsenalSpurs(),
            channels = listOf(channel("c1", "Sky Sports 1 HD")),
            programmes = listOf(
                programme(
                    channelId = "c1",
                    title = "Arsenal vs Tottenham",
                    startMs = start + 5L * 60L * 60L * 1000L,
                    stopMs = start + 7L * 60L * 60L * 1000L,
                ),
            ),
        )

        assertTrue(result.matches.isEmpty())
    }

    @Test
    fun `short codes and missing separator still match both teams`() {
        val result = FixtureMatcher.match(
            event = arsenalSpurs(),
            channels = listOf(
                channel("c1", "ARS vs TOT"),
                channel("c2", "Arsenal Tottenham"),
            ),
        )

        assertEquals(listOf("c1", "c2"), result.matches.map(StreamMatch::channelId).sorted())
        assertTrue(result.matches.all { it.autoPlayEligible })
        assertTrue(result.matches.all { it.reasons.contains("both teams in channel name") })
    }

    @Test
    fun `one team plus competition is medium confidence and not auto-played`() {
        val result = FixtureMatcher.match(
            event = arsenalSpurs(),
            channels = listOf(channel("c1", "Arsenal Premier League HD")),
        )

        val best = result.best
        assertNotNull(best)
        assertTrue(best!!.confidence >= FixtureMatcher.DISPLAY_THRESHOLD)
        assertTrue(best.confidence < FixtureMatcher.AUTO_PLAY_THRESHOLD)
        assertFalse(best.autoPlayEligible)
        assertTrue(best.reasons.contains("one team in channel name"))
        assertTrue(best.reasons.contains("competition match"))
    }

    @Test
    fun `quality then source id break confidence ties`() {
        val result = FixtureMatcher.match(
            event = arsenalSpurs(),
            channels = listOf(
                channel("c-hd-late", "Arsenal vs Tottenham", language = "EN", qualityRank = 2, sourceId = 9),
                channel("c-4k", "Arsenal vs Tottenham", language = "EN", qualityRank = 4, sourceId = 8),
                channel("c-hd-early", "Arsenal vs Tottenham", language = "EN", qualityRank = 2, sourceId = 1),
            ),
        )

        assertEquals(listOf("c-4k", "c-hd-early", "c-hd-late"), result.matches.map(StreamMatch::channelId))
    }

    @Test
    fun `competition only is display confidence and never auto-play`() {
        val result = FixtureMatcher.match(
            event = arsenalSpurs(),
            channels = listOf(channel("c1", "Premier League HD")),
        )

        val best = result.best
        assertNotNull(best)
        assertTrue(best!!.confidence >= FixtureMatcher.DISPLAY_THRESHOLD)
        assertTrue(best.confidence < FixtureMatcher.AUTO_PLAY_THRESHOLD)
        assertFalse(best.autoPlayEligible)
        assertTrue(best.reasons.contains("competition match"))
    }

    @Test
    fun `a matching EPG row outside the window blocks auto-play even when the name matches`() {
        val result = FixtureMatcher.match(
            event = arsenalSpurs(),
            channels = listOf(channel("c1", "Arsenal vs Tottenham HD")),
            programmes = listOf(
                programme(
                    channelId = "c1",
                    title = "Arsenal vs Tottenham",
                    startMs = start + 5L * 60L * 60L * 1000L,
                    stopMs = start + 7L * 60L * 60L * 1000L,
                ),
            ),
        )

        val best = result.best
        assertNotNull(best)
        assertFalse(best!!.autoPlayEligible)
        assertTrue(best.reasons.contains("EPG programme outside time window"))
    }

    @Test
    fun `next style channel name is treated as a channel signal`() {
        val result = FixtureMatcher.match(
            event = arsenalSpurs(),
            channels = listOf(channel("c1", "Next: Arsenal vs Spurs")),
        )

        val best = result.best
        assertNotNull(best)
        assertTrue(best!!.autoPlayEligible)
        assertTrue(best.reasons.contains("both teams in channel name"))
    }
}
