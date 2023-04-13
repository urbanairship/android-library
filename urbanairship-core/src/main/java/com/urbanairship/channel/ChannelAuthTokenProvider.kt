/* Copyright Airship and Contributors */
package com.urbanairship.channel

import androidx.annotation.RestrictTo
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.AuthToken
import com.urbanairship.http.AuthTokenProvider
import com.urbanairship.http.RequestException
import com.urbanairship.util.Clock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Auth manager.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class ChannelAuthTokenProvider : AuthTokenProvider {

    private val apiClient: ChannelAuthApiClient
    private val channelIDProvider: () -> String?

    private val lock = ReentrantLock()
    private val clock: Clock
    private var cachedAuth: AuthToken? = null

    constructor(runtimeConfig: AirshipRuntimeConfig, channelIDProvider: () -> String) : this(
        ChannelAuthApiClient(runtimeConfig),
        Clock.DEFAULT_CLOCK,
        channelIDProvider
    )

    constructor(apiClient: ChannelAuthApiClient, clock: Clock, channelIDProvider: () -> String) {
        this.apiClient = apiClient
        this.channelIDProvider = channelIDProvider
        this.clock = clock
    }

    private fun getCachedToken(channelId: String): String? {
        val token = cachedAuth ?: return null

        if (channelId != token.identifier) {
            return null
        }

        if (clock.currentTimeMillis() > token.expirationTimeMS - 30000) {
            return null
        }

        return token.token
    }

    override fun fetchToken(identifier: String): String {
        lock.withLock {
            val channelId: String = requireNotNull(this.channelIDProvider())
            require(channelId == identifier)

            val cached = getCachedToken(identifier)
            if (cached != null) {
                return cached
            }

            val authResponse = apiClient.getToken(channelId)
            if (authResponse.isSuccessful && authResponse.result != null) {
                this.cachedAuth = authResponse.result
                return authResponse.result.token
            }

            throw RequestException("Failed to fetch token: ${authResponse.status}")
        }
    }

    override fun expireToken(token: String) {
        lock.withLock {
            if (cachedAuth?.token == token) {
                cachedAuth = null
            }
        }
    }
}
