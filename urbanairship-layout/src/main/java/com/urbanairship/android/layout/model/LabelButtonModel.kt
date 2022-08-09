/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.model.Accessible.Companion.contentDescriptionFromJson
import com.urbanairship.android.layout.model.BaseModel.Companion.backgroundColorFromJson
import com.urbanairship.android.layout.model.ButtonModel.Companion.actionsFromJson
import com.urbanairship.android.layout.model.ButtonModel.Companion.buttonClickBehaviorsFromJson
import com.urbanairship.android.layout.model.ButtonModel.Companion.buttonEnableBehaviorsFromJson
import com.urbanairship.android.layout.model.Identifiable.Companion.identifierFromJson
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.ButtonClickBehaviorType
import com.urbanairship.android.layout.property.ButtonEnableBehaviorType
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

internal class LabelButtonModel(
    id: String,
    val label: LabelModel,
    clickBehaviors: List<ButtonClickBehaviorType>,
    actions: Map<String, JsonValue>,
    enableBehaviors: List<ButtonEnableBehaviorType>,
    backgroundColor: Color?,
    border: Border?,
    contentDescription: String?
) : ButtonModel(
    type = ViewType.LABEL_BUTTON,
    identifier = id,
    buttonClickBehaviors = clickBehaviors,
    actions = actions,
    enableBehaviors = enableBehaviors,
    backgroundColor = backgroundColor,
    border = border,
    contentDescription = contentDescription
) {

    override fun reportingDescription(): String =
        contentDescription ?: label.text.ifEmpty { identifier }

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): LabelButtonModel {
            val labelJson = json.opt("label").optMap()
            return LabelButtonModel(
                id = identifierFromJson(json),
                label = LabelModel.fromJson(labelJson),
                clickBehaviors = buttonClickBehaviorsFromJson(json),
                actions = actionsFromJson(json),
                enableBehaviors = buttonEnableBehaviorsFromJson(json),
                backgroundColor = backgroundColorFromJson(json),
                border = borderFromJson(json),
                contentDescription = contentDescriptionFromJson(json)
            )
        }
    }
}
