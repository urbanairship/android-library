package com.urbanairship.locale;

import android.annotation.SuppressLint;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.core.os.ConfigurationCompat;

import com.urbanairship.Logger;
import com.urbanairship.job.JobDispatcher;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Locale manager. Handles listening for locale changes.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class LocaleManager {

    private final Context context;
    private volatile Locale locale;
    @SuppressLint("StaticFieldLeak")
    private static LocaleManager instance;
    private List<LocaleChangedListener> localeChangedListeners = new CopyOnWriteArrayList<>();

    /**
     * Gets the shared instance.
     *
     * @param context The application context.
     * @return The Local Manager.
     */
    @NonNull
    public static LocaleManager shared(@NonNull Context context) {
        if (instance == null) {
            synchronized (JobDispatcher.class) {
                if (instance == null) {
                    instance = new LocaleManager(context);
                }
            }
        }

        return instance;
    }

    @VisibleForTesting
    protected LocaleManager(Context context) {
        this.context = context.getApplicationContext();
    }

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
     */
    void notifyLocaleChanged() {
        synchronized (this) {
            locale = ConfigurationCompat.getLocales(context.getResources().getConfiguration()).get(0);
            Logger.debug("Locale changed. Locale: %s.", locale);
            for (LocaleChangedListener listener : localeChangedListeners) {
                listener.onLocaleChanged(locale);
            }
        }
    }

    /**
     * Gets the current default locale.
     *
     * @return The locale.
     */
    @NonNull
    public Locale getDefaultLocale() {
        if (locale == null) {
            locale = ConfigurationCompat.getLocales(context.getResources().getConfiguration()).get(0);
            Logger.debug("Locale: %s.", locale);
            Logger.debug("Locales: %s.", ConfigurationCompat.getLocales(context.getResources().getConfiguration()));
            Logger.debug("System Locale: %s.", Locale.getDefault());
        }
        return locale;
    }

}
