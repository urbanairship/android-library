/* Copyright Airship and Contributors */

package com.urbanairship.automation

import com.urbanairship.audience.CompoundAudienceSelector
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.util.Objects

/**
 * Automation compound audience selector.
 */
public class AutomationCompoundAudience internal constructor(
    /**
     * Audience selector.
     */
    internal val selector: CompoundAudienceSelector,
    /**
     * Miss behavior.
     */
    internal val missBehavior: AutomationAudience.MissBehavior
): JsonSerializable {

    public companion object {
        private const val SELECTOR = "selector"
        private const val MISS_BEHAVIOR = "miss_behavior"

        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): AutomationCompoundAudience {
            val content = value.requireMap()
            return AutomationCompoundAudience(
                selector = CompoundAudienceSelector.fromJson(content.require(SELECTOR)),
                missBehavior = AutomationAudience.MissBehavior.fromJson(content.require(
                    MISS_BEHAVIOR))
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        SELECTOR to selector,
        MISS_BEHAVIOR to missBehavior
    ).toJsonValue()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AutomationCompoundAudience

        if (selector != other.selector) return false
        if (missBehavior != other.missBehavior) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(selector, missBehavior)
    }
}
