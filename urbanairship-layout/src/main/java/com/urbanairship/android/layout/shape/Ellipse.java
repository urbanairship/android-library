/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.shape;

import android.content.Context;

import com.google.android.material.shape.RelativeCornerSize;
import com.google.android.material.shape.RoundedCornerTreatment;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Circle shape.
 */
public class Ellipse extends Shape {
    public Ellipse(float aspectRatio, float scale, @Nullable Border border, @Nullable Color color) {
        super(ShapeType.ELLIPSE, aspectRatio, scale, border, color);
    }

    @NonNull
    public static Ellipse fromJson(@NonNull JsonMap json) throws JsonException {
        float aspectRatio = json.opt("aspect_ratio").getFloat(1f);
        float scale = json.opt("scale").getFloat(1f);
        JsonMap borderJson = json.opt("border").optMap();
        Border border = Border.fromJson(borderJson);
        Color color = Color.fromJsonField(json, "color");

        return new Ellipse(aspectRatio, scale, border, color);
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
