package dev.matchday.tv.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import tv.own.owntv.player.HeroPreviewEngine
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.player.livePreviewEngine
import tv.own.owntv.player.ownTVPlayer

/**
 * Playback bindings, adapted from OwnTV's TV shell (GPLv3). See THIRD_PARTY_NOTICES.md.
 * The engine builders themselves live in tv.own.owntv.player-core.
 */
val playerModule = module {
    single { tv.own.owntv.player.PlayerDiagnostics() }
    single { ownTVPlayer() }
    single { livePreviewEngine() }
    single { tv.own.owntv.player.LiveEnginePool { livePreviewEngine() } }
    single {
        HeroPreviewEngine(
            androidContext(),
            get(),
            get(),
            streamInUse = { get<OwnTVPlayer>().hasActiveStream },
        )
    }
    single {
        tv.own.owntv.player.PlaybackEngines(
            player = get(),
            livePreview = get(),
            pool = get(),
            heroPreview = get(),
        )
    }
    single { tv.own.owntv.player.PlaybackSession(androidContext()) }
    single { tv.own.owntv.player.ScreenOff(androidContext()) }
    single {
        tv.own.owntv.player.SleepTimer(
            active = get<tv.own.owntv.player.PlaybackSession>().active,
            screenOff = get(),
            itemEnd = get<OwnTVPlayer>(),
        )
    }
    single { tv.own.owntv.core.subtitles.SubtitleController(get(), get(), get(), get()) }
}
