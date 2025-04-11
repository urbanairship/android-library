/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.shape;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.StateSet;

import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.Image;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.android.layout.widget.ShapeDrawableWrapper;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import static com.urbanairship.android.layout.util.ResourceUtils.dpToPx;

/**
 * Representation of a Shape.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Shape {
    private static final int[] CHECKED_STATE_SET = { android.R.attr.state_checked };
    private static final int[] DISABLED_CHECKED_STATE_SET = { -android.R.attr.state_enabled, android.R.attr.state_checked };
    private static final int[] DISABLED_UNCHECKED_STATE_SET = { -android.R.attr.state_enabled, -android.R.attr.state_checked };
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
        ShapeType type = ShapeType.from(typeString);
        float aspectRatio = json.opt("aspect_ratio").getFloat(1f);
        float scale = json.opt("scale").getFloat(1f);
        JsonMap borderJson = json.opt("border").optMap();
        Border border = Border.fromJson(borderJson);
        Color color = Color.fromJsonField(json, "color");

        return new Shape(type, aspectRatio, scale, border, color);
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
        LayerDrawable checkedDrawable = buildLayerDrawable(context, checkedShapes, checkedIcon, true);
        LayerDrawable disabledCheckedDrawable = buildLayerDrawable(context, checkedShapes, checkedIcon, false);

        // Build layer drawables from unchecked shapes/icons
        LayerDrawable uncheckedDrawable = buildLayerDrawable(context, uncheckedShapes, uncheckedIcon, true);
        LayerDrawable disabledUncheckedDrawable = buildLayerDrawable(context, uncheckedShapes, uncheckedIcon, false);

        // Combine layer drawables into a single state list drawable
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(DISABLED_CHECKED_STATE_SET, disabledCheckedDrawable);
        drawable.addState(DISABLED_UNCHECKED_STATE_SET, disabledUncheckedDrawable);
        drawable.addState(CHECKED_STATE_SET, checkedDrawable);
        drawable.addState(EMPTY_STATE_SET, uncheckedDrawable);

        return drawable;
    }

    @NonNull
    private static LayerDrawable buildLayerDrawable(@NonNull Context context, @NonNull List<Shape> shapes, @Nullable Image.Icon icon, boolean enabledState) {
        int layerCount = shapes.size() + (icon != null ? 1 : 0);
        Drawable[] layers = new Drawable[layerCount];
        for (int i = 0; i < shapes.size(); i++) {
            layers[i] = shapes.get(i).getDrawable(context, enabledState);
        }
        if (icon != null) {
            layers[layerCount - 1] = icon.getDrawable(context, enabledState);
        }
        return new LayerDrawable(layers);
    }

    @NonNull
    public Drawable getDrawable(@NonNull Context context) {
        return getDrawable(context,true);
    }

    @NonNull
    public Drawable getDrawable(@NonNull Context context, boolean enabledState) {
        @ColorInt int drawableColor = color != null ? color.resolve(context) : Color.TRANSPARENT;
        int strokeWidth = border != null && border.getStrokeWidth() != null
            ? (int) dpToPx(context, border.getStrokeWidth())
            : 0;
        @ColorInt int strokeColor = border != null && border.getStrokeColor() != null
            ? border.getStrokeColor().resolve(context)
            : 0;
        float radius = border != null && border.getRadius() != null
            ? dpToPx(context, border.getRadius())
            : 0;

        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(type.getDrawableShapeType());
        drawable.setColor(enabledState ? drawableColor : LayoutUtils.generateDisabledColor(drawableColor));
        drawable.setStroke(strokeWidth, enabledState ? strokeColor : LayoutUtils.generateDisabledColor(strokeColor));
        drawable.setCornerRadius(radius);

        return new ShapeDrawableWrapper(drawable, aspectRatio, scale, null);
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
}
