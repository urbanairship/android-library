/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.NpsFormControllerInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.FormBehaviorType
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.FormData

/**
 * Controller that manages NPS form views.
 */
internal class NpsFormController(
    override val view: AnyModel,
    private val npsIdentifier: String,
    identifier: String,
    responseType: String?,
    submitBehavior: FormBehaviorType?,
    formEnabled: List<EnableBehaviorType>? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    formState: SharedState<State.Form>,
    parentFormState: SharedState<State.Form>?,
    pagerState: SharedState<State.Pager>?,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseFormController<View>(
    ViewType.NPS_FORM_CONTROLLER,
    identifier = identifier,
    responseType = responseType,
    submitBehavior = submitBehavior,
    formEnabled = formEnabled,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    formState = formState,
    parentFormState = parentFormState,
    pagerState = pagerState,
    environment = environment,
    properties = properties
) {
    constructor(
        info: NpsFormControllerInfo,
        view: AnyModel,
        formState: SharedState<State.Form>,
        parentFormState: SharedState<State.Form>?,
        pagerState: SharedState<State.Pager>?,
        env: ModelEnvironment,
        props: ModelProperties
    ) : this(
        view = view,
        npsIdentifier = info.npsIdentifier,
        identifier = info.identifier,
        responseType = info.responseType,
        submitBehavior = info.submitBehavior,
        formEnabled = info.formEnabled,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        formState = formState,
        parentFormState = parentFormState,
        pagerState = pagerState,
        environment = env,
        properties = props
    )

    override fun onCreateView(context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?) =
        view.createView(context, viewEnvironment, itemProperties)

    override fun buildFormData(state: State.Form) =
        FormData.Nps(identifier, npsIdentifier, responseType, state.data.values.toSet())
}
