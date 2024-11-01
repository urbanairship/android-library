/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.LabelButtonInfo
import com.urbanairship.android.layout.info.LocalizedContentDescription
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.ButtonClickBehaviorType
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.TapEffect
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.util.resolveContentDescription
import com.urbanairship.android.layout.view.LabelButtonView
import com.urbanairship.json.JsonValue

internal class LabelButtonModel(
    identifier: String,
    val label: LabelModel,
    actions: Map<String, JsonValue>? = null,
    clickBehaviors: List<ButtonClickBehaviorType>,
    tapEffect: TapEffect,
    private val contentDescription: String? = null,
    private val localizedContentDescription: LocalizedContentDescription? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    reportingMetadata: JsonValue? = null,
    formState: SharedState<State.Form>?,
    pagerState: SharedState<State.Pager>?,
    environment: ModelEnvironment,
    properties: ModelProperties,
) : ButtonModel<LabelButtonView>(
    viewType = ViewType.LABEL_BUTTON,
    identifier = identifier,
    actions = actions,
    clickBehaviors = clickBehaviors,
    tapEffect = tapEffect,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    reportingMetadata = reportingMetadata,
    formState = formState,
    pagerState = pagerState,
    environment = environment,
    properties = properties,
) {
    constructor(
        info: LabelButtonInfo,
        label: LabelModel,
        formState: SharedState<State.Form>?,
        pagerState: SharedState<State.Pager>?,
        env: ModelEnvironment,
        props: ModelProperties,
    ) : this(
        identifier = info.identifier,
        label = label,
        actions = info.actions,
        clickBehaviors = info.clickBehaviors,
        tapEffect = info.tapEffect,
        contentDescription = info.contentDescription,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors ?: emptyList(),
        reportingMetadata = info.reportingMetadata,
        formState = formState,
        pagerState = pagerState,
        environment = env,
        properties = props,
    )

    override fun contentDescription(context: Context): String? {
        return context.resolveContentDescription(contentDescription, localizedContentDescription)
    }

    override fun reportingDescription(context: Context): String {
        return context.resolveContentDescription(contentDescription, localizedContentDescription) ?: identifier
    }

    override fun onCreateView(context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?) =
        LabelButtonView(context, this).apply {
            id = viewId
        }
}
