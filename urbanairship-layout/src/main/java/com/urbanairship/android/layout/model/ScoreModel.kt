/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.event.Event.ViewAttachedToWindow
import com.urbanairship.android.layout.event.FormEvent.DataChange
import com.urbanairship.android.layout.event.ScoreEvent
import com.urbanairship.android.layout.info.ScoreInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.ScoreStyle
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.AttributeName
import com.urbanairship.android.layout.reporting.FormData.Score
import com.urbanairship.android.layout.reporting.LayoutData
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
    val style: ScoreStyle,
    override val identifier: String,
    override val isRequired: Boolean = false,
    override val contentDescription: String? = null,
    private val attributeName: AttributeName? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    environment: ModelEnvironment
) : BaseModel(
    viewType = ViewType.SCORE,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment
), Identifiable, Accessible, Validatable {

    constructor(info: ScoreInfo, env: ModelEnvironment) : this(
        style = info.style,
        identifier = info.identifier,
        isRequired = info.isRequired,
        contentDescription = info.contentDescription,
        attributeName = info.attributeName,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        environment = env
    )

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
}
