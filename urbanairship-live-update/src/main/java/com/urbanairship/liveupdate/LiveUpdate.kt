package com.urbanairship.liveupdate

import com.urbanairship.json.JsonMap
import com.urbanairship.liveupdate.data.LiveUpdateContent
import com.urbanairship.liveupdate.data.LiveUpdateState

/**
 * Information about a Live Update.
 */
public data class LiveUpdate(
    /**
     * The Live Update name.
     */
    public val name: String,

    /**
     * The Live Update type.
     */
    public val type: String,

    /**
     * The Live Update content.
     */
    public val content: JsonMap,

    /**
     * The timestamp of the last UPDATE event for this Live Update.
     */
    public val lastContentUpdateTime: Long,

    /**
     * The timestamp of the last START or END event for this Live Update.
     */
    public val lastStateChangeTime: Long,

    /**
     * The optional dismissal timestamp for this Live Update.
     */
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
}
