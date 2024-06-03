/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics

import com.urbanairship.UALog
import com.urbanairship.automation.engine.ScheduleStoreInterface
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField

internal data class MessageDisplayHistory(
    val lastImpression: LastImpression? = null,
    val lastDisplay: LastDisplay? = null
) : JsonSerializable {

    internal data class LastImpression(
        val date: Long,
        val triggerSessionId: String
    ) : JsonSerializable {
        companion object {
            private const val KEY_DATE = "date"
            private const val KEY_TRIGGER_SESSION_ID = "trigger_session_id"

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): LastImpression {
                val json = value.requireMap()
                return LastImpression(
                    date = json.requireField(KEY_DATE),
                    triggerSessionId = json.requireField(KEY_TRIGGER_SESSION_ID)
                )
            }
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            KEY_DATE to date,
            KEY_TRIGGER_SESSION_ID to triggerSessionId
        ).toJsonValue()
    }

    internal data class LastDisplay(
        val triggerSessionId: String
    ) : JsonSerializable {
        companion object {
            private const val KEY_TRIGGER_SESSION_ID = "trigger_session_id"

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): LastDisplay {
                val json = value.requireMap()
                return LastDisplay(
                    triggerSessionId = json.requireField(KEY_TRIGGER_SESSION_ID)
                )
            }
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            KEY_TRIGGER_SESSION_ID to triggerSessionId
        ).toJsonValue()
    }

    companion object {
        private const val KEY_LAST_IMPRESSION = "last_impression"
        private const val KEY_LAST_DISPLAY = "last_display"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): MessageDisplayHistory {
            val json = value.requireMap()
            return MessageDisplayHistory(
                lastImpression = LastImpression.fromJson(json.require(KEY_LAST_IMPRESSION)),
                lastDisplay = LastDisplay.fromJson(json.require(KEY_LAST_DISPLAY))
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        KEY_LAST_IMPRESSION to lastImpression,
        KEY_LAST_DISPLAY to lastDisplay
    ).toJsonValue()
}

internal interface MessageDisplayHistoryStoreInterface {
    suspend fun set(history: MessageDisplayHistory, scheduleID: String)
    suspend fun get(scheduleID: String): MessageDisplayHistory
}

internal class MessageDisplayHistoryStore(
    val store: ScheduleStoreInterface,
) : MessageDisplayHistoryStoreInterface {

    override suspend fun set(history: MessageDisplayHistory, scheduleID: String){
        store.updateSchedule(scheduleID) { data ->
            data.associatedData = history.toJsonValue()
            data
        }
    }

    override suspend fun get(scheduleID: String): MessageDisplayHistory {
        val data = store.getSchedule(scheduleID)?.associatedData
            ?: return MessageDisplayHistory()

        return data.let {
            try {
                val history = MessageDisplayHistory.fromJson(data)
                history
            } catch (ex: JsonException) {
                UALog.e(ex) { "failed to retrieve message history" }
                MessageDisplayHistory()
            }
        }
    }

}
