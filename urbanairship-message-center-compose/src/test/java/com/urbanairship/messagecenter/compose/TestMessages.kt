/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter.compose

import com.urbanairship.json.JsonValue
import com.urbanairship.messagecenter.Message
import java.time.Instant

internal fun createMessage(
    id: String,
    contentType: Message.ContentType = Message.ContentType.Html,
    bodyUrl: String = "https://go.urbanairship.com/api/user/tests/messages/$id/body/",
    expirationDate: Instant? = null,
    extras: Map<String, String?>? = null,
): Message = Message(
    id = id,
    title = "$id title",
    bodyUrl = bodyUrl,
    sentDate = Instant.parse("2026-01-01T00:00:00Z"),
    expirationDate = expirationDate,
    isUnread = true,
    extras = extras,
    contentType = contentType,
    messageUrl = "https://go.urbanairship.com/api/user/tests/messages/$id",
    reporting = JsonValue.wrap(id),
    rawMessageJson = JsonValue.NULL,
    isDeletedClient = false,
)
