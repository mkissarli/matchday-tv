package dev.matchday.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SportRegistryTest {

    @Test
    fun `launch sports exist and are fully described`() {
        assertEquals(
            listOf("football", "tennis", "motorsport", "cycling", "olympic", "fighting"),
            SportRegistry.launchSports.map { it.id },
        )

        SportRegistry.launchSports.forEach { sport ->
            assertEquals(sport, SportRegistry.byIdOrNull(sport.id))
            assertEquals(sport, SportRegistry.require(sport.id))
            assertTrue(sport.displayName.isNotBlank())
            assertTrue(sport.iconKey.isNotBlank())
            assertTrue(sport.keywords.isNotEmpty())
            assertTrue(SportRegistry.isLaunchSport(sport.id))
        }
    }

    @Test
    fun `the broader catalogue is present and ordered`() {
        val ids = SportRegistry.all.map { it.id }

        assertTrue(
            ids.containsAll(
                listOf(
                    "football", "tennis", "motorsport", "cycling", "olympic", "fighting",
                    "basketball", "cricket", "rugby", "american-football", "ice-hockey",
                    "golf", "other",
                ),
            ),
        )
        assertEquals(ids.distinct(), ids)
        assertEquals(SportRegistry.all.sortedBy { it.order }, SportRegistry.all)
        assertNull(SportRegistry.byIdOrNull("pickleball"))
    }

    @Test
    fun `participant styles are data per sport`() {
        assertEquals(
            ParticipantStyle.TEAM_VS_TEAM,
            SportRegistry.require("football").participantStyle,
        )
        assertEquals(
            ParticipantStyle.ATHLETE_VS_ATHLETE,
            SportRegistry.require("tennis").participantStyle,
        )
        assertEquals(ParticipantStyle.GRID, SportRegistry.require("motorsport").participantStyle)
        assertEquals(ParticipantStyle.EVENT, SportRegistry.require("cycling").participantStyle)
        assertEquals(ParticipantStyle.EVENT, SportRegistry.require("olympic").participantStyle)
        assertEquals(ParticipantStyle.FIGHT_CARD, SportRegistry.require("fighting").participantStyle)
    }

    @Test
    fun `adding a sport needs no enum change`() {
        val pickleball = SportRegistryEntry(
            id = "pickleball",
            displayName = "Pickleball",
            iconKey = "other",
            order = 13,
            keywords = listOf("pickleball"),
            participantStyle = ParticipantStyle.TEAM_VS_TEAM,
        )

        val extended = SportRegistry.with(listOf(pickleball))

        assertEquals(SportRegistry.all.size + 1, extended.size)
        assertEquals(pickleball, extended.first { it.id == "pickleball" })
        assertEquals(extended.sortedBy { it.order }, extended)
        assertFalse(SportRegistry.isLaunchSport("pickleball"))
    }

    @Test
    fun `overriding a registry entry keeps one entry per sport`() {
        val override = SportRegistry.football.copy(displayName = "Association football")

        val extended = SportRegistry.with(listOf(override))

        assertEquals(SportRegistry.all.size, extended.size)
        assertEquals("Association football", extended.first { it.id == "football" }.displayName)
        assertEquals("Football", SportRegistry.require("football").displayName)
    }
}
