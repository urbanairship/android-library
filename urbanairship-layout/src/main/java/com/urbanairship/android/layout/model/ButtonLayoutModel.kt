/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ButtonLayoutInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.ButtonClickBehaviorType
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.TapEffect
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.view.ButtonLayoutView
import com.urbanairship.json.JsonValue

internal class ButtonLayoutModel(
    val view: AnyModel,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    identifier: String,
    clickBehaviors: List<ButtonClickBehaviorType>,
    actions: Map<String, JsonValue>?,
    tapEffect: TapEffect,
    contentDescription: String? = null,
    reportingMetadata: JsonValue?,
    formState: SharedState<State.Form>?,
    pagerState: SharedState<State.Pager>?,
    environment: ModelEnvironment,
    properties: ModelProperties,
) : ButtonModel<ButtonLayoutView>(
    viewType = ViewType.BUTTON_LAYOUT,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    identifier = identifier,
    clickBehaviors = clickBehaviors,
    actions = actions,
    tapEffect = tapEffect,
    contentDescription = contentDescription,
    localizedContentDescription = null,
    reportingMetadata = reportingMetadata,
    formState = formState,
    pagerState = pagerState,
    environment = environment,
    properties = properties,
) {
    constructor(
        info: ButtonLayoutInfo,
        formState: SharedState<State.Form>?,
        pagerState: SharedState<State.Pager>?,
        view: AnyModel,
        env: ModelEnvironment,
        props: ModelProperties
    ) : this(
        view = view,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        identifier = info.identifier,
        clickBehaviors = info.clickBehaviors,
        actions = info.actions,
        tapEffect = info.tapEffect,
        contentDescription = info.contentDescription,
        reportingMetadata = info.reportingMetadata,
        formState = formState,
        pagerState = pagerState,
        environment = env,
        properties = props,
    )

    override val reportingDescription: String = contentDescription ?: identifier

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = ButtonLayoutView(context, this, viewEnvironment).apply {
        id = viewId
    }
}
