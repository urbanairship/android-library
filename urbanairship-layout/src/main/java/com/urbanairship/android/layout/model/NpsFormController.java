/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.property.FormBehaviorType;
import com.urbanairship.android.layout.property.ViewType;
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
        @NonNull String scoreIdentifier,
        @NonNull BaseModel view,
        @Nullable FormBehaviorType submitBehavior
    ) {
        super(ViewType.NPS_FORM_CONTROLLER, identifier, view, submitBehavior);

        this.scoreIdentifier = scoreIdentifier;
    }

    @NonNull
    public static NpsFormController fromJson(@NonNull JsonMap json) throws JsonException {
        String identifier = identifierFromJson(json);
        String scoreIdentifier = json.opt("nps_identifier").optString();
        BaseModel view = viewFromJson(json);
        FormBehaviorType submitBehavior = submitBehaviorFromJson(json);

        return new NpsFormController(identifier, scoreIdentifier, view, submitBehavior);
    }

    @NonNull
    public String getScoreIdentifier() {
        return scoreIdentifier;
    }

    @Override
    public boolean onEvent(@NonNull Event event) {
        Logger.verbose("onEvent: %s", event.getType());

        // TODO: switch on NPS form events and consume any that should be internal to the controller and its children.

        // Pass along any other non-nps-form events.
        return super.onEvent(event);
    }
}
