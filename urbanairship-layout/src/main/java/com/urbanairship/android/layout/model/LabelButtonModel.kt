/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.info.LabelButtonInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.ButtonClickBehaviorType
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.json.JsonValue

internal class LabelButtonModel(
    identifier: String,
    val label: LabelModel,
    actions: Map<String, JsonValue>,
    clickBehaviors: List<ButtonClickBehaviorType>,
    contentDescription: String? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    environment: ModelEnvironment
) : ButtonModel(
    viewType = ViewType.LABEL_BUTTON,
    identifier = identifier,
    actions = actions,
    clickBehaviors = clickBehaviors,
    contentDescription = contentDescription,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment
) {
    constructor(info: LabelButtonInfo, env: ModelEnvironment) : this(
        identifier = info.identifier,
        label = env.modelProvider.create(info.label, env) as LabelModel,
        actions = info.actions,
        clickBehaviors = info.clickBehaviors,
        contentDescription = info.contentDescription,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors ?: emptyList(),
        environment = env
    )

    override fun reportingDescription(): String =
        contentDescription ?: label.text.ifEmpty { identifier }
}
