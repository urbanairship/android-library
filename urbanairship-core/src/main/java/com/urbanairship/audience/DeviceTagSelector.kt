/* Copyright Airship and Contributors */

package com.urbanairship.audience

import androidx.annotation.RestrictTo
import androidx.annotation.Size
import androidx.core.util.ObjectsCompat
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

/*
 * <tag_selector>   := <tag> | <not> | <and> | <or>
 * <tag>            := { "tag": string }
 * <not>            := { "not": <tag_selector> }
 * <and>            := { "and": [<tag_selector>, <tag_selector>, ...] }
 * <or>             := { "or": [<tag_selector>, <tag_selector>, ...] }
 */

/**
 * Tag selector.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DeviceTagSelector
    private constructor(
        internal val type: Type,
        internal val tag: String? = null,
        internal val selectors: List<DeviceTagSelector> = emptyList()
    ) : JsonSerializable {

    public companion object {
        public fun and(@Size(min = 1) selectors: List<DeviceTagSelector>): DeviceTagSelector {
            return DeviceTagSelector(Type.AND, selectors = selectors)
        }

        public fun and(@Size(min = 1) vararg selectors: DeviceTagSelector): DeviceTagSelector {
            return DeviceTagSelector(Type.AND, selectors = listOf(*selectors))
        }

        public fun or(@Size(min = 1) selectors: List<DeviceTagSelector>): DeviceTagSelector {
            return DeviceTagSelector(Type.OR, selectors = selectors)
        }

        public fun or(@Size(min = 1) vararg selectors: DeviceTagSelector): DeviceTagSelector {
            return DeviceTagSelector(Type.OR, selectors = listOf(*selectors))
        }

        public fun not(selector: DeviceTagSelector): DeviceTagSelector {
            return DeviceTagSelector(Type.NOT, selectors = listOf(selector))
        }

        public fun tag(tag: String): DeviceTagSelector {
            return DeviceTagSelector(Type.TAG, tag)
        }

        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): DeviceTagSelector {
            val jsonMap = value.optMap()
            if (jsonMap.containsKey(Type.TAG.value)) {
                val tag = jsonMap.opt(Type.TAG.value).string
                    ?: throw JsonException("Tag selector expected a tag: " + jsonMap.opt(Type.TAG.value))
                return tag(tag)
            }
            if (jsonMap.containsKey(Type.OR.value)) {
                val selectors = jsonMap.opt(Type.OR.value).list ?: throw JsonException(
                    "OR selector expected array of tag selectors: " + jsonMap.opt(
                        Type.OR.value
                    )
                )
                return or(parseSelectors(selectors))
            }
            if (jsonMap.containsKey(Type.AND.value)) {
                val selectors = jsonMap.opt(Type.AND.value).list ?: throw JsonException(
                    "AND selector expected array of tag selectors: " + jsonMap.opt(
                        Type.AND.value
                    )
                )
                return and(parseSelectors(selectors))
            }
            if (jsonMap.containsKey(Type.NOT.value)) {
                return not(fromJson(jsonMap.opt(Type.NOT.value)))
            }
            throw JsonException("Json value did not contain a valid selector: $value")
        }

        @Throws(JsonException::class)
        private fun parseSelectors(jsonList: JsonList): List<DeviceTagSelector> {
            val selectors = mutableListOf<DeviceTagSelector>()
            for (jsonValue in jsonList) {
                selectors.add(fromJson(jsonValue))
            }
            if (selectors.isEmpty()) {
                throw JsonException("Expected 1 or more selectors")
            }
            return selectors
        }
    }

    public fun apply(tags: Collection<String>): Boolean {
        return when (type) {
            Type.TAG -> tags.contains(tag)
            Type.NOT -> !selectors[0].apply(tags)
            Type.AND -> {
                for (selector in selectors) {
                    if (!selector.apply(tags)) {
                        return false
                    }
                }
                true
            }

            Type.OR -> {
                for (selector in selectors) {
                    if (selector.apply(tags)) {
                        return true
                    }
                }
                false
            }
        }
    }

    override fun toJsonValue(): JsonValue {
        val builder = JsonMap.newBuilder()

        when (type) {
            Type.TAG -> builder.put(type.value, tag)
            Type.NOT -> builder.put(type.value, selectors[0])
            Type.OR, Type.AND -> builder.put(type.value, JsonValue.wrapOpt(selectors))
        }

        return builder.build().toJsonValue()
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that: DeviceTagSelector = o as DeviceTagSelector
        return ObjectsCompat.equals(type, that.type) && ObjectsCompat.equals(
            tag,
            that.tag
        ) && ObjectsCompat.equals(selectors, that.selectors)
    }

    override fun hashCode(): Int {
        return ObjectsCompat.hash(type, tag, selectors)
    }

    override fun toString(): String {
        return toJsonValue().toString()
    }

    internal enum class Type(val value: String) {
        OR("or"),
        AND("and"),
        NOT("not"),
        TAG("tag");
    }
}
