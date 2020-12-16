package com.urbanairship.locale;

import android.content.Context;

import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.os.ConfigurationCompat;

/**
 * Locale manager. Handles listening for locale changes.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class LocaleManager {

    private final Context context;
    private volatile Locale deviceLocale;
    private final List<LocaleChangedListener> localeChangedListeners = new CopyOnWriteArrayList<>();
    private final PreferenceDataStore preferenceDataStore;

    public static final String LOCALE_OVERRIDE_LANGUAGE_KEY = "com.urbanairship.LOCALE_OVERRIDE_LANGUAGE";
    public static final String LOCALE_OVERRIDE_COUNTRY_KEY = "com.urbanairship.LOCALE_OVERRIDE_COUNTRY";
    public static final String LOCALE_OVERRIDE_VARIANT_KEY = "com.urbanairship.LOCALE_OVERRIDE_VARIANT";

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param preferenceDataStore The Airship Preference Data Store.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public LocaleManager(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore) {
        this.preferenceDataStore = preferenceDataStore;
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
     * Called by {@link LocaleChangeReceiver} to notify the device's locale changed.
     */
    void onDeviceLocaleChanged() {
        synchronized (this) {
            deviceLocale = ConfigurationCompat.getLocales(context.getResources().getConfiguration()).get(0);
            Logger.debug("Device Locale changed. Locale: %s.", deviceLocale);
            if (getLocaleOverride() == null) {
                notifyLocaleChanged(deviceLocale);
            }
        }
    }

    /**
     * Called by {@link LocaleChangeReceiver} to notify the override locale changed.
     */
    void notifyLocaleChanged(Locale locale) {
        for (LocaleChangedListener listener : localeChangedListeners) {
            listener.onLocaleChanged(locale);
        }
    }

    /**
     * Sets a locale to override the device's locale.
     *
     * @param locale The locale to set.
     */
    public void setLocaleOverride(@Nullable Locale locale) {
        synchronized (this) {
            Locale currentLocale = getLocale();

            if (locale != null) {
                this.preferenceDataStore.put(LOCALE_OVERRIDE_COUNTRY_KEY, locale.getCountry());
                this.preferenceDataStore.put(LOCALE_OVERRIDE_LANGUAGE_KEY, locale.getLanguage());
                this.preferenceDataStore.put(LOCALE_OVERRIDE_VARIANT_KEY, locale.getVariant());
            } else {
                this.preferenceDataStore.remove(LOCALE_OVERRIDE_COUNTRY_KEY);
                this.preferenceDataStore.remove(LOCALE_OVERRIDE_LANGUAGE_KEY);
                this.preferenceDataStore.remove(LOCALE_OVERRIDE_VARIANT_KEY);
            }

            if (currentLocale != getLocale()) {
                notifyLocaleChanged(getLocale());
            }
        }
    }

    /**
     * Gets the Locale stored in the DataStore.
     *
     * @return The override Locale stored in the DataStore.
     */
    @Nullable
    private Locale getLocaleOverride() {
        String language = this.preferenceDataStore.getString(LOCALE_OVERRIDE_LANGUAGE_KEY, null);
        String country = this.preferenceDataStore.getString(LOCALE_OVERRIDE_COUNTRY_KEY, null);
        String variant = this.preferenceDataStore.getString(LOCALE_OVERRIDE_VARIANT_KEY, null);

        if (language != null && country != null && variant != null) {
            return new Locale(language, country, variant);
        } else {
            return null;
        }
    }

    /**
     * Gets the current default locale.
     *
     * @return The locale.
     */
    @NonNull
    public Locale getLocale() {
        if (getLocaleOverride() != null) {
            return getLocaleOverride();
        } else {
            if (deviceLocale == null) {
                deviceLocale = ConfigurationCompat.getLocales(context.getResources().getConfiguration()).get(0);
            }
            return deviceLocale;
        }
    }
}
