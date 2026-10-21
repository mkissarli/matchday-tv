package dev.matchday.tv.data

import dev.matchday.domain.schedule.Participant
import dev.matchday.domain.schedule.ScheduleEvent
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing rules for the LiveSoccerTV "where to watch" pages, exercised against
 * synthetic HTML shaped like the real server-rendered tables.
 */
class BroadcastLookupTest {

    private val lookup = BroadcastLookup(OkHttpClient())

    private fun event(
        title: String = "Arsenal vs Everton",
        names: List<String> = listOf("Arsenal", "Everton"),
        startMs: Long = 1_760_000_000_000L,
    ) = ScheduleEvent(
        id = "e1",
        providerId = "test",
        sportId = "football",
        competitionId = null,
        competitionName = "English Premier League",
        title = title,
        participants = names.map { Participant(id = it, name = it, shortName = it.take(1)) },
        startMs = startMs,
    )

    // ---- fixture row resolution --------------------------------------------

    @Test
    fun `row link keeps the fragment but strips it from the page path`() {
        val html = """
            <tr id="5774601" class="matchrow" data-ko="2026-10-24 12:30:00" data-cid="50">
              <td class='timecol'><div class='meta'><span class='ts' dv='1794006600000' df='h:MMtt'>12:30pm</span></div></td>
              <td class="matchcol"><a href="/match/arsenal-vs-everton/17701#5774601" title="Arsenal vs Everton"class='topmatch' id="g5774601">Arsenal vs Everton</a></td>
            </tr>
        """.trimIndent()
        val page = notNull(lookup.matchInPage(html, event()))
        assertEquals("/match/arsenal-vs-everton/17701", page.path)
    }

    @Test
    fun `row within the kickoff window counts as exact`() {
        val epoch = 1_794_006_600_000L
        val html = """
            <tr id="5774601" class="matchrow" data-ko="2026-10-24 12:30:00">
              <td><span class='ts' dv='$epoch' df='h:MMtt'>12:30pm</span></td>
              <td><a href="/match/arsenal-vs-everton/17701#5774601" title="Arsenal vs Everton" id="g5774601">Arsenal vs Everton</a></td>
            </tr>
        """.trimIndent()
        // ±6h window: a provider's kickoff an hour off still resolves exactly.
        assertTrue(notNull(lookup.matchInPage(html, event(startMs = epoch - 3_600_000))).exact)
        assertFalse(notNull(lookup.matchInPage(html, event(startMs = epoch + 12 * 3_600_000L))).exact)
    }

    @Test
    fun `sidebar link without a row id attribute is ignored`() {
        // The "Top Matches" sidebar anchors carry the competition title and no id="g…";
        // they must never resolve as fixtures.
        val html = """
            <li class="lc-tm-item"><a href="/match/arsenal-vs-everton/17701#5774601" title="English Premier League">
              <span class="lc-tm-time ts" dv="1794006600000" df="h:MMtt">12:30pm</span>
            </a></li>
        """.trimIndent()
        assertNull(lookup.matchInPage(html, event()))
    }

    @Test
    fun `team titles with accents and entities still match`() {
        val epoch = 1_792_609_200_000L
        val html = """
            <tr id="5774601" class="matchrow">
              <td><span class='ts' dv='$epoch' df='h:MMtt'>3:00pm</span></td>
              <td><a href="/match/bayern-vs-arsenal/18160#5774601" title="Bayern M&uuml;nchen vs Arsenal" id="g5774601">Bayern M&uuml;nchen vs Arsenal</a></td>
            </tr>
        """.trimIndent()
        val page = lookup.matchInPage(
            html,
            event(title = "Bayern Munich vs Arsenal", names = listOf("Bayern Munich", "Arsenal")),
        )
        assertNotNull(page)
        assertEquals("/match/bayern-vs-arsenal/18160", page!!.path)
    }

    @Test
    fun `single participant fixture short-circuits`() {
        val html = """<tr id="1"><td><a href="/match/x/1#1" title="Ferrari" id="g1">Ferrari</a></td></tr>"""
        assertNull(lookup.matchInPage(html, event(names = listOf("Ferrari"))))
    }

    // ---- broadcast table parsing --------------------------------------------

    private val matchPage = """
        <tr><td><span class="flag england">United Kingdom</span></td><td>
        <a title="TNT Sports 1" href="#">TNT Sports 1</a>
        <a href="#">TNT Sports 2</a><a href="#">TNT Sports&#8230;</a></td></tr>
        <tr><td><span class="flag usa">USA</span></td><td>
        <a href="#">USA Network</a><a href="#">Universo</a></td></tr>
        <tr><td><span class="flag algeria">Algeria</span></td><td>
        <a href="#">beIN Sports HD 1</a><a href="#">beIN SPORTS CONNECT</a><a href="#">TOD</a></td></tr>
        <tr><td><span class="flag france">France</span></td><td>
        <a href="#">Canal+ Foot</a><a href="#">Canal+ Live 2</a></td></tr>
    """.trimIndent()

    @Test
    fun `country table yields channels ranked by region tier`() {
        val listing = notNull(lookup.parse(matchPage))
        assertEquals(9, listing.channels.size) // "TNT Sports…" ellipsis rows drop out
        // UK first, then USA, then France, then Algeria (tier order; ties keep row order).
        assertEquals(
            listOf(
                "TNT Sports 1", "TNT Sports 2",
                "USA Network", "Universo",
                "Canal+ Foot", "Canal+ Live 2",
                "beIN Sports HD 1", "TOD", "beIN SPORTS CONNECT",
            ),
            listing.channels.map { it.channelName },
        )
        assertEquals(1, BroadcastLookup.tierOf("United Kingdom"))
        assertEquals(1, BroadcastLookup.tierOf("Great Britain"))
        assertEquals(2, BroadcastLookup.tierOf("USA"))
        assertEquals(4, BroadcastLookup.tierOf("Saudi Arabia"))
        assertEquals(5, BroadcastLookup.tierOf("Germany"))
        assertEquals(9, BroadcastLookup.tierOf("Chile"))
    }

    @Test
    fun `ellipsis and entity rows collapse to nothing`() {
        val html = """
            <tr><td><span class="flag usa">USA</span></td><td>
            <a href="#">&hellip;</a><a href="#">Råge</a><a href="#">&nbsp;</a></td></tr>
        """.trimIndent()
        val listing = notNull(lookup.parse(html))
        assertEquals(listOf("Råge"), listing.channels.map { it.channelName })
        assertEquals("USA", listing.countries.single())
    }

    @Test
    fun `empty page has no listing`() {
        assertNull(lookup.parse("<html><body><p>Nothing here</p></body></html>"))
    }
}

private fun <T : Any> notNull(value: T?): T {
    org.junit.Assert.assertNotNull(value)
    return value!!
}
