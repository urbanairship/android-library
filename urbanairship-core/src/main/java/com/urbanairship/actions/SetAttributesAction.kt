/* Copyright Airship and Contributors */
package com.urbanairship.actions

import com.urbanairship.UALog
import com.urbanairship.Airship
import com.urbanairship.actions.ActionResult.Companion.newEmptyResult
import com.urbanairship.channel.AttributeEditor
import com.urbanairship.json.JsonValue
import java.util.Date

/**
 * An action that sets attributes.
 *
 *
 * Accepted situations: all
 *
 *
 * Accepted argument value types: A JSON payload for setting or removing attributes. An example JSON payload:
 * ```
 * {
 *  "channel": {
 *      set: {"key_1": value_1, "key_2": value_2},
 *      remove: ["attribute_1", "attribute_2", "attribute_3"]
 *  },
 *  "named_user": {
 *      set: {"key_4": value_4, "key_5": value_5},
 *      remove: ["attribute_4", "attribute_5", "attribute_6"]
 *  }
 * ```
 */
public class SetAttributesAction public constructor() : Action() {

    override fun perform(arguments: ActionArguments): ActionResult {
        val args = arguments.value.map ?: return newEmptyResult()

        // Channel Attribute
        args[CHANNEL_KEY]?.optMap()?.let { channel ->
            val editor = Airship.shared().channel.editAttributes()
            channel.map.entries.forEach { handleAttributeActions(editor, it)}
            editor.apply()
        }

        // Contact Attribute
        args[NAMED_USER_KEY]?.optMap()?.let { user ->
            val editor = Airship.shared().contact.editAttributes()
            user.map.entries.forEach { handleAttributeActions(editor, it)}
            editor.apply()
        }

        return newEmptyResult()
    }

    override fun acceptsArguments(arguments: ActionArguments): Boolean {
        if (arguments.value.isNull) {
            return false
        }

        if (arguments.value.map == null) {
            return false
        }

        // Channel attributes
        val channel = arguments.value.map.opt(CHANNEL_KEY)
        if (channel !== JsonValue.NULL && !areAttributeMutationsValid(channel)) {
            return false
        }

        // Named User attributes
        val namedUser = arguments.value.map.opt(NAMED_USER_KEY)
        if (namedUser !== JsonValue.NULL && !areAttributeMutationsValid(namedUser)) {
            return false
        }

        return channel !== JsonValue.NULL || namedUser !== JsonValue.NULL
    }

    private fun areAttributeMutationsValid(attributeMutations: JsonValue): Boolean {
        if (attributeMutations.map == null) {
            return false
        }

        val set = attributeMutations.optMap().opt(SET_KEY)
        if (set !== JsonValue.NULL && !isSetAttributeMutationValid(set)) {
            return false
        }

        val remove = attributeMutations.optMap().opt(REMOVE_KEY)
        return !(remove !== JsonValue.NULL && !isRemoveAttributeMutationValid(remove))
    }

    private fun isSetAttributeMutationValid(setAttributeMutation: JsonValue): Boolean {
        return setAttributeMutation.map != null
    }

    private fun isRemoveAttributeMutationValid(removeAttributeMutation: JsonValue): Boolean {
        return removeAttributeMutation.list != null
    }

    /**
     * Handles the attributes updates
     * @param attributeEditor The attribute editor
     * @param entry The attribute entry
     */
    private fun handleAttributeActions(
        attributeEditor: AttributeEditor, entry: Map.Entry<String, JsonValue>
    ) {
        when (entry.key) {
            SET_KEY -> {
                entry.value
                    .optMap()
                    .entrySet()
                    .forEach {
                        val value = it.value.value ?: return@forEach
                        setAttribute(attributeEditor, it.key, value)
                    }
            }

            REMOVE_KEY -> {
                entry.value
                    .optList()
                    .list
                    .map { it.optString() }
                    .forEach(attributeEditor::removeAttribute)
            }

            else -> {}
        }
    }

    /**
     * Apply the attribute settings.
     * @param attributeEditor The attribute editor.
     * @param key The attribute key.
     * @param value The attribute value.
     */
    private fun setAttribute(attributeEditor: AttributeEditor, key: String, value: Any) {
        when (value) {
            is Int -> attributeEditor.setAttribute(key, value)
            is Long -> attributeEditor.setAttribute(key, value)
            is Float -> attributeEditor.setAttribute(key, value)
            is Double -> attributeEditor.setAttribute(key, value)
            is String -> attributeEditor.setAttribute(key, value)
            is Date -> attributeEditor.setAttribute(key, value)
            else -> {
                UALog.w("SetAttributesAction - Invalid value type for the key: $key")
            }
        }
    }

    /**
     * Default [SetAttributesAction.SetAttributesPredicate] predicate.
     */
    public class SetAttributesPredicate public constructor() : ActionRegistry.Predicate {

        override fun apply(arguments: ActionArguments): Boolean {
            return Situation.PUSH_RECEIVED != arguments.situation
        }
    }

    public companion object {

        /**
         * Default registry name
         */
        public const val DEFAULT_REGISTRY_NAME: String = "set_attributes_action"

        /**
         * Default registry short name
         */
        public const val DEFAULT_REGISTRY_SHORT_NAME: String = "^a"

        /**
         * JSON key for channel attributes changes.
         */
        private const val CHANNEL_KEY = "channel"

        /**
         * JSON key for named user attributes changes.
         */
        private const val NAMED_USER_KEY = "named_user"

        /**
         * JSON key for setting attributes.
         */
        private const val SET_KEY = "set"

        /**
         * JSON key for removing attributes.
         */
        private const val REMOVE_KEY = "remove"
    }
}
