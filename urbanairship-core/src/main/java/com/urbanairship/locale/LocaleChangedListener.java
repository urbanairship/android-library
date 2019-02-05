package com.urbanairship.locale;

import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

import java.util.Locale;

/**
 * Listener for locale changes.
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
