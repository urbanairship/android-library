/* Copyright 2018 Urban Airship and Contributors */

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
import android.support.annotation.RestrictTo;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.ColorUtils;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.ViewCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.ImageSpan;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.TextView;

import com.urbanairship.Fonts;
import com.urbanairship.Logger;
import com.urbanairship.iam.ButtonInfo;
import com.urbanairship.iam.InAppMessageCache;
import com.urbanairship.iam.MediaInfo;
import com.urbanairship.iam.TextInfo;
import com.urbanairship.util.UAStringUtil;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * In-app view utils.
 *
 * @hide
 */
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

        ViewCompat.setBackground(button, background);
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
                    text = new SpannableString("  " + textInfo.getText());
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

        if (textInfo.getAlignment() != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
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

    /**
     * Loads the media info into the media view and scales the media's views height to match the
     * aspect ratio of the media. If the aspect ratio is unavailable in the cache, 16:9 will be used.
     *
     * @param mediaView The media view.
     * @param mediaInfo The media info.
     * @param cache THe cache containing the cached image and/or height/width info.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void loadMediaInfo(MediaView mediaView, final MediaInfo mediaInfo, final InAppMessageCache cache) {
        if (mediaView.getWidth() == 0) {
            final WeakReference<MediaView> weakReference = new WeakReference<>(mediaView);
            mediaView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    MediaView mediaView = weakReference.get();
                    if (mediaView != null) {
                        loadMediaInfo(mediaView, mediaInfo, cache);
                        mediaView.getViewTreeObserver().removeOnPreDrawListener(this);
                    }
                    return false;
                }
            });
            return;
        }

        // Default to a 16:9 aspect ratio
        int width = 16;
        int height = 9;

        String cachedLocation = null;

        if (cache != null) {
            width = cache.getBundle().getInt(InAppMessageCache.IMAGE_WIDTH_CACHE_KEY, width);
            height = cache.getBundle().getInt(InAppMessageCache.IMAGE_HEIGHT_CACHE_KEY, height);
            cachedLocation = cache.getBundle().getString(InAppMessageCache.MEDIA_CACHE_KEY);
        }

        ViewGroup.LayoutParams params = mediaView.getLayoutParams();


        // Check if we can grow the image horizontally to fit the width
        if (params.height == ViewGroup.LayoutParams.WRAP_CONTENT) {
            float scale = (float) mediaView.getWidth() / (float) width;
            params.height = Math.round(scale * height);
        } else {
            float imageRatio = (float) width / (float) height;
            float viewRatio = (float) mediaView.getWidth() / mediaView.getHeight();

            if (imageRatio >= viewRatio) {
                // Image is wider than the view
                params.height = Math.round(mediaView.getWidth() / imageRatio);
            } else {
                // View is wider than the image
                params.width = Math.round(mediaView.getHeight() * imageRatio);
            }
        }

        mediaView.setLayoutParams(params);
        mediaView.setMediaInfo(mediaInfo, cachedLocation);
    }
}
