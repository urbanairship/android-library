/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.view.EmptyView;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * An empty view that can have a background and border.
 *
 * Useful for the nub on a banner or as dividers between items.
 *
 * @see EmptyView
 */
public class EmptyModel extends BaseModel {
    public EmptyModel(@Nullable Color backgroundColor, @Nullable Border border) {
        super(ViewType.EMPTY_VIEW, backgroundColor, border);
    }

    @NonNull
    public static EmptyModel fromJson(@NonNull JsonMap json) throws JsonException {
        Color backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);
        return new EmptyModel(backgroundColor, border);
    }
}
