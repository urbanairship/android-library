/* Copyright Airship and Contributors */
package com.urbanairship.actions.tags

import androidx.annotation.CallSuper
import androidx.annotation.OpenForTesting
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionRegistry
import com.urbanairship.channel.TagEditor
import com.urbanairship.channel.TagGroupsEditor

/**
 * An action that adds tags.
 *
 * Accepted situations: all
 *
 * Accepted argument value types:
 * - A [String] for a single tag
 * - A [Collection] of [String]s for multiple tags
 * - A JSON payload for tag groups. An example JSON payload:
 *
 * ```
 * {
 *   "channel": {
 *     "channel_tag_group": ["channel_tag_1", "channel_tag_2"],
 *     "other_channel_tag_group": ["other_channel_tag_1"]
 *   },
 *   "named_user": {
 *     "named_user_tag_group": ["named_user_tag_1", "named_user_tag_2"],
 *     "other_named_user_tag_group": ["other_named_user_tag_1"]
 *   },
 *   "device": ["tag 1", "tag 2"]
 * }
 * ```
 *
 * Result value: `null`
 *
 * Default Registration Names:
 * - ^+t
 * - add_tags_action
 *
 * Default Registration Predicate: Rejects [com.urbanairship.actions.Action.SITUATION_PUSH_RECEIVED]
 */
@OpenForTesting
public class AddTagsAction internal constructor(
    private val channelTagEditor: () -> TagEditor,
    private val channelTagGroupEditor: () -> TagGroupsEditor,
    private val contactTagGroupEditor: () -> TagGroupsEditor
) : BaseTagsAction() {

    public constructor() : this(
        channelTagEditor = { UAirship.shared().channel.editTags() },
        channelTagGroupEditor = { UAirship.shared().channel.editTagGroups() },
        contactTagGroupEditor = { UAirship.shared().contact.editTagGroups() },
    )

    @CallSuper
    public override fun applyChannelTags(tags: Set<String>) {
        UALog.i("Adding tags: %s", tags)
        channelTagEditor().addTags(tags).apply()
    }

    @CallSuper
    public override fun applyChannelTagGroups(tags: Map<String, Set<String>>) {
        UALog.i("Adding channel tag groups: %s", tags)
        channelTagGroupEditor().apply {
            tags.forEach {
                addTags(it.key, it.value)
            }
        }.apply()
    }

    @CallSuper
    public override fun applyContactTagGroups(tags: Map<String, Set<String>>) {
        UALog.i("Adding contact user tag groups: %s", tags)
        contactTagGroupEditor().apply {
            tags.forEach {
                addTags(it.key, it.value)
            }
        }.apply()
    }

    /**
     * Default [AddTagsAction] predicate.
     */
    public class AddTagsPredicate public constructor() : ActionRegistry.Predicate {

        override fun apply(arguments: ActionArguments): Boolean {
            return Situation.PUSH_RECEIVED != arguments.situation
        }
    }

    public companion object {

        /**
         * Default registry name
         */
        public const val DEFAULT_REGISTRY_NAME: String = "add_tags_action"

        /**
         * Default registry short name
         */
        public const val DEFAULT_REGISTRY_SHORT_NAME: String = "^+t"
    }
}
