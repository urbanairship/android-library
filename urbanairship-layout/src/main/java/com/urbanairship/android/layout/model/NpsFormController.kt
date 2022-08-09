/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.event.FormEvent
import com.urbanairship.android.layout.event.FormEvent.DataChange
import com.urbanairship.android.layout.event.ReportingEvent.FormResult
import com.urbanairship.android.layout.model.Identifiable.Companion.identifierFromJson
import com.urbanairship.android.layout.property.FormBehaviorType
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.FormData.Nps
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap

/**
 * Controller that manages NPS form views.
 */
internal class NpsFormController(
    identifier: String,
    responseType: String,
    /** The identifier of the score input to use as the NPS score.  */
    private val scoreIdentifier: String,
    view: BaseModel,
    submitBehavior: FormBehaviorType?
) : BaseFormController(
    viewType = ViewType.NPS_FORM_CONTROLLER,
    identifier = identifier,
    responseType = responseType,
    view = view,
    submitBehavior = submitBehavior
) {
    override val formType: String = "nps"

    override val initEvent: FormEvent.Init
        get() = FormEvent.Init(identifier, isFormValid)

    override val formDataChangeEvent: DataChange
        get() = DataChange(
            Nps(identifier, responseType, scoreIdentifier, formData.values),
            isFormValid,
            attributes
        )

    override val formResultEvent: FormResult
        get() = FormResult(
            Nps(identifier, responseType, scoreIdentifier, formData.values),
            formInfo,
            attributes
        )

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): NpsFormController {
            return NpsFormController(
                identifier = identifierFromJson(json),
                responseType = json.opt("response_type").optString(),
                scoreIdentifier = json.opt("nps_identifier").optString(),
                view = viewFromJson(json),
                submitBehavior = submitBehaviorFromJson(json)
            )
        }
    }
}
