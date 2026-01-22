package com.urbanairship.android.layout.analytics

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public enum class LayoutEventSource(internal val json: String) : JsonSerializable {
    AIRSHIP("urban-airship"),
    APP_DEFINED("app-defined");

    override fun toJsonValue(): JsonValue = JsonValue.Companion.wrap(json)
}
