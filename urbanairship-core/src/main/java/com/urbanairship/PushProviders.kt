/* Copyright Airship and Contributors */
package com.urbanairship

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.base.Supplier
import com.urbanairship.push.PushProvider

/**
 * Loads push providers.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public open class PushProviders @VisibleForTesting protected constructor(
    private val airshipConfigOptions: AirshipConfigOptions
) {

    private val supportedProviders = mutableListOf<PushProvider>()
    private val availableProviders = mutableListOf<PushProvider>()

    /**
     * Loads all the plugins that are currently supported by the device.
     */
    private fun init(context: Context) {
        val providers = createProviders()

        if (providers.isEmpty()) {
            UALog.w("No push providers found!. Make sure to install either `urbanairship-fcm` or `urbanairship-adm`.")
            return
        }

        providers
            .filter { isValid(it) && it.isSupported(context) }
            .forEach { provider ->
                supportedProviders.add(provider)
                if (provider.isAvailable(context)) {
                    availableProviders.add(provider)
                }
            }
    }

    private fun isValid(provider: PushProvider): Boolean {
        if (provider is AirshipVersionInfo) {
            if (Airship.getVersion() != provider.airshipVersion) {
                UALog.e(
                    "Provider: %s version %s does not match the SDK version %s. Make sure all Airship dependencies are the same version.",
                    provider,
                    provider.airshipVersion,
                    Airship.getVersion()
                )
                return false
            }
        }

        when (provider.deliveryType) {
            PushProvider.DeliveryType.ADM -> {
                if (provider.platform != Airship.Platform.AMAZON) {
                    UALog.e("Invalid Provider: $provider. ADM delivery is only available for Amazon platforms.")
                    return false
                }
            }
            PushProvider.DeliveryType.FCM,
            PushProvider.DeliveryType.HMS -> {
                if (provider.platform != Airship.Platform.ANDROID) {
                    UALog.e(
                        "Invalid Provider: %s. %s delivery is only available for Android platforms.",
                        provider.deliveryType,
                        provider
                    )
                    return false
                }
            }
        }

        return true
    }

    /**
     * Creates the list of push providers.
     *
     * @return The list of push providers.
     */
    private fun createProviders(): List<PushProvider> {
        val providers = mutableListOf<PushProvider>()

        airshipConfigOptions.customPushProvider?.let { providers.add(it) }

        for (className in createAllowedProviderClassList()) {
            try {
                val providerClass = Class.forName(className)
                val provider = providerClass.getDeclaredConstructor().newInstance() as PushProvider
                UALog.v("Found provider: $provider")
                providers.add(provider)
            } catch (e: InstantiationException) {
                UALog.e(e, "Unable to create provider %s", className)
                continue
            } catch (e: IllegalAccessException) {
                UALog.e(e, "Unable to create provider %s", className)
                continue
            } catch (e: ClassNotFoundException) {
                continue
            }
        }

        return providers.toList()
    }

    public open fun getAvailableProviders(): List<PushProvider> {
        return availableProviders.toList()
    }

    /**
     * Gets the best provider for the specified platform.
     *
     * @param platform The specified platform.
     * @return The best provider for the platform, or `null` if no provider is found.
     */
    public open fun getBestProvider(platform: Airship.Platform): PushProvider? {
        return availableProviders.firstOrNull { it.platform == platform }
            ?: supportedProviders.firstOrNull { it.platform == platform }
    }

    /**
     * Gets the best provider.
     */
    public open val bestProvider: PushProvider?
        get() {
            return availableProviders.firstOrNull()
                ?: supportedProviders.firstOrNull()
        }

    /**
     * Gets the provider with the specified class and platform.
     *
     * @param platform The provider platform.
     * @param providerClass The provider's class.
     * @return The provider or `null` if the specified provider is not available.
     */
    public open fun getProvider(
        platform: Airship.Platform,
        providerClass: String
    ): PushProvider? {
        return supportedProviders.firstOrNull {
            it.platform == platform && it.javaClass.toString() == providerClass
        }
    }

    private fun createAllowedProviderClassList(): List<String> {
        val providers = mutableListOf<String>()
        if (airshipConfigOptions.allowedTransports.contains(AirshipConfigOptions.FCM_TRANSPORT)) {
            providers.add(FCM_PUSH_PROVIDER_CLASS)
        }

        if (airshipConfigOptions.allowedTransports.contains(AirshipConfigOptions.ADM_TRANSPORT)) {
            providers.add(ADM_PUSH_PROVIDER_CLASS)
        }

        if (airshipConfigOptions.allowedTransports.contains(AirshipConfigOptions.HMS_TRANSPORT)) {
            providers.add(HMS_PUSH_PROVIDER_CLASS)
        }

        return providers
    }

    private class LazyLoader(
        private val context: Context,
        private val config: AirshipConfigOptions
    ) : Supplier<PushProviders> {

        var pushProviders: PushProviders? = null

        @Synchronized
        override fun get(): PushProviders {
            return pushProviders ?: run {
                val provider = load(context, config)
                pushProviders = provider
                provider
            }
        }
    }

    public companion object {

        private const val FCM_PUSH_PROVIDER_CLASS = "com.urbanairship.push.fcm.FcmPushProvider"
        private const val ADM_PUSH_PROVIDER_CLASS = "com.urbanairship.push.adm.AdmPushProvider"
        private const val HMS_PUSH_PROVIDER_CLASS = "com.urbanairship.push.hms.HmsPushProvider"

        /**
         * Factory method to load push providers.
         *
         * @param context The application context.
         * @param config The airship config.
         * @return A PushProviders class with the loaded providers.
         */
        public fun load(context: Context, config: AirshipConfigOptions): PushProviders {
            val providers = PushProviders(config)
            providers.init(context)
            return providers
        }

        public fun lazyLoader(
            context: Context, config: AirshipConfigOptions
        ): Supplier<PushProviders> {
            return LazyLoader(context, config)
        }
    }
}
