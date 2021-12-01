/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.FormEvent;
import com.urbanairship.android.layout.event.RadioEvent;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.FormData;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * Controller for radio inputs.
 */
public class RadioInputController extends LayoutModel implements Identifiable, Accessible, Validatable {
    @NonNull
    private final String identifier;
    @NonNull
    private final BaseModel view;
    private final boolean isRequired;
    @Nullable
    private final String contentDescription;

    @NonNull
    private final List<RadioInputModel> radioInputs = new ArrayList<>();

    @Nullable
    private String selectedValue = null;

    public RadioInputController(
        @NonNull String identifier,
        @NonNull BaseModel view,
        boolean isRequired,
        @Nullable String contentDescription
    ) {
        super(ViewType.RADIO_INPUT_CONTROLLER, null, null);

        this.identifier = identifier;
        this.view = view;
        this.isRequired = isRequired;
        this.contentDescription = contentDescription;

        view.addListener(this);
    }

    @NonNull
    public static RadioInputController fromJson(@NonNull JsonMap json) throws JsonException {
        String identifier = Identifiable.identifierFromJson(json);
        JsonMap viewJson = json.opt("view").optMap();
        boolean isRequired = Validatable.requiredFromJson(json);
        String contentDescription = Accessible.contentDescriptionFromJson(json);

        BaseModel view = Thomas.model(viewJson);

        return new RadioInputController(identifier, view, isRequired, contentDescription);
    }

    @Override
    public List<BaseModel> getChildren() {
        return Collections.singletonList(view);
    }

    @NonNull
    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public boolean isRequired() {
        return isRequired;
    }

    @Nullable
    @Override
    public String getContentDescription() {
        return contentDescription;
    }

    @NonNull
    public BaseModel getView() {
        return view;
    }

    public boolean isValid() {
        return selectedValue != null || !isRequired;
    }


    @NonNull
    @VisibleForTesting
    public List<RadioInputModel> getRadioInputs() {
        return radioInputs;
    }

    @Nullable
    @VisibleForTesting
    public String getSelectedValue() {
        return selectedValue;
    }


    @Override
    public boolean onEvent(@NonNull Event event) {
        Logger.verbose("onEvent: %s", event.getType());

        switch (event.getType()) {
            case VIEW_INIT:
                return onViewInit((Event.ViewInit) event);
            case RADIO_INPUT_CHANGE:
                return onInputChange((RadioEvent.InputChange) event);

            default:
                // Pass along any other events
                return super.onEvent(event);
        }
    }

    private boolean onViewInit(Event.ViewInit event) {
        if (event.getViewType() == ViewType.RADIO_INPUT) {
            if (radioInputs.isEmpty()) {
                bubbleEvent(new RadioEvent.ControllerInit(identifier, isValid()));
            }
            RadioInputModel model = (RadioInputModel) event.getModel();
            radioInputs.add(model);
            return true;
        } else {
            return false;
        }
    }

    private boolean onInputChange(RadioEvent.InputChange event) {
        if (event.isChecked() && !event.getValue().equals(selectedValue)) {
            selectedValue = event.getValue();
            trickleEvent(new RadioEvent.ViewUpdate(event.getValue(), event.isChecked()));
            bubbleEvent(new FormEvent.DataChange(identifier, new FormData.RadioInputController(event.getValue()), isValid()));
        }

        return true;
    }
}
