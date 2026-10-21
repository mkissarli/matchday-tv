package dev.matchday.tv.data

import dev.matchday.domain.schedule.FixtureProvider
import dev.matchday.domain.schedule.ScheduleEvent
import dev.matchday.domain.schedule.ScheduleQuery
import dev.matchday.domain.schedule.ScheduleStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

/**
 * Keyless MotoGP race-weekend calendar. It is schedule-only: no session results, no
 * timing and no championship points.
 */
class MotoGpProvider(
    client: OkHttpClient,
) : FixtureProvider {

    override val id: String = "motogp"
    override val displayName: String = "MotoGP"
    override val attribution: String = "MotoGP calendar"

    private val http = client.newBuilder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedYear: Int = 0

    @Volatile
    private var cachedAtMs: Long = 0L

    @Volatile
    private var cached: List<ScheduleEvent> = emptyList()

    override suspend fun events(query: ScheduleQuery): List<ScheduleEvent> = withContext(Dispatchers.IO) {
        val year = LocalDate.now().year
        val events = if (year == cachedYear && System.currentTimeMillis() - cachedAtMs < CACHE_TTL_MS) {
            cached
        } else {
            fetchSeason(year).also {
                cachedYear = year
                cachedAtMs = System.currentTimeMillis()
                cached = it
            }
        }
        events.filter { query.matches(it) }.sortedBy { it.startMs }
    }

    private fun fetchSeason(year: Int): List<ScheduleEvent> {
        val url = "https://api.motogp.pulselive.com/motogp/v1/events?seasonYear=$year"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Matchday/0.1 (Android TV)")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("MotoGP HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val array = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
            val out = ArrayList<ScheduleEvent>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
                if (!name.contains("GRAND PRIX", ignoreCase = true)) continue
                val start = parseDate(item.optString("date_start")) ?: continue
                val end = parseDate(item.optString("date_end"))
                val circuit = item.optJSONObject("circuit")
                val venue = circuit?.optString("name")?.takeIf { it.isNotBlank() }
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: "$start:$name"
                out += ScheduleEvent(
                    id = "motogp:$id",
                    providerId = id,
                    sportId = "motorsport",
                    competitionId = null,
                    competitionName = "MotoGP",
                    title = name,
                    participants = emptyList(),
                    startMs = start,
                    endMs = end,
                    venue = venue,
                    status = if (item.optString("status").contains("CANCEL", true)) {
                        ScheduleStatus.CANCELLED
                    } else {
                        ScheduleStatus.SCHEDULED
                    },
                )
            }
            return out
        }
    }

    private fun parseDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
    }

    private companion object {
        const val CACHE_TTL_MS = 60 * 60_000L
    }
}
