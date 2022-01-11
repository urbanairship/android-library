/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.event.FormEvent;
import com.urbanairship.android.layout.event.ReportingEvent;
import com.urbanairship.android.layout.property.FormBehaviorType;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.FormData;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Controller that manages form input views.
 */
public class FormController extends BaseFormController {

    public FormController(
        @NonNull String identifier,
        @Nullable String responseType,
        @NonNull BaseModel view,
        @Nullable FormBehaviorType submitBehavior
    ) {
        super(ViewType.FORM_CONTROLLER, identifier, responseType, view, submitBehavior);
    }

    @NonNull
    public static FormController fromJson(@NonNull JsonMap json) throws JsonException {
        String identifier = identifierFromJson(json);
        BaseModel view = viewFromJson(json);
        FormBehaviorType submitBehavior = submitBehaviorFromJson(json);
        String responseType = json.opt("response_type").getString();

        return new FormController(identifier, responseType, view, submitBehavior);
    }

    @Override
    protected FormEvent.Init getInitEvent() {
        return new FormEvent.Init(getIdentifier(), isFormValid());
    }

    @Override
    protected FormEvent.DataChange getFormDataChangeEvent() {
        return new FormEvent.DataChange(new FormData.Form(getIdentifier(), getResponseType(), getFormData()), isFormValid());
    }

    @Override
    protected ReportingEvent.FormResult getFormResultEvent() {
        return new ReportingEvent.FormResult(new FormData.Form(getIdentifier(), getResponseType(), getFormData()), getFormInfo(), getAttributes());
    }

    @NonNull
    @Override
    protected String getFormType() {
        return "form";
    }

}
