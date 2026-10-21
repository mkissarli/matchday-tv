package dev.matchday.tv.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import dev.matchday.domain.catalog.QualityTier
import dev.matchday.tv.BuildConfig
import dev.matchday.tv.design.FocusableSurface
import dev.matchday.tv.design.LocalMatchdayType
import dev.matchday.tv.design.MatchdayColors
import dev.matchday.tv.design.SectionLabel
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    onOpenCatalog: () -> Unit = {},
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.startPhoneSetup() }
    DisposableEffect(Unit) {
        onDispose { vm.stopPhoneSetup() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Settings", style = LocalMatchdayType.current.display, color = MatchdayColors.Ivory)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Your sources, your screen. Nothing is bundled or uploaded.",
            style = LocalMatchdayType.current.body,
            color = MatchdayColors.Muted,
        )
        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1.15f)) {
                PhoneSetupCard(
                    state = state,
                    onRestart = {
                        vm.stopPhoneSetup()
                        vm.startPhoneSetup()
                    },
                )
                Spacer(Modifier.height(14.dp))
                AddSourceCard(
                    state = state,
                    onImport = vm::importManual,
                )
            }
            Column(Modifier.weight(0.85f)) {
                PlaybackCard(state = state, onSelectTier = vm::setMaxTier)
                Spacer(Modifier.height(14.dp))
                SourcesCard(
                    state = state,
                    onClearSchedule = vm::clearScheduleCache,
                    onOpenCatalog = onOpenCatalog,
                )
            }
        }

        state.message?.let { message ->
            Spacer(Modifier.height(14.dp))
            Text(message, style = LocalMatchdayType.current.meta, color = MatchdayColors.Pitch)
        }
        state.error?.let { error ->
            Spacer(Modifier.height(10.dp))
            Text(error, style = LocalMatchdayType.current.meta, color = MatchdayColors.Brick)
        }
        Spacer(Modifier.height(22.dp))
        SectionLabel("Attribution")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Matchday is built on OwnTV Core (GPLv3). Source and licence notices ship in the repository. " +
                "Fixtures: ESPN football, TheSportsDB other sports. Matchday shows schedules, never scores.",
            style = LocalMatchdayType.current.meta,
            color = MatchdayColors.Dim,
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PhoneSetupCard(state: SettingsUiState, onRestart: () -> Unit) {
    Column(Modifier.card()) {
        SectionLabel("PHONE SETUP")
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (state.setup != null) "Send a playlist from your phone." else "Opening the setup page…",
            style = LocalMatchdayType.current.title.copy(fontSize = 23.sp),
            color = MatchdayColors.Ivory,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "Same Wi-Fi. No account, no cloud relay. The pairing code is shown only on this TV.",
            style = LocalMatchdayType.current.meta,
            color = MatchdayColors.Muted,
        )
        Spacer(Modifier.height(12.dp))
        val setup = state.setup
        if (setup == null) {
            Text(
                text = state.setupError ?: "Starting…",
                style = LocalMatchdayType.current.meta,
                color = if (state.setupError != null) MatchdayColors.Brick else MatchdayColors.Muted,
            )
            Spacer(Modifier.height(10.dp))
            FocusableSurface(
                onClick = onRestart,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text("Try again", style = LocalMatchdayType.current.subtitle.copy(fontSize = 15.sp), color = MatchdayColors.Ivory)
            }
        } else {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("OPEN ON YOUR PHONE")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = setup.url,
                        style = LocalMatchdayType.current.title.copy(fontSize = 26.sp),
                        color = MatchdayColors.Ivory,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    SectionLabel("CODE")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = setup.code,
                        style = LocalMatchdayType.current.title.copy(fontSize = 30.sp),
                        color = MatchdayColors.Brick,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (state.importing) state.message ?: "Importing…" else "Keep this screen open while the phone sends the playlist.",
                style = LocalMatchdayType.current.meta,
                color = if (state.importing) MatchdayColors.Pitch else MatchdayColors.Dim,
            )
            Spacer(Modifier.height(10.dp))
            FocusableSurface(
                onClick = onRestart,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("Restart setup page", style = LocalMatchdayType.current.meta, color = MatchdayColors.Muted)
            }
        }
    }
}

