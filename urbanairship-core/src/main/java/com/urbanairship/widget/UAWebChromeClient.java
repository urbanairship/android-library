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

import java.lang.ref.WeakReference;

/**
 * Web Chrome Client that enables full screen video.
 */
public class UAWebChromeClient extends WebChromeClient {

    private final WeakReference<Activity> weakActivity;
    private View customView;

    /**
     * Default constructor.
     *
     * @param activity The activity.
     */
    public UAWebChromeClient(@Nullable Activity activity) {
        this.weakActivity = new WeakReference<>(activity);
    }

    @Nullable
    @Override
    public Bitmap getDefaultVideoPoster() {
        return Bitmap.createBitmap(new int[] { Color.TRANSPARENT }, 1, 1, Bitmap.Config.ARGB_8888);
    }

    @Override
    public void onShowCustomView(@NonNull View view, @NonNull final CustomViewCallback callback) {
        Activity activity = weakActivity.get();
        if (activity == null) {
            return;
        }

        if (customView != null) {
            ViewGroup parent = (ViewGroup) customView.getParent();
            parent.removeView(customView);
        }

        customView = view;
        customView.setBackgroundColor(Color.BLACK);

        activity.getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        activity.getWindow().addContentView(customView,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER));
    }

    @Override
    public void onHideCustomView() {
        Activity activity = weakActivity.get();
        if (activity == null || customView == null) {
            return;
        }

        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        ViewGroup parent = (ViewGroup) customView.getParent();
        parent.removeView(customView);
        this.customView = null;
    }

}