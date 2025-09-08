/* Copyright Airship and Contributors */

package com.urbanairship.iam.info

import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import java.util.Objects

/**
 * In-app button display info.
 */

public class InAppMessageButtonInfo(
    /**
     * The button's ID.
     */
    public val identifier: String,
    /**
     * The button's label.
     */
    public val label: InAppMessageTextInfo,
    /**
     * The action names and values to be run when the button is clicked.
     */
    public val actions: JsonMap? = null,
    /**
     * The button's click behavior.
     */
    public val behavior: Behavior? = null,
    /**
     * The button's background color.
     */
    public val backgroundColor: InAppMessageColor? = null,
    /**
     * The button's border color.
     */
    public val borderColor: InAppMessageColor? = null,
    /**
     * The border radius in dps.
     */
    public val borderRadius: Float = 0F
) : JsonSerializable {
    public enum class Behavior(internal val json: String) : JsonSerializable {
        /**
         * Dismisses the in-app message when clicked.
         */
        DISMISS("dismiss"),

        /**
         * Cancels the in-app message's schedule when clicked.
         */
        CANCEL("cancel");

        internal companion object {

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): Behavior {
                val content = value.requireString()
                return entries.firstOrNull { it.json == content }
                    ?: throw JsonException("Invalid behaviour value $content")
            }
        }

        override fun toJsonValue(): JsonValue = JsonValue.wrap(json)
    }

    public companion object {
        /**
         * Max button ID length.
         */
        public const val MAX_ID_LENGTH: Int = 100

        private const val LABEL_KEY = "label"
        private const val ID_KEY = "id"
        private const val BEHAVIOR_KEY = "behavior"
        private const val BORDER_RADIUS_KEY = "border_radius"
        private const val BACKGROUND_COLOR_KEY = "background_color"
        private const val BORDER_COLOR_KEY = "border_color"
        private const val ACTIONS_KEY = "actions"

        /**
         * Parses an [InAppMessageButtonInfo] from a [JsonValue].
         *
         * @param source The json value.
         * @return The parsed button info.
         * @throws JsonException If the button info was unable to be parsed.
         */
        @Throws(JsonException::class)
        public fun fromJson(source: JsonValue): InAppMessageButtonInfo {
            val content = source.optMap()

            if (content.require(ID_KEY).requireString().length > MAX_ID_LENGTH) {
                throw JsonException("identifier is too long")
            }

            return InAppMessageButtonInfo(
                identifier = content.requireField(ID_KEY),
                label = content.require(LABEL_KEY).let(InAppMessageTextInfo.Companion::fromJson),
                behavior = content[BEHAVIOR_KEY]?.let(Behavior.Companion::fromJson),
                borderRadius = content.opt(BORDER_RADIUS_KEY).getFloat(0F),
                borderColor = content[BORDER_COLOR_KEY]?.let(InAppMessageColor.Companion::fromJson),
                backgroundColor = content[BACKGROUND_COLOR_KEY]?.let(InAppMessageColor.Companion::fromJson),
                actions = content.optionalField(ACTIONS_KEY)
            )
        }
    }

    internal fun validate(): Boolean {
        if (!label.validate()) {
            UALog.d { "In-app button infos require a nonempty label" }
            return false
        }

        if (identifier.isEmpty() || identifier.length > MAX_ID_LENGTH) {
            UALog.d { "In-app button infos require an identifier between [1, $MAX_ID_LENGTH] characters" }
            return false
        }

        return true
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        ID_KEY to identifier,
        LABEL_KEY to label,
        BEHAVIOR_KEY to behavior,
        BORDER_RADIUS_KEY to borderRadius,
        BORDER_COLOR_KEY to borderColor,
        BACKGROUND_COLOR_KEY to backgroundColor,
        ACTIONS_KEY to actions
    ).toJsonValue()

    override fun toString(): String = toJsonValue().toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InAppMessageButtonInfo

        if (identifier != other.identifier) return false
        if (label != other.label) return false
        if (actions != other.actions) return false
        if (behavior != other.behavior) return false
        if (backgroundColor != other.backgroundColor) return false
        if (borderColor != other.borderColor) return false
        return borderRadius == other.borderRadius
    }

    override fun hashCode(): Int {
        return Objects.hash(identifier, label, actions, behavior, backgroundColor, borderColor, borderRadius)
    }

}
