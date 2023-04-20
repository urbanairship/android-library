/* Copyright Airship and Contributors */

package com.urbanairship.sample;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.StrictMode;
import android.webkit.WebView;

import com.urbanairship.UAirship;

import java.util.Locale;

import androidx.annotation.NonNull;

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

        Locale userLocalePreference = Locale.FRANCE;

        setAppLocale(userLocalePreference);

    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(setContextLocale(base, Locale.FRANCE));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        newConfig.setLocale(Locale.FRANCE);
        super.onConfigurationChanged(newConfig);
    }

    private void setAppLocale(@NonNull Locale locale) {
        Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        // Update the locale stored in the current resource configuration.
        config.setLocale(locale);

        // Update the app's configuration to reflect the locale change.
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N) {
            getApplicationContext().createConfigurationContext(config);
        } else {
            resources.updateConfiguration(config, resources.getDisplayMetrics());
        }

        // Also set the Airship locale override, to use the preferred local for content fetched via the Airship API.
        UAirship.shared().getLocaleManager().setLocaleOverride(locale);
    }

    public static Context setContextLocale(Context context, Locale locale)
    {
        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        config.setLocale(locale);
        context = context.createConfigurationContext(config);
        return context;
    }
}
