package com.urbanairship.debug.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object AirshipColors {
    internal val Black = Color(0xFF000000)
    internal val CyanBluishGray = Color(0xFFabb8c3)
    internal val White = Color(0xFFffffff)
    internal val PalePink = Color(0xFFf78da7)
    internal val VividRed = Color(0xFFcf2e2e)
    internal val LuminousVividOrange = Color(0xFFff6900)
    internal val LuminousVividAmber = Color(0xFFfcb900)
    internal val LightGreenCyan = Color(0xFF7bdcb5)
    internal val VividGreenCyan = Color(0xFF00d084)
    internal val PaleCyanBlue = Color(0xFF8ed1fc)
    internal val VividCyanBlue = Color(0xFF0693e3)
    internal val VividPurple = Color(0xFF9b51e0)
    internal val Base = Color(0xFFFFFFFF)
    internal val Contrast = Color(0xFF000000)
    internal val Aqua = Color(0xFF5ECEFA)
    internal val LightBlue = Color(0xFF1550F5)
    internal val Blue = Color(0xFF2A55DD)
    internal val Blush = Color(0xFFE3D3D3)
    internal val Teal = Color(0xFF6AE6B6)
    internal val Lime = Color(0xFFA2D243)
    internal val Yellow = Color(0xFFF1CA44)
    internal val Red = Color(0xFFEB364F)
    internal val Pink = Color(0xFFFF008B)
    internal val LightestGray = Color(0xFFF2F3F4)
    internal val LighterGray = Color(0xFFE7E8E9)
    internal val LightGray = Color(0xFFAEAEAE)
    internal val Gray = Color(0xFF8C8C8C)
    internal val DarkGray = Color(0xFF525252)
    internal val DarkerGray = Color(0xFF1A1A1A)
    internal val DarkestGray = Color(0xFF070720)
}

internal val LightColorScheme = lightColorScheme(
    primary = AirshipColors.Blue
)

internal val DarkColorScheme = darkColorScheme(
    primary = AirshipColors.Blue
)

/*
Gradients from airship.com

--wp--preset--gradient--vivid-cyan-blue-to-vivid-purple: linear-gradient(135deg,rgba(6,147,227,1) 0%,rgb(155,81,224) 100%);
--wp--preset--gradient--light-green-cyan-to-vivid-green-cyan: linear-gradient(135deg,rgb(122,220,180) 0%,rgb(0,208,130) 100%);
--wp--preset--gradient--luminous-vivid-amber-to-luminous-vivid-orange: linear-gradient(135deg,rgba(252,185,0,1) 0%,rgba(255,105,0,1) 100%);
--wp--preset--gradient--luminous-vivid-orange-to-vivid-red: linear-gradient(135deg,rgba(255,105,0,1) 0%,rgb(207,46,46) 100%);
--wp--preset--gradient--very-light-gray-to-cyan-bluish-gray: linear-gradient(135deg,rgb(238,238,238) 0%,rgb(169,184,195) 100%);
--wp--preset--gradient--cool-to-warm-spectrum: linear-gradient(135deg,rgb(74,234,220) 0%,rgb(151,120,209) 20%,rgb(207,42,186) 40%,rgb(238,44,130) 60%,rgb(251,105,98) 80%,rgb(254,248,76) 100%);
--wp--preset--gradient--blush-light-purple: linear-gradient(135deg,rgb(255,206,236) 0%,rgb(152,150,240) 100%);
--wp--preset--gradient--blush-bordeaux: linear-gradient(135deg,rgb(254,205,165) 0%,rgb(254,45,45) 50%,rgb(107,0,62) 100%);
--wp--preset--gradient--luminous-dusk: linear-gradient(135deg,rgb(255,203,112) 0%,rgb(199,81,192) 50%,rgb(65,88,208) 100%);
--wp--preset--gradient--pale-ocean: linear-gradient(135deg,rgb(255,245,203) 0%,rgb(182,227,212) 50%,rgb(51,167,181) 100%);
--wp--preset--gradient--electric-grass: linear-gradient(135deg,rgb(202,248,128) 0%,rgb(113,206,126) 100%);
--wp--preset--gradient--midnight: linear-gradient(135deg,rgb(2,3,129) 0%,rgb(40,116,252) 100%);
--wp--preset--gradient--blueish-purple: linear-gradient(273.31deg, #4F16FF 0.53%, #6725FA 23.08%, #8236F5 48.56%, #3244F7 81.51%, #44FFE9 102.43%);
--wp--preset--gradient--purpleish-pink: linear-gradient(272.09deg, #793ED4 0.61%, #CC8095 33.81%, #FB828A 59.42%, #FF5B97 99.27%);
 */
