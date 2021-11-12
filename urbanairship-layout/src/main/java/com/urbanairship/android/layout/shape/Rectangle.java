/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.shape;

import android.content.Context;

import com.google.android.material.shape.AbsoluteCornerSize;
import com.google.android.material.shape.RoundedCornerTreatment;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.json.JsonMap;

import androidx.annotation.ColorInt;
import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.urbanairship.android.layout.util.ResourceUtils.dpToPx;

/**
 * Rectangle shape.
 */
public class Rectangle extends Shape {
    private final int width;
    private final int height;

    public Rectangle(int width, int height, @Nullable Border border, @Nullable @ColorInt Integer color) {
        super(ShapeType.RECTANGLE, border, color);

        this.width = width;
        this.height = height;
    }

    @NonNull
    public static Rectangle fromJson(@NonNull JsonMap json) {
        int width = json.opt("width").getInt(-1);
        int height = json.opt("height").getInt(-1);
        JsonMap borderJson = json.opt("border").optMap();
        Border border = Border.fromJson(borderJson);
        @ColorInt Integer color = Color.fromJsonField(json, "color");

        return new Rectangle(width, height, border, color);
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
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
