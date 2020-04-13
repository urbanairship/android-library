package com.urbanairship.locale;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Listener for locale changes.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface LocaleChangedListener {

    /**
     * Called when the locale changed.
     *
     * @param locale The current default locale.
     */
    void onLocaleChanged(@NonNull Locale locale);

}
