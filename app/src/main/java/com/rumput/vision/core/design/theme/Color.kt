package com.rumput.vision.core.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Raw palette. Screens use MaterialTheme.colorScheme or [StatusColors], never these directly. */
internal object Palette {
    val Ink950 = Color(0xFF0B0D10)
    val Ink900 = Color(0xFF101318)
    val Ink850 = Color(0xFF14181E)
    val Ink800 = Color(0xFF191E25)
    val Ink750 = Color(0xFF1F252D)
    val Ink700 = Color(0xFF262D37)
    val Ink600 = Color(0xFF353E4A)
    val Ink400 = Color(0xFF6B7684)
    val Ink300 = Color(0xFF9AA3AF)
    val Ink100 = Color(0xFFE6E9EE)

    val Grass400 = Color(0xFF4FD1A1)
    val Grass700 = Color(0xFF00513C)
    val Grass900 = Color(0xFF00382A)
    val Grass100 = Color(0xFFB8F3DB)

    val Slate300 = Color(0xFFA9C1D9)
    val Slate800 = Color(0xFF23384D)

    val Red400 = Color(0xFFF2545B)
    val Red900 = Color(0xFF5C1418)
    val Amber400 = Color(0xFFF5B84C)
    val Green400 = Color(0xFF3DD68C)
    val Live = Color(0xFFFF4D4F)

    val Paper = Color(0xFFF7F8FA)
    val PaperDim = Color(0xFFE9ECF0)
}

/** Semantic status colours used by indicators. Kept separate from the M3 scheme on purpose. */
@Immutable
data class StatusColors(
    val online: Color,
    val offline: Color,
    val warning: Color,
    val unknown: Color,
    val live: Color,
)

internal val DarkStatusColors = StatusColors(
    online = Palette.Green400,
    offline = Palette.Red400,
    warning = Palette.Amber400,
    unknown = Palette.Ink400,
    live = Palette.Live,
)

internal val LightStatusColors = StatusColors(
    online = Color(0xFF0E8A55),
    offline = Color(0xFFC62E36),
    warning = Color(0xFF9A6400),
    unknown = Color(0xFF6B7684),
    live = Color(0xFFD9262A),
)
