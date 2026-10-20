package dev.matchday.tv.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

@Composable
fun FocusableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
    contentPadding: PaddingValues = PaddingValues(22.dp),
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val border = when {
        focused -> BorderStroke(3.dp, MatchdayColors.Focus)
        selected -> BorderStroke(1.dp, MatchdayColors.Brick)
        else -> BorderStroke(1.dp, MatchdayColors.CardLine)
    }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.Spacebar)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .background(
                if (focused) MatchdayColors.SurfaceRaised else MatchdayColors.Surface,
                shape,
            )
            .border(border, shape)
            .padding(contentPadding),
    ) {
        content()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = LocalMatchdayType.current.label,
        color = MatchdayColors.Muted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun MetaChip(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Box(
        modifier = modifier
            .background(
                if (accent) MatchdayColors.Brick.copy(alpha = 0.16f) else MatchdayColors.SurfaceRaised,
                RoundedCornerShape(50),
            )
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = LocalMatchdayType.current.meta.copy(fontSize = 12.sp, letterSpacing = 0.8.sp),
            color = if (accent) MatchdayColors.Brick else MatchdayColors.Muted,
            maxLines = 1,
        )
    }
}

@Composable
fun EmptyState(
    eyebrow: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.padding(top = 36.dp)) {
        SectionLabel(eyebrow)
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            style = LocalMatchdayType.current.display,
            color = MatchdayColors.Ivory,
            maxLines = 3,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = body,
            style = LocalMatchdayType.current.body,
            color = MatchdayColors.Muted,
            modifier = Modifier.width(620.dp),
        )
        if (action != null) {
            Spacer(Modifier.height(28.dp))
            action()
        }
    }
}

@Composable
fun TopBar(
    selected: NavDestination,
    onSelect: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "MATCHDAY",
                style = LocalMatchdayType.current.title.copy(fontSize = 26.sp, letterSpacing = 0.5.sp),
                color = MatchdayColors.Ivory,
            )
            Text(
                text = "SPORT  ·  LIVE TV  ·  YOUR SOURCES",
                style = LocalMatchdayType.current.label.copy(fontSize = 10.sp, letterSpacing = 1.4.sp),
                color = MatchdayColors.Dim,
            )
        }
        Spacer(Modifier.width(56.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 12.dp),
        ) {
            items(NavDestination.entries) { destination ->
                NavPill(
                    label = destination.label,
                    selected = destination == selected,
                    onClick = { onSelect(destination) },
                )
            }
        }
    }
}

@Composable
private fun NavPill(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.Spacebar)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .background(
                when {
                    focused -> MatchdayColors.Ivory
                    selected -> MatchdayColors.SurfaceRaised
                    else -> Color.Transparent
                },
                shape,
            )
            .border(
                when {
                    focused -> BorderStroke(0.dp, Color.Transparent)
                    selected -> BorderStroke(1.dp, MatchdayColors.CardLine)
                    else -> BorderStroke(1.dp, Color.Transparent)
                },
                shape,
            )
            .padding(horizontal = 20.dp, vertical = 11.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = LocalMatchdayType.current.label.copy(fontSize = 12.sp, letterSpacing = 1.2.sp),
            color = when {
                focused -> MatchdayColors.Ink
                selected -> MatchdayColors.Ivory
                else -> MatchdayColors.Muted
            },
            maxLines = 1,
        )
    }
}

enum class NavDestination(val label: String) {
    SPORTS("Sports"),
    LIVE("Live TV"),
    GUIDE("Guide"),
    SETTINGS("Settings"),
}
