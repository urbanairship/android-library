package com.urbanairship.liveupdate

import com.urbanairship.json.JsonMap
import com.urbanairship.liveupdate.data.LiveUpdateContent
import com.urbanairship.liveupdate.data.LiveUpdateState
import java.util.Objects

/**
 * Information about a Live Update.
 *
 * @property name The Live Update name.
 * @property type The Live Update type.
 * @property content The Live Update content.
 * @property lastContentUpdateTime The timestamp of the last UPDATE event for this Live Update.
 * @property lastStateChangeTime The timestamp of the last START or END event for this Live Update.
 * @property dismissalTime The optional dismissal timestamp for this Live Update.
 */
public class LiveUpdate(
    public val name: String,
    public val type: String,
    public val content: JsonMap,
    public val lastContentUpdateTime: Long,
    public val lastStateChangeTime: Long,
    public val dismissalTime: Long? = null,
) {
    internal companion object {
        internal fun from(state: LiveUpdateState, content: LiveUpdateContent) = LiveUpdate(
            name = state.name,
            type = state.type,
            content = content.content,
            lastContentUpdateTime = content.timestamp,
            lastStateChangeTime = state.timestamp,
            dismissalTime = state.dismissalDate
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LiveUpdate

        if (name != other.name) return false
        if (type != other.type) return false
        if (content != other.content) return false
        if (lastContentUpdateTime != other.lastContentUpdateTime) return false
        if (lastStateChangeTime != other.lastStateChangeTime) return false
        if (dismissalTime != other.dismissalTime) return false

        return true
    }

    override fun hashCode(): Int = Objects.hash(
        name, type, content, lastContentUpdateTime, lastStateChangeTime, dismissalTime
    )
}
