package dev.matchday.tv.data

import dev.matchday.domain.schedule.Artwork
import dev.matchday.domain.schedule.Participant
import dev.matchday.domain.schedule.ParticipantKind
import dev.matchday.domain.schedule.ScheduleEvent
import dev.matchday.domain.schedule.ScheduleStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Last-good schedule on disk. The Sports Hub opens instantly on a cold start and keeps working
 * when a free provider is rate-limited or the TV is offline.
 */
class ScheduleCache(private val file: File) {

    data class Cached(
        val events: List<ScheduleEvent>,
        val savedAtMs: Long,
    )

    @Synchronized
    fun load(): Cached {
        if (!file.isFile) return Cached(emptyList(), 0L)
        return runCatching {
            val root = JSONObject(file.readText())
            val savedAt = root.optLong("savedAtMs", 0L)
            val array = root.optJSONArray("events") ?: JSONArray()
            val events = buildList {
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let(::readEvent)?.let(::add)
                }
            }
            Cached(events.sortedBy { it.startMs }, savedAt)
        }.getOrDefault(Cached(emptyList(), 0L))
    }

    @Synchronized
    fun save(events: List<ScheduleEvent>) {
        runCatching {
            val root = JSONObject()
                .put("savedAtMs", System.currentTimeMillis())
                .put("events", JSONArray().apply { events.forEach { put(writeEvent(it)) } })
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(root.toString())
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }

    private fun writeEvent(event: ScheduleEvent): JSONObject = JSONObject().apply {
        put("id", event.id)
        put("providerId", event.providerId)
        put("sportId", event.sportId)
        put("competitionId", event.competitionId)
        put("competitionName", event.competitionName)
        put("title", event.title)
        put("startMs", event.startMs)
        put("endMs", event.endMs)
        put("venue", event.venue)
        put("broadcaster", event.broadcaster)
        put("status", event.status.name)
        put("eventLogoUrl", event.artwork.eventLogoUrl)
        put("competitionLogoUrl", event.artwork.competitionLogoUrl)
        put("participants", JSONArray().apply {
            event.participants.forEach { participant ->
                put(JSONObject().apply {
                    put("id", participant.id)
                    put("name", participant.name)
                    put("shortName", participant.shortName)
                    put("kind", participant.kind.name)
                    put("logoUrl", participant.logoUrl)
                    put("portraitUrl", participant.portraitUrl)
                    put("countryCode", participant.countryCode)
                })
            }
        })
    }

    private fun readEvent(json: JSONObject): ScheduleEvent? {
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        val title = json.optString("title").takeIf { it.isNotBlank() } ?: return null
        val startMs = json.optLong("startMs", 0L)
        if (startMs <= 0L) return null
        val participantsJson = json.optJSONArray("participants") ?: JSONArray()
        val participants = buildList {
            for (i in 0 until participantsJson.length()) {
                val item = participantsJson.optJSONObject(i) ?: continue
                val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
                add(
                    Participant(
                        id = item.optString("id", name),
                        name = name,
                        shortName = item.optString("shortName").takeIf { it.isNotBlank() },
                        kind = runCatching { ParticipantKind.valueOf(item.optString("kind")) }
                            .getOrDefault(ParticipantKind.OTHER),
                        logoUrl = item.optString("logoUrl").takeIf { it.isNotBlank() },
                        portraitUrl = item.optString("portraitUrl").takeIf { it.isNotBlank() },
                        countryCode = item.optString("countryCode").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
        return ScheduleEvent(
            id = id,
            providerId = json.optString("providerId"),
            sportId = json.optString("sportId", "other"),
            competitionId = json.optString("competitionId").takeIf { it.isNotBlank() },
            competitionName = json.optString("competitionName").takeIf { it.isNotBlank() },
            title = title,
            participants = participants,
            startMs = startMs,
            endMs = json.optLong("endMs", 0L).takeIf { it > 0L },
            venue = json.optString("venue").takeIf { it.isNotBlank() },
            broadcaster = json.optString("broadcaster").takeIf { it.isNotBlank() },
            artwork = Artwork(
                eventLogoUrl = json.optString("eventLogoUrl").takeIf { it.isNotBlank() },
                competitionLogoUrl = json.optString("competitionLogoUrl").takeIf { it.isNotBlank() },
            ),
            status = runCatching { ScheduleStatus.valueOf(json.optString("status")) }
                .getOrDefault(ScheduleStatus.SCHEDULED),
        )
    }
}
