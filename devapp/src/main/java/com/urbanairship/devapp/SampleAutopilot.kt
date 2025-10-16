/* Copyright Airship and Contributors */
package com.urbanairship.devapp

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_HIGH
import androidx.core.net.toUri
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.Autopilot
import com.urbanairship.Airship
import com.urbanairship.liveupdate.LiveUpdateManager
import com.urbanairship.messagecenter.MessageCenter
import com.urbanairship.devapp.glance.SampleAppWidgetLiveUpdate
import androidx.core.content.edit
import com.urbanairship.android.layout.AirshipCustomViewManager
import com.urbanairship.devapp.thomas.customviews.CustomAdView
import com.urbanairship.devapp.thomas.customviews.CustomMapView
import com.urbanairship.devapp.thomas.customviews.CustomWeatherView
import com.urbanairship.devapp.thomas.customviews.CustomWeatherViewXml
import com.urbanairship.devapp.thomas.customviews.SceneControllerCustomView
import com.urbanairship.messagecenter.messageCenter

/**
 * Autopilot that enables user notifications on first run.
 */
class SampleAutopilot : Autopilot() {

    override fun onAirshipReady(context: Context) {

        val preferences = context.getSharedPreferences(NO_BACKUP_PREFERENCES, MODE_PRIVATE)

        val isFirstRun = preferences.getBoolean(FIRST_RUN_KEY, true)
        if (isFirstRun) {
            preferences.edit { putBoolean(FIRST_RUN_KEY, false) }

            // Enable user notifications on first run
            Airship.push.userNotificationsEnabled = true
        }

        // Create notification channel for Live Updates.
        val sportsChannel = NotificationChannelCompat.Builder("sports", IMPORTANCE_HIGH)
            .setDescription("Sports updates!")
            .setName("Sports!")
            .setVibrationEnabled(false)
            .build()

        val deliveryChannel = NotificationChannelCompat.Builder("delivery", IMPORTANCE_HIGH)
            .setDescription("Delivery updates!")
            .setName("Delivery")
            .setVibrationEnabled(false)
            .build()

        NotificationManagerCompat.from(context).apply {
            createNotificationChannel(sportsChannel)
            createNotificationChannel(deliveryChannel)
        }

        // Register handlers for Live Updates.
        with(LiveUpdateManager.shared()) {
            register("sports", SampleLiveUpdate())
            register("sports-async", SampleAsyncLiveUpdate())
            register("medals-widget", SampleAppWidgetLiveUpdate())
            register("delivery", SampleDeliveryLiveUpdate())
        }

        // Register custom views
        AirshipCustomViewManager.register("weather_custom_view_xml") { data ->
            CustomWeatherViewXml(context).apply {
                bind(data)
            }
        }
        AirshipCustomViewManager.register("weather_custom_view", CustomWeatherView())
        AirshipCustomViewManager.register("ad_custom_view", CustomAdView())
        AirshipCustomViewManager.register("map_custom_view", CustomMapView())
        AirshipCustomViewManager.register("scene_controller_test", SceneControllerCustomView())

        // Set up message center deep link handling
        Airship.messageCenter.setOnShowMessageCenterListener { messageId: String? ->
            // Use an implicit navigation deep link for now as explicit deep links are broken
            // with multi navigation host fragments
            val uri = if (messageId != null) {
               "vnd.urbanairship.sample://deepLink/inbox/message/$messageId"
            } else {
                "vnd.urbanairship.sample://deepLink/inbox"
            }.toUri()

            val intent = Intent(Intent.ACTION_VIEW, uri)
                .setPackage(context.packageName)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            Airship.application.startActivity(intent)
            true
        }

        val airshipListener = AirshipListener()

        with(Airship.push) {
            addPushListener(airshipListener)
            addPushTokenListener(airshipListener)
            notificationListener = airshipListener
        }

        Airship.channel.addChannelListener(airshipListener)

        // Register the "squareview" InApp Message Content Extender
        SampleInAppMessageContentExtender.register()
    }

    override fun createAirshipConfigOptions(context: Context): AirshipConfigOptions? {
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
        return super.createAirshipConfigOptions(context)
    }

    companion object {
        private const val NO_BACKUP_PREFERENCES = "com.urbanairship.sample.no_backup"
        private const val FIRST_RUN_KEY = "first_run"
    }
}
