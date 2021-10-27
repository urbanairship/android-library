/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import android.graphics.Color;

import com.urbanairship.json.JsonMap;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Border {
    @Nullable
    private final Integer radius;
    @Nullable
    private final Integer strokeWidth;
    @Nullable
    @ColorInt
    private final Integer strokeColor;

    public Border(@Nullable Integer radius, @Nullable Integer strokeWidth, @Nullable @ColorInt Integer strokeColor) {
        this.radius = radius;
        this.strokeWidth = strokeWidth;
        this.strokeColor = strokeColor;
    }

    @NonNull
    public static Border fromJson(@NonNull JsonMap json) {
        Integer radius = json.opt("radius").getInteger();
        Integer strokeWidth = json.opt("strokeWidth").getInteger();
        String colorString = json.opt("strokeColor").getString();
        @ColorInt Integer strokeColor = colorString == null ? null : Color.parseColor(colorString);

        return new Border(radius, strokeWidth, strokeColor);
    }

    @Nullable
    public Integer getRadius() {
        return radius;
    }

    @Nullable
    public Integer getStrokeWidth() {
        return strokeWidth;
    }

    @Nullable
    @ColorInt
    public Integer getStrokeColor() {
        return strokeColor;
    }
}
