package com.urbanairship.locale

import java.util.Locale

/**
 * Listener for locale changes.
 *
 * @hide
 */
public fun interface LocaleChangedListener {

    /**
     * Called when the locale changed.
     *
     * @param locale The current default locale.
     */
    fun onLocaleChanged(locale: Locale)
}
