/* Copyright Airship and Contributors */

package com.urbanairship.devapp;

import android.app.Application;
import android.os.StrictMode;
import android.webkit.WebView;

public class SampleApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());

            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());

            WebView.setWebContentsDebuggingEnabled(true);
        }
    }

}
