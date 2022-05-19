/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.FormEvent;
import com.urbanairship.android.layout.event.RadioEvent;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.AttributeName;
import com.urbanairship.android.layout.reporting.FormData;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import static com.urbanairship.android.layout.model.Accessible.contentDescriptionFromJson;
import static com.urbanairship.android.layout.model.Identifiable.identifierFromJson;
import static com.urbanairship.android.layout.model.Validatable.requiredFromJson;
import static com.urbanairship.android.layout.reporting.AttributeName.attributeNameFromJson;

/**
 * Controller for radio inputs.
 */
public class RadioInputController extends LayoutModel implements Identifiable, Accessible, Validatable {
    @NonNull
    private final String identifier;
    @NonNull
    private final BaseModel view;
    @Nullable
    private final AttributeName attributeName;
    private final boolean isRequired;
    @Nullable
    private final String contentDescription;

    @NonNull
    private final List<RadioInputModel> radioInputs = new ArrayList<>();

    @Nullable
    private JsonValue selectedValue = null;

    public RadioInputController(
        @NonNull String identifier,
        @NonNull BaseModel view,
        @Nullable AttributeName attributeName,
        boolean isRequired,
        @Nullable String contentDescription
    ) {
        super(ViewType.RADIO_INPUT_CONTROLLER, null, null);

        this.identifier = identifier;
        this.view = view;
        this.attributeName = attributeName;
        this.isRequired = isRequired;
        this.contentDescription = contentDescription;

        view.addListener(this);
    }

    @NonNull
    public static RadioInputController fromJson(@NonNull JsonMap json) throws JsonException {
        String identifier = identifierFromJson(json);
        JsonMap viewJson = json.opt("view").optMap();
        AttributeName attributeName = attributeNameFromJson(json);
        boolean isRequired = requiredFromJson(json);
        String contentDescription = contentDescriptionFromJson(json);

        BaseModel view = Thomas.model(viewJson);

        return new RadioInputController(identifier, view, attributeName, isRequired, contentDescription);
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
    public JsonValue getSelectedValue() {
        return selectedValue;
    }


    @Override
    public boolean onEvent(@NonNull Event event, @NonNull LayoutData layoutData) {
        switch (event.getType()) {
            case VIEW_INIT:
                return onViewInit((Event.ViewInit) event, layoutData);
            case RADIO_INPUT_CHANGE:
                return onInputChange((RadioEvent.InputChange) event, layoutData);
            case VIEW_ATTACHED:
                return onViewAttached((Event.ViewAttachedToWindow) event, layoutData);
            default:
                // Pass along any other events
                return super.onEvent(event, layoutData);
        }
    }

    private boolean onViewInit(@NonNull Event.ViewInit event, @NonNull LayoutData layoutData) {
        if (event.getViewType() == ViewType.RADIO_INPUT) {
            if (radioInputs.isEmpty()) {
                bubbleEvent(new RadioEvent.ControllerInit(identifier, isValid()), layoutData);
            }
            RadioInputModel model = (RadioInputModel) event.getModel();
            if (!radioInputs.contains(model)) {
                // This is the first time we've seen this radio input; Add it to our list.
                radioInputs.add(model);
            }
            return true;
        } else {
            return false;
        }
    }

    private boolean onInputChange(@NonNull RadioEvent.InputChange event, @NonNull LayoutData layoutData) {
        if (event.isChecked() && !event.getValue().equals(selectedValue)) {
            selectedValue = event.getValue();
            trickleEvent(new RadioEvent.ViewUpdate(event.getValue(), event.isChecked()), layoutData);
            bubbleEvent(new FormEvent.DataChange(new FormData.RadioInputController(identifier, event.getValue()), isValid(), attributeName, event.getAttributeValue()), layoutData);
        }

        return true;
    }

    private boolean onViewAttached(@NonNull Event.ViewAttachedToWindow event, @NonNull LayoutData layoutData) {
        if (event.getViewType() == ViewType.RADIO_INPUT
            && event.getModel() instanceof RadioInputModel
            && selectedValue != null) {

            // Restore radio state.
            JsonValue value = ((RadioInputModel) event.getModel()).getReportingValue();
            boolean isSelected = selectedValue.equals(value);
            if (isSelected) {
                trickleEvent(new RadioEvent.ViewUpdate(value, true), layoutData);
            }
        }
        // Always pass the event on.
        return super.onEvent(event, layoutData);
    }
}
