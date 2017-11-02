/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import com.urbanairship.iam.TextInfo;


/**
 * In-app message text view.
 */
@SuppressLint("AppCompatCustomView")
public class InAppTextView extends TextView {

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     */
    public InAppTextView(Context context) {
        super(context);
    }

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     */
    public InAppTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     */
    public InAppTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     * @param defResStyle A resource identifier of a style resource that supplies default values for
     * the view, used only if defStyle is 0 or cannot be found in the theme. Can be 0 to not
     * look for defaults.
     */
    public InAppTextView(Context context, AttributeSet attrs, int defStyle, int defResStyle) {
        super(context, attrs, defStyle, defResStyle);
    }


    /**
     * Sets the text info.
     *
     * @param textInfo The text info.
     */
    public void setTextInfo(TextInfo textInfo) {
        setText(textInfo.getText());
        setTextSize(textInfo.getFontSize());
        setTextColor(textInfo.getColor());

        int typefaceFlags = Typeface.NORMAL;
        for (@TextInfo.Style String style : textInfo.getStyles()) {
            switch (style) {
                case TextInfo.STYLE_BOLD:
                    typefaceFlags |= Typeface.BOLD;
                    break;
                case TextInfo.STYLE_ITALIC:
                    typefaceFlags |= Typeface.ITALIC;
                    break;
                case TextInfo.STYLE_UNDERLINE:
                    setPaintFlags(getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                    break;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            switch (textInfo.getAlignment()) {
                case TextInfo.ALIGNMENT_CENTER:
                    setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                    break;

                case TextInfo.ALIGNMENT_LEFT:
                    setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                    break;

                case TextInfo.ALIGNMENT_RIGHT:
                    setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
                    break;
            }
        }
        setTypeface(getTypeface(), typefaceFlags);
    }
}
