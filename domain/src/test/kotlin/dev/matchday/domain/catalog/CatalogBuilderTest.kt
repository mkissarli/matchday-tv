package dev.matchday.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogBuilderTest {

    private fun raw(
        id: Long,
        name: String,
        sourceId: Long = 1,
        tvgId: String? = null,
        tvgName: String? = null,
        group: String? = null,
        country: String? = null,
        language: String? = null,
    ) = RawChannel(
        id = id,
        sourceId = sourceId,
        name = name,
        streamUrl = "http://example.test/$id",
        tvgId = tvgId,
        tvgName = tvgName,
        tvgLanguage = language,
        tvgCountry = country,
        groupTitle = group,
    )

    @Test
    fun `normalizer strips provider tags but keeps identity`() {
        assertEquals("canal", NameNormalizer.normalize("FR: Canal+ UHD HEVC"))
        assertEquals("bein sports 1", NameNormalizer.normalize("|AR| beIN SPORTS 1 HD"))
        assertEquals("bbc one", NameNormalizer.normalize("UK| BBC ONE +1"))
        assertEquals("sky sports f1", NameNormalizer.normalize("Sky Sports F1 [4K]"))
    }

    @Test
    fun `quality parser picks the highest advertised tier`() {
        assertEquals(QualityTier.EIGHT_K, NameNormalizer.quality("8K").tier)
        assertEquals(QualityTier.UHD, NameNormalizer.quality("4K UHD").tier)
        assertEquals(QualityTier.FHD, NameNormalizer.quality("1080p FHD").tier)
        assertEquals(QualityTier.HD, NameNormalizer.quality("720p HD").tier)
        assertEquals(QualityTier.SD, NameNormalizer.quality("SD").tier)
        assertEquals("HEVC", NameNormalizer.quality("4K HEVC").codec)
    }

    @Test
    fun `language detects provider prefixes and countries`() {
        assertEquals("FR", LanguageCatalog.detect("FR: Canal+", null, null)?.code)
        assertEquals("AR", LanguageCatalog.detect("|AR| beIN", null, null)?.code)
        assertEquals("EN", LanguageCatalog.detect("UK| BBC One", null, null)?.code)
        assertEquals("AR", LanguageCatalog.detect("MBC 1", null, "AR")?.code)
        assertEquals("EN", LanguageCatalog.fromCountry("US")?.code)
    }

    @Test
    fun `genre classifier is rule based and ordered`() {
        assertEquals("news", GenreCatalog.classify("Sky News HD").id)
        assertEquals("sport", GenreCatalog.classify("Sky Sports F1").id)
        assertEquals("kids", GenreCatalog.classify("Cartoon Network").id)
        assertEquals("movies", GenreCatalog.classify("Sky Cinema Premiere").id)
        assertEquals("music", GenreCatalog.classify("MTV Hits").id)
        assertEquals("unsorted", GenreCatalog.classify("Channel 47").id)
    }

    @Test
    fun `same normalised name in one source merges into variants`() {
        val result = CatalogBuilder.build(
            listOf(
                raw(1, "Canal+ UHD", tvgName = "Canal+", group = "Movies"),
                raw(2, "Canal+ HD", tvgName = "Canal+", group = "Movies"),
            ),
        )
        assertEquals(1, result.channels.size)
        val channel = result.channels.single()
        assertEquals(2, channel.variants.size)
        assertEquals(QualityTier.UHD, channel.variants.first().quality)
        assertEquals(0.95, channel.confidence, 0.001)
        assertFalse(channel.unsorted)
    }

    @Test
    fun `same name across sources merges but is marked low confidence`() {
        val result = CatalogBuilder.build(
            listOf(
                raw(1, "BBC One HD", sourceId = 1),
                raw(2, "BBC One FHD", sourceId = 2),
            ),
        )
        assertEquals(1, result.channels.size)
        assertEquals(0.82, result.channels.single().confidence, 0.001)
        assertEquals(1, result.lowConfidence.size)
        assertEquals(2, result.merges.single().rawChannelIds.size)
    }

    @Test
    fun `different epg ids do not merge even when names match`() {
        val result = CatalogBuilder.build(
            listOf(
                raw(1, "Sky Sports 1 HD", tvgId = "sky.sports.1"),
                raw(2, "Sky Sports 1 HD", tvgId = "sky.sports.1.alt"),
            ),
        )
        assertEquals(2, result.channels.size)
        assertNotEquals(result.channels[0].key, result.channels[1].key)
    }

    @Test
    fun `different channel numbers do not merge`() {
        val result = CatalogBuilder.build(
            listOf(
                raw(1, "Sky Sports 1 HD"),
                raw(2, "Sky Sports 2 HD"),
            ),
        )
        assertEquals(2, result.channels.size)
    }

    @Test
    fun `unsorted channels are kept and counted`() {
        val result = CatalogBuilder.build(
            listOf(
                raw(1, "Channel 47"),
                raw(2, "Sky News"),
            ),
        )
        assertEquals(2, result.channels.size)
        assertEquals(1, result.unsorted.size)
        assertEquals("Channel 47", result.unsorted.single().name)
    }

    @Test
    fun `user override wins over rules`() {
        val result = CatalogBuilder.build(
            listOf(raw(1, "Channel 47", country = "FR")),
            overrides = mapOf(1L to ChannelOverride(languageCode = "AR", genreId = "movies")),
        )
        val channel = result.channels.single()
        assertEquals("AR", channel.language?.code)
        assertEquals("movies", channel.genre.id)
        assertFalse(channel.unsorted)
    }

    @Test
    fun `quality variants are ordered best first`() {
        val result = CatalogBuilder.build(
            listOf(
                raw(1, "Canal+ SD"),
                raw(2, "Canal+ 4K"),
                raw(3, "Canal+ HD"),
            ),
        )
        assertEquals(
            listOf(QualityTier.UHD, QualityTier.HD, QualityTier.SD),
            result.channels.single().variants.map { it.quality },
        )
    }
}
