package dev.matchday.tv.feature.live

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.matchday.tv.design.EmptyState

@Composable
fun GuideScreen(modifier: Modifier = Modifier) {
    EmptyState(
        eyebrow = "TV GUIDE",
        title = "The guide comes next.",
        body = "The EPG grid, now/next and channel timelines are milestone 3. The playlist and player land first.",
        modifier = modifier,
    )
}
