package dev.matchday.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogScaleTest {

    @Test
    fun `builds a fifty thousand entry catalogue`() {
        val countries = listOf("US", "UK", "DE", "FR", "ES", "BR", "MX", "AR")
        val genres = listOf("Sports", "News", "Movies", "Kids", "Documentary")
        val raw = (1..50_000).map { index ->
            RawChannel(
                id = index.toLong(),
                sourceId = 1,
                name = "${countries[index % countries.size]}: Channel $index HD",
                streamUrl = "http://example.test/live/$index.m3u8",
                tvgId = "channel.$index",
                groupTitle = genres[index % genres.size],
            )
        }

        val started = System.currentTimeMillis()
        val result = CatalogBuilder.build(raw)
        val elapsed = System.currentTimeMillis() - started

        assertEquals(50_000, result.stats.rawCount)
        assertEquals(50_000, result.stats.logicalCount)
        assertTrue("catalogue build took ${elapsed}ms", elapsed < 30_000)
    }
}
