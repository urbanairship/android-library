/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.model.Accessible.Companion.contentDescriptionFromJson
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.TextAppearance
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap

internal class LabelModel(
    val text: String,
    val textAppearance: TextAppearance,
    override val contentDescription: String?,
    backgroundColor: Color?,
    border: Border?
) : BaseModel(ViewType.LABEL, backgroundColor, border), Accessible {

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): LabelModel {
            val textAppearanceJson = json.opt("text_appearance").optMap()
            return LabelModel(
                text = json.opt("text").optString(),
                textAppearance = TextAppearance.fromJson(textAppearanceJson),
                contentDescription = contentDescriptionFromJson(json),
                backgroundColor = backgroundColorFromJson(json),
                border = borderFromJson(json)
            )
        }
    }
}
