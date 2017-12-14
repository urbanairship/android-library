/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.ColorUtils;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.ImageSpan;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.urbanairship.Fonts;
import com.urbanairship.Logger;
import com.urbanairship.iam.ButtonInfo;
import com.urbanairship.iam.TextInfo;
import com.urbanairship.util.UAStringUtil;

import java.util.List;

public class InAppViewUtils {

    private static final float PRESSED_ALPHA_PERCENT = .2f;
    private static final int DEFAULT_STROKE_WIDTH_DPS = 2;
    private static final float DEFAULT_BORDER_RADIUS = 0;

    /**
     * Applies button info to a button.
     *
     * @param button The button view.
     * @param buttonInfo The button info.
     * @param borderRadiusFlag The border radius flag.
     */
    public static void applyButtonInfo(@NonNull Button button, @NonNull ButtonInfo buttonInfo, @BorderRadius.BorderRadiusFlag int borderRadiusFlag) {
        applyTextInfo(button, buttonInfo.getLabel());

        int textColor = buttonInfo.getLabel().getColor() == null ? button.getCurrentTextColor() : buttonInfo.getLabel().getColor();
        int backgroundColor = buttonInfo.getBackgroundColor() == null ? Color.TRANSPARENT : buttonInfo.getBackgroundColor();
        int pressedColor = ColorUtils.setAlphaComponent(textColor, Math.round(Color.alpha(textColor) * PRESSED_ALPHA_PERCENT));
        int strokeColor = buttonInfo.getBorderColor() == null ? backgroundColor : buttonInfo.getBorderColor();

        float borderRadius = buttonInfo.getBorderRadius() == null ? DEFAULT_BORDER_RADIUS : buttonInfo.getBorderRadius();

        Drawable background = BackgroundDrawableBuilder.newBuilder(button.getContext())
                                                       .setBackgroundColor(backgroundColor)
                                                       .setBorderRadius(borderRadius, borderRadiusFlag)
                                                       .setPressedColor(pressedColor)
                                                       .setStrokeColor(strokeColor)
                                                       .setStrokeWidth(DEFAULT_STROKE_WIDTH_DPS)
                                                       .build();

        button.setBackground(background);
    }

    /**
     * Applies text info to a text view.
     *
     * @param textView The text view.
     * @param textInfo The text info.
     */
    public static void applyTextInfo(@NonNull TextView textView, @NonNull TextInfo textInfo) {

        if (textInfo.getFontSize() != null) {
            textView.setTextSize(textInfo.getFontSize());
        }

        if (textInfo.getColor() != null) {
            textView.setTextColor(textInfo.getColor());
        }

        if (textInfo.getDrawable() != 0) {

            int size = Math.round(textView.getTextSize());
            int color = textView.getCurrentTextColor();

            try {
                Drawable drawable = ContextCompat.getDrawable(textView.getContext(), textInfo.getDrawable());
                Drawable wrappedDrawable = DrawableCompat.wrap(drawable).mutate();
                wrappedDrawable.setBounds(0, 0, size, size);
                wrappedDrawable.setColorFilter(color, PorterDuff.Mode.MULTIPLY);

                CenteredImageSpan imageSpan = new CenteredImageSpan(wrappedDrawable);
                SpannableString text;

                if (textInfo.getText() == null) {
                    text = new SpannableString(" ");
                    text.setSpan(imageSpan, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    text = new SpannableString( "  " + textInfo.getText());
                    text.setSpan(imageSpan, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    text.setSpan(new RemoveUnderlineSpan(), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                textView.setText(text);
            } catch (Resources.NotFoundException e) {
                Logger.error("Unable to find button drawable.", e);
                textView.setText(textInfo.getText());
            }
        } else {
            textView.setText(textInfo.getText());
        }



        int typefaceFlags = textView.getTypeface() == null ? Typeface.NORMAL : textView.getTypeface().getStyle();
        int paintFlags = textView.getPaintFlags() | Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG;

        for (@TextInfo.Style String style : textInfo.getStyles()) {
            switch (style) {
                case TextInfo.STYLE_BOLD:
                    typefaceFlags |= Typeface.BOLD;
                    break;
                case TextInfo.STYLE_ITALIC:
                    typefaceFlags |= Typeface.ITALIC;
                    break;
                case TextInfo.STYLE_UNDERLINE:
                    paintFlags |= Paint.UNDERLINE_TEXT_FLAG;
                    break;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            switch (textInfo.getAlignment()) {
                case TextInfo.ALIGNMENT_CENTER:
                    textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                    break;

                case TextInfo.ALIGNMENT_LEFT:
                    textView.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                    break;

                case TextInfo.ALIGNMENT_RIGHT:
                    textView.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
                    break;
            }
        }


        Typeface typeface = getTypeFace(textView.getContext(), textInfo.getFontFamilies());
        if (typeface == null) {
            typeface = textView.getTypeface();
        }

        textView.setTypeface(typeface, typefaceFlags);
        textView.setPaintFlags(paintFlags);
    }

    /**
     * Span that removes underline.
     */
    private static class RemoveUnderlineSpan extends CharacterStyle {
        @Override
        public void updateDrawState(TextPaint textPaint) {
            textPaint.setUnderlineText(false);
        }
    }

    /**
     * Centered image span.
     */
    private static class CenteredImageSpan extends ImageSpan {

        public CenteredImageSpan(Drawable drawable) {
            super(drawable);
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            canvas.save();

            Drawable drawable = getDrawable();

            int dy = bottom - drawable.getBounds().bottom - paint.getFontMetricsInt().descent / 2;
            canvas.translate(x, dy);
            drawable.draw(canvas);

            canvas.restore();
        }
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


}
