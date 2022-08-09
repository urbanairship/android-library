/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.event.Event.ViewAttachedToWindow
import com.urbanairship.android.layout.event.FormEvent.DataChange
import com.urbanairship.android.layout.event.ScoreEvent
import com.urbanairship.android.layout.model.Accessible.Companion.contentDescriptionFromJson
import com.urbanairship.android.layout.model.Identifiable.Companion.identifierFromJson
import com.urbanairship.android.layout.model.Validatable.Companion.requiredFromJson
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ScoreStyle
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.AttributeName
import com.urbanairship.android.layout.reporting.AttributeName.attributeNameFromJson
import com.urbanairship.android.layout.reporting.FormData.Score
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

/**
 * Model for Score views.
 *
 * Must be a descendant of `FormController` or `NpsFormController`.
 *
 * Numbers will have an equal height/width and will scale to fill the container.
 * With auto width, the container will be up to 320dp. The top-level background and border apply to the entire widget.
 */
internal class ScoreModel(
    override val identifier: String,
    val style: ScoreStyle,
    private val attributeName: AttributeName?,
    override val isRequired: Boolean,
    override val contentDescription: String?,
    backgroundColor: Color?,
    border: Border?
) : BaseModel(ViewType.SCORE, backgroundColor, border), Identifiable, Accessible, Validatable {

    final var selectedScore: Int? = null
        private set

    override val isValid: Boolean
        get() = selectedScore?.let { it > -1 } == true || !isRequired

    fun onConfigured() {
        bubbleEvent(ScoreEvent.Init(identifier, isValid), LayoutData.empty())
    }

    fun onAttachedToWindow() {
        bubbleEvent(ViewAttachedToWindow(this), LayoutData.empty())
    }

    fun onScoreChange(score: Int) {
        selectedScore = score
        bubbleEvent(
            DataChange(
                Score(identifier, score),
                isValid,
                attributeName,
                JsonValue.wrap(score)
            ),
            LayoutData.empty()
        )
    }

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): ScoreModel {
            val styleJson = json.opt("style").optMap()
            return ScoreModel(
                identifier = identifierFromJson(json),
                style = ScoreStyle.fromJson(styleJson),
                attributeName = attributeNameFromJson(json),
                isRequired = requiredFromJson(json),
                contentDescription = contentDescriptionFromJson(json),
                backgroundColor = backgroundColorFromJson(json),
                border = borderFromJson(json)
            )
        }
    }
}
