package dev.matchday.domain.schedule

import org.junit.Assert.assertTrue
import org.junit.Test

class LeaguePopularityTest {

    @Test
    fun `big competitions sort ahead of smaller ones`() {
        val ordered = listOf(
            "English Premier League",
            "UEFA Champions League",
            "Spanish LALIGA",
            "Formula 1",
            "ATP",
            "Japanese J.League",
            "Club Friendly",
            "UFC 333",
            "NFL",
        )
        val ranks = ordered.map(LeaguePopularity::rank)
        assertTrue("expected ascending ranks, got $ranks", ranks.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `unknown competitions sort after major leagues`() {
        assertTrue(LeaguePopularity.rank("Some Regional Cup") > LeaguePopularity.rank("English Premier League"))
        assertTrue(LeaguePopularity.rank(null) > LeaguePopularity.rank("English Premier League"))
    }
}
