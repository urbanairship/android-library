package com.urbanairship.remotedata

import android.net.Uri
import android.os.Build
import androidx.annotation.RestrictTo
import com.urbanairship.PushProviders
import com.urbanairship.UAirship
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.base.Supplier
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.config.UrlBuilder
import com.urbanairship.util.UAStringUtil
import java.util.Locale

/**
 * @hide
 */
// TODO: Remove public once everything is in kotlin
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@OpenForTesting
public class RemoteDataUrlFactory(
    private val runtimeConfig: AirshipRuntimeConfig,
    private val pushProvidersSupplier: Supplier<PushProviders>,
) {

    private val platform: String
    get() {
        return if (runtimeConfig.platform == UAirship.AMAZON_PLATFORM) AMAZON else ANDROID
    }

    private val manufacturer: String
    get() = (Build.MANUFACTURER ?: "").lowercase()

    private val pushProviders: String?
    get() {
        val deliveryTypes: MutableSet<String> = HashSet()
        val providers: PushProviders = pushProvidersSupplier.get() ?: return null
        for (provider in providers.availableProviders) {
            deliveryTypes.add(provider.deliveryType)
        }
        if (deliveryTypes.isEmpty()) {
            return null
        }
        return UAStringUtil.join(deliveryTypes, ",")
    }

    public fun createAppUrl(locale: Locale, randomValue: Int): Uri? {
        // api/remote-data/app/{appkey}/{platform}?sdk_version={version}&language={language}&country={country}&manufacturer={manufacturer}&push_providers={push_providers}
        val builder: UrlBuilder = runtimeConfig.urlConfig.remoteDataUrl()
            .appendEncodedPath("api/remote-data/app/")
            .appendPath(runtimeConfig.configOptions.appKey)
            .appendPath(this.platform)
            .appendQueryParameter(
                SDK_VERSION_QUERY_PARAM,
                UAirship.getVersion()
            ).appendQueryParameter(
                RANDOM_VALUE_QUERY_PARAM,
                randomValue.toString()
            )

        if (MANUFACTURERS_ALLOWED.contains(manufacturer)) {
            builder.appendQueryParameter(MANUFACTURER_QUERY_PARAM, manufacturer)
        }

        pushProviders?.let {
            builder.appendQueryParameter(PUSH_PROVIDER_QUERY_PARAM, it)
        }

        if (!UAStringUtil.isEmpty(locale.language)) {
            builder.appendQueryParameter(LANGUAGE_QUERY_PARAM, locale.language)
        }

        if (!UAStringUtil.isEmpty(locale.country)) {
            builder.appendQueryParameter(COUNTRY_QUERY_PARAM, locale.country)
        }

        return builder.build()
    }

    private companion object {
        private const val SDK_VERSION_QUERY_PARAM = "sdk_version"

        // ISO 639-2 two digit country code
        private const val COUNTRY_QUERY_PARAM = "country"

        // ISO 3166-2 two digit language code
        private const val LANGUAGE_QUERY_PARAM = "language"

        private const val RANDOM_VALUE_QUERY_PARAM = "random_value"

        private const val MANUFACTURER_QUERY_PARAM = "manufacturer"
        private const val PUSH_PROVIDER_QUERY_PARAM = "push_providers"

        private val MANUFACTURERS_ALLOWED = listOf("huawei")

        private const val AMAZON = "amazon"
        private const val ANDROID = "android"
    }
}
