package dev.matchday.tv

import android.app.Application
import androidx.work.Configuration
import dev.matchday.tv.di.appModule
import dev.matchday.tv.di.playerModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import tv.own.owntv.core.CoreBuildInfo
import tv.own.owntv.core.di.coreModule
import tv.own.owntv.core.di.databaseModule
import tv.own.owntv.core.di.dataModule
import tv.own.owntv.core.sync.work.KoinWorkerFactory

class MatchdayApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(KoinWorkerFactory())
            .build()

    override fun onCreate() {
        super.onCreate()

        // Everything core needs from its host, before Koin starts.
        CoreBuildInfo.versionName = BuildConfig.VERSION_NAME
        CoreBuildInfo.versionCode = BuildConfig.VERSION_CODE
        CoreBuildInfo.debug = BuildConfig.DEBUG
        CoreBuildInfo.devTools = false
        CoreBuildInfo.diagnosticBuild = false
        CoreBuildInfo.tvHome = false
        CoreBuildInfo.releaseRepo = ""

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@MatchdayApp)
            modules(coreModule, databaseModule, dataModule, playerModule, appModule)
        }

        // Persisted playback quirks and one-shot migrations. Safe to run after Koin.
        runCatching {
            tv.own.owntv.player.PlaybackStartup.start(
                context = this,
                scope = kotlinx.coroutines.CoroutineScope(
                    kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
                ),
                settings = GlobalContext.get().get(),
                archiveStore = GlobalContext.get().get(),
            )
        }
    }
}
