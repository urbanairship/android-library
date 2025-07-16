/* Copyright Airship and Contributors */
package com.urbanairship.channel

import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.util.Clock

/**
 * Subscription list editor. See [AirshipChannel.editSubscriptionLists].
 */
public abstract class SubscriptionListEditor
/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) protected constructor(
    private val clock: Clock = Clock.DEFAULT_CLOCK
) {

    private val mutations = mutableListOf<SubscriptionListMutation>()

    /**
     * Subscribe to a list.
     *
     * @param subscriptionListId The ID of the list to subscribe to.
     * @return The [SubscriptionListEditor] instance.
     */
    public fun subscribe(subscriptionListId: String): SubscriptionListEditor {
        val trimmed = subscriptionListId.trim { it <= ' ' }
        if (trimmed.isEmpty()) {
            UALog.e("The subscription list ID must not be null or empty.")
            return this
        }

        mutations.add(
            SubscriptionListMutation.newSubscribeMutation(
                listId = trimmed,
                timestamp = clock.currentTimeMillis()
            )
        )

        return this
    }

    /**
     * Subscribe to a set of lists.
     *
     * @param subscriptionListIds A [Set] of list IDs to subscribe to.
     * @return The [SubscriptionListEditor] instance.
     */
    public fun subscribe(subscriptionListIds: Set<String>): SubscriptionListEditor {
        return this.also { subscriptionListIds.forEach { subscribe(it) } }
    }

    /**
     * Unsubscribe from a list.
     *
     * @param subscriptionListId The ID of the list to unsubscribe from.
     * @return The [SubscriptionListEditor] instance.
     */
    public fun unsubscribe(subscriptionListId: String): SubscriptionListEditor {
        val trimmed = subscriptionListId.trim { it <= ' ' }
        if (trimmed.isEmpty()) {
            UALog.e("The subscription list ID must not be null or empty.")
            return this
        }

        mutations.add(
            SubscriptionListMutation.newUnsubscribeMutation(
                trimmed, clock.currentTimeMillis()
            )
        )

        return this
    }

    /**
     * Unsubscribe from a set of lists.
     *
     * @param subscriptionListIds A [Set] of list IDs to unsubscribe from.
     * @return The [SubscriptionListEditor] instance.
     */
    public fun unsubscribe(subscriptionListIds: Set<String>): SubscriptionListEditor {
        return this.also { subscriptionListIds.forEach { unsubscribe(it) } }
    }

    /**
     * Internal helper that uses a boolean flag to indicate whether to subscribe or unsubscribe.
     *
     * @param subscriptionListId The ID of the list to subscribe to or unsubscribe from.
     * @param isSubscribe `true` to subscribe or `false` to unsubscribe.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun mutate(subscriptionListId: String, isSubscribe: Boolean): SubscriptionListEditor {
        return if (isSubscribe){
            subscribe(subscriptionListId)
        } else {
            unsubscribe(subscriptionListId)
        }
    }

    /**
     * Apply the subscription list changes.
     */
    public fun apply() {
        onApply(SubscriptionListMutation.collapseMutations(mutations))
    }

    /**
     * Called when apply is called.
     *
     * @param collapsedMutations The collapsed list of mutations to be applied.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected abstract fun onApply(collapsedMutations: List<SubscriptionListMutation>)
}
