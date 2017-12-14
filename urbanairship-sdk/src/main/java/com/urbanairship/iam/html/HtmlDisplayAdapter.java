/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam.html;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.annotation.NonNull;

import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;
import com.urbanairship.iam.fullscreen.FullScreenActivity;

/**
 * Html display adapter.
 */
public class HtmlDisplayAdapter implements InAppMessageAdapter {

    private final InAppMessage message;

    /**
     * Default constructor.
     *
     * @param message The HTML in-app message.
     */
    public HtmlDisplayAdapter(InAppMessage message) {
        this.message = message;
    }

    @Override
    public int onPrepare(@NonNull Context context) {
        return isNetworkAvailable(context) ? OK : RETRY;
    }

    @Override
    public boolean onDisplay(@NonNull Activity activity, boolean isRedisplay, DisplayHandler displayHandler) {
        if (!isNetworkAvailable(activity)) {
            return false;
        }

        Intent intent = new Intent(activity, HtmlActivity.class)
                .putExtra(FullScreenActivity.DISPLAY_HANDLER_EXTRA_KEY, displayHandler)
                .putExtra(FullScreenActivity.IN_APP_MESSAGE_KEY, message);

        activity.startActivity(intent);

        return true;
    }

    @Override
    public void onFinish() {}

    /**
     * Helper method to check for network connectivity.
     *
     * @param context The application context.
     * @return {@code true} if network connectivity is available, otherwise {@code false}.
     */
    private static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }

        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
}
