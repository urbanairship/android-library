/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics

import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

internal data class InAppCustomEventContext(
    val id: InAppEventMessageId,
    val context: InAppEventContext?
): JsonSerializable {

    companion object {
        private const val ID_KEY = "id"
        private const val CONTEXT_KEY = "context"
    }
    override fun toJsonValue(): JsonValue = jsonMapOf(
        ID_KEY to id,
        CONTEXT_KEY to context
    ).toJsonValue()
}
