package com.urbanairship.iam.view;
/* Copyright 2017 Urban Airship and Contributors */


import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.ColorUtils;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.urbanairship.Logger;
import com.urbanairship.iam.ButtonInfo;
import com.urbanairship.iam.TextInfo;

public class InAppViewUtils {

    private static final float PRESSED_ALPHA_PERCENT = .2f;
    private static final int DEFAULT_STROKE_WIDTH_DPS = 2;

    /**
     * Applies button info to a button.
     *
     * @param button The button view.
     * @param buttonInfo The button info.
     * @param borderRadiusFlag The border radius flag.
     */
    public static void applyButtonInfo(@NonNull Button button, @NonNull ButtonInfo buttonInfo, @BorderRadius.BorderRadiusFlag int borderRadiusFlag) {
        applyTextInfo(button, buttonInfo.getLabel());

        int textColor = buttonInfo.getLabel().getColor();
        int pressedColor = ColorUtils.setAlphaComponent(textColor, Math.round(Color.alpha(textColor) * PRESSED_ALPHA_PERCENT));
        int strokeColor = buttonInfo.getBorderColor() == Color.TRANSPARENT ? buttonInfo.getBackgroundColor() : buttonInfo.getBorderColor();

        Drawable background = BackgroundDrawableBuilder.newBuilder(button.getContext())
                                                       .setBackgroundColor(buttonInfo.getBackgroundColor())
                                                       .setBorderRadius(buttonInfo.getBorderRadius(), borderRadiusFlag)
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

        if (textInfo.getDrawable() != 0) {
            int size = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textInfo.getFontSize(), textView.getResources().getDisplayMetrics()));

            try {
                Drawable drawable = ContextCompat.getDrawable(textView.getContext(), textInfo.getDrawable());
                drawable.setBounds(0, 0, size, size);
                drawable.setColorFilter(textInfo.getColor(), PorterDuff.Mode.MULTIPLY);
                String label = textInfo.getText() == null ? " " : "  " + textInfo.getText();
                CenteredImageSpan imageSpan = new CenteredImageSpan(drawable);
                SpannableString text = new SpannableString(label);
                text.setSpan(imageSpan, 0, 1, 0);
                textView.setText(text);
            } catch (Resources.NotFoundException e) {
                Logger.error("Unable to find button drawable.", e);
                textView.setText(textInfo.getText());
            }
        } else {
            textView.setText(textInfo.getText());
        }

        textView.setTextSize(textInfo.getFontSize());
        textView.setTextColor(textInfo.getColor());

        int typefaceFlags = textView.getTypeface().getStyle();
        for (@TextInfo.Style String style : textInfo.getStyles()) {
            switch (style) {
                case TextInfo.STYLE_BOLD:
                    typefaceFlags |= Typeface.BOLD;
                    break;
                case TextInfo.STYLE_ITALIC:
                    typefaceFlags |= Typeface.ITALIC;
                    break;
                case TextInfo.STYLE_UNDERLINE:
                    textView.setPaintFlags(textView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
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
        textView.setTypeface(textView.getTypeface(), typefaceFlags);
    }


    /**
     * Helper class that centers the image span vertically.
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
}
