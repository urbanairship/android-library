/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.FormControllerInfo
import com.urbanairship.android.layout.reporting.FormData

/**
 * Controller that manages form input views.
 */
internal class FormController(
    viewInfo: FormControllerInfo,
    override val view: AnyModel,
    formState: SharedState<State.Form>,
    parentFormState: SharedState<State.Form>?,
    pagerState: SharedState<State.Pager>?,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseFormController<View, FormControllerInfo>(
    viewInfo = viewInfo,
    formState = formState,
    parentFormState = parentFormState,
    pagerState = pagerState,
    environment = environment,
    properties = properties
) {

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = view.createView(context, viewEnvironment, itemProperties)

    override fun buildFormData(state: State.Form) =
        FormData.Form(viewInfo.identifier, viewInfo.responseType, state.data.values.toSet())
}
