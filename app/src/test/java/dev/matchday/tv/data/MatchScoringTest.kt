package dev.matchday.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity

/**
 * Broadcast-channel → user-channel name matching, pinning the real-world cases
 * pulled from ISP playlists ("Sky Sport Premier League" vs "Sky Sports Premier League HD",
 * numbered variants, broadcasting lookalikes).
 */
class MatchScoringTest {

    private fun user(name: String) = ChannelScorer.ChannelEntry(
        channel = ChannelEntity(sourceId = 0, name = name, streamUrl = "http://x"),
    )

    private fun score(broadcastName: String, userName: String): Int? =
        ChannelScorer.score(ChannelScorer.BroadcastKey(broadcastName), user(userName))

    @Test
    fun `exact name scores best`() {
        assertEquals(0, score("TNT Sports 1", "TNT Sports 1"))
        assertEquals(0, score("TNT SPORTS 1", "TNT Sports 1"))
    }

    @Test
    fun `contained name is second best`() {
        // "HD" quality words are stripped by the normalizer → same channel, best score.
        assertEquals(0, score("USA Network", "USA Network HD"))
        assertEquals(1, score("USA Network", "USA Net"))
    }

    @Test
    fun `stem match helps Sky Sport vs Sky Sports`() {
        // Sky's listings say "Sky Sport Premier League"; the user's playlist
        // says "Sky Sports Premier League HD".
        assertEquals(2, score("Sky Sport Premier League", "Sky Sports Premier League HD"))
    }

    @Test
    fun `different numbers never match`() {
        assertNull(score("TNT Sports 1", "TNT Sports 2"))
        assertNull(score("Canal+ Live 2", "Canal+ Live 6"))
    }

    @Test
    fun `numberless copy matches a numbered broadcast`() {
        assertEquals(1, score("TNT Sports 1", "TNT Sports"))
    }

    @Test
    fun `unrelated names score nothing`() {
        assertNull(score("TNT Sports 1", "Sky Sports Main Event"))
        assertNull(score("Sky Sport Premier League", "Sky Sports News HD"))
        assertNull(score("Universo", "Universal Movies HD"))
        assertEquals(2, score("DAZN Germany", "DAZN1 Germany"))
        assertNull(score("beIN SPORTS CONNECT", "beIN Sports English"))
    }}
