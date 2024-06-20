package com.urbanairship.liveupdate

/** Live Update event types. */
public enum class LiveUpdateEvent {
    /** The Live Update was started. */
    START,
    /** The Live Update was ended. */
    END,
    /** The Live Update content was updated. */
    UPDATE;

    internal companion object {
        @Throws(IllegalArgumentException::class)
        fun from(value: String): LiveUpdateEvent {
            for (event in entries) {
                if (event.name.equals(value, ignoreCase = true)) {
                    return event
                }
            }
            throw IllegalArgumentException("Invalid Live Update event: $value")
        }
    }
}
