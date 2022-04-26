/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.urbanairship.Fonts;
import com.urbanairship.android.layout.R;
import com.urbanairship.android.layout.model.BaseModel;
import com.urbanairship.android.layout.model.LabelButtonModel;
import com.urbanairship.android.layout.model.LabelModel;
import com.urbanairship.android.layout.model.TextInputModel;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.FormInputType;
import com.urbanairship.android.layout.property.SwitchStyle;
import com.urbanairship.android.layout.property.TextAppearance;
import com.urbanairship.android.layout.property.TextStyle;
import com.urbanairship.android.layout.widget.Clippable;
import com.urbanairship.util.UAStringUtil;

import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.Dimension;
import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.ColorUtils;

import static com.urbanairship.android.layout.util.ResourceUtils.dpToPx;

/**
 * Helpers for layout rendering.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class LayoutUtils {
    private static final float PRESSED_ALPHA_PERCENT = 0.2f;
    private static final int DEFAULT_STROKE_WIDTH_DPS = 2;
    private static final int DEFAULT_BORDER_RADIUS = 0;

    private static final float MATERIAL_ALPHA_FULL = 1.0f;
    private static final float MATERIAL_ALPHA_LOW = 0.32f;
    private static final float MATERIAL_ALPHA_DISABLED = 0.38f;

    private static final String NBSP = "\u00A0";
    private static final String NARROW_NBSP = "\u202F";

    private LayoutUtils() {}

    public static void applyBorderAndBackground(@NonNull View view, @NonNull BaseModel model) {
        applyBorderAndBackground(view, model.getBorder(), model.getBackgroundColor());
    }

    public static void applyBorderAndBackground(
        @NonNull View view,
        @Nullable Border border,
        @Nullable Color backgroundColor
    ) {
        Context context = view.getContext();

        if (border != null) {
            @Dimension float cornerRadius = border.getRadius() == null ? 0 : dpToPx(context, border.getRadius());
            ShapeAppearanceModel shapeModel = ShapeAppearanceModel.builder()
                                                                  .setAllCorners(CornerFamily.ROUNDED, cornerRadius)
                                                                  .build();
            MaterialShapeDrawable shapeDrawable = new MaterialShapeDrawable(shapeModel);

            if (view instanceof Clippable) {
                ((Clippable) view).setClipPathBorderRadius(cornerRadius);
            }

            int borderPadding = -1;
            if (border.getStrokeWidth() != null) {
                float strokeWidth = dpToPx(context, border.getStrokeWidth());
                shapeDrawable.setStrokeWidth(strokeWidth);
                borderPadding = (int) strokeWidth;
            }

            if (border.getStrokeColor() != null) {
                shapeDrawable.setStrokeColor(ColorStateList.valueOf(border.getStrokeColor().resolve(context)));
            }

            @ColorInt int fillColor = backgroundColor != null ? backgroundColor.resolve(context) : Color.TRANSPARENT;
            shapeDrawable.setFillColor(ColorStateList.valueOf(fillColor));

            mergeBackground(view, shapeDrawable);

            if (borderPadding > -1) {
                addPadding(view, borderPadding);
            }
        } else if (backgroundColor != null) {
            mergeBackground(view, new ColorDrawable(backgroundColor.resolve(context)));
        }
    }

    public static void resetBorderAndBackground(@NonNull View view) {
        view.setBackground(null);
        view.setPadding(0,0,0,0);

        if (view instanceof Clippable) {
            ((Clippable) view).setClipPathBorderRadius(0);
        }
    }

    private static void mergeBackground(@NonNull View view, @NonNull Drawable drawable) {
        Drawable background = drawable;
        if (view.getBackground() != null) {
            background = new LayerDrawable(new Drawable[]{view.getBackground(), drawable});
        }
        view.setBackground(background);
    }

    public static void applyButtonModel(@NonNull MaterialButton button, @NonNull LabelButtonModel model) {
        applyLabelModel(button, model.getLabel());

        Context context = button.getContext();
        TextAppearance textAppearance = model.getLabel().getTextAppearance();

        int textColor = textAppearance.getColor().resolve(context);
        int backgroundColor = model.getBackgroundColor() == null
            ? Color.TRANSPARENT
            : model.getBackgroundColor().resolve(button.getContext());
        int pressedColor = ColorUtils.setAlphaComponent(textColor, Math.round(Color.alpha(textColor) * PRESSED_ALPHA_PERCENT));
        int disabledColor = generateDisabledColor(backgroundColor);
        int strokeWidth = model.getBorder() == null || model.getBorder().getStrokeWidth() == null
            ? DEFAULT_STROKE_WIDTH_DPS
            : model.getBorder().getStrokeWidth();
        int strokeColor = model.getBorder() == null || model.getBorder().getStrokeColor() == null
            ? backgroundColor
            : model.getBorder().getStrokeColor().resolve(context);
        int disabledStrokeColor = generateDisabledColor(strokeColor);
        int borderRadius = model.getBorder() == null || model.getBorder().getRadius() == null
            ? DEFAULT_BORDER_RADIUS
            : model.getBorder().getRadius();

        button.setBackgroundTintList(new ColorStateListBuilder()
            .add(disabledColor, -android.R.attr.state_enabled)
            .add(backgroundColor)
            .build());
        button.setRippleColor(ColorStateList.valueOf(pressedColor));
        int strokeWidthDp = (int) dpToPx(context, strokeWidth);
        button.setStrokeWidth(strokeWidthDp);
        if (strokeWidthDp > 0) {
            addPadding(button, strokeWidthDp);
        }
        button.setStrokeColor(new ColorStateListBuilder()
            .add(disabledStrokeColor, -android.R.attr.state_enabled)
            .add(strokeColor)
            .build());
        button.setCornerRadius((int) dpToPx(context, borderRadius));
    }

    public static void applyLabelModel(@NonNull TextView textView, @NonNull LabelModel label) {
        TextAppearance appearance = label.getTextAppearance();
        String text = label.getText();

        applyTextAppearance(textView, appearance);

        // Work around TextView rendering issues that cause ends of lines to be clipped off when using certain
        // fancy custom fonts that aren't measured properly. We use a full non-breaking space for italic text and a
        // narrow non-breaking space for non-italic text to minimize the impact on the overall layout. The issue
        // also occurs for end-justified multiline text, but that's a bit harder to address in a reasonable way, so
        // we'll consider it an edge-case for now.
        Fonts fonts = Fonts.shared(textView.getContext());
        boolean isCustomFont = false;
        for (String font : appearance.getFontFamilies()) {
            if (!fonts.isSystemFont(font)) {
                isCustomFont = true;
                break;
            }
        }
        boolean isItalic = appearance.getTextStyles().contains(TextStyle.ITALIC);
        if (isCustomFont && isItalic) {
            text += NBSP;
        } else if (isCustomFont || isItalic) {
            text += NARROW_NBSP;
        }

        textView.setText(text);
    }

    public static void applyTextInputModel(@NonNull AppCompatEditText editText, @NonNull TextInputModel textInput) {
        applyBorderAndBackground(editText, textInput);
        applyTextAppearance(editText, textInput.getTextAppearance());
        int padding = (int) dpToPx(editText.getContext(), 8);
        editText.setPadding(padding, padding, padding, padding);
        editText.setInputType(textInput.getInputType().getTypeMask());
        editText.setSingleLine(textInput.getInputType() != FormInputType.TEXT_MULTILINE);
        editText.setGravity(editText.getGravity() | Gravity.TOP);

        if (!UAStringUtil.isEmpty(textInput.getHintText())) {
            editText.setHint(textInput.getHintText());
            Color hintColor = textInput.getTextAppearance().getHintColor();
            if (hintColor != null) {
                editText.setHintTextColor(hintColor.resolve(editText.getContext()));
            }
        }

        if (!UAStringUtil.isEmpty(textInput.getContentDescription())) {
            editText.setContentDescription(textInput.getContentDescription());
        }
    }

    public static void applyTextAppearance(@NonNull TextView textView, @NonNull TextAppearance textAppearance) {
        Context context = textView.getContext();

        textView.setTextSize(textAppearance.getFontSize());

        int textColor = textAppearance.getColor().resolve(context);
        int disabledTextColor = generateDisabledColor(Color.TRANSPARENT, textColor);

        textView.setTextColor(new ColorStateListBuilder()
            .add(disabledTextColor, -android.R.attr.state_enabled)
            .add(textColor)
            .build());

        int typefaceFlags = Typeface.NORMAL;
        int paintFlags = Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG;

        for (TextStyle style : textAppearance.getTextStyles()) {
            switch (style) {
                case BOLD:
                    typefaceFlags |= Typeface.BOLD;
                    break;
                case ITALIC:
                    typefaceFlags |= Typeface.ITALIC;
                    break;
                case UNDERLINE:
                    paintFlags |= Paint.UNDERLINE_TEXT_FLAG;
                    break;
            }
        }

        switch (textAppearance.getAlignment()) {
            case CENTER:
                textView.setGravity(Gravity.CENTER);
                break;
            case START:
                textView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                break;
            case END:
                textView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                break;
        }

        Typeface typeface = getTypeFace(textView.getContext(), textAppearance.getFontFamilies());

        textView.setTypeface(typeface, typefaceFlags);
        textView.setPaintFlags(paintFlags);
    }

    /**
     * Finds the first available font in the list.
     *
     * @param context The application context.
     * @param fontFamilies The list of font families.
     * @return The typeface with a specified font, or null if the font was not found.
     */
    @Nullable
    private static Typeface getTypeFace(@NonNull Context context, @NonNull List<String> fontFamilies) {
        for (String fontFamily : fontFamilies) {
            if (UAStringUtil.isEmpty(fontFamily)) {
                continue;
            }

            Typeface typeface = Fonts.shared(context).getFontFamily(fontFamily);
            if (typeface != null) {
                return typeface;
            }
        }

        return null;
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void applySwitchStyle(@NonNull SwitchCompat view, @NonNull SwitchStyle style) {
        Context context = view.getContext();

        int trackOn = style.getOnColor().resolve(context);
        int trackOff = style.getOffColor().resolve(context);

        int thumbOn = MaterialColors.layer(Color.WHITE, trackOn, MATERIAL_ALPHA_LOW);
        int thumbOff = MaterialColors.layer(Color.WHITE, trackOff, MATERIAL_ALPHA_LOW);

        view.setTrackTintList(checkedColorStateList(trackOn, trackOff));
        view.setThumbTintList(checkedColorStateList(thumbOn, thumbOff));

        view.setBackgroundResource(R.drawable.ua_layout_imagebutton_ripple);

        view.setGravity(Gravity.CENTER);
    }

    private static ColorStateList checkedColorStateList(@ColorInt int checkedColor, @ColorInt int normalColor) {
        return new ColorStateListBuilder()
            .add(checkedColor, android.R.attr.state_checked)
            .add(normalColor)
            .build();
    }

    public static void doOnAttachToWindow(@NonNull View view, @NonNull Runnable callback) {
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                v.removeOnAttachStateChangeListener(this);
                callback.run();
            }

            @Override
            public void onViewDetachedFromWindow(View v) { /* no-op */ }
        });
    }

    public static void addPadding(@NonNull View view, int padding) {
        addPadding(view, padding, padding, padding, padding);
    }

    public static void addPadding(@NonNull View view, int left, int top, int right, int bottom) {
        view.setPadding(
            view.getPaddingLeft() + left,
            view.getPaddingTop() + top,
            view.getPaddingRight() + right,
            view.getPaddingBottom() + bottom
        );
    }

    @ColorInt
    public static int generatePressedColor(@ColorInt int baseColor) {
        return generatePressedColor(baseColor, Color.WHITE);
    }

    @ColorInt
    public static int generateDisabledColor(@ColorInt int baseColor) {
        return generateDisabledColor(baseColor, Color.WHITE);
    }

    @ColorInt
    public static int generatePressedColor(@ColorInt int background, @ColorInt int foreground) {
        return overlayColors(background, foreground, PRESSED_ALPHA_PERCENT);
    }

    @ColorInt
    public static int generateDisabledColor(@ColorInt int background, @ColorInt int foreground) {
        return overlayColors(background, foreground, MATERIAL_ALPHA_DISABLED);
    }

    @ColorInt
    private static int overlayColors(
        @ColorInt int backgroundColor,
        @ColorInt int overlayColor,
        @FloatRange(from = 0, to = 1) float overlayAlpha
    ) {
        int alpha = Math.round(Color.alpha(overlayColor) * overlayAlpha);
        int overlay = ColorUtils.setAlphaComponent(overlayColor, alpha);
        return ColorUtils.compositeColors(overlay, backgroundColor);
    }
}
