/* Copyright Airship and Contributors */

package com.urbanairship.automation.engine

internal enum class AutomationScheduleState(internal val json: String) {
    IDLE("idle"),
    TRIGGERED("triggered"),
    PREPARED("prepared"),
    EXECUTING("executing"),
    // interval
    PAUSED("paused"),
    // waiting to be cleaned up after grace period
    FINISHED("finished");

    internal companion object {

        @Throws(IllegalArgumentException::class)
        fun fromString(value: String): AutomationScheduleState {
            return entries.firstOrNull { it.json == value } ?: throw IllegalArgumentException()
        }
    }

    override fun toString(): String = json
}
