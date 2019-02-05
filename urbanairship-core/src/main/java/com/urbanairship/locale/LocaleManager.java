package com.urbanairship.locale;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;
import android.support.annotation.VisibleForTesting;
import android.support.v4.os.ConfigurationCompat;

import com.urbanairship.Logger;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * Locale manager. Handles listening for locale changes.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class LocaleManager {
    private volatile Locale locale;
    private static LocaleManager instance = new LocaleManager();
    private List<LocaleChangedListener> localeChangedListeners = new CopyOnWriteArrayList<>();

    /**
     * Gets the shared instance.
     *
     * @return The LocaleManager.
     */
    @NonNull
    public static LocaleManager shared() {
        return instance;
    }

    @VisibleForTesting
    protected LocaleManager() {}

    /**
     * Adds a locale change listener.
     *
     * @param listener The locale listener.
     */
    public void addListener(@NonNull LocaleChangedListener listener) {
        localeChangedListeners.add(listener);
    }

    /**
     * Removes the locale change listener.
     *
     * @param listener The locale listener.
     */
    public void removeListener(@NonNull LocaleChangedListener listener) {
        localeChangedListeners.remove(listener);
    }

    /**
     * Called by {@link LocaleChangeReceiver} to notify the locale changed.
     *
     * @param context The application context.
     */
    void notifyLocaleChanged(@NonNull Context context) {
        synchronized (this) {
            locale = ConfigurationCompat.getLocales(context.getResources().getConfiguration()).get(0);
            Logger.debug("Locale changed. Default locale: %s.", locale);
            Logger.debug("Locale: %s.", locale);
            Logger.debug("Locales: %s.", ConfigurationCompat.getLocales(context.getResources().getConfiguration()));
            Logger.debug("System Locale: %s.", Locale.getDefault());
            for (LocaleChangedListener listener : localeChangedListeners) {
                listener.onLocaleChanged(locale);
            }
        }
    }

    /**
     * Gets the current default locale.
     *
     * @param context The application context.
     * @return The locale.
     */
    @NonNull
    public Locale getDefaultLocale(@NonNull Context context) {
        if (locale == null) {
            locale = ConfigurationCompat.getLocales(context.getResources().getConfiguration()).get(0);
            Logger.debug("Locale: %s.", locale);
            Logger.debug("Locales: %s.", ConfigurationCompat.getLocales(context.getResources().getConfiguration()));
            Logger.debug("System Locale: %s.", Locale.getDefault());
        }
        return locale;
    }
}
