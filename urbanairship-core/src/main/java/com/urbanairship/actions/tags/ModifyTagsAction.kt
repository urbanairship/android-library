/* Copyright Airship and Contributors */
package com.urbanairship.actions.tags

import com.urbanairship.Airship
import com.urbanairship.Provider
import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionRegistry
import com.urbanairship.actions.ActionResult
import com.urbanairship.channel.TagEditor
import com.urbanairship.channel.TagGroupsEditor
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireList

/**
 * An action that modifies either channel or contact tags.
 *
 * Accepted situations: all
 *
 * Accepted argument value types:
 * - A JSON payload for tag groups. An example JSON payload:
 *
 * ```
 * [
 *     {
 *       "action": "add",
 *       "tags": [
 *         "channel_tag_1",
 *         "channel_tag_2"
 *       ],
 *       "type": "channel"
 *     },
 *
 *     {
 *       "action": "remove",
 *       "group": "tag_group"
 *       "tags": [
 *         "contact_tag_1",
 *         "contact_tag_2"
 *       ],
 *       "type": "contact"
 *     }
 * ]
 * ```
 *
 * Result value: `null`
 *
 * Default Registration Names:
 * - ^t
 * - tag_action
 *
 * Default Registration Predicate: Rejects [com.urbanairship.actions.Action.SITUATION_PUSH_RECEIVED]
 */
