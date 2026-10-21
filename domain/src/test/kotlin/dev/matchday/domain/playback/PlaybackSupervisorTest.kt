package dev.matchday.domain.playback

import dev.matchday.domain.catalog.QualityTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSupervisorTest {

    private var now = 1_000_000L
    private val supervisor = PlaybackSupervisor(clock = { now })

    private fun candidate(
        key: String,
        channel: String,
        quality: QualityTier,
        language: String? = null,
    ) = PlaybackCandidate(
        key = key,
        url = "https://example.test/$key.m3u8",
        channelKey = channel,
        channelName = channel,
        quality = quality,
        languageCode = language,
    )

    @Test
    fun `prefers english before other languages`() {
        val choice = supervisor.start(
            listOf(
                candidate("fr-fhd", "fr", QualityTier.FHD, "fr"),
                candidate("en-hd", "en", QualityTier.HD, "en"),
            ),
        )
        assertEquals("en-hd", choice?.candidate?.key)
    }

    @Test
    fun `respects the quality cap`() {
        val choice = supervisor.start(
            listOf(candidate("uhd", "a", QualityTier.UHD), candidate("sd", "a", QualityTier.SD)),
            maxTier = QualityTier.HD,
        )
        assertEquals("sd", choice?.candidate?.key)
    }

    @Test
    fun `steps down within the same channel before failing over`() {
        supervisor.start(
            listOf(
                candidate("hd", "main", QualityTier.FHD, "en"),
                candidate("sd", "main", QualityTier.SD, "en"),
                candidate("backup", "zzz-backup", QualityTier.HD, "en"),
            ),
        )
        val next = supervisor.onFailure(PlaybackFailureKind.NETWORK)
        assertEquals("sd", next?.candidate?.key)
        assertEquals("main", next?.candidate?.channelKey)
    }

    @Test
    fun `moves to the next language after exhausting a channel`() {
        supervisor.start(
            listOf(
                candidate("main", "main", QualityTier.HD, "en"),
                candidate("en-backup", "en-backup", QualityTier.SD, "en"),
                candidate("fr-backup", "fr-backup", QualityTier.FHD, "fr"),
            ),
        )
        val english = supervisor.onFailure(PlaybackFailureKind.HTTP)
        assertEquals("en-backup", english?.candidate?.key)
        val french = supervisor.onFailure(PlaybackFailureKind.HTTP)
        assertEquals("fr-backup", french?.candidate?.key)
    }

    @Test
    fun `failed candidate cools down before another retry`() {
        supervisor.start(
            listOf(
                candidate("main", "main", QualityTier.HD, "en"),
                candidate("backup", "backup", QualityTier.SD, "en"),
            ),
        )
        supervisor.onFailure(PlaybackFailureKind.HTTP)
        assertEquals("backup", supervisor.current()?.key)
        assertTrue(supervisor.snapshot().failures.containsKey("main"))
        supervisor.onFailure(PlaybackFailureKind.HTTP)
        assertNull(supervisor.current())
    }

    @Test
    fun `blacklists after retry cap`() {
        supervisor.start(listOf(candidate("main", "main", QualityTier.HD, "en")))
        supervisor.onFailure(PlaybackFailureKind.HTTP)
        assertNull(supervisor.current())
        now += 21_000L
        supervisor.retry() // selects the cooled-down candidate
        assertEquals("main", supervisor.current()?.key)
        supervisor.onFailure(PlaybackFailureKind.HTTP) // second attempt blacklists it
        assertNull(supervisor.current())
        assertFalse(supervisor.snapshot().blacklisted.isEmpty())
    }

    @Test
    fun `stable playback clears failure counters`() {
        supervisor.start(
            listOf(candidate("a", "a", QualityTier.HD, "en"), candidate("b", "b", QualityTier.SD, "en")),
        )
        supervisor.onFailure(PlaybackFailureKind.STALL)
        assertTrue(supervisor.snapshot().failures.isNotEmpty())
        now += 20_000L
        supervisor.onPlaying(now)
        supervisor.onPlaying(now)
        val snapshot = supervisor.snapshot()
        assertTrue(snapshot.stable)
        assertTrue(snapshot.failures.isEmpty())
    }

    @Test
    fun `channel failovers are capped`() {
        val candidates = (1..6).map { candidate("c$it", "channel$it", QualityTier.HD, "en") }
        supervisor.start(candidates)
        repeat(3) {
            supervisor.onFailure(PlaybackFailureKind.NETWORK)
        }
        assertNull(supervisor.onFailure(PlaybackFailureKind.NETWORK))
        assertEquals(3, supervisor.snapshot().channelFailovers)
    }
}
