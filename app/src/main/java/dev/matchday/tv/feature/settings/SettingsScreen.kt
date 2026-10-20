package dev.matchday.tv.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.matchday.tv.BuildConfig
import dev.matchday.tv.design.LocalMatchdayType
import dev.matchday.tv.design.MatchdayColors
import dev.matchday.tv.design.SectionLabel

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Settings",
            style = LocalMatchdayType.current.display,
            color = MatchdayColors.Ivory,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "The essentials now. Playback tuning, schedule sources and phone setup follow.",
            style = LocalMatchdayType.current.body,
            color = MatchdayColors.Muted,
        )
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SettingCard(
                title = "Visual direction",
                value = "Matchday Editorial",
                note = "Warm paper, ink and brick. Tokenised so Floodlit can replace it.",
                modifier = Modifier.weight(1f),
            )
            SettingCard(
                title = "Playback",
                value = "Media3 + libmpv",
                note = "Raw MPEG-TS, HLS and DASH. libmpv is the working path while ExoPlayer is repaired.",
                modifier = Modifier.weight(1f),
            )
            SettingCard(
                title = "Build",
                value = BuildConfig.VERSION_NAME,
                note = "GPLv3 · no accounts · no telemetry",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(30.dp))
        SectionLabel("Attribution")
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Matchday is built on OwnTV Core (GPLv3). Source and licence notices ship in the repository.",
            style = LocalMatchdayType.current.body,
            color = MatchdayColors.Muted,
        )
    }
}

@Composable
private fun SettingCard(
    title: String,
    value: String,
    note: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MatchdayColors.Surface, RoundedCornerShape(18.dp))
            .padding(22.dp),
    ) {
        SectionLabel(title)
        Spacer(Modifier.height(14.dp))
        Text(
            text = value,
            style = LocalMatchdayType.current.title.copy(fontSize = 26.sp),
            color = MatchdayColors.Ivory,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = note,
            style = LocalMatchdayType.current.meta,
            color = MatchdayColors.Muted,
        )
    }
}
