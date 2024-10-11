/* Copyright Airship and Contributors */

package com.urbanairship.channel

import com.urbanairship.AirshipDispatchers
import com.urbanairship.PrivacyManager
import com.urbanairship.audience.AudienceOverrides
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.util.AutoRefreshingDataProvider
import com.urbanairship.util.Clock
import com.urbanairship.util.TaskSleeper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

internal class SubscriptionsProvider(
    private val apiClient: SubscriptionListApiClient,
    private val privacyManager: PrivacyManager,
    stableContactIdUpdates: Flow<String>,
    overrideUpdates: Flow<AudienceOverrides.Channel>,
    clock: Clock = Clock.DEFAULT_CLOCK,
    taskSleeper: TaskSleeper = TaskSleeper.default,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher()
) : AutoRefreshingDataProvider<Set<String>, AudienceOverrides.Channel> (
    identifierUpdates = stableContactIdUpdates,
    overrideUpdates = overrideUpdates,
    clock = clock,
    taskSleeper = taskSleeper,
    dispatcher = dispatcher
) {
    constructor(
        config: AirshipRuntimeConfig,
        privacyManager: PrivacyManager,
        stableContactIdUpdates: Flow<String>,
        overrideUpdates: Flow<AudienceOverrides.Channel>,
    ): this(
        apiClient = SubscriptionListApiClient(config),
        privacyManager = privacyManager,
        stableContactIdUpdates = stableContactIdUpdates,
        overrideUpdates = overrideUpdates
    )

    override suspend fun onFetch(identifier: String): Result<Set<String>> {
        if (!privacyManager.isEnabled(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)) {
            return Result.failure(
                IllegalStateException("Unable to fetch subscriptions when FEATURE_TAGS_AND_ATTRIBUTES are disabled")
            )
        }

        val result = apiClient.getSubscriptionLists(identifier)
        if (result.isSuccessful && result.value != null) {
            return Result.success(result.value)
        }

        return Result.failure(
            result.exception ?: IllegalStateException("Missing response body")
        )
    }

    override fun onApplyOverrides(data: Set<String>, overrides: AudienceOverrides.Channel): Set<String> {
        val mutations = overrides.subscriptions
        if (mutations.isNullOrEmpty()) {
            return data
        }

        val subscriptions = data.toMutableSet()

        mutations.forEach { mutation ->
            mutation.apply(subscriptions)
        }

        return subscriptions
    }
}
