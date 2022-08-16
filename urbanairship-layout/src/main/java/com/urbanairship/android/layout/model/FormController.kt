/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.event.FormEvent
import com.urbanairship.android.layout.event.FormEvent.DataChange
import com.urbanairship.android.layout.event.ReportingEvent.FormResult
import com.urbanairship.android.layout.info.FormControllerInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.FormBehaviorType
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.FormData

/**
 * Controller that manages form input views.
 */
internal class FormController(
    final override val view: BaseModel,
    identifier: String,
    responseType: String?,
    submitBehavior: FormBehaviorType?,
    backgroundColor: Color? = null,
    border: Border? = null,
    environment: ModelEnvironment
) : BaseFormController<FormControllerInfo>(
    viewType = ViewType.FORM_CONTROLLER,
    identifier = identifier,
    responseType = responseType,
    submitBehavior = submitBehavior,
    backgroundColor = backgroundColor,
    border = border,
    environment = environment
) {
    constructor(info: FormControllerInfo, env: ModelEnvironment) : this(
        view = env.modelProvider.create(info.view, env),
        identifier = info.identifier,
        responseType = info.responseType,
        submitBehavior = info.submitBehavior,
        backgroundColor = info.backgroundColor,
        border = info.border,
        environment = env
    )

    override val children: List<BaseModel> = listOf(view)

    override val formType: String = "form"

    override val initEvent: FormEvent.Init
        get() = FormEvent.Init(identifier, isFormValid)

    override val formDataChangeEvent: DataChange
        get() = DataChange(
            FormData.Form(identifier, responseType, formData.values),
            isFormValid
        )

    override val formResultEvent: FormResult
        get() = FormResult(
            FormData.Form(identifier, responseType, formData.values),
            formInfo,
            attributes
        )

    init {
        view.addListener(this)
    }
}
