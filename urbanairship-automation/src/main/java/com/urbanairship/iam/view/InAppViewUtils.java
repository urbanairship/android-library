/* Copyright Airship and Contributors */

package com.urbanairship.iam.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.ImageSpan;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.TextView;

import com.urbanairship.Fonts;
import com.urbanairship.Logger;
import com.urbanairship.iam.ButtonInfo;
import com.urbanairship.iam.MediaInfo;
import com.urbanairship.iam.TextInfo;
import com.urbanairship.iam.assets.AirshipPrepareAssetsDelegate;
import com.urbanairship.iam.assets.Assets;
import com.urbanairship.json.JsonMap;
import com.urbanairship.util.UAStringUtil;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;

/**
 * In-app view utils.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
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

        Drawable drawable = null;
        @DrawableRes int drawableId = textInfo.getDrawable(textView.getContext());
        if (drawableId != 0) {
            try {
                drawable = ContextCompat.getDrawable(textView.getContext(), drawableId);
            } catch (android.content.res.Resources.NotFoundException e) {
                Logger.debug("Drawable " + drawableId + " no longer exists.");
            }
        }

        if (drawable != null) {
            int size = Math.round(textView.getTextSize());
            int color = textView.getCurrentTextColor();

            try {
                Drawable wrappedDrawable = DrawableCompat.wrap(drawable).mutate();
                wrappedDrawable.setBounds(0, 0, size, size);
                wrappedDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));

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
                Logger.error(e, "Unable to find button drawable.");
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

        if (textInfo.getAlignment() != null) {
            switch (textInfo.getAlignment()) {
                case TextInfo.ALIGNMENT_CENTER:
                    textView.setGravity(Gravity.CENTER_HORIZONTAL);
                    break;

                case TextInfo.ALIGNMENT_LEFT:
                    textView.setGravity(Gravity.START);
                    break;

                case TextInfo.ALIGNMENT_RIGHT:
                    textView.setGravity(Gravity.END);
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
        public void updateDrawState(@NonNull TextPaint textPaint) {
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
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
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
     * @param assets The cached assets.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void loadMediaInfo(@NonNull MediaView mediaView, @NonNull final MediaInfo mediaInfo, @Nullable final Assets assets) {
        if (mediaView.getWidth() == 0) {
            final WeakReference<MediaView> weakReference = new WeakReference<>(mediaView);
            mediaView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    MediaView mediaView = weakReference.get();
                    if (mediaView != null) {
                        loadMediaInfo(mediaView, mediaInfo, assets);
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

        if (assets != null) {
            File cachedFile = assets.file(mediaInfo.getUrl());
            if (cachedFile.exists()) {
                JsonMap metadata = assets.getMetadata(mediaInfo.getUrl()).optMap();
                width = metadata.opt(AirshipPrepareAssetsDelegate.IMAGE_WIDTH_CACHE_KEY).getInt(width);
                height = metadata.opt(AirshipPrepareAssetsDelegate.IMAGE_HEIGHT_CACHE_KEY).getInt(height);
                cachedLocation = Uri.fromFile(cachedFile).toString();
            }
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

    /**
     * Returns the largest child Z value in the view group.
     *
     * @param group The view group.
     * @return The largest child Z value in the view group.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static float getLargestChildZValue(@NonNull ViewGroup group) {
        float z = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            z = Math.max(group.getChildAt(0).getZ(), z);
        }
        return z;
    }

}
