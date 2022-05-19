/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.event.CheckboxEvent;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.FormEvent;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.FormData;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * Controller for checkbox inputs.
 *
 * Must be a descendant of {@code FormController} or {@code NpsFormController}.
 */
public class CheckboxController extends LayoutModel implements Identifiable, Accessible, Validatable {
    @NonNull
    private final String identifier;
    @NonNull
    private final BaseModel view;
    private final int minSelection;
    private final int maxSelection;
    private final boolean isRequired;
    @Nullable
    private final String contentDescription;

    @NonNull
    private final List<CheckboxModel> checkboxes = new ArrayList<>();

    private final Set<JsonValue> selectedValues = new HashSet<>();

    public CheckboxController(
        @NonNull String identifier,
        @NonNull BaseModel view,
        int minSelection,
        int maxSelection,
        boolean isRequired,
        @Nullable String contentDescription
    ) {
        super(ViewType.CHECKBOX_CONTROLLER, null, null);

        this.identifier = identifier;
        this.view = view;
        this.minSelection = minSelection;
        this.maxSelection = maxSelection;
        this.isRequired = isRequired;
        this.contentDescription = contentDescription;

        view.addListener(this);
    }

    @NonNull
    public static CheckboxController fromJson(@NonNull JsonMap json) throws JsonException {
        String identifier = Identifiable.identifierFromJson(json);
        JsonMap viewJson = json.opt("view").optMap();
        boolean isRequired = Validatable.requiredFromJson(json);
        int minSelection = json.opt("min_selection").getInt(isRequired ? 1 : 0);
        int maxSelection = json.opt("max_selection").getInt(Integer.MAX_VALUE);
        String contentDescription = Accessible.contentDescriptionFromJson(json);

        BaseModel view = Thomas.model(viewJson);

        return new CheckboxController(identifier, view, minSelection, maxSelection, isRequired, contentDescription);
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

    @Override
    public boolean isValid() {
        int count = selectedValues.size();
        boolean isFilled = count>= minSelection && count <= maxSelection;
        boolean isOptional = count == 0 && !isRequired;
        return isFilled || isOptional;
    }

    @NonNull
    @VisibleForTesting
    public List<CheckboxModel> getCheckboxes() {
        return checkboxes;
    }

    @NonNull
    @VisibleForTesting
    public Set<JsonValue> getSelectedValues() {
        return selectedValues;
    }

    @Override
    public boolean onEvent(@NonNull Event event, @NonNull LayoutData layoutData) {
        switch (event.getType()) {
            case VIEW_INIT:
                return onViewInit((Event.ViewInit) event, layoutData);

            case CHECKBOX_INPUT_CHANGE:
                return onCheckboxInputChange((CheckboxEvent.InputChange) event, layoutData);

            case VIEW_ATTACHED:
                return onViewAttached((Event.ViewAttachedToWindow) event, layoutData);

            default:
                // Pass along any other events
                return super.onEvent(event, layoutData);
        }
    }

    private boolean onViewInit(Event.ViewInit event, @NonNull LayoutData layoutData) {
        if (event.getViewType() == ViewType.CHECKBOX) {
            if (checkboxes.isEmpty()) {
                bubbleEvent(new CheckboxEvent.ControllerInit(identifier, isValid()), layoutData);
            }
            CheckboxModel model = (CheckboxModel) event.getModel();

            if (!checkboxes.contains(model)) {
                // This is the first time we've seen this checkbox; Add it to our list.
                checkboxes.add(model);
            }

            return true;
        } else {
            return false;
        }
    }

    private boolean onCheckboxInputChange(@NonNull CheckboxEvent.InputChange event, @NonNull LayoutData layoutData) {
        if (event.isChecked() && selectedValues.size() + 1 > maxSelection) {
            // Can't check any more boxes, so we'll ignore it and consume the event.
            Logger.debug("Ignoring checkbox input change for '%s'. Max selections reached!", event.getValue());
            return true;
        }

        if (event.isChecked()) {
            selectedValues.add(event.getValue());
        } else {
            selectedValues.remove(event.getValue());
        }

        trickleEvent(new CheckboxEvent.ViewUpdate(event.getValue(), event.isChecked()), layoutData);
        bubbleEvent(new FormEvent.DataChange(new FormData.CheckboxController(identifier, selectedValues), isValid()), layoutData);
        return true;
    }

    private boolean onViewAttached(@NonNull Event.ViewAttachedToWindow event, @NonNull LayoutData layoutData) {
        if (event.getViewType() == ViewType.CHECKBOX
            && event.getModel() instanceof CheckboxModel
            && !selectedValues.isEmpty()) {

            CheckboxModel model = (CheckboxModel) event.getModel();
            boolean isChecked = selectedValues.contains(model.getReportingValue());
            trickleEvent(new CheckboxEvent.ViewUpdate(model.getReportingValue(), isChecked), layoutData);
        }
        // Always pass the event on.
        return super.onEvent(event, layoutData);
    }
}
