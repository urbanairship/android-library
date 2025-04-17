/* Copyright Airship and Contributors */
package com.urbanairship.actions.tags

import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionResult
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Abstract tag action class.
 */
public abstract class BaseTagsAction : Action() {

    private val mutableMutations: MutableSharedFlow<TagActionMutation> =  MutableSharedFlow()

    /**
     * Tag mutations
     */
    public val mutationsFlow: SharedFlow<TagActionMutation> = mutableMutations.asSharedFlow()

    override fun acceptsArguments(arguments: ActionArguments): Boolean {
        if (arguments.value.isNull) {
            return false
        }

        if (arguments.value.string != null) {
            return true
        }

        if (arguments.value.list != null) {
            return true
        }

        return arguments.value.map != null
    }

    override fun perform(arguments: ActionArguments): ActionResult {
        if (arguments.value.string != null) {
            arguments.value.string?.let {
                val tags = setOf(it)
                applyChannelTags(tags)
                mutableMutations.tryEmit(TagActionMutation.ChannelTags(tags))
            }
        }

        if (arguments.value.list != null) {
            val tags = arguments.value.list?.mapNotNull { it.string }?.toSet()
            if (!tags.isNullOrEmpty()) {
                applyChannelTags(tags)
                mutableMutations.tryEmit(TagActionMutation.ChannelTags(tags))
            }
        }

        if (arguments.value.map != null) {
            // Channel Tag Groups
            arguments.value.map?.get(CHANNEL_KEY)?.optMap()?.let { map ->
                val tagGroups = mutableMapOf<String, Set<String>>()
                map.forEach {
                    val tags = it.value.list?.mapNotNull { it.string }
                    if (!tags.isNullOrEmpty()) {
                        tagGroups[it.key] = tags.toSet()
                    }
                }
                if (tagGroups.isNotEmpty()) {
                    applyChannelTagGroups(tagGroups)
                    mutableMutations.tryEmit(TagActionMutation.ChannelTagGroups(tagGroups))
                }
            }

            // Contact Tag Groups
            arguments.value.map?.get(NAMED_USER_KEY)?.optMap()?.let { map ->
                val tagGroups = mutableMapOf<String, Set<String>>()
                map.forEach {
                    val tags = it.value.list?.mapNotNull { it.string }
                    if (!tags.isNullOrEmpty()) {
                        tagGroups[it.key] = tags.toSet()
                    }
                }
                if (tagGroups.isNotEmpty()) {
                    applyContactTagGroups(tagGroups)
                    mutableMutations.tryEmit(TagActionMutation.ContactTagGroups(tagGroups))
                }
            }

            // Device Tags
            arguments.value.map?.get(DEVICE_KEY)?.optList()?.let { list ->
                val tags = list.mapNotNull { tag -> tag.string }.toSet()
                if (tags.isNotEmpty()) {
                    applyChannelTags(tags)
                    mutableMutations.tryEmit(TagActionMutation.ChannelTags(tags))
                }
            }
        }

        return ActionResult.newEmptyResult()
    }

    /**
     * Applies tag updates.
     *
     * @param tags The set of tags.
     */
    internal abstract fun applyChannelTags(tags: Set<String>)

    /**
     * Applies channel tag group updates.
     *
     * @param tags The map of tag groups.
     */
    internal abstract fun applyChannelTagGroups(tags: Map<String, Set<String>>)

    /**
     * Applies contact tag group updates.
     *
     * @param tags The map of tag groups.
     */
    internal abstract fun applyContactTagGroups(tags: Map<String, Set<String>>)

    internal companion object {

        /**
         * JSON key for channel tag group changes.
         */
        private const val CHANNEL_KEY = "channel"

        /**
         * JSON key for named user tag group changes.
         */
        private const val NAMED_USER_KEY = "named_user"

        /**
         * JSON key for device tags.
         */
        private const val DEVICE_KEY = "device"
    }
}
