/* Copyright Airship and Contributors */

package com.urbanairship.sample;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Autopilot;
import com.urbanairship.UAirship;
import com.urbanairship.liveupdate.LiveUpdateManager;
import com.urbanairship.messagecenter.MessageCenter;
import com.urbanairship.sample.glance.SampleAppWidgetLiveUpdate;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationManagerCompat;

import static androidx.core.app.NotificationManagerCompat.IMPORTANCE_HIGH;

/**
 * Autopilot that enables user notifications on first run.
 */
public class SampleAutopilot extends Autopilot {

    private static final String NO_BACKUP_PREFERENCES = "com.urbanairship.sample.no_backup";

    private static final String FIRST_RUN_KEY = "first_run";

    @Override
    public void onAirshipReady(@NonNull UAirship airship) {
        SharedPreferences preferences = UAirship.getApplicationContext().getSharedPreferences(NO_BACKUP_PREFERENCES, Context.MODE_PRIVATE);

        boolean isFirstRun = preferences.getBoolean(FIRST_RUN_KEY, true);
        if (isFirstRun) {
            preferences.edit().putBoolean(FIRST_RUN_KEY, false).apply();

            // Enable user notifications on first run
            airship.getPushManager().setUserNotificationsEnabled(true);
        }

        // Create notification channel for Live Updates.
        NotificationChannelCompat sportsChannel =
                new NotificationChannelCompat.Builder("sports", IMPORTANCE_HIGH)
                        .setDescription("Live sports updates!")
                        .setName("Sports!")
                        .setVibrationEnabled(false)
                        .build();

        Context context = UAirship.getApplicationContext();
        NotificationManagerCompat.from(context).createNotificationChannel(sportsChannel);

        // Register handlers for Live Updates.
        LiveUpdateManager.shared().register("sports", new SampleLiveUpdate());
        LiveUpdateManager.shared().register("sports-async", new SampleAsyncLiveUpdate());
        LiveUpdateManager.shared().register("medals-widget", new SampleAppWidgetLiveUpdate());

        // TODO(m3-inbox): Not sure we need to do this any more... we're not using multi-nav-hosts
        //   any more, and we could potentially use the new MessageCenterView instead of separate
        //   fragments for the inbox and message views. Starting a new task makes it so
        //   that the bottom nav bar flickers when the message center is opened, so we'll need to at
        //   least fix that if we keep the separate fragments.
        MessageCenter.shared().setOnShowMessageCenterListener(messageId -> {
            // Use an implicit navigation deep link for now as explicit deep links are broken
            // with multi navigation host fragments
            Uri uri;
            if (messageId != null) {
                uri = Uri.parse("vnd.urbanairship.sample://deepLink/inbox/message/" + messageId);
            } else {
                uri = Uri.parse("vnd.urbanairship.sample://deepLink/inbox");
            }

            Intent intent = new Intent(Intent.ACTION_VIEW, uri)
                    .setPackage(UAirship.getPackageName())
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            UAirship.getApplicationContext().startActivity(intent);
            return true;
        });

        AirshipListener airshipListener = new AirshipListener();
        airship.getPushManager().addPushListener(airshipListener);
        airship.getPushManager().addPushTokenListener(airshipListener);
        airship.getPushManager().setNotificationListener(airshipListener);
        airship.getChannel().addChannelListener(airshipListener);

        // Register the "squareview" InApp Message Content Extender
        SampleInAppMessageContentExtender.register();
    }

    @Nullable
    @Override
    public AirshipConfigOptions createAirshipConfigOptions(@NonNull Context context) {
        /*
          Optionally, customize your config at runtime:

             AirshipConfigOptions options = new AirshipConfigOptions.Builder()
                    .setInProduction(!BuildConfig.DEBUG)
                    .setDevelopmentAppKey("Your Development App Key")
                    .setDevelopmentAppSecret("Your Development App Secret")
                    .setProductionAppKey("Your Production App Key")
                    .setProductionAppSecret("Your Production App Secret")
                    .setNotificationAccentColor(ContextCompat.getColor(context, R.color.color_accent))
                    .setNotificationIcon(R.drawable.ic_airship)
                    .build();

            return options;
         */

        // defaults to loading config from airshipconfig.properties file
        return super.createAirshipConfigOptions(context);
    }

}
