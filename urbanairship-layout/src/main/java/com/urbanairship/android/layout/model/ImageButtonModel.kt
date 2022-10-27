/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.info.ImageButtonInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.ButtonClickBehaviorType
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.Image
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.json.JsonValue

internal class ImageButtonModel(
    identifier: String,
    val image: Image,
    actions: Map<String, JsonValue>,
    buttonClickBehaviors: List<ButtonClickBehaviorType>,
    contentDescription: String? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    environment: ModelEnvironment
) : ButtonModel(
    viewType = ViewType.IMAGE_BUTTON,
    identifier = identifier,
    actions = actions,
    clickBehaviors = buttonClickBehaviors,
    contentDescription = contentDescription,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment
) {
    constructor(info: ImageButtonInfo, env: ModelEnvironment) : this(
        identifier = info.identifier,
        image = info.image,
        actions = info.actions,
        buttonClickBehaviors = info.clickBehaviors,
        contentDescription = info.contentDescription,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        environment = env
    )
    override fun reportingDescription(): String = contentDescription ?: identifier
}
