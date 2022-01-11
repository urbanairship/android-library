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
 * Controller that manages NPS form views.
 */
public class NpsFormController extends BaseFormController {
    /** The identifier of the score input to use as the NPS score. */
    @NonNull
    private final String scoreIdentifier;

    public NpsFormController(
        @NonNull String identifier,
        @NonNull String responseType,
        @NonNull String scoreIdentifier,
        @NonNull BaseModel view,
        @Nullable FormBehaviorType submitBehavior
    ) {
        super(ViewType.NPS_FORM_CONTROLLER, identifier, responseType, view, submitBehavior);

        this.scoreIdentifier = scoreIdentifier;
    }

    @NonNull
    public static NpsFormController fromJson(@NonNull JsonMap json) throws JsonException {
        String identifier = identifierFromJson(json);
        String scoreIdentifier = json.opt("nps_identifier").optString();
        String responseType = json.opt("response_type").getString();
        BaseModel view = viewFromJson(json);
        FormBehaviorType submitBehavior = submitBehaviorFromJson(json);

        return new NpsFormController(identifier, responseType, scoreIdentifier, view, submitBehavior);
    }

    @NonNull
    public String getScoreIdentifier() {
        return scoreIdentifier;
    }

    @Override
    protected ReportingEvent.FormResult getFormResultEvent() {
        return new ReportingEvent.FormResult(new FormData.Nps(getIdentifier(), getResponseType(), getScoreIdentifier(), getFormData()), getFormInfo(), getAttributes());
    }

    @Override
    protected FormEvent.Init getInitEvent() {
        return new FormEvent.Init(getIdentifier(), isFormValid());
    }

    @Override
    protected FormEvent.DataChange getFormDataChangeEvent() {
        return new FormEvent.DataChange(new FormData.Nps(getIdentifier(), getResponseType(), getScoreIdentifier(), getFormData()), isFormValid(), getAttributes());
    }

    @NonNull
    @Override
    protected String getFormType() {
        return "nps";
    }
}
