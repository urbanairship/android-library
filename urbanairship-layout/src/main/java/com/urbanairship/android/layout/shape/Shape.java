/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.shape;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.ColorInt;
import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.urbanairship.android.layout.util.ResourceUtils.dpToPx;

/**
 * Base representation of a Shape.
 */
public abstract class Shape {
    @NonNull
    private final ShapeType type;
    @ColorInt
    private final int color;
    @Nullable
    private final Border border;

    public Shape(@NonNull ShapeType type, @Nullable Border border, @Nullable Integer color) {
        this.type = type;
        this.border = border;
        this.color = color != null ? color : Color.BLACK;
    }

    @NonNull
    public static Shape fromJson(@NonNull JsonMap json) throws JsonException {
        String typeString = json.opt("type").optString();

        switch (ShapeType.from(typeString)) {
            case RECTANGLE:
                return Rectangle.fromJson(json);
            case CIRCLE:
                return Circle.fromJson(json);
        }

        throw new JsonException("Failed to parse shape! Unknown type: " + typeString);
    }

    @NonNull
    public ShapeType getType() {
        return type;
    }

    @Nullable
    public Border getBorder() {
        return border;
    }

    @ColorInt
    public int getColor() {
        return color;
    }

    @Dimension(unit = Dimension.DP)
    public abstract int getWidth();

    @Dimension(unit = Dimension.DP)
    public abstract int getHeight();

    @NonNull
    public Drawable getDrawable(@NonNull Context context) {
        MaterialShapeDrawable drawable = new MaterialShapeDrawable(buildShapeAppearanceModel(context));

        drawable.setFillColor(ColorStateList.valueOf(getColor()));

        if (border != null && border.getStrokeWidth() != null) {
            drawable.setStrokeWidth(dpToPx(context, border.getStrokeWidth()));
        }

        if (border != null && border.getStrokeColor() != null) {
            drawable.setStrokeColor(ColorStateList.valueOf(border.getStrokeColor()));
        }

        return drawable;
    }

    @NonNull
    protected abstract ShapeAppearanceModel buildShapeAppearanceModel(@NonNull Context context);
}