@Composable
private fun AddSourceCard(state: SettingsUiState, onImport: (String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("My playlist") }
    var url by rememberSaveable { mutableStateOf(if (BuildConfig.DEBUG) "http://10.0.2.2:8088/playlist.m3u" else "") }
    var epg by rememberSaveable { mutableStateOf("") }

    Column(Modifier.card()) {
        SectionLabel("ADD A SOURCE")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Paste it here if you prefer the remote.",
            style = LocalMatchdayType.current.title.copy(fontSize = 21.sp),
            color = MatchdayColors.Ivory,
        )
        Spacer(Modifier.height(10.dp))
        SettingsField(value = name, onValueChange = { name = it }, label = "Playlist name")
        Spacer(Modifier.height(8.dp))
        SettingsField(
            value = url,
            onValueChange = { url = it },
            label = "M3U / M3U8 / Xtream get.php URL",
            keyboardType = KeyboardType.Uri,
        )
        Spacer(Modifier.height(8.dp))
        SettingsField(
            value = epg,
            onValueChange = { epg = it },
            label = "XMLTV EPG URL · optional",
            keyboardType = KeyboardType.Uri,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            FocusableSurface(
                onClick = { onImport(name, url, epg) },
                selected = true,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    text = if (state.importing) "Importing…" else "Import source",
                    style = LocalMatchdayType.current.subtitle.copy(fontSize = 15.sp),
                    color = MatchdayColors.Ivory,
                )
            }
            state.progress?.let {
                Spacer(Modifier.width(12.dp))
                Text(it, style = LocalMatchdayType.current.meta, color = MatchdayColors.Muted)
            }
        }
        state.message?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(message, style = LocalMatchdayType.current.meta, color = MatchdayColors.Pitch)
        }
        state.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(error, style = LocalMatchdayType.current.meta, color = MatchdayColors.Brick)
        }
    }
}

@Composable
private fun PlaybackCard(state: SettingsUiState, onSelectTier: (QualityTier) -> Unit) {
    Column(Modifier.card()) {
        SectionLabel("PLAYBACK")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Auto quality ceiling",
            style = LocalMatchdayType.current.title.copy(fontSize = 21.sp),
            color = MatchdayColors.Ivory,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "The player steps down on errors and fails over to another channel for the same fixture. Language order: English → French → Arabic → German.",
            style = LocalMatchdayType.current.meta,
            color = MatchdayColors.Muted,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            QualityTier.entries
                .filter { it != QualityTier.UNKNOWN && it != QualityTier.EIGHT_K }
                .forEach { tier ->
                    TierChip(
                        label = if (tier == QualityTier.UHD) "AUTO / 4K" else tier.label.ifBlank { tier.name },
                        selected = state.maxTier == tier,
                        onClick = { onSelectTier(tier) },
                    )
                }
        }
    }
}

@Composable
private fun SourcesCard(
    state: SettingsUiState,
    onClearSchedule: () -> Unit,
    onOpenCatalog: () -> Unit,
) {
    Column(Modifier.card()) {
        SectionLabel("SOURCES")
        Spacer(Modifier.height(8.dp))
        if (state.sources.isEmpty()) {
            Text(
                text = "No sources yet. Use your phone or paste a URL above.",
                style = LocalMatchdayType.current.meta,
                color = MatchdayColors.Muted,
            )
        } else {
            state.sources.forEach { source ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = source.name,
                            style = LocalMatchdayType.current.subtitle.copy(fontSize = 15.sp),
                            color = MatchdayColors.Ivory,
                        )
                        Text(
                            text = "${source.type.name.lowercase()} · ${source.url.take(42)}",
                            style = LocalMatchdayType.current.meta.copy(fontSize = 11.sp),
                            color = MatchdayColors.Dim,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusableSurface(
                onClick = onClearSchedule,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("Clear saved schedule", style = LocalMatchdayType.current.meta, color = MatchdayColors.Muted)
            }
            if (BuildConfig.DEBUG) {
                FocusableSurface(
                    onClick = onOpenCatalog,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("Catalog debug", style = LocalMatchdayType.current.meta, color = MatchdayColors.Muted)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "GPLv3 · no accounts · no telemetry · ${BuildConfig.VERSION_NAME}",
            style = LocalMatchdayType.current.meta.copy(fontSize = 11.sp),
            color = MatchdayColors.Dim,
        )
    }
}

@Composable
private fun TierChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        selected = selected,
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = LocalMatchdayType.current.meta.copy(fontSize = 12.sp),
            color = if (selected) MatchdayColors.Ink else MatchdayColors.Muted,
            maxLines = 1,
        )
    }
}

@Composable
private fun SettingsField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Column {
        Text(
            text = label.uppercase(),
            style = LocalMatchdayType.current.label.copy(fontSize = 9.sp),
            color = if (focused) MatchdayColors.Brick else MatchdayColors.Dim,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MatchdayColors.Ink, RoundedCornerShape(7.dp))
                .border(
                    if (focused) 2.dp else 1.dp,
                    if (focused) MatchdayColors.Focus else MatchdayColors.CardLine,
                    RoundedCornerShape(7.dp),
                )
                .padding(horizontal = 11.dp, vertical = 9.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalMatchdayType.current.meta.copy(color = MatchdayColors.Ivory, fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isBlank()) {
                        Text(
                            text = if (keyboardType == KeyboardType.Uri) "https://…" else "Playlist",
                            style = LocalMatchdayType.current.meta.copy(fontSize = 14.sp),
                            color = Color(0xFF5F5B55),
                        )
                    }
                    inner()
                },
            )
        }
    }
}

private fun Modifier.card(): Modifier = this
    .fillMaxWidth()
    .background(MatchdayColors.Surface, RoundedCornerShape(12.dp))
    .border(1.dp, MatchdayColors.CardLine, RoundedCornerShape(12.dp))
    .padding(16.dp)
