/* Copyright Airship and Contributors */

package com.urbanairship.chat

import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

/** Data class for building routing payloads to be sent along with chat messages. */
data class ChatRouting(
    /**
     * Value for routing a conversation to a specific agent.
     */
    val agent: String?
) : JsonSerializable {
    companion object {
        private const val KEY_AGENT = "agent"

        fun fromJsonMap(jsonMap: JsonMap?): ChatRouting {
            val agent = jsonMap?.get(KEY_AGENT)?.string
            return ChatRouting(
                    agent = agent
            )
        }
    }

    override fun toJsonValue(): JsonValue =
            JsonMap.newBuilder()
                    .put(KEY_AGENT, agent)
                    .build()
                    .toJsonValue()
}
