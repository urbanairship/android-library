/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.FormEvent;
import com.urbanairship.android.layout.property.FormBehaviorType;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.FormData;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Base model for top-level form controllers.
 *
 * @see FormController
 * @see NpsFormController
 */
public abstract class BaseFormController extends LayoutModel implements Identifiable {
    @NonNull
    private final String identifier;
    @NonNull
    private final BaseModel view;
    @Nullable
    private final FormBehaviorType submitBehavior;

    @NonNull
    private final Map<String, FormData<?>> formData = new HashMap<>();

    @NonNull
    private final Map<String, Boolean> inputValidity = new HashMap<>();

    public BaseFormController(
        @NonNull ViewType viewType,
        @NonNull String identifier,
        @NonNull BaseModel view,
        @Nullable FormBehaviorType submitBehavior
    ) {
        super(viewType, null, null);

        this.identifier = identifier;
        this.view = view;
        this.submitBehavior = submitBehavior;

        view.addListener(this);
    }

    @NonNull
    @Override
    public String getIdentifier() {
        return identifier;
    }

    @NonNull
    public BaseModel getView() {
        return view;
    }

    @Nullable
    public FormBehaviorType getSubmitBehavior() {
        return submitBehavior;
    }

    @Override
    public List<BaseModel> getChildren() {
        return Collections.singletonList(view);
    }

    protected static String identifierFromJson(@NonNull JsonMap json) throws JsonException {
        return Identifiable.identifierFromJson(json);
    }

    protected static BaseModel viewFromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap viewJson = json.opt("view").optMap();
        return Thomas.model(viewJson);
    }

    protected static FormBehaviorType submitBehaviorFromJson(@NonNull JsonMap json) {
        String submitString = json.opt("submit").optString();
        return FormBehaviorType.from(submitString);
    }

    @Override
    public boolean onEvent(@NonNull Event event) {
        Logger.verbose("onEvent: %s", event.getType());

        switch (event.getType()) {
            case FORM_INPUT_INIT:
                reduceFormInputInit((FormEvent.InputInit) event);
                return true;
            case FORM_DATA_CHANGE:
                reduceFormDataChange((FormEvent.DataChange) event);
                return true;
            case BUTTON_BEHAVIOR_FORM_SUBMIT:
                submitForm();
                return true;
        }

        return super.onEvent(event);
    }

    protected void submitForm() {
        Logger.debug("FormData = %s", JsonValue.wrapOpt(formData).toString());
    }

    private void reduceFormInputInit(FormEvent.InputInit init) {
        inputValidity.put(init.getIdentifier(), init.isValid());
        trickleValidationUpdate();
    }

    private void reduceFormDataChange(FormEvent.DataChange data) {
        String identifier = data.getIdentifier();
        boolean isValid = data.isValid();
        inputValidity.put(identifier, isValid);

        if (isValid) {
            formData.put(identifier, data.getValue());
        } else {
            formData.remove(identifier);
        }

        trickleValidationUpdate();
    }

    private void trickleValidationUpdate() {
        trickleEvent(new FormEvent.ValidationUpdate(isFormValid()));
    }

    private boolean isFormValid() {
        for (Map.Entry<String, Boolean> validity : inputValidity.entrySet()) {
            if (!validity.getValue()) {
                return false;
            }
        }
        return true;
    }
}
