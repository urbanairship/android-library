/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.shape;

import android.content.Context;

import com.google.android.material.shape.RelativeCornerSize;
import com.google.android.material.shape.RoundedCornerTreatment;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.json.JsonMap;

import androidx.annotation.ColorInt;
import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Circle shape.
 */
public class Circle extends Shape {
    private final int radius;

    public Circle(int radius, @Nullable Border border, @Nullable @ColorInt Integer color) {
        super(ShapeType.CIRCLE, border, color);

        this.radius = radius;
    }

    @NonNull
    public static Circle fromJson(@NonNull JsonMap json) {
        int radius = json.opt("radius").getInt(-1);
        JsonMap borderJson = json.opt("border").optMap();
        Border border = Border.fromJson(borderJson);
        @ColorInt Integer color = Color.fromJsonField(json, "color");

        return new Circle(radius, border, color);
    }

    @Override
    public int getWidth() {
        return radius * 2;
    }

    @Override
    public int getHeight() {
        return radius * 2;
    }

    @Dimension(unit = Dimension.DP)
    public int getRadius() {
        return radius;
    }

    @NonNull
    @Override
    protected ShapeAppearanceModel buildShapeAppearanceModel(@NonNull Context context) {
        ShapeAppearanceModel.Builder builder = ShapeAppearanceModel.builder()
            .setAllCorners(new RoundedCornerTreatment())
            .setAllCornerSizes(new RelativeCornerSize(0.5f));

        return builder.build();
    }
}
