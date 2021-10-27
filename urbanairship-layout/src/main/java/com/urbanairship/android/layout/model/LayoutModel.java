/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;

public abstract class LayoutModel extends BaseModel {
    public LayoutModel(@NonNull ViewType viewType) {
        super(viewType);
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
}
