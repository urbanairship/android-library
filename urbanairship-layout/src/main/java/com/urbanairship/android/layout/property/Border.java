/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Border {
    @Nullable
    private final Integer radius;
    @Nullable
    private final Integer strokeWidth;
    @Nullable
    private final Color strokeColor;

    public Border(@Nullable Integer radius, @Nullable Integer strokeWidth, @Nullable Color strokeColor) {
        this.radius = radius;
        this.strokeWidth = strokeWidth;
        this.strokeColor = strokeColor;
    }

    @NonNull
    public static Border fromJson(@NonNull JsonMap json) throws JsonException {
        Integer radius = json.opt("radius").getInteger();
        Integer strokeWidth = json.opt("stroke_width").getInteger();
        JsonMap colorJson = json.opt("stroke_color").optMap();
        Color strokeColor = colorJson.isEmpty() ? null : Color.fromJsonField(json, "stroke_color");

        return new Border(radius, strokeWidth, strokeColor);
    }

    @Dimension(unit = Dimension.DP)
    @Nullable
    public Integer getRadius() {
        return radius;
    }

    @Nullable
    public Integer getStrokeWidth() {
        return strokeWidth;
    }

    @Nullable
    public Color getStrokeColor() {
        return strokeColor;
    }

    @Dimension(unit = Dimension.DP)
    @Nullable
    public Integer getInnerRadius() {
        if (strokeWidth == null || strokeWidth <= 0) {
            return null;
        }

        if (radius == null || radius <= strokeWidth) {
            return null;
        }

        return radius - strokeWidth;
    }
}
