/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.urbanairship.Fonts;
import com.urbanairship.android.layout.model.BaseModel;
import com.urbanairship.android.layout.model.LabelButtonModel;
import com.urbanairship.android.layout.model.LabelModel;
import com.urbanairship.android.layout.model.MediaModel;
import com.urbanairship.android.layout.model.TextInputModel;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.TextAppearance;
import com.urbanairship.android.layout.property.TextStyle;
import com.urbanairship.android.layout.view.MediaView;
import com.urbanairship.android.layout.widget.Clippable;
import com.urbanairship.util.UAStringUtil;

import java.lang.ref.WeakReference;
import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.graphics.ColorUtils;
import androidx.core.text.HtmlCompat;

public final class LayoutUtils {
    private static final float PRESSED_ALPHA_PERCENT = 0.2f;
    private static final int DEFAULT_STROKE_WIDTH_DPS = 2;
    private static final int DEFAULT_BORDER_RADIUS = 0;

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
            @Dimension float cornerRadius = border.getRadius() == null ? 0 : ResourceUtils.dpToPx(context, border.getRadius());
            ShapeAppearanceModel shapeModel = ShapeAppearanceModel.builder()
                                                                  .setAllCorners(CornerFamily.ROUNDED, cornerRadius)
                                                                  .build();
            MaterialShapeDrawable shapeDrawable = new MaterialShapeDrawable(shapeModel);

            if (view instanceof Clippable) {
                ((Clippable) view).setClipPathBorderRadius(cornerRadius);
            }

            if (border.getStrokeWidth() != null) {
                float strokeWidth = ResourceUtils.dpToPx(context, border.getStrokeWidth());
                shapeDrawable.setStrokeWidth(strokeWidth);
            }

            if (border.getStrokeColor() != null) {
                shapeDrawable.setStrokeColor(ColorStateList.valueOf(border.getStrokeColor().resolve(context)));
            }

            @ColorInt int fillColor = backgroundColor != null ? backgroundColor.resolve(context) : Color.TRANSPARENT;
            shapeDrawable.setFillColor(ColorStateList.valueOf(fillColor));

            view.setBackground(shapeDrawable);
        } else if (backgroundColor != null) {
            view.setBackgroundColor(backgroundColor.resolve(context));
        }
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
        int strokeWidth = model.getBorder() == null || model.getBorder().getStrokeWidth() == null
            ? DEFAULT_STROKE_WIDTH_DPS
            : model.getBorder().getStrokeWidth();
        int strokeColor = model.getBorder() == null || model.getBorder().getStrokeColor() == null
            ? backgroundColor
            : model.getBorder().getStrokeColor().resolve(context);
        int borderRadius = model.getBorder() == null || model.getBorder().getRadius() == null
            ? DEFAULT_BORDER_RADIUS
            : model.getBorder().getRadius();

        button.setTextColor(textColor);
        button.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        button.setRippleColor(ColorStateList.valueOf(pressedColor));
        button.setStrokeWidth((int) ResourceUtils.dpToPx(context, strokeWidth));
        button.setStrokeColor(ColorStateList.valueOf(strokeColor));
        button.setCornerRadius((int) ResourceUtils.dpToPx(context, borderRadius));
    }

    public static void applyLabelModel(@NonNull TextView textView, @NonNull LabelModel label) {
        Context context = textView.getContext();
        TextAppearance textAppearance = label.getTextAppearance();

        textView.setTextSize(textAppearance.getFontSize());
        textView.setTextColor(textAppearance.getColor().resolve(context));
        textView.setText(HtmlCompat.fromHtml(label.getText(), HtmlCompat.FROM_HTML_MODE_LEGACY));

        int typefaceFlags = textView.getTypeface() == null ? Typeface.NORMAL : textView.getTypeface().getStyle();
        int paintFlags = textView.getPaintFlags() | Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG;

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
        if (typeface == null) {
            typeface = textView.getTypeface();
        }

        textView.setTypeface(typeface, typefaceFlags);
        textView.setPaintFlags(paintFlags);
    }

    public static void applyTextInputModel(@NonNull AppCompatEditText editText, @NonNull TextInputModel textInput) {
        LayoutUtils.applyBorderAndBackground(editText, textInput);

        Context context = editText.getContext();
        TextAppearance textAppearance = textInput.getTextAppearance();

        editText.setTextSize(textAppearance.getFontSize());
        editText.setTextColor(textAppearance.getColor().resolve(context));
        editText.setHint(textInput.getHintText());
        editText.setInputType(textInput.getInputType().getTypeMask());

        int typefaceFlags = editText.getTypeface() == null ? Typeface.NORMAL : editText.getTypeface().getStyle();
        int paintFlags = editText.getPaintFlags() | Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG;

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

        Typeface typeface = getTypeFace(editText.getContext(), textAppearance.getFontFamilies());
        if (typeface == null) {
            typeface = editText.getTypeface();
        }

        if (!UAStringUtil.isEmpty(textInput.getContentDescription())) {
            editText.setContentDescription(textInput.getContentDescription());
        }

        editText.setTypeface(typeface, typefaceFlags);
        editText.setPaintFlags(paintFlags);
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
     * @param mediaView The media view. //* @param media The media info. //* @param assets The cached
     * assets.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void loadMediaInfo(@NonNull MediaView mediaView, @NonNull final MediaModel media) {
        // TODO: @Nullable final Assets assets
        if (mediaView.getWidth() == 0) {
            final WeakReference<MediaView> weakReference = new WeakReference<>(mediaView);
            mediaView.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        MediaView mediaView = weakReference.get();
                        if (mediaView != null) {
                            loadMediaInfo(mediaView, media); // TODO: , assets);
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

        // TODO: caching
        // String cachedLocation = null;
        //
        //        if (assets != null) {
        //            File cachedFile = assets.file(mediaInfo.getUrl());
        //            if (cachedFile.exists()) {
        //                JsonMap metadata = assets.getMetadata(mediaInfo.getUrl()).optMap();
        //                width =
        // metadata.opt(AirshipPrepareAssetsDelegate.IMAGE_WIDTH_CACHE_KEY).getInt(width);
        //                height =
        // metadata.opt(AirshipPrepareAssetsDelegate.IMAGE_HEIGHT_CACHE_KEY).getInt(height);
        //                cachedLocation = Uri.fromFile(cachedFile).toString();
        //            }
        //        }

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
        mediaView.setModel(media); // TODO: , cachedLocation);
    }
}
