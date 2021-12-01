/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.property.FormBehaviorType;
import com.urbanairship.android.layout.property.ViewType;
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
        @NonNull BaseModel view,
        @Nullable FormBehaviorType submitBehavior
    ) {
        super(ViewType.FORM_CONTROLLER, identifier, view, submitBehavior);
    }

    @NonNull
    public static FormController fromJson(@NonNull JsonMap json) throws JsonException {
        String identifier = identifierFromJson(json);
        BaseModel view = viewFromJson(json);
        FormBehaviorType submitBehavior = submitBehaviorFromJson(json);

        return new FormController(identifier, view, submitBehavior);
    }

    @Override
    protected void submitForm() {
        super.submitForm();
        // TODO: submit form
    }
}
