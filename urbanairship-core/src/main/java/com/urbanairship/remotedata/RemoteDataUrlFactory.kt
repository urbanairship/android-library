/* Copyright Airship and Contributors */

package com.urbanairship.remotedata

import android.net.Uri
import android.os.Build
import com.urbanairship.Airship
import com.urbanairship.Platform
import com.urbanairship.PushProviders
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.config.UrlBuilder
import java.util.Locale

/**
 * @hide
 */
internal class RemoteDataUrlFactory(
    private val runtimeConfig: AirshipRuntimeConfig,
    private val pushProvidersProvider: () -> PushProviders
) {

    private val platform: String
    get() {
        return when(runtimeConfig.platform) {
            Platform.AMAZON -> AMAZON
            else -> ANDROID
        }
    }

    private val manufacturer: String
    get() = (Build.MANUFACTURER ?: "").lowercase()

    private val pushProviders: String?
    get() {
        val deliveryTypes = pushProvidersProvider().getAvailableProviders()
            .map { it.deliveryType.value }
            .toSet()

        if (deliveryTypes.isEmpty()) {
            return null
        }
        return deliveryTypes.joinToString(",")
    }

    fun createContactUrl(contactID: String, locale: Locale, randomValue: Int): Uri? {
        return createUrl(
            path = "api/remote-data-contact/$platform/$contactID",
            locale = locale,
            randomValue = randomValue
        )
    }

    fun createAppUrl(locale: Locale, randomValue: Int): Uri? {
        return createUrl(
            path = "api/remote-data/app/${runtimeConfig.configOptions.appKey}/$platform",
            locale = locale,
            randomValue = randomValue
        )
    }

    private fun createUrl(path: String, locale: Locale, randomValue: Int): Uri? {
        // {path}?sdk_version={version}&language={language}&country={country}&manufacturer={manufacturer}&push_providers={push_providers}
        val builder: UrlBuilder = runtimeConfig.remoteDataUrl
            .appendEncodedPath(path)
            .appendQueryParameter(
                SDK_VERSION_QUERY_PARAM,
                Airship.version
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

        if (locale.language.isNotEmpty()) {
            builder.appendQueryParameter(LANGUAGE_QUERY_PARAM, locale.language)
        }

        if (locale.country.isNotEmpty()) {
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
