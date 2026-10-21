package dev.matchday.tv.data

import android.content.Context
import dev.matchday.domain.catalog.QualityTier

/** Local playback preferences. No account, no telemetry. */
class PlaybackPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("matchday_playback", Context.MODE_PRIVATE)

    fun maxTier(): QualityTier {
        val raw = prefs.getString(KEY_MAX_TIER, QualityTier.UHD.name)
        return runCatching { QualityTier.valueOf(raw ?: "") }.getOrDefault(QualityTier.UHD)
    }

    fun setMaxTier(tier: QualityTier) {
        prefs.edit().putString(KEY_MAX_TIER, tier.name).apply()
    }

    private companion object {
        const val KEY_MAX_TIER = "max_tier"
    }
}
