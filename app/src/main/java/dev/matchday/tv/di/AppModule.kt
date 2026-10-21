package dev.matchday.tv.di

import dev.matchday.tv.data.BroadcastLookup
import dev.matchday.tv.data.CompositeScheduleProvider
import dev.matchday.tv.data.EspnMultiSportProvider
import dev.matchday.tv.data.EspnSoccerProvider
import dev.matchday.tv.data.MatchRepository
import dev.matchday.tv.data.MergedSportProvider
import dev.matchday.tv.data.MotoGpProvider
import dev.matchday.tv.data.TheSportsDbSeasonProvider
import dev.matchday.tv.data.PlaybackPreferences
import dev.matchday.tv.data.ScheduleCache
import dev.matchday.tv.data.TheSportsDbProvider
import dev.matchday.domain.schedule.FixtureProvider
import dev.matchday.tv.feature.catalog.CatalogDebugViewModel
import dev.matchday.tv.feature.live.GuideViewModel
import dev.matchday.tv.feature.live.LiveViewModel
import dev.matchday.tv.feature.settings.SettingsViewModel
import dev.matchday.tv.feature.sports.SportsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single<FixtureProvider> {
        CompositeScheduleProvider(
            football = EspnSoccerProvider(get()),
            otherSports = MergedSportProvider(
                listOf(
                    EspnMultiSportProvider(get()),
                    MotoGpProvider(get()),
                    TheSportsDbSeasonProvider(get()),
                ),
            ),
            fallbackSports = TheSportsDbProvider(get()),
        )
    }
    single { ScheduleCache(androidContext().filesDir.resolve("schedule-cache.json")) }
    single { PlaybackPreferences(androidContext()) }
    single { MatchRepository(get(), get(), get(), get()) }
    single { BroadcastLookup(get()) }
    viewModelOf(::LiveViewModel)
    viewModelOf(::GuideViewModel)
    viewModelOf(::SportsViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::CatalogDebugViewModel)
}
