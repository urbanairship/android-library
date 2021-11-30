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
    private final float aspectRatio;
    private final float scale;

    public Ellipse(float aspectRatio, float scale, @Nullable Border border, @Nullable Color color) {
        super(ShapeType.ELLIPSE, border, color);

        this.aspectRatio = aspectRatio;
        this.scale = scale;
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

    @Override
    public float getAspectRatio() {
        return aspectRatio;
    }

    @Override
    public float getScale() {
        return scale;
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
