/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ModalPlacement {
    @NonNull
    private final ConstrainedSize size;
    @Nullable
    private final Margin margin;
    @Nullable
    private final Position position;
    @Nullable
    @ColorInt
    private final Integer shadeColor;

    public ModalPlacement(
        @NonNull ConstrainedSize size,
        @Nullable Margin margin,
        @Nullable Position position,
        @Nullable @ColorInt Integer shadeColor) {
        this.size = size;
        this.margin = margin;
        this.position = position;
        this.shadeColor = shadeColor;
    }

    @NonNull
    public static ModalPlacement fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap sizeJson = json.opt("size").optMap();
        if (sizeJson.isEmpty()) {
            throw new JsonException("Failed to parse Modal Placement! Field 'size' is required.");
        }
        JsonMap positionJson = json.opt("position").optMap();
        JsonMap backgroundColorJson = json.opt("shade_color").optMap();
        JsonMap marginJson = json.opt("margin").optMap();

        ConstrainedSize size = ConstrainedSize.fromJson(sizeJson);
        Margin margin = marginJson.isEmpty() ? null : Margin.fromJson(marginJson);
        Position position = positionJson.isEmpty() ? null : Position.fromJson(positionJson);
        @ColorInt Integer backgroundColor = backgroundColorJson.isEmpty() ? null : Color.fromJson(backgroundColorJson);

        return new ModalPlacement(size, margin, position, backgroundColor);
    }

    @Nullable
    public Margin getMargin() {
        return margin;
    }

    @NonNull
    public ConstrainedSize getSize() {
        return size;
    }

    @Nullable
    public Position getPosition() {
        return position;
    }

    @Nullable
    @ColorInt
    public Integer getShadeColor() {
        return shadeColor;
    }
}
