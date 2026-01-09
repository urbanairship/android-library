/* Copyright Airship and Contributors */
package com.urbanairship.channel

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import java.util.Objects

/**
 * Defines a tag group mutations.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class TagGroupsMutation private constructor(
    addTags: Map<String, Set<String>>? = null,
    removeTags: Map<String, Set<String>>? = null,
    setTags: Map<String, Set<String>>? = null
) : JsonSerializable {

    internal val addTags: Map<String, Set<String>> = addTags ?: emptyMap()
    internal val removeTags: Map<String, Set<String>> = removeTags ?: emptyMap()
    internal val setTags: Map<String, Set<String>> = setTags ?: emptyMap()

    override fun toJsonValue(): JsonValue {
        val builder = JsonMap.newBuilder()

        if (addTags.isNotEmpty()) {
            builder.put(ADD_KEY, JsonValue.wrapOpt(addTags))
        }

        if (removeTags.isNotEmpty()) {
            builder.put(REMOVE_KEY, JsonValue.wrapOpt(removeTags))
        }

        if (setTags.isNotEmpty()) {
            builder.put(SET_KEY, JsonValue.wrapOpt(setTags))
        }

        return builder.build().toJsonValue()
    }

    public val isEmpty: Boolean
        get() {
            if (addTags.isNotEmpty()) {
                return false
            }

            if (removeTags.isNotEmpty()) {
                return false
            }

            if (setTags.isNotEmpty()) {
                return false
            }

            return true
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val mutation = other as TagGroupsMutation

        if (addTags != mutation.addTags) return false
        if (removeTags != mutation.removeTags) return false
        if (setTags != mutation.setTags) return false
        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(addTags, removeTags, setTags)
    }

    override fun toString(): String {
        return "TagGroupsMutation{addTags=$addTags, removeTags=$removeTags, setTags=$setTags}"
    }

    public fun apply(tagGroups: MutableMap<String, MutableSet<String>>) {
        // Add tags
        addTags.let { tagsToAdd ->
            tagsToAdd.forEach { (group, tags) ->
                tagGroups
                    .getOrPut(group) { mutableSetOf() }
                    .addAll(tags)
            }
        }

        // Remove tags
        removeTags.forEach { (group, tags) ->
            tagGroups[group]?.removeAll(tags)
        }

        // Set tags
        setTags.forEach { (group, tags) ->
            tagGroups[group] = tags.toMutableSet()
        }
    }

    public companion object {

        private const val ADD_KEY = "add"
        private const val REMOVE_KEY = "remove"
        private const val SET_KEY = "set"

        /**
         * Creates a mutation to add tags to a group.
         *
         * @param group Group ID.
         * @param tags Tags to add.
         * @return Tag group mutation [TagGroupsMutation].
         */
        public fun newAddTagsMutation(group: String, tags: Set<String>): TagGroupsMutation {
            return TagGroupsMutation(
                addTags = mapOf(group to tags.toMutableSet())
            )
        }

        /**
         * Creates a mutation to remove tags to a group.
         *
         * @param group Group ID.
         * @param tags Tags to remove.
         * @return Tag group mutation [TagGroupsMutation].
         */
        public fun newRemoveTagsMutation(group: String, tags: Set<String>): TagGroupsMutation {
            return TagGroupsMutation(
                removeTags = mapOf(group to tags.toMutableSet())
            )
        }

        /**
         * Creates a mutation to set tags to a group.
         *
         * @param group Group ID.
         * @param tags Tags to set.
         * @return Tag group mutation [TagGroupsMutation].
         */
        public fun newSetTagsMutation(group: String, tags: Set<String>): TagGroupsMutation {
            return TagGroupsMutation(
                setTags = mapOf(group to tags.toMutableSet())
            )
        }

        /**
         * Collapses mutations down to a minimum set of mutations.
         *
         * @param mutations List of mutations to collapse.
         * @return A new list of collapsed mutations.
         * @hide
         */
        internal fun collapseMutations(mutations: List<TagGroupsMutation>?): List<TagGroupsMutation> {
            if (mutations.isNullOrEmpty()) {
                return emptyList()
            }

            val addTags = mutableMapOf<String, MutableSet<String>>()
            val removeTags = mutableMapOf<String, MutableSet<String>>()
            val setTags = mutableMapOf<String, MutableSet<String>>()

            for (mutation in mutations) {
                // Add tags
                mutation.addTags
                    .mapNotNull { (key, value) ->
                        val trimmed = key.trim { it <= ' ' }
                        if (trimmed.isEmpty() || value.isEmpty()) {
                            null
                        } else {
                            trimmed to value
                        }
                    }
                    .forEach { (group, tags) ->
                        if (setTags.containsKey(group)) {
                            setTags[group]?.addAll(tags)
                            return@forEach
                        }

                        if (removeTags.containsKey(group)) {
                            val current = removeTags[group]
                            current?.removeAll(tags)
                            if (current?.isEmpty() == true) {
                                removeTags.remove(group)
                            }
                        }

                        addTags
                            .getOrPut(group) { mutableSetOf() }
                            .addAll(tags)
                    }

                // Remove tags
                mutation.removeTags
                    .mapNotNull { (group, tags) ->
                        val trimmed = group.trim { it <= ' ' }
                        if (trimmed.isEmpty() || tags.isEmpty()) {
                            null
                        } else {
                            trimmed to tags
                        }
                    }
                    .forEach { (group, tags) ->
                        // Remove from the set tag groups if we can
                        if (setTags.containsKey(group)) {
                            setTags[group]?.removeAll(tags)
                            return@forEach
                        }

                        // Remove from add tag groups
                        if (addTags.containsKey(group)) {
                            val current = addTags[group]
                            current?.removeAll(tags)
                            if (current?.isEmpty() == true) {
                                addTags.remove(group)
                            }
                        }

                        // Add to remove tags
                        removeTags
                            .getOrPut(group) { mutableSetOf() }
                            .addAll(tags)
                    }

                mutation.setTags
                    .mapNotNull { (group, tags) ->
                        val trimmed = group.trim { it <= ' ' }
                        if (trimmed.isEmpty()) {
                            null
                        } else {
                            trimmed to tags
                        }
                    }
                    .forEach { (group, tags) ->
                        setTags[group] = tags.toMutableSet()

                        // Remove from add and remove tags
                        removeTags.remove(group)
                        addTags.remove(group)
                    }
            }

            val collapsedMutations = mutableListOf<TagGroupsMutation>()

            // Set must be a separate mutation
            if (setTags.isNotEmpty()) {
                val mutation = TagGroupsMutation(setTags = setTags)
                collapsedMutations.add(mutation)
            }

            // Add and remove can be collapsed into one mutation
            if (addTags.isNotEmpty() || removeTags.isNotEmpty()) {
                val mutation = TagGroupsMutation(addTags = addTags, removeTags = removeTags)
                collapsedMutations.add(mutation)
            }

            return collapsedMutations
        }

        public fun fromJsonValue(jsonValue: JsonValue): TagGroupsMutation {
            val jsonMap = jsonValue.optMap()

            val addTags = TagUtils.convertToTagsMap(jsonMap.opt(ADD_KEY))
            val removeTags = TagUtils.convertToTagsMap(jsonMap.opt(REMOVE_KEY))
            val setTags = TagUtils.convertToTagsMap(jsonMap.opt(SET_KEY))

            return TagGroupsMutation(
                addTags = addTags,
                removeTags = removeTags,
                setTags = setTags
            )
        }

        public fun fromJsonList(jsonList: JsonList): List<TagGroupsMutation> {
            return jsonList.map(::fromJsonValue)
        }
    }
}
