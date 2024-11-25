package com.urbanairship.debug.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.urbanairship.debug2.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val lato = GoogleFont("Lato")

private val Lato: FontFamily = FontFamily(
    Font(lato, provider, FontWeight.Light),
    Font(lato, provider, FontWeight.Light, FontStyle.Italic),
    Font(lato, provider, FontWeight.Normal),
    Font(lato, provider, FontWeight.Normal, FontStyle.Italic),
    Font(lato, provider, FontWeight.Bold),
    Font(lato, provider, FontWeight.Bold, FontStyle.Italic),
)

internal val AirshipTypography: Typography = with(Typography()) {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = Lato),
        displayMedium = displayMedium.copy(fontFamily = Lato),
        displaySmall = displaySmall.copy(fontFamily = Lato),
        headlineLarge = headlineLarge.copy(fontFamily = Lato),
        headlineMedium = headlineMedium.copy(fontFamily = Lato),
        headlineSmall = headlineSmall.copy(fontFamily = Lato),
        titleLarge = titleLarge.copy(fontFamily = Lato),
        titleMedium = titleMedium.copy(fontFamily = Lato),
        titleSmall = titleSmall.copy(fontFamily = Lato),
        bodyLarge = bodyLarge.copy(fontFamily = Lato),
        bodyMedium = bodyMedium.copy(fontFamily = Lato),
        bodySmall = bodySmall.copy(fontFamily = Lato),
        labelLarge = labelLarge.copy(fontFamily = Lato),
        labelMedium = labelMedium.copy(fontFamily = Lato),
        labelSmall = labelSmall.copy(fontFamily = Lato),
    )
}
