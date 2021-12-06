/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.shape;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.StateSet;

import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.Image;
import com.urbanairship.android.layout.widget.ShapeDrawableWrapper;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import static com.urbanairship.android.layout.util.ResourceUtils.dpToPx;

/**
 * Base representation of a Shape.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class Shape {
    private static final int[] CHECKED_STATE_SET = { android.R.attr.state_checked };
    private static final int[] EMPTY_STATE_SET = StateSet.NOTHING;

    @NonNull
    private final ShapeType type;
    @Nullable
    private final Color color;
    @Nullable
    private final Border border;
    private final float aspectRatio;
    private final float scale;

    public Shape(@NonNull ShapeType type, float aspectRatio, float scale, @Nullable Border border, @Nullable Color color) {
        this.type = type;
        this.aspectRatio = aspectRatio;
        this.scale = scale;
        this.border = border;
        this.color = color;
    }

    @NonNull
    public static Shape fromJson(@NonNull JsonMap json) throws JsonException {
        String typeString = json.opt("type").optString();

        switch (ShapeType.from(typeString)) {
            case RECTANGLE:
                return Rectangle.fromJson(json);
            case ELLIPSE:
                return Ellipse.fromJson(json);
        }

        throw new JsonException("Failed to parse shape! Unknown type: " + typeString);
    }

    @NonNull
    public static StateListDrawable buildStateListDrawable(
        @NonNull Context context,
        @NonNull List<Shape> checkedShapes,
        @NonNull List<Shape> uncheckedShapes
    ) {
        return buildStateListDrawable(context, checkedShapes, uncheckedShapes, null, null);
    }

    @NonNull
    public static StateListDrawable buildStateListDrawable(
        @NonNull Context context,
        @NonNull List<Shape> checkedShapes,
        @NonNull List<Shape> uncheckedShapes,
        @Nullable Image.Icon checkedIcon,
        @Nullable Image.Icon uncheckedIcon
    ) {
        // Build layer drawables from checked shapes/icons
        int checkedLayerCount = checkedShapes.size() + (checkedIcon != null ? 1 : 0);
        Drawable[] checkedLayers = new Drawable[checkedLayerCount];
        for (int i = 0; i < checkedShapes.size(); i++) {
            checkedLayers[i] = checkedShapes.get(i).getDrawable(context);
        }
        if (checkedIcon != null) {
            checkedLayers[checkedLayerCount - 1] = checkedIcon.getDrawable(context);
        }
        LayerDrawable checkedDrawable = new LayerDrawable(checkedLayers);

        // Build layer drawables from unchecked shapes/icons
        int uncheckedLayerCount = uncheckedShapes.size() + (uncheckedIcon != null ? 1 : 0);
        Drawable[] uncheckedLayers = new Drawable[uncheckedLayerCount];
        for (int i = 0; i < uncheckedShapes.size(); i++) {
            uncheckedLayers[i] = uncheckedShapes.get(i).getDrawable(context);
        }
        if (uncheckedIcon != null) {
            uncheckedLayers[uncheckedLayerCount - 1] = uncheckedIcon.getDrawable(context);
        }
        LayerDrawable uncheckedDrawable = new LayerDrawable(uncheckedLayers);

        // Combine layer drawables into a single state list drawable
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(CHECKED_STATE_SET, checkedDrawable);
        drawable.addState(EMPTY_STATE_SET, uncheckedDrawable);

        return drawable;
    }

    @NonNull
    public ShapeType getType() {
        return type;
    }

    @Nullable
    public Border getBorder() {
        return border;
    }

    @Nullable
    public Color getColor() {
        return color;
    }

    public float getAspectRatio() {
        return aspectRatio;
    }

    public float getScale() {
        return scale;
    }

    @NonNull
    public Drawable getDrawable(@NonNull Context context) {
        MaterialShapeDrawable drawable = new MaterialShapeDrawable(buildShapeAppearanceModel(context));

        if (getColor() != null) {
            drawable.setFillColor(ColorStateList.valueOf(getColor().resolve(context)));
        } else {
            drawable.setFillColor(ColorStateList.valueOf(android.graphics.Color.BLACK));
        }

        if (border != null && border.getStrokeWidth() != null) {
            drawable.setStrokeWidth(dpToPx(context, border.getStrokeWidth()));
        }

        if (border != null && border.getStrokeColor() != null) {
            drawable.setStrokeColor(ColorStateList.valueOf(border.getStrokeColor().resolve(context)));
        }

        return new ShapeDrawableWrapper(drawable, aspectRatio, scale);
    }

    @NonNull
    protected abstract ShapeAppearanceModel buildShapeAppearanceModel(@NonNull Context context);
}