public class ModifyTagsAction internal constructor(
    private val channelTagEditor: Provider<TagEditor>,
    private val channelTagGroupEditor: Provider<TagGroupsEditor>,
    private val contactTagGroupEditor: Provider<TagGroupsEditor>
): Action() {

    public constructor() : this(
        channelTagEditor = { Airship.channel.editTags() },
        channelTagGroupEditor = { Airship.channel.editTagGroups() },
        contactTagGroupEditor = { Airship.contact.editTagGroups() },
    )

    override fun acceptsArguments(arguments: ActionArguments): Boolean {
        if (arguments.value.isNull) {
            return false
        }

        val content = arguments.value.list ?: return false

        return try {
            content.map(Arguments::fromJson)
            true
        } catch (_ : JsonException) {
            false
        }
    }

    override fun perform(arguments: ActionArguments): ActionResult {
        val content = arguments.value.list
            ?: return ActionResult.newEmptyResultWithStatus(ActionResult.Status.REJECTED_ARGUMENTS)

        val args = try {
            content.map(Arguments::fromJson)
        } catch (ex : JsonException) {
            return ActionResult.newErrorResult(ex)
        }

        val channelEditor = channelTagEditor.get()
        val channelGroupEditor = channelTagGroupEditor.get()
        val contactGroupEditor = contactTagGroupEditor.get()

        val onEditingDoneCallbacks = mutableMapOf<EditorType, () -> Unit>()

        args.forEach { item ->
            performAction(
                arguments = item,
                channelEditor = {
                    if (!onEditingDoneCallbacks.containsKey(EditorType.CHANNEL)) {
                        onEditingDoneCallbacks.put(EditorType.CHANNEL, channelEditor::apply)
                    }
                    channelEditor
                },
                groupEditor = { type ->
                    val (key, editor) = when(type) {
                        Arguments.Type.CHANNEL -> EditorType.CHANNEL_GROUP to channelGroupEditor
                        Arguments.Type.CONTACT -> EditorType.CONTACT_GROUP to contactGroupEditor
                    }

                    if (!onEditingDoneCallbacks.containsKey(key)) {
                        onEditingDoneCallbacks.put(key, editor::apply)
                    }

                    editor
                }
            )
        }

        onEditingDoneCallbacks.values.forEach { it() }

        return ActionResult.newEmptyResult()
    }

    private fun performAction(
        arguments: Arguments,
        channelEditor: () -> TagEditor,
        groupEditor: (Arguments.Type) -> TagGroupsEditor
    ) {
        val group = arguments.tagGroup
        if (group != null) {
            val editor = groupEditor(arguments.type)

            when(arguments.action) {
                Arguments.Action.ADD -> editor.addTags(group, arguments.tags)
                Arguments.Action.REMOVE -> editor.removeTags(group, arguments.tags)
            }

        } else {
            when(arguments) {
                is Arguments.Channel -> {
                    val editor = channelEditor()
                    when(arguments.action) {
                        Arguments.Action.ADD -> editor.addTags(arguments.tags)
                        Arguments.Action.REMOVE -> editor.removeTags(arguments.tags)
                    }
                }
                else -> {}
            }
        }
    }

    public class ModifyTagsPredicate public constructor() : ActionRegistry.Predicate {
        override fun apply(arguments: ActionArguments): Boolean {
            return Situation.PUSH_RECEIVED != arguments.situation
        }
    }

    private enum class EditorType {
        CHANNEL, CHANNEL_GROUP, CONTACT_GROUP
    }

    public companion object {

        /**
         * Default registry name
         */
        public const val DEFAULT_REGISTRY_NAME: String = "tag_action"

        /**
         * Default registry short name
         */
        public const val DEFAULT_REGISTRY_SHORT_NAME: String = "^t"
    }

    private sealed class Arguments(
        val type: Type,
        val action: Action,
        val tags: Set<String>
    ): JsonSerializable {

        class Channel(
            action: Action,
            tags: Set<String>,
            val group: String?
        ) : Arguments(Type.CHANNEL, action, tags) {

            override fun jsonFields(): JsonMap = jsonMapOf(KEY_GROUP to group)
        }

        class Contact(
            action: Action,
            tags: Set<String>,
            val group: String
        ) : Arguments(Type.CONTACT, action, tags) {
            override fun jsonFields(): JsonMap  = jsonMapOf(KEY_GROUP to group)
        }

        val tagGroup: String?
            get() {
                return when(this) {
                    is Channel -> group
                    is Contact -> group
                }
            }

        abstract fun jsonFields(): JsonMap

        override fun toJsonValue(): JsonValue {
            return JsonMap
                .newBuilder()
                .putAll(jsonFields())
                .put(KEY_TYPE, type)
                .put(KEY_ACTION, action)
                .put(KEY_TAGS, JsonValue.wrap(tags))
                .build()
                .toJsonValue()
        }

        enum class Type(val jsonValue: String): JsonSerializable {
            CHANNEL("channel"),
            CONTACT("contact");

            override fun toJsonValue(): JsonValue = JsonValue.wrap(jsonValue)

            companion object {
                @Throws(JsonException::class)
                fun fromJson(value: JsonValue): Type {
                    val content = value.requireString()
                    return entries.firstOrNull { it.jsonValue == content }
                        ?: throw JsonException("Unknown type: $content")
                }
            }
        }

        enum class Action(val jsonValue: String): JsonSerializable {
            ADD("add"),
            REMOVE("remove");

            override fun toJsonValue(): JsonValue = JsonValue.wrap(jsonValue)

            companion object {
                @Throws(JsonException::class)
                fun fromJson(value: JsonValue): Action {
                    val content = value.requireString()
                    return entries.firstOrNull { it.jsonValue == content }
                        ?: throw JsonException("Unknown action: $content")
                }
            }
        }

        companion object {
            private const val KEY_TYPE = "type"
            private const val KEY_ACTION = "action"
            private const val KEY_TAGS = "tags"
            private const val KEY_GROUP = "group"

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): Arguments {
                val content = value.requireMap()
                val type = Type.fromJson(content.require(KEY_TYPE))
                val action = Action.fromJson(content.require(KEY_ACTION))
                val tags = content.requireList(KEY_TAGS).map { it.requireString() }.toSet()

                return when(type) {
                    Type.CHANNEL -> Channel(action, tags, content[KEY_GROUP]?.requireString())
                    Type.CONTACT -> Contact(action, tags, content.require(KEY_GROUP).requireString())
                }
            }
        }
    }
}
