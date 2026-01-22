/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics

import com.urbanairship.android.layout.analytics.LayoutEventMessageId
import com.urbanairship.android.layout.analytics.LayoutEventContext
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

internal data class InAppCustomEventContext(
    val id: LayoutEventMessageId,
    val context: LayoutEventContext?
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
