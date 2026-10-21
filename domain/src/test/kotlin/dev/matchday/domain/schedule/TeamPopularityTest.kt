package dev.matchday.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamPopularityTest {

    private fun participant(name: String) = Participant(id = name, name = name)

    @Test
    fun `elite clubs and players boost headline score`() {
        assertTrue(TeamPopularity.boost(listOf(participant("Real Madrid"))) > 0)
        assertTrue(TeamPopularity.boost(listOf(participant("Carlos Alcaraz"))) > 0)
        assertEquals(0, TeamPopularity.boost(listOf(participant("Some Local XI"))))
    }

    @Test
    fun `elite boost is stronger than the second tier`() {
        assertTrue(
            TeamPopularity.boost(listOf(participant("Liverpool"))) >
                TeamPopularity.boost(listOf(participant("Napoli"))),
        )
    }
}
