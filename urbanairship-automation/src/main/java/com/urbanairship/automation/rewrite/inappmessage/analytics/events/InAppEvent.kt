package com.urbanairship.automation.rewrite.inappmessage.analytics.events

import com.urbanairship.json.JsonSerializable

internal interface InAppEvent {
    val name: String
    val data:  JsonSerializable?
}
