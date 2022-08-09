/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.event.FormEvent
import com.urbanairship.android.layout.event.FormEvent.DataChange
import com.urbanairship.android.layout.event.ReportingEvent.FormResult
import com.urbanairship.android.layout.model.Identifiable.Companion.identifierFromJson
import com.urbanairship.android.layout.property.FormBehaviorType
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap

/**
 * Controller that manages form input views.
 */
internal class FormController(
    identifier: String,
    responseType: String?,
    view: BaseModel,
    submitBehavior: FormBehaviorType?
) : BaseFormController(ViewType.FORM_CONTROLLER, identifier, responseType, view, submitBehavior) {

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

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): FormController =
            FormController(
                identifier = identifierFromJson(json),
                responseType = json.opt("response_type").string,
                view = viewFromJson(json),
                submitBehavior = submitBehaviorFromJson(json)
            )
    }
}
