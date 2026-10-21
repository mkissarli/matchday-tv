package dev.matchday.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class SportHintCatalogTest {

    @Test
    fun `football means association football not gridiron`() {
        assertEquals("football", SportHintCatalog.detect("Sky Sports Football")?.id)
        assertEquals("football", SportHintCatalog.detect("Premier League")?.id)
        assertEquals("football", SportHintCatalog.detect("Liga MX")?.id)
        assertEquals("football", SportHintCatalog.detect("Copa Libertadores")?.id)
    }

    @Test
    fun `american football stays separate`() {
        assertEquals("american-football", SportHintCatalog.detect("NFL Sunday Ticket")?.id)
        assertEquals("american-football", SportHintCatalog.detect("American Football")?.id)
        assertEquals("american-football", SportHintCatalog.detect("NCAA Football")?.id)
        assertEquals("american-football", SportHintCatalog.detect("Super Bowl")?.id)
    }
}
