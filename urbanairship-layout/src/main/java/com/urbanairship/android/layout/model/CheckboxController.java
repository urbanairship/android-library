/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.property.ViewType;

import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
    @Nullable
    private final Integer minSelection;
    @Nullable
    private final Integer maxSelection;
    @Nullable
    private final Boolean isRequired;
    @Nullable
    private final String contentDescription;

    @NonNull
    private final List<CheckboxModel> checkboxes;

    public CheckboxController(
        @NonNull String identifier,
        @NonNull BaseModel view,
        @Nullable Integer minSelection,
        @Nullable Integer maxSelection,
        @Nullable Boolean isRequired,
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

        checkboxes = Thomas.findAllByType(CheckboxModel.class, view);
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

    @Nullable
    @Override
    public Boolean isRequired() {
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

    @Nullable
    public Integer getMinSelection() {
        return minSelection;
    }

    @Nullable
    public Integer getMaxSelection() {
        return maxSelection;
    }

    @Nullable
    public Boolean getRequired() {
        return isRequired;
    }

    @Override
    public boolean onEvent(@NonNull Event event) {
        Logger.verbose("onEvent: %s", event.getType());

        // TODO: switch on checkbox events and consume any that should be internal to the controller and its children.

        // Pass along any other events
        return super.onEvent(event);
    }
}
