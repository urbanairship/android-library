package com.urbanairship.iam.analytics.events

import com.urbanairship.json.JsonSerializable

internal class InAppDisplayEvent : InAppEvent {
    override val name: String = "in_app_display"
    override val data: JsonSerializable? = null
}
