/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.NpsFormControllerInfo
import com.urbanairship.android.layout.reporting.FormData

/**
 * Controller that manages NPS form views.
 */
internal class NpsFormController(
    viewInfo: NpsFormControllerInfo,
    override val view: AnyModel,
    formState: SharedState<State.Form>,
    parentFormState: SharedState<State.Form>?,
    pagerState: SharedState<State.Pager>?,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseFormController<View, NpsFormControllerInfo>(
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

    override fun buildFormData(state: State.Form) = FormData.Nps(
        viewInfo.identifier,
        viewInfo.npsIdentifier,
        viewInfo.responseType,
        state.data.values.toSet()
    )
}
