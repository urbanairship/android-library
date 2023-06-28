/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ImageButtonInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.ButtonClickBehaviorType
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.Image
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.view.ImageButtonView
import com.urbanairship.json.JsonValue

internal class ImageButtonModel(
    identifier: String,
    val image: Image,
    actions: Map<String, JsonValue>? = null,
    buttonClickBehaviors: List<ButtonClickBehaviorType>,
    contentDescription: String? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    reportingMetadata: JsonValue? = null,
    formState: SharedState<State.Form>?,
    pagerState: SharedState<State.Pager>?,
    environment: ModelEnvironment,
    properties: ModelProperties
) : ButtonModel<ImageButtonView>(
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
    reportingMetadata = reportingMetadata,
    formState = formState,
    pagerState = pagerState,
    environment = environment,
    properties = properties
) {
    constructor(
        info: ImageButtonInfo,
        formState: SharedState<State.Form>?,
        pagerState: SharedState<State.Pager>?,
        env: ModelEnvironment,
        props: ModelProperties
    ) : this(
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
        reportingMetadata = info.reportingMetadata,
        formState = formState,
        pagerState = pagerState,
        environment = env,
        properties = props
    )

    override val reportingDescription: String = contentDescription ?: identifier

    override fun onCreateView(context: Context, viewEnvironment: ViewEnvironment) =
        ImageButtonView(context, this, viewEnvironment).apply {
            id = viewId
        }
}
