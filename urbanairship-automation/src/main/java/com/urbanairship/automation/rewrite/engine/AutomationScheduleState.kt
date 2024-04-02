package com.urbanairship.automation.rewrite.engine

internal enum class AutomationScheduleState(val json: String) {
    IDLE("idle"),
    TRIGGERED("triggered"),
    PREPARED("prepared"),
    EXECUTING("executing"),
    // interval
    PAUSED("paused"),
    // waiting to be cleaned up after grace period
    FINISHED("finished");

    companion object {

        @Throws(IllegalArgumentException::class)
        fun fromString(value: String): AutomationScheduleState {
            return entries.firstOrNull { it.json == value } ?: throw IllegalArgumentException()
        }
    }

    override fun toString(): String = json
}
