/* Copyright Airship and Contributors */

package com.urbanairship.webkit;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Message;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.urbanairship.Logger;
import com.urbanairship.javascript.NativeBridge;
import com.urbanairship.util.UriUtils;

import java.lang.ref.WeakReference;

/**
 * Web Chrome Client that enables full screen video.
 */
public class AirshipWebChromeClient extends WebChromeClient {

    private final WeakReference<Activity> weakActivity;
    private View customView;

    /**
     * Default constructor.
     *
     * @param activity The activity.
     */
    public AirshipWebChromeClient(@Nullable Activity activity) {
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

    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
        if (isUserGesture && resultMsg != null && resultMsg.obj instanceof WebView.WebViewTransport) {
            WebView tempWebView = new WebView(view.getContext());

            tempWebView.setWebViewClient(new WebViewClient(){
                @Override
                public boolean shouldOverrideUrlLoading (WebView view, String url) {
                    if (url != null) {
                        Uri uri = Uri.parse(url);

                        if (uri.getHost() == null || NativeBridge.UA_ACTION_SCHEME.equals(uri.getScheme())) {
                            return false;
                        }

                        Intent intent = new Intent(Intent.ACTION_VIEW, UriUtils.parse(url));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            view.getContext().startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            Logger.error(e);
                        }
                    }
                    return true;
                }
            });

            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(tempWebView);
            resultMsg.sendToTarget();

            return true;
        }

        return false;
    }

}
