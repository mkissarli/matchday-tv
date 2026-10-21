package dev.matchday.tv.data

import dev.matchday.domain.schedule.FixtureProvider
import dev.matchday.domain.schedule.ScheduleEvent
import dev.matchday.domain.schedule.ScheduleQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException

/** Fan-out provider for independent keyless sources; one broken feed does not hide the rest. */
class MergedSportProvider(
    private val providers: List<FixtureProvider>,
) : FixtureProvider {
    override val id: String = "merged-sport"
    override val displayName: String = "ESPN + MotoGP + TheSportsDB"
    override val attribution: String = providers.joinToString(" · ") { it.displayName }

    override suspend fun events(query: ScheduleQuery): List<ScheduleEvent> = withContext(Dispatchers.IO) {
        val results = coroutineScope {
            providers.map { provider -> async { runCatching { provider.events(query) } } }.awaitAll()
        }
        val events = results.flatMap { it.getOrDefault(emptyList()) }
        if (events.isEmpty() && results.all { it.isFailure }) {
            throw results.firstNotNullOfOrNull { it.exceptionOrNull() }
                ?: IOException("No sport schedule provider answered")
        }
        events.distinctBy { it.id }.sortedBy { it.startMs }
    }
}
