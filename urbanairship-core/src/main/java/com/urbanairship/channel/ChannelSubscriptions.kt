package com.urbanairship.channel

import com.urbanairship.audience.AudienceOverridesProvider
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.RequestException
import com.urbanairship.util.CachedValue
import com.urbanairship.util.Clock
import com.urbanairship.util.SerialQueue

internal class ChannelSubscriptions(
    private val subscriptionListApiClient: SubscriptionListApiClient,
    private val audienceOverridesProvider: AudienceOverridesProvider,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
) {

    constructor(runtimeConfig: AirshipRuntimeConfig, audienceOverridesProvider: AudienceOverridesProvider) : this(
        SubscriptionListApiClient(runtimeConfig),
        audienceOverridesProvider
    )

    private val subscriptionFetchQueue = SerialQueue()
    private val subscriptionListCache: CachedValue<SubscriptionsResult?> = CachedValue(clock)

    suspend fun fetchSubscriptionLists(channelId: String): Result<Set<String>> {
        val result = resolveSubscriptionLists(channelId)
        val subscriptions = result.getOrNull()?.toMutableSet()

        if (result.isFailure || subscriptions == null) {
            return result
        }

        audienceOverridesProvider.channelOverrides(channelId).apply {
            this.subscriptions?.forEach { mutation ->
                mutation.apply(subscriptions)
            }
        }

        return Result.success(subscriptions)
    }

    private suspend fun resolveSubscriptionLists(channelId: String): Result<Set<String>> {
        return subscriptionFetchQueue.run {
            val cached = subscriptionListCache.get()
            if (cached != null && cached.channelId == channelId) {
                return@run Result.success(cached.subscriptions)
            }

            val response = subscriptionListApiClient.getSubscriptionLists(channelId)
            if (response.isSuccessful && response.value != null) {
                subscriptionListCache.set(
                    SubscriptionsResult(channelId, response.value),
                    clock.currentTimeMillis() + SUBSCRIPTION_CACHE_LIFETIME_MS
                )
                return@run Result.success(response.value)
            }

            return@run Result.failure(RequestException("Failed to fetch subscription lists with status: ${response.status}"))
        }
    }

    private companion object {
        /**
         * Max age for the channel subscription listing cache.
         */
        private const val SUBSCRIPTION_CACHE_LIFETIME_MS: Long = 10 * 60 * 1000 // 10M
    }
}

private data class SubscriptionsResult(
    val channelId: String,
    val subscriptions: Set<String>
)
