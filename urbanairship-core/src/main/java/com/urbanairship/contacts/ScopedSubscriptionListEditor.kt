/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.util.Clock

/**
 * Subscription list editor. See [Contact.editSubscriptionLists].
 */
public abstract class ScopedSubscriptionListEditor
/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) protected constructor(
    private val clock: Clock = Clock.DEFAULT_CLOCK
 ) {

    private val mutations = mutableListOf<ScopedSubscriptionListMutation>()

    /**
     * Subscribe to a list.
     *
     * @param subscriptionListId The subscription list ID.
     * @param scope Defines the channel types that the change applies to.
     * @return The [ScopedSubscriptionListEditor] instance.
     */
    public fun subscribe(subscriptionListId: String, scope: Scope): ScopedSubscriptionListEditor {
        val trimmed = subscriptionListId.trim { it <= ' ' }
        if (trimmed.isEmpty()) {
            UALog.e("The subscription list ID must not be null or empty.")
            return this
        }

        mutations.add(
            ScopedSubscriptionListMutation.newSubscribeMutation(
                listId = trimmed,
                scope = scope,
                timestamp = clock.currentTimeMillis()
            )
        )
        return this
    }

    /**
     * Subscribes from a set of lists.
     *
     * @param subscriptionListIds A [Set] of list IDs.
     * @param scope Defines the channel types that the change applies to.
     * @return The [ScopedSubscriptionListEditor] instance.
     */
    public fun subscribe(
        subscriptionListIds: Set<String>,
        scope: Scope
    ): ScopedSubscriptionListEditor {
        subscriptionListIds.forEach { subscribe(it, scope) }
        return this
    }

    /**
     * Unsubscribe from a list.
     *
     * @param subscriptionListId The subscription list ID.
     * @param scope Defines the channel types that the change applies to.
     * @return The [ScopedSubscriptionListEditor] instance.
     */
    public fun unsubscribe(subscriptionListId: String, scope: Scope): ScopedSubscriptionListEditor {
        val trimmedListId = subscriptionListId.trim { it <= ' ' }
        if (trimmedListId.isEmpty()) {
            UALog.e("The subscription list ID must not be null or empty.")
            return this
        }

        mutations.add(
            ScopedSubscriptionListMutation.newUnsubscribeMutation(
                listId = trimmedListId,
                scope = scope,
                timestamp = clock.currentTimeMillis()
            )
        )
        return this
    }

    /**
     * Unsubscribes from a set of lists.
     *
     * @param subscriptionListIds A [Set] of list IDs.
     * @param scope Defines the channel types that the change applies to.
     * @return The [ScopedSubscriptionListEditor] instance.
     */
    public fun unsubscribe(
        subscriptionListIds: Set<String>,
        scope: Scope
    ): ScopedSubscriptionListEditor {

        subscriptionListIds.forEach { unsubscribe(it, scope) }
        return this
    }

    /**
     * Internal helper that uses a boolean flag to indicate whether to subscribe or unsubscribe.
     *
     * @param subscriptionListId The ID of the list to subscribe to or unsubscribe from.
     * @param scopes Defines the set of channel types that the change applies to.
     * @param isSubscribe `true` to subscribe or `false` to unsubscribe.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun mutate(
        subscriptionListId: String,
        scopes: Set<Scope>,
        isSubscribe: Boolean
    ): ScopedSubscriptionListEditor {
        for (scope in scopes) {
            if (isSubscribe) {
                subscribe(subscriptionListId, scope)
            } else {
                unsubscribe(subscriptionListId, scope)
            }
        }
        return this
    }

    /**
     * Apply the subscription list changes.
     */
    public fun apply() {
        onApply(ScopedSubscriptionListMutation.collapseMutations(mutations))
    }

    /**
     * Called when apply is called.
     *
     * @param mutations The list of mutations to be applied.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected abstract fun onApply(mutations: List<ScopedSubscriptionListMutation>)
}
