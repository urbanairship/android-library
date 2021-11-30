/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.shape;

import android.content.Context;

import com.google.android.material.shape.AbsoluteCornerSize;
import com.google.android.material.shape.RoundedCornerTreatment;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.urbanairship.android.layout.util.ResourceUtils.dpToPx;

/**
 * Rectangle shape.
 */
public class Rectangle extends Shape {
    private final float aspectRatio;
    private final float scale;

    public Rectangle(float aspectRatio, float scale, @Nullable Border border, @Nullable Color color) {
        super(ShapeType.RECTANGLE, border, color);

        this.aspectRatio = aspectRatio;
        this.scale = scale;
    }

    @NonNull
    public static Rectangle fromJson(@NonNull JsonMap json) throws JsonException {
        float aspectRatio = json.opt("aspect_ratio").getFloat(1f);
        float scale = json.opt("scale").getFloat(1f);
        JsonMap borderJson = json.opt("border").optMap();
        Border border = Border.fromJson(borderJson);
        Color color = Color.fromJsonField(json, "color");

        return new Rectangle(aspectRatio, scale, border, color);
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
        Border border = getBorder();
        @Dimension
        float cornerRadius = (border != null && border.getRadius() != null) ? dpToPx(context, border.getRadius()) : 0;

        ShapeAppearanceModel.Builder builder = new ShapeAppearanceModel.Builder()
            .setAllCorners(new RoundedCornerTreatment())
            .setAllCornerSizes(new AbsoluteCornerSize(cornerRadius));

        return builder.build();
    }
}
