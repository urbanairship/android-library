/* Copyright Airship and Contributors */
package com.urbanairship.channel

import androidx.annotation.RestrictTo
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.AuthToken
import com.urbanairship.http.AuthTokenProvider
import com.urbanairship.http.RequestException
import com.urbanairship.util.CachedValue
import com.urbanairship.util.Clock
import com.urbanairship.util.SerialQueue

/**
 * Channel Auth provider.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ChannelAuthTokenProvider internal constructor(
    private val apiClient: ChannelAuthApiClient,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    private val channelIDProvider: () -> String?
) : AuthTokenProvider {

    private var cachedAuth = CachedValue<AuthToken>(clock)
    private var queue = SerialQueue()

    internal constructor(runtimeConfig: AirshipRuntimeConfig, channelIDProvider: () -> String?) : this(
        apiClient = ChannelAuthApiClient(runtimeConfig),
        channelIDProvider = channelIDProvider
    )

    private fun getCachedToken(channelId: String): String? {
        val token = cachedAuth.get() ?: return null

        if (channelId != token.identifier) {
            return null
        }

        if (clock.currentTimeMillis() > token.expirationDateMillis - 30000) {
            return null
        }

        return token.token
    }

    override suspend fun fetchToken(identifier: String): Result<String> = queue.run {
        val channelId: String? = this.channelIDProvider()
        if (channelId == null || identifier != channelId) {
            return@run Result.failure(RequestException("Channel mismatch."))
        }

        val cached = getCachedToken(identifier)
        if (cached != null) {
            return@run Result.success(cached)
        }

        val authResponse = apiClient.getToken(channelId)
        return@run if (authResponse.isSuccessful && authResponse.value != null) {
            this.cachedAuth.set(authResponse.value, authResponse.value.expirationDateMillis)
            Result.success(authResponse.value.token)
        } else {
            Result.failure(RequestException("Failed to fetch token: ${authResponse.status}"))
        }
    }

    override suspend fun expireToken(token: String) {
        cachedAuth.expireIf {
            token == it.token
        }
    }
}
