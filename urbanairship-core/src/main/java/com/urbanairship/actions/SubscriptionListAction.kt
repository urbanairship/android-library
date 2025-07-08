/* Copyright Airship and Contributors */
package com.urbanairship.actions

import androidx.annotation.VisibleForTesting
import androidx.core.util.ObjectsCompat
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.actions.ActionResult.Companion.newErrorResult
import com.urbanairship.actions.ActionResult.Companion.newResult
import com.urbanairship.base.Supplier
import com.urbanairship.channel.SubscriptionListEditor
import com.urbanairship.contacts.Scope
import com.urbanairship.contacts.ScopedSubscriptionListEditor
import com.urbanairship.json.JsonException
import com.urbanairship.json.requireField

/**
 * Action for subscribing/unsubscribing to lists.
 *
 *
 * Accepted situations: all
 *
 *
 * Accepted argument value types: A JSON Payload containing a type, a list, an action and a scope.
 * ```
 * An example JSON Payload :
 * [
 *  {
 *      "type": "contact",
 *      "action": "subscribe",
 *      "list": "mylist",
 *      "scope": "app"
 *  },
 *  {
 *      "type": "channel",
 *      "action": "unsubscribe",
 *      "list": "thelist"
 *  }
 * ]
 * ```
 *
 * Result value: The payload used.
 *
 *
 * Default Registration Names: [DEFAULT_REGISTRY_SHORT_NAME], [ALT_DEFAULT_REGISTRY_SHORT_NAME],
 * [DEFAULT_REGISTRY_NAME], [ALT_DEFAULT_REGISTRY_NAME]
 *
 *
 * Default Registration Predicate: none
 */

public class SubscriptionListAction @VisibleForTesting internal constructor(
    private val channelEditorSupplier: Supplier<SubscriptionListEditor?>,
    private val contactEditorSupplier: Supplier<ScopedSubscriptionListEditor?>
) : Action() {

    /**
     * Default constructor.
     */
    public constructor() : this(
        Supplier<SubscriptionListEditor?> { UAirship.shared().channel.editSubscriptionLists() },
        Supplier<ScopedSubscriptionListEditor?> { UAirship.shared().contact.editSubscriptionLists() })

    override fun perform(arguments: ActionArguments): ActionResult {
        val channelEditor = requireNotNull(channelEditorSupplier.get())
        val contactEditor = requireNotNull(contactEditorSupplier.get())

        arguments.value
            .toJsonValue()
            .optList()
            .map { it.requireMap() }
            .forEach { operation ->
                try {
                    val listId: String = operation.requireField(LIST_KEY)
                    val type: String = operation.requireField(TYPE_KEY)
                    val action: String = operation.requireField(ACTION_KEY)

                    when (type) {
                        CHANNEL_KEY -> applyChannelOperation(channelEditor, listId, action)
                        CONTACT_KEY -> {
                            val scope = Scope.fromJson(operation.require(SCOPE_KEY))
                            applyContactOperation(contactEditor, listId, action, scope)
                        }
                    }
                } catch (e: JsonException) {
                    UALog.e(e, "Invalid argument")
                    return newErrorResult(e)
                }
            }

        channelEditor.apply()
        contactEditor.apply()
        return newResult(arguments.value)
    }

    @Throws(JsonException::class)
    private fun applyContactOperation(
        editor: ScopedSubscriptionListEditor,
        listId: String,
        action: String,
        scope: Scope
    ) {
        when (action) {
            SUBSCRIBE_KEY -> editor.subscribe(listId, scope)
            UNSUBSCRIBE_KEY -> editor.unsubscribe(listId, scope)
            else -> throw JsonException("Invalid action: $action")
        }
    }

    @Throws(JsonException::class)
    private fun applyChannelOperation(
        editor: SubscriptionListEditor,
        listId: String,
        action: String
    ) {
        when (action) {
            SUBSCRIBE_KEY -> editor.subscribe(listId)
            UNSUBSCRIBE_KEY -> editor.unsubscribe(listId)
            else -> throw JsonException("Invalid action: $action")
        }
    }

    override fun acceptsArguments(arguments: ActionArguments): Boolean {
        return !arguments.value.isNull && arguments.situation != Situation.PUSH_RECEIVED
    }

    public companion object {

        // Arg keys
        private const val TYPE_KEY = "type"
        private const val LIST_KEY = "list"
        private const val ACTION_KEY = "action"
        private const val SCOPE_KEY = "scope"
        private const val SUBSCRIBE_KEY = "subscribe"
        private const val UNSUBSCRIBE_KEY = "unsubscribe"
        private const val CHANNEL_KEY = "channel"
        private const val CONTACT_KEY = "contact"

        /**
         * Default registry name
         */
        public const val DEFAULT_REGISTRY_NAME: String = "subscription_list_action"

        /**
         * Default registry short name
         */
        public const val DEFAULT_REGISTRY_SHORT_NAME: String = "^sla"

        /**
         * Default registry short name
         */
        public const val ALT_DEFAULT_REGISTRY_SHORT_NAME: String = "^sl"

        /**
         * Default registry short name
         */
        public const val ALT_DEFAULT_REGISTRY_NAME: String = "edit_subscription_list_action"
    }
}
