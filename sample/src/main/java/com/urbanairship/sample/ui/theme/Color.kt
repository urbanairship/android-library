package com.urbanairship.sample.ui.theme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object AirshipColors {
    internal val airshipBlue = Color(0xFF0023ca)
    internal val lightBlue = Color(0xFF1a39cf)
    internal val accent = Color(0xFFff0b48)
    internal val colorPrimary = Color(0xFF000000)
    internal val colorOnPrimary = Color(0xFFffffff)
    internal val colorSecondary = Color(0xFF0023ca)
    internal val colorOnSecondary = Color(0xFF000000)
    internal val colorAccent = Color(0xFFff0b48)
    internal val colorSecondaryVariant = Color(0xFFc40021)
    internal val colorPrimaryVariant = Color(0xFF000000)
}


internal val LightColorScheme = lightColorScheme(
    primary = AirshipColors.airshipBlue
)

internal val DarkColorScheme = darkColorScheme(
    primary = AirshipColors.airshipBlue
)

