/* Copyright Airship and Contributors */

package com.urbanairship.widget;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.widget.FrameLayout;

import com.urbanairship.webkit.AirshipWebChromeClient;

import java.lang.ref.WeakReference;

/**
 * Web Chrome Client that enables full screen video.
 * @deprecated Use {@link AirshipWebChromeClient} instead.
 */
public class UAWebChromeClient extends AirshipWebChromeClient {

    /**
     * Default constructor.
     *
     * @param activity The activity.
     */
    public UAWebChromeClient(@Nullable Activity activity) {
        super(activity);
    }

}
