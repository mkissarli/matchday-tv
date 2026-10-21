package dev.matchday.tv.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * Matchday Editorial.
 *
 * Every colour and text style used by a screen is named here. The Floodlit theme will be a second
 * token set behind the same names, so switching visual direction never requires touching a screen.
 */
object MatchdayColors {
    val Ink = Color(0xFF0E0F10)
    val InkSoft = Color(0xFF151719)
    val Surface = Color(0xFF191C1E)
    val SurfaceRaised = Color(0xFF222629)
    val Ivory = Color(0xFFF2EBDD)
    val Body = Color(0xFFD7D0C4)
    val Muted = Color(0xFFA6A095)
    val Dim = Color(0xFF706B62)
    val Brick = Color(0xFFD95D43)
    val BrickSoft = Color(0xFF8F3D2D)
    val Pitch = Color(0xFF477456)
    val Focus = Color(0xFFF2EBDD)
    val Live = Color(0xFFE45B45)
    val CardLine = Color(0xFF303538)
    val HeroLine = Color(0xFF3B4144)
}

data class MatchdayType(
    val display: TextStyle,
    val title: TextStyle,
    val subtitle: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val meta: TextStyle,
    val score: TextStyle,
)

val MatchdayTypography = MatchdayType(
    display = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp,
        color = MatchdayColors.Ivory,
    ),
    title = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
        color = MatchdayColors.Ivory,
    ),
    subtitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        color = MatchdayColors.Body,
    ),
    body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = MatchdayColors.Body,
    ),
    label = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.3.sp,
        color = MatchdayColors.Muted,
    ),
    meta = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        color = MatchdayColors.Muted,
    ),
    score = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.8).sp,
        color = MatchdayColors.Ivory,
    ),
)

val LocalMatchdayType = staticCompositionLocalOf { MatchdayTypography }

@Composable
fun MatchdayTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = MatchdayColors.Brick,
        onPrimary = MatchdayColors.Ivory,
        secondary = MatchdayColors.Pitch,
        onSecondary = MatchdayColors.Ivory,
        background = MatchdayColors.Ink,
        onBackground = MatchdayColors.Ivory,
        surface = MatchdayColors.Surface,
        onSurface = MatchdayColors.Ivory,
        surfaceVariant = MatchdayColors.SurfaceRaised,
        onSurfaceVariant = MatchdayColors.Body,
    )
    CompositionLocalProvider(LocalMatchdayType provides MatchdayTypography) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

@Composable
fun MatchdayBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to MatchdayColors.Ink,
                    0.55f to MatchdayColors.InkSoft,
                    1f to Color(0xFF101214),
                ),
            ),
    ) {
        content()
    }
}
