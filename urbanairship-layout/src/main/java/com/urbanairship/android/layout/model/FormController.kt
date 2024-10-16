/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.FormControllerInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.FormBehaviorType
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.FormData

/**
 * Controller that manages form input views.
 */
internal class FormController(
    override val view: AnyModel,
    formState: SharedState<State.Form>,
    parentFormState: SharedState<State.Form>?,
    pagerState: SharedState<State.Pager>?,

    identifier: String,
    responseType: String?,
    submitBehavior: FormBehaviorType?,
    formEnabled: List<EnableBehaviorType>? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseFormController<View>(
    viewType = ViewType.FORM_CONTROLLER,
    formState = formState,
    parentFormState = parentFormState,
    pagerState = pagerState,
    identifier = identifier,
    responseType = responseType,
    submitBehavior = submitBehavior,
    formEnabled = formEnabled,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment,
    properties = properties
) {
    constructor(
        info: FormControllerInfo,
        view: AnyModel,
        formState: SharedState<State.Form>,
        parentFormState: SharedState<State.Form>?,
        pagerState: SharedState<State.Pager>?,
        env: ModelEnvironment,
        props: ModelProperties
    ) : this(
        view = view,
        formState = formState,
        parentFormState = parentFormState,
        pagerState = pagerState,
        identifier = info.identifier,
        responseType = info.responseType,
        submitBehavior = info.submitBehavior,
        formEnabled = info.formEnabled,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        environment = env,
        properties = props
    )

    override fun onCreateView(context: Context, viewEnvironment: ViewEnvironment) =
        view.createView(context, viewEnvironment)

    override fun buildFormData(state: State.Form) =
        FormData.Form(identifier, responseType, state.data.values.toSet())
}
