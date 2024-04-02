package com.urbanairship.automation.rewrite.inappmessage.analytics

import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

internal enum class InAppEventSource(val json: String) : JsonSerializable {
    AIRSHIP("urban-airship"),
    APP_DEFINED("app-defined");

    override fun toJsonValue(): JsonValue = JsonValue.wrap(json)
}
