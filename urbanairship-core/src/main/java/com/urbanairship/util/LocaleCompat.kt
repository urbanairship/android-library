package com.urbanairship.util

import android.os.Build
import java.util.Locale

/**
 * Locale compatibility methods.
 */
@Suppress("DEPRECATION")
internal object LocaleCompat {
    /** Creates a locale for the given language, country, and variant.  */
    fun of(language: String, country: String? = null, variant: String? = null): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            when {
                country != null && variant != null -> Locale.of(language, country, variant)
                country != null -> Locale.of(language, country)
                else -> Locale.of(language)
            }
        } else {
            when {
                country != null && variant != null -> Locale(language, country, variant)
                country != null -> Locale(language, country)
                else -> Locale(language)
            }
        }
    }
}
