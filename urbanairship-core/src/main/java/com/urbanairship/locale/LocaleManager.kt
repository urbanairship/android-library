package com.urbanairship.locale

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import androidx.core.os.ConfigurationCompat
import com.urbanairship.AirshipDispatchers
import com.urbanairship.preferences.PreferenceStore
import com.urbanairship.UALog
import com.urbanairship.util.LocaleCompat
import java.util.Locale
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Locale manager.
 *
 */
public class LocaleManager @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)  constructor(
    context: Context,
    private val preferenceStore: PreferenceStore,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.IO
) {

    private val context: Context = context.applicationContext

    @Volatile
    private var deviceLocale: Locale? = null
    private val localeChangedListeners = mutableListOf<LocaleChangedListener>()

    private val updateScope = CoroutineScope(dispatcher)

    private val localeUpdatesFlow = MutableSharedFlow<Locale>()
    public val localeUpdates: Flow<Locale> = localeUpdatesFlow.asSharedFlow()

    init {
        updateScope.launch {
            localeUpdatesFlow
                .distinctUntilChanged()
                .collect { locale ->
                    localeChangedListeners.forEach { it.onLocaleChanged(locale) }
                }
        }
    }

    /**
     * Called by [LocaleChangeReceiver] to notify the device's locale changed.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun onDeviceLocaleChanged() {
        synchronized(this) {
            val locale = ConfigurationCompat.getLocales(context.resources.configuration).get(0) ?: return
            UALog.d("Device Locale changed. Locale: $locale.")
            deviceLocale = locale
            if (localeOverride == null) {
                notifyLocaleChanged(locale)
            }
        }
    }

    /**
     * Called by [LocaleChangeReceiver] to notify the override locale changed.
     */
    internal fun notifyLocaleChanged(locale: Locale) {
        updateScope.launch { localeUpdatesFlow.emit(locale) }
    }

    /**
     * Sets the locale to override the device's locale.
     * @param locale The locale. Pass null to reset the override.
     */
    public fun setLocaleOverride(locale: Locale?) {
        synchronized(this) {
            val currentLocale = this.locale
            if (locale != null) {
                preferenceStore.sync.put(LOCALE_OVERRIDE_COUNTRY_KEY, locale.country)
                preferenceStore.sync.put (LOCALE_OVERRIDE_LANGUAGE_KEY, locale.language)
                preferenceStore.sync.put(LOCALE_OVERRIDE_VARIANT_KEY, locale.variant)
            } else {
                preferenceStore.sync.remove(LOCALE_OVERRIDE_COUNTRY_KEY)
                preferenceStore.sync.remove (LOCALE_OVERRIDE_LANGUAGE_KEY)
                preferenceStore.sync.remove (LOCALE_OVERRIDE_VARIANT_KEY)
            }

            if (currentLocale !== this.locale) {
                notifyLocaleChanged(this.locale)
            }
        }
    }

    /**
     * Locale to override the device's locale.
     */
    private val localeOverride: Locale?
        get() {
            val language = preferenceStore.sync.getString(LOCALE_OVERRIDE_LANGUAGE_KEY, null)
                ?: return null
            val country = preferenceStore.sync.getString(LOCALE_OVERRIDE_COUNTRY_KEY, null)
                ?: return null
            val variant = preferenceStore.sync.getString(LOCALE_OVERRIDE_VARIANT_KEY, null)
                ?: return null

            return LocaleCompat.of(language, country, variant)
        }

    /**
     * Gets the current default locale.
     *
     * @throws IllegalArgumentException if no locale is found.
     */
    public val locale: Locale
        get() {
            localeOverride?.let { return it }

            val result = deviceLocale ?: run {
                val locale = ConfigurationCompat.getLocales(context.resources.configuration).get(0)
                    ?: throw IllegalStateException("No locale found.")
                deviceLocale = locale
                locale
            }

            return result
        }

    public companion object {
        public const val LOCALE_OVERRIDE_LANGUAGE_KEY: String = "com.urbanairship.LOCALE_OVERRIDE_LANGUAGE"
        public const val LOCALE_OVERRIDE_COUNTRY_KEY: String = "com.urbanairship.LOCALE_OVERRIDE_COUNTRY"
        public const val LOCALE_OVERRIDE_VARIANT_KEY: String = "com.urbanairship.LOCALE_OVERRIDE_VARIANT"
    }
}
