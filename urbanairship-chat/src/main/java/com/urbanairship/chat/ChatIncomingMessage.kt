/* Copyright Airship and Contributors */

package com.urbanairship.chat

import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import org.json.JSONArray

/** Data class for decoding incoming message payloads to be sent when opening deeplinks or the OpenChatAction. */
internal data class ChatIncomingMessage(
    val message: String?,
    val url: String?,
    val date: String?,
    val id: String?
) : JsonSerializable {
    companion object {
        private const val KEY_MESSAGE = "msg"
        private const val KEY_URL = "url"
        private const val KEY_DATE = "date"
        private const val KEY_ID = "id"

        private fun fromJsonMap(jsonMap: JsonMap?): ChatIncomingMessage {
            val message = jsonMap?.get(KEY_MESSAGE)?.string
            val url = jsonMap?.get(KEY_URL)?.string
            val date = jsonMap?.get(KEY_DATE)?.string
            val id = jsonMap?.get(KEY_ID)?.string
            return ChatIncomingMessage(
                    message = message,
                    url = url,
                    date = date,
                    id = id
            )
        }

        fun getListFromJSONArrayString(json: String): List<ChatIncomingMessage> {
            val messages: MutableList<ChatIncomingMessage> = mutableListOf<ChatIncomingMessage>()
            val jsonMessages = JSONArray(json)
            for (i in 0 until jsonMessages.length()) {
                var tmpMsg = fromJsonMap(JsonValue.parseString(jsonMessages.getString(i)).optMap())
                messages.plusAssign(tmpMsg)
            }
            return messages.toList()
        }
    }

    override fun toJsonValue(): JsonValue =
            JsonMap.newBuilder()
                    .put(KEY_MESSAGE, message)
                    .put(KEY_URL, url)
                    .put(KEY_DATE, date)
                    .put(KEY_ID, id)
                    .build()
                    .toJsonValue()
}
