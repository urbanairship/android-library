/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;
import android.widget.TextView;

import com.urbanairship.Logger;
import com.urbanairship.R;

/**
 * View utility methods.
 */
public final class ViewUtils {

    /**
     * Helper method to apply custom text view styles.
     *
     * @param context The application context.
     * @param textView The text view.
     * @param textAppearance Optional text appearance.
     * @param typeface Optional typeface.
     */
    public static void applyTextStyle(@NonNull Context context, @NonNull TextView textView, @StyleRes int textAppearance, @Nullable Typeface typeface) {
        // Apply text appearance first before the color or type face.
        if (textAppearance != -1) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                textView.setTextAppearance(textAppearance);
            } else {
                //noinspection deprecation
                textView.setTextAppearance(context, textAppearance);
            }
        }

        // Called after setting the text appearance so we can keep style defined in the text appearance
        if (typeface != null) {
            int style = -1;
            if (textView.getTypeface() != null) {
                style = textView.getTypeface().getStyle();
            }

            textView.setPaintFlags(textView.getPaintFlags() | Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

            if (style >= 0) {
                textView.setTypeface(typeface, style);
            } else {
                textView.setTypeface(typeface);
            }
        }
    }

    /**
     * Creates a typeface defined by the attribute {@code urbanAirshipFontPath} from the textAppearance style.
     * @param context The application context.
     * @param textAppearance The text appearance style.
     * @return The defined Typeface or null if the text appearance does not define the {@code urbanAirshipFontPath} or
     * fails to load the typeface.
     */
    @Nullable
    public static Typeface createTypeface(@NonNull Context context, @StyleRes int textAppearance) {
        TypedArray attributes = context.getTheme().obtainStyledAttributes(textAppearance, R.styleable.TextAppearance);

        String fontPath = attributes.getString(R.styleable.TextAppearance_urbanAirshipFontPath);
        if (!UAStringUtil.isEmpty(fontPath)) {
            try {
                return Typeface.createFromAsset(context.getAssets(), fontPath);
            } catch (RuntimeException e) {
                Logger.error("Failed to load font path: " + fontPath);
            }
        }

        return null;
    }
}
