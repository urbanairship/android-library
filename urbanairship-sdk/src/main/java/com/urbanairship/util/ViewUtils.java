/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

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
