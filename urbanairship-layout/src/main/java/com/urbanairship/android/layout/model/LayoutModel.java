/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class LayoutModel extends BaseModel {
    public LayoutModel(@NonNull ViewType viewType) {
        super(viewType);
    }

    public LayoutModel(@NonNull ViewType viewType, @Nullable @ColorInt Integer backgroundColor, @Nullable Border border) {
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
        }

        throw new JsonException("Error inflating layout! Unrecognized view type: " + typeString);
    }

    /**
     * Implement in subclasses to return a list of {@linkplain BaseModel BaseModels} for items in the layout.
     *
     * @return a list of child {@code BaseModel} objects.
     */
    public abstract List<BaseModel> getChildren();

    //
    // BaseModel overrides
    //

    /**
     * {@inheritDoc}
     * <p>
     * Overrides the default behavior in {@link BaseModel} to propagate the event by bubbling it up.
     */
    @Override
    public boolean onEvent(@NonNull Event event) {
        Logger.verbose("%s - onEvent: bubbling up %s", getType(), event.getType().name());

        return bubbleEvent(event);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overrides the default behavior in {@link BaseModel} to propagate the event by trickling it
     * down to the children of this layout.
     */
    @Override
    public boolean trickleEvent(@NonNull Event event) {
        Logger.verbose("%s - trickleEvent: %s", getType(), event.getType().name());

        for (BaseModel child : getChildren()) {
            if (child.trickleEvent(event)) { return true; }
        }

        return false;
    }
}
