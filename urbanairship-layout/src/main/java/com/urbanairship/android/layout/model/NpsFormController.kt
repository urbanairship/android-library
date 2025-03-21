/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.NpsFormControllerInfo
import com.urbanairship.android.layout.reporting.ThomasFormField

/**
 * Controller that manages NPS form views.
 */
internal class NpsFormController(
    viewInfo: NpsFormControllerInfo,
    override val view: AnyModel,
    formState: ThomasForm,
    parentForm: ThomasForm?,
    pagerState: SharedState<State.Pager>?,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseFormController<View, NpsFormControllerInfo>(
    viewInfo = viewInfo,
    formState = formState,
    parentFormState = parentForm,
    pagerState = pagerState,
    environment = environment,
    properties = properties
) {

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = view.createView(context, viewEnvironment, itemProperties)

    override fun buildFormData(state: State.Form) = ThomasFormField.Nps(
        viewInfo.identifier,
        viewInfo.npsIdentifier,
        viewInfo.responseType,
        state.filteredFields.values.toSet(),
        filedType = ThomasFormField.FiledType.just(emptySet())
    )
}
