package com.urbanairship.widget;

import android.content.Context;
import android.util.AttributeSet;

import com.urbanairship.messagecenter.MessageWebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A web view that sets settings appropriate for Airship content.
 *
 * @deprecated Use {@link AirshipWebView} or {@link MessageWebView} instead.
 */
@Deprecated
public class UAWebView extends MessageWebView {

    /**
     * UAWebView Constructor
     *
     * @param context A Context object used to access application assets.
     */
    public UAWebView(@NonNull Context context) {
        super(context);
    }

    /**
     * UAWebView Constructor
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     */
    public UAWebView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * UAWebView Constructor
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     */
    public UAWebView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * UAWebView Constructor
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     * @param defResStyle A resource identifier of a style resource that supplies default values for
     * the view, used only if defStyle is 0 or cannot be found in the theme. Can be 0 to not
     */
    public UAWebView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle, int defResStyle) {
        super(context, attrs, defStyle, defResStyle);
    }
}
