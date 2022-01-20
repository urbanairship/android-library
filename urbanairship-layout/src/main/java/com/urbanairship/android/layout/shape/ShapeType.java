/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.shape;

import android.graphics.drawable.GradientDrawable;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.urbanairship.json.JsonException;

/**
 * Shape types.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public enum ShapeType {
    RECTANGLE("rectangle", GradientDrawable.RECTANGLE),
    ELLIPSE("ellipse", GradientDrawable.OVAL);

    @NonNull
    private final String value;
    private final int drawableShape;

    ShapeType(@NonNull String value, int drawableShape) {
        this.value = value;
        this.drawableShape = drawableShape;
    }

    @NonNull
    public static ShapeType from(@NonNull String value) throws JsonException {
        for (ShapeType type : ShapeType.values()) {
            if (type.value.equals(value.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        throw new JsonException("Unknown ShapeType value: " + value);
    }

    public int getDrawableShapeType() {
        return drawableShape;
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
