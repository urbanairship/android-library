/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import android.graphics.Color;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ModalPlacement {
    @Nullable
    private final Margin margin;
    @Nullable
    private final Size size;
    @Nullable
    private final Position position;
    @Nullable
    @ColorInt
    private final Integer backgroundColor;
    // TODO: add support for background images to match web?
//    @Nullable
//    private final Uri backgroundImage;

    public ModalPlacement(
        @Nullable Margin margin,
        @Nullable Size size,
        @Nullable Position position,
        @Nullable @ColorInt Integer backgroundColor) {
        this.margin = margin;
        this.size = size;
        this.position = position;
        this.backgroundColor = backgroundColor;
    }

    @NonNull
    public static ModalPlacement fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap marginJson = json.opt("margin").optMap();
        JsonMap sizeJson = json.opt("size").optMap();
        JsonMap positionJson = json.opt("position").optMap();
        String backgroundColorString = json.opt("backgroundColor").optString();

        Margin margin = marginJson.isEmpty() ? null : Margin.fromJson(marginJson);
        Size size = sizeJson.isEmpty() ? null : Size.fromJson(sizeJson);
        Position position = positionJson.isEmpty() ? null : Position.fromJson(positionJson);
        @ColorInt Integer backgroundColor = backgroundColorString.isEmpty() ? null : Color.parseColor(backgroundColorString);

        return new ModalPlacement(margin, size, position, backgroundColor);
    }

    @Nullable
    public Margin getMargin() {
        return margin;
    }

    @Nullable
    public Size getSize() {
        return size;
    }

    @Nullable
    public Position getPosition() {
        return position;
    }

    @Nullable
    @ColorInt
    public Integer getBackgroundColor() {
        return backgroundColor;
    }
}
