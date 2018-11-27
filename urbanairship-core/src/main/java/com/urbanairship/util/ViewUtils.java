/* Copyright 2018 Urban Airship and Contributors */

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
     */
    public static void applyTextStyle(@NonNull Context context, @NonNull TextView textView, @StyleRes int textAppearance) {
        // Apply text appearance first before the color or type face.
        if (textAppearance != -1) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                textView.setTextAppearance(textAppearance);
            } else {
                //noinspection deprecation
                textView.setTextAppearance(context, textAppearance);
            }
        }
    }
}
