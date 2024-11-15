/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.text.Html;
import android.text.Spanned;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.urbanairship.Fonts;
import com.urbanairship.android.layout.R;
import com.urbanairship.android.layout.model.Background;
import com.urbanairship.android.layout.model.ButtonLayoutModel;
import com.urbanairship.android.layout.model.LabelModel;
import com.urbanairship.android.layout.model.TextInputModel;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.FormInputType;
import com.urbanairship.android.layout.property.MarkdownOptions;
import com.urbanairship.android.layout.property.MarkdownOptionsKt;
import com.urbanairship.android.layout.property.SwitchStyle;
import com.urbanairship.android.layout.property.TapEffect;
import com.urbanairship.android.layout.property.TextAppearance;
import com.urbanairship.android.layout.property.TextStyle;
import com.urbanairship.android.layout.widget.Clippable;
import com.urbanairship.util.UAStringUtil;

import java.util.Arrays;
import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.Dimension;
import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.ColorUtils;

import static com.urbanairship.android.layout.util.ResourceUtils.dpToPx;

/**
 * Helpers for layout rendering.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class LayoutUtils {

    public static final float HOVERED_ALPHA_PERCENT = 0.1f;
    public static final float PRESSED_ALPHA_PERCENT = 0.2f;
    public static final int DEFAULT_STROKE_WIDTH_DPS = 2;
    public static final int DEFAULT_BORDER_RADIUS = 0;

    private static final float MATERIAL_ALPHA_LOW = 0.32f;
    private static final float MATERIAL_ALPHA_DISABLED = 0.38f;

    private static final String NBSP = "\u00A0";
    private static final String NARROW_NBSP = "\u202F";

    private LayoutUtils() {}


    public static void updateBackground(
            @NonNull View view,
            @Nullable Drawable baseBackground,
            @Nullable Background oldBackground,
            @NonNull Background newBackground
    ) {
        if (oldBackground != null && oldBackground.getBorder() != null && oldBackground.getBorder().getStrokeWidth() != null) {
            float padding = dpToPx(view.getContext(), oldBackground.getBorder().getStrokeWidth());
            removePadding(view, (int) padding);
        }

        applyBorderAndBackground(view, baseBackground, newBackground.getBorder(), newBackground.getColor());
    }

    public static void updateBackground(@NonNull View view, @Nullable Background oldBackground, @NonNull Background newBackground) {
       updateBackground(view, null, oldBackground, newBackground);
    }

    public static void applyBorderAndBackground(
            @NonNull View view,
            @Nullable Drawable baseBackground,
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
                @ColorInt int strokeColor = border.getStrokeColor().resolve(context);
                shapeDrawable.setStrokeColor(new ColorStateListBuilder()
                        .add(generateDisabledColor(strokeColor), -android.R.attr.state_enabled)
                        .add(strokeColor)
                        .build());
            }

            @ColorInt int fillColor = backgroundColor != null ? backgroundColor.resolve(context) : Color.TRANSPARENT;
            shapeDrawable.setFillColor(new ColorStateListBuilder()
                    .add(generateDisabledColor(fillColor), -android.R.attr.state_enabled)
                    .add(fillColor)
                    .build());

            applyBackgrounds(view, baseBackground, shapeDrawable);

            if (borderPadding > -1) {
                addPadding(view, borderPadding);
            }
        } else if (backgroundColor != null) {
            applyBackgrounds(view, baseBackground, new ColorDrawable(backgroundColor.resolve(context)));
        }
    }

    private static void applyBackgrounds(@NonNull View view, @Nullable Drawable base, @NonNull Drawable drawable) {
        Drawable background = drawable;
        if (base != null) {
            background = new LayerDrawable(new Drawable[] { base, drawable });
        }
        view.setBackground(background);
    }

    public static void applyButtonLayoutModel(@NonNull FrameLayout button, @NonNull ButtonLayoutModel model) {
        TapEffect tapEffect = model.getViewInfo().getTapEffect();
        if (tapEffect instanceof TapEffect.Default) {
            Border border = model.getViewInfo().getBorder();
            Integer borderRadius = border != null ? border.getRadius() : null;
            applyRippleEffect(button, borderRadius);
        } else if (tapEffect instanceof TapEffect.None) {
            button.setForeground(null);
        }
    }

    private static RippleDrawable generateRippleDrawable(@NonNull Context context, @Nullable Integer borderRadius) {
        float radius = ResourceUtils.dpToPx(context, borderRadius != null ? borderRadius : 0);
        float[] outerRadii = new float[8];
        Arrays.fill(outerRadii, radius);
        ShapeDrawable mask = new ShapeDrawable(new RoundRectShape(outerRadii, null, null));
        ColorStateList colors = MaterialColors.getColorStateList(context,
                com.google.android.material.R.attr.colorControlHighlight,
                ColorStateList.valueOf(Color.TRANSPARENT)
        );

        return new RippleDrawable(colors, null, mask);
    }

    private static void applyRippleEffect(@NonNull FrameLayout frameLayout, @Nullable Integer borderRadius) {
        RippleDrawable ripple = generateRippleDrawable(frameLayout.getContext(), borderRadius);
        frameLayout.setForeground(ripple);
    }

    /** Applies a ripple effect and tint list to handle various interactions with an ImageButton button. */
    public static void applyImageButtonRippleAndTint(@NonNull ImageButton view, @Nullable Integer borderRadius) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            applyImageButtonRippleAndTintApi23(view, borderRadius);
        } else {
            applyImageButtonRippleAndTintCompat(view, borderRadius);
        }
    }

    /** Applies a ripple effect to the view's foreground and sets a disabled color for API 23 and above. */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static void applyImageButtonRippleAndTintApi23(@NonNull ImageButton view, @Nullable Integer borderRadius) {
        // Sets the view's foreground to a ripple drawable
        view.setForeground(generateRippleDrawable(view.getContext(), borderRadius));

        // Discard source pixels that don't overlap the destination pixels
        view.setImageTintMode(PorterDuff.Mode.SRC_ATOP);

        // Using transparent as the normal color means no tint unless the image is disabled
        int normalColor = Color.TRANSPARENT;
        ColorStateList compatStateList = new ColorStateListBuilder()
                .add(generateDisabledColor(normalColor), -android.R.attr.state_enabled)
                .add(normalColor)
                .build();

        view.setImageTintList(compatStateList);
    }

    /** Applies a compat tap effect that is similar to a ripple, and disabled/hover colors for API 22 and below. */
    private static void applyImageButtonRippleAndTintCompat(@NonNull ImageButton view, @Nullable Integer borderRadius) {
        // Discard source pixels that don't overlap the destination pixels
        view.setImageTintMode(PorterDuff.Mode.SRC_ATOP);

        // Using transparent as the color means no tint unless the image is pressed or disabled
        ColorStateList compatStateList = pressedColorStateList(Color.TRANSPARENT);

        view.setImageTintList(compatStateList);
    }


    public static void applyLabelModel(@NonNull TextView textView, @NonNull LabelModel label, @NonNull String text) {
        TextAppearance appearance = label.getViewInfo().getTextAppearance();

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

        Context context = textView.getContext();
        MarkdownOptions markdown = label.getViewInfo().getMarkdownOptions();
        boolean isMarkdownEnabled = MarkdownOptionsKt.isEnabled(markdown);

        if (isMarkdownEnabled) {
            boolean underlineLinks = MarkdownOptionsKt.getUnderlineLinks(markdown);
            @Nullable Integer linkColor = MarkdownOptionsKt.resolvedLinkColor(markdown, context);

            Spanned html = Html.fromHtml(StringExtensionsKt.markdownToHtml(text));
            ViewExtensionsKt.setHtml(textView, html, underlineLinks, linkColor);
        } else {
            textView.setText(text);
        }
    }

    public static void applyTextInputModel(@NonNull AppCompatEditText editText, @NonNull TextInputModel textInput) {
        applyTextAppearance(editText, textInput.getViewInfo().getTextAppearance());
        int padding = (int) dpToPx(editText.getContext(), 8);
        editText.setPadding(padding, padding, padding, padding);
        editText.setInputType(textInput.getViewInfo().getInputType().getTypeMask());
        editText.setSingleLine(textInput.getViewInfo().getInputType() != FormInputType.TEXT_MULTILINE);
        editText.setGravity(editText.getGravity() | Gravity.TOP);

        if (!UAStringUtil.isEmpty(textInput.getViewInfo().getHintText())) {
            editText.setHint(textInput.getViewInfo().getHintText());
            Color hintColor = textInput.getViewInfo().getTextAppearance().getHintColor();
            if (hintColor != null) {
                editText.setHintTextColor(hintColor.resolve(editText.getContext()));
            }
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
                .add(generateDisabledColor(checkedColor), android.R.attr.state_checked, -android.R.attr.state_enabled)
                .add(generateDisabledColor(normalColor), -android.R.attr.state_checked, -android.R.attr.state_enabled)
                .add(checkedColor, android.R.attr.state_checked)
                .add(normalColor)
                .build();
    }

    public static ColorStateList pressedColorStateList(@ColorInt int normalColor) {
        return new ColorStateListBuilder()
                .add(generatePressedColor(normalColor, Color.BLACK), android.R.attr.state_pressed)
                .add(generateHoveredColor(normalColor, Color.BLACK), android.R.attr.state_hovered)
                .add(generateDisabledColor(normalColor), -android.R.attr.state_enabled)
                .add(normalColor)
                .build();
    }

    public static void dismissSoftKeyboard(@NonNull View view) {
        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static void doOnAttachToWindow(@NonNull View view, @NonNull Runnable callback) {
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                v.removeOnAttachStateChangeListener(this);
                callback.run();
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) { /* no-op */ }
        });
    }

    public static void addPadding(@NonNull View view, int padding) {
        addPadding(view, padding, padding, padding, padding);
    }

    public static void removePadding(@NonNull View view, int padding) {
        addPadding(view, -padding, -padding, -padding, -padding);
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
    public static int generateHoveredColor(@ColorInt int baseColor) {
        return generateHoveredColor(baseColor, Color.WHITE);
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
    public static int generateHoveredColor(@ColorInt int background, @ColorInt int foreground) {
        return overlayColors(background, foreground, HOVERED_ALPHA_PERCENT);
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
