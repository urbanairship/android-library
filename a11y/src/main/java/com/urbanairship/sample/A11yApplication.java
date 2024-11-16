/* Copyright Airship and Contributors */

package com.urbanairship.sample;

import android.app.Application;
import android.os.StrictMode;
import android.webkit.WebView;

public class A11yApplication extends Application {

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
