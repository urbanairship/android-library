/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.info.ImageButtonInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.ButtonClickBehaviorType
import com.urbanairship.android.layout.property.ButtonEnableBehaviorType
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.Image
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.json.JsonValue

internal class ImageButtonModel(
    identifier: String,
    val image: Image,
    actions: Map<String, JsonValue>,
    buttonClickBehaviors: List<ButtonClickBehaviorType>,
    enableBehaviors: List<ButtonEnableBehaviorType>,
    contentDescription: String? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    environment: ModelEnvironment
) : ButtonModel(
    viewType = ViewType.IMAGE_BUTTON,
    identifier = identifier,
    actions = actions,
    clickBehaviors = buttonClickBehaviors,
    enableBehaviors = enableBehaviors,
    contentDescription = contentDescription,
    backgroundColor = backgroundColor,
    border = border,
    environment = environment
) {
    constructor(info: ImageButtonInfo, env: ModelEnvironment) : this(
        identifier = info.identifier,
        image = info.image,
        actions = info.actions,
        buttonClickBehaviors = info.clickBehaviors,
        enableBehaviors = info.enableBehaviors,
        contentDescription = info.contentDescription,
        backgroundColor = info.backgroundColor,
        border = info.border,
        environment = env
    )
    override fun reportingDescription(): String = contentDescription ?: identifier
}
