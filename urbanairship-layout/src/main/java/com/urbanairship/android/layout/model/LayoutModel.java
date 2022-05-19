/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class LayoutModel extends BaseModel {

    public LayoutModel(@NonNull ViewType viewType, @Nullable Color backgroundColor, @Nullable Border border) {
        super(viewType, backgroundColor, border);
    }

    @NonNull
    public static LayoutModel fromJson(@NonNull JsonMap json) throws JsonException {
        String typeString = json.opt("type").optString();

        switch (ViewType.from(typeString)) {
            case CONTAINER:
                return ContainerLayoutModel.fromJson(json);
            case LINEAR_LAYOUT:
                return LinearLayoutModel.fromJson(json);
            case SCROLL_LAYOUT:
                return ScrollLayoutModel.fromJson(json);
            case PAGER_CONTROLLER:
                return PagerController.fromJson(json);
            case FORM_CONTROLLER:
                return FormController.fromJson(json);
            case NPS_FORM_CONTROLLER:
                return NpsFormController.fromJson(json);
            case CHECKBOX_CONTROLLER:
                return CheckboxController.fromJson(json);
            case RADIO_INPUT_CONTROLLER:
                return RadioInputController.fromJson(json);
        }

        throw new JsonException("Error inflating layout! Unrecognized view type: " + typeString);
    }

    /**
     * Implement in subclasses to return a list of {@linkplain BaseModel BaseModels} for items in the layout.
     *
     * @return a list of child {@code BaseModel} objects.
     */
    public abstract List<BaseModel> getChildren();

    /**
     * {@inheritDoc}
     * <p>
     * Overrides the default behavior in {@link BaseModel} to propagate the event by bubbling it up.
     */
    @Override
    public boolean onEvent(@NonNull Event event, @NonNull LayoutData layoutData) {
        return bubbleEvent(event, layoutData);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overrides the default behavior in {@link BaseModel} to propagate the event by trickling it
     * down to the children of this layout.
     */
    @Override
    public boolean trickleEvent(@NonNull Event event, @NonNull LayoutData layoutData) {
        for (BaseModel child : getChildren()) {
            if (child.trickleEvent(event, layoutData)) { return true; }
        }
        return false;
    }
}
