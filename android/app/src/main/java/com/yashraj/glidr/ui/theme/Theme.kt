package com.yashraj.glidr.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yashraj.glidr.R

@Immutable
class GlidrColors(
    val bg: Color, val s1: Color, val s2: Color, val s3: Color,
    val line: Color, val text: Color, val dim: Color, val dot: Color,
    val accent: Color, val accentText: Color, val ink: Color, val light: Boolean,
    /** Errors and "can't connect" states. */
    val danger: Color = Color(0xFFFF6B6B),
)

/**
 * Corner radii, one scale for the whole app. Nested shapes follow `inner = outer - padding` (segmented control
 * 14 → 11, dock 32 → 26) so curves stay concentric.
 */
object Radius {
    val key = 12.dp
    val chip = 14.dp
    val control = 20.dp
    val tile = 22.dp
    val card = 24.dp
    val sheet = 28.dp
    val pad = 32.dp
}

/** Spacing scale (multiples of 2 or 4): gaps inside a group, between groups, and the screen gutter. */
object Space {
    val hair = 2.dp
    val xs = 6.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

private val ACCENTS = listOf(
    Color(0xFFD4FF3F) to Color(0xFF4A6000), // Volt
    Color(0xFFFF8A5B) to Color(0xFFA23A0C), // Ember
    Color(0xFF7DB2FF) to Color(0xFF1D4FB0), // Sky
    Color(0xFFFF7FB0) to Color(0xFFA8265B), // Rose
)
val ACCENT_NAMES = listOf("Volt", "Ember", "Sky", "Rose")
val ACCENT_COLORS: List<Color> = ACCENTS.map { it.first }

fun glidrColors(light: Boolean, accentIndex: Int): GlidrColors {
    val (ac, deep) = ACCENTS[accentIndex.coerceIn(0, ACCENTS.lastIndex)]
    val ink = Color(0xFF0B0C0A)
    return if (light) {
        GlidrColors(
            bg = Color(0xFFF1F2F4), s1 = Color(0xFFFFFFFF), s2 = Color(0xFFE7E8EB), s3 = Color(0xFFD8DADF),
            line = Color(0x17000000), text = Color(0xFF141517), dim = Color(0xFF565963), dot = Color(0x1A000000),
            accent = ac, accentText = deep, ink = ink, light = true, danger = Color(0xFFB3261E),
        )
    } else {
        GlidrColors(
            bg = Color(0xFF000000), s1 = Color(0xFF0C0D0F), s2 = Color(0xFF16171A), s3 = Color(0xFF222328),
            line = Color(0x14FFFFFF), text = Color(0xFFF3F2EE), dim = Color(0xFF9A9CA6), dot = Color(0x13FFFFFF),
            accent = ac, accentText = ac, ink = ink, light = false,
        )
    }
}

@Immutable
class GlidrType(
    val body: TextStyle, val label: TextStyle, val title: TextStyle, val mono: TextStyle, val small: TextStyle,
    /** Sheet and dialog titles; screen titles use [title]. */
    val heading: TextStyle = title.copy(fontSize = 22.sp),
    /** Emphasised line inside a card, such as the track title. */
    val bodyLarge: TextStyle = body.copy(fontSize = 20.sp),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun geist(w: Int) = Font(R.font.geist, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun mono(w: Int) = Font(R.font.geist_mono, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

private val Geist = FontFamily(geist(400), geist(500), geist(600), geist(700))
private val GeistMono = FontFamily(mono(400), mono(500), mono(600))

private val Type = GlidrType(
    body = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    label = TextStyle(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.05.sp),
    title = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.8).sp),
    mono = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    small = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

private val LocalColors = compositionLocalOf { glidrColors(false, 0) }
private val LocalType = compositionLocalOf { Type }
val LocalReduceMotion = compositionLocalOf { false }

object Glidr {
    val colors: GlidrColors @Composable @ReadOnlyComposable get() = LocalColors.current
    val type: GlidrType @Composable @ReadOnlyComposable get() = LocalType.current
}

@Composable
fun GlidrTheme(light: Boolean, accent: Int, reduceMotion: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalColors provides glidrColors(light, accent),
        LocalType provides Type,
        LocalReduceMotion provides reduceMotion,
        content = content,
    )
}
