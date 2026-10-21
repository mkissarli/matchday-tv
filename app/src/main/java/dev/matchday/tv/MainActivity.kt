package dev.matchday.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.matchday.tv.design.MatchdayBackground
import dev.matchday.tv.design.MatchdayTheme
import dev.matchday.tv.design.NavDestination
import dev.matchday.tv.design.TopBar
import dev.matchday.tv.feature.catalog.CatalogDebugScreen
import dev.matchday.tv.feature.live.GuideScreen
import dev.matchday.tv.feature.live.LiveScreen
import dev.matchday.tv.feature.live.PlayableChannel
import dev.matchday.tv.feature.player.PlayerScreen
import dev.matchday.tv.feature.settings.SettingsScreen
import dev.matchday.tv.feature.sports.SportsScreen
import org.koin.android.ext.android.inject
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.player.PlaybackEngines

class MainActivity : ComponentActivity() {

    private val engines: PlaybackEngines by inject()
    private val debugUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        debugUrl.value = if (BuildConfig.DEBUG) intent?.getStringExtra(EXTRA_DEBUG_URL) else null
        setContent {
            MatchdayTheme {
                MatchdayRoot(debugUrl = debugUrl.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (BuildConfig.DEBUG) debugUrl.value = intent.getStringExtra(EXTRA_DEBUG_URL)
    }

    private companion object {
        const val EXTRA_DEBUG_URL = "debug_url"
    }

    override fun onStart() {
        super.onStart()
        engines.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) engines.onAppBackgrounded()
    }
}

@Composable
private fun MatchdayRoot(debugUrl: String?) {
    var destination by rememberSaveable { mutableStateOf(NavDestination.SPORTS) }
    var catalogDebug by remember { mutableStateOf(false) }
    var queue by remember {
        mutableStateOf(
            debugUrl?.let {
                listOf(
                    PlayableChannel(
                        channel = ChannelEntity(sourceId = 0, name = "Debug stream", streamUrl = it),
                        source = null,
                        categoryName = "Debug",
                    ),
                )
            } ?: emptyList(),
        )
    }

    if (queue.isNotEmpty()) {
        PlayerScreen(queue = queue, onBack = { queue = emptyList() })
        return
    }

    if (catalogDebug) {
        MatchdayBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 44.dp, vertical = 22.dp),
            ) {
                CatalogDebugScreen(onBack = { catalogDebug = false })
            }
        }
        return
    }

    MatchdayBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 44.dp, vertical = 22.dp),
        ) {
            TopBar(
                selected = destination,
                onSelect = { destination = it },
            )
            Spacer(Modifier.height(16.dp))
            when (destination) {
                NavDestination.SPORTS -> SportsScreen(
                    onPlay = { queue = it },
                    onOpenChannels = { destination = NavDestination.LIVE },
                    modifier = Modifier.weight(1f),
                )
                NavDestination.LIVE -> LiveScreen(
                    onPlay = { queue = it },
                    modifier = Modifier.weight(1f),
                )
                NavDestination.GUIDE -> GuideScreen(modifier = Modifier.weight(1f))
                NavDestination.SETTINGS -> SettingsScreen(
                    onOpenCatalog = { catalogDebug = true },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
