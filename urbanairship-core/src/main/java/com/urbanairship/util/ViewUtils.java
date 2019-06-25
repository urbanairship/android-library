/* Copyright Airship and Contributors */

package com.urbanairship.util;

import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.StyleRes;
import android.widget.TextView;

/**
 * View utility methods.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
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
        if (textAppearance != 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                textView.setTextAppearance(textAppearance);
            } else {
                //noinspection deprecation
                textView.setTextAppearance(context, textAppearance);
            }
        }
    }

}
