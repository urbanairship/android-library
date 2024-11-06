/* Copyright Airship and Contributors */

package com.urbanairship.contacts

import com.urbanairship.AirshipDispatchers
import com.urbanairship.PrivacyManager
import com.urbanairship.audience.AudienceOverrides
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.util.AutoRefreshingDataProvider
import com.urbanairship.util.Clock
import com.urbanairship.util.TaskSleeper
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

internal class SubscriptionsProvider(
    private val apiClient: SubscriptionListApiClient,
    private val privacyManager: PrivacyManager,
    stableContactIdUpdates: Flow<String>,
    overrideUpdates: Flow<AudienceOverrides.Contact>,
    clock: Clock = Clock.DEFAULT_CLOCK,
    taskSleeper: TaskSleeper = TaskSleeper.default,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher()
) : AutoRefreshingDataProvider<Map<String, Set<Scope>>, AudienceOverrides.Contact> (
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
        overrideUpdates: Flow<AudienceOverrides.Contact>,
    ): this(
        apiClient = SubscriptionListApiClient(config),
        privacyManager = privacyManager,
        stableContactIdUpdates = stableContactIdUpdates,
        overrideUpdates = overrideUpdates
    )

    override suspend fun onFetch(identifier: String): Result<Map<String, Set<Scope>>> {
        if (!privacyManager.isContactsAudienceEnabled) {
            return Result.failure(
                IllegalStateException("Unable to fetch subscriptions when FEATURE_TAGS_AND_ATTRIBUTES or FEATURE_CONTACTS are disabled")
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

    override fun onApplyOverrides(data: Map<String, Set<Scope>>, overrides: AudienceOverrides.Contact): Map<String, Set<Scope>> {
        val mutations = overrides.subscriptions
        if (mutations.isNullOrEmpty()) {
            return data
        }

        val subscriptions = data.toMutableMap().mapValues { it.value.toMutableSet() }

        mutations.forEach { mutation ->
            mutation.apply(subscriptions)
        }

        return subscriptions
    }
}
