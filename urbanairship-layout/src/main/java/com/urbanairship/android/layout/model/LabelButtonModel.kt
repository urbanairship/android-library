/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.LabelButtonInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.ButtonClickBehaviorType
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.view.LabelButtonView
import com.urbanairship.json.JsonValue

internal class LabelButtonModel(
    identifier: String,
    val label: LabelModel,
    actions: Map<String, JsonValue>? = null,
    clickBehaviors: List<ButtonClickBehaviorType>,
    contentDescription: String? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    formState: SharedState<State.Form>?,
    pagerState: SharedState<State.Pager>?,
    environment: ModelEnvironment,
    properties: ModelProperties
) : ButtonModel<LabelButtonView>(
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
    formState = formState,
    pagerState = pagerState,
    environment = environment,
    properties = properties
) {
    constructor(
        info: LabelButtonInfo,
        label: LabelModel,
        formState: SharedState<State.Form>?,
        pagerState: SharedState<State.Pager>?,
        env: ModelEnvironment,
        props: ModelProperties
    ) : this(
        identifier = info.identifier,
        label = label,
        actions = info.actions,
        clickBehaviors = info.clickBehaviors,
        contentDescription = info.contentDescription,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors ?: emptyList(),
        formState = formState,
        pagerState = pagerState,
        environment = env,
        properties = props
    )

    override val reportingDescription: String =
        contentDescription ?: label.text.ifEmpty { identifier }

    override fun onCreateView(context: Context, viewEnvironment: ViewEnvironment) =
        LabelButtonView(context, this).apply {
            id = viewId
        }
}
