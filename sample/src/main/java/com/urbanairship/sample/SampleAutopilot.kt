/* Copyright Airship and Contributors */
package com.urbanairship.sample

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.analytics.CustomEvent
import com.urbanairship.analytics.EventType
import com.urbanairship.json.jsonMapOf
import com.urbanairship.liveupdate.LiveUpdateManager.Companion.shared
import com.urbanairship.messagecenter.MessageCenter
import com.urbanairship.push.pushNotificationStatusFlow
import com.urbanairship.sample.SampleInAppMessageContentExtender.Companion.register
import com.urbanairship.sample.glance.SampleAppWidgetLiveUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * Autopilot that enables user notifications on first run.
 */
class SampleAutopilot : Autopilot() {

    override fun onAirshipReady(airship: UAirship) {
        val preferences = UAirship.getApplicationContext().getSharedPreferences(
            NO_BACKUP_PREFERENCES, Context.MODE_PRIVATE
        )

        val isFirstRun = preferences.getBoolean(FIRST_RUN_KEY, true)
        if (isFirstRun) {
            preferences.edit().putBoolean(FIRST_RUN_KEY, false).apply()

            // Enable user notifications on first run
            airship.pushManager.userNotificationsEnabled = true
        }

        // Create notification channel for Live Updates.
        val sportsChannel =
            NotificationChannelCompat.Builder("sports", NotificationManagerCompat.IMPORTANCE_HIGH)
                .setDescription("Live sports updates!").setName("Sports!")
                .setVibrationEnabled(false).build()

        val context = UAirship.getApplicationContext()
        NotificationManagerCompat.from(context).createNotificationChannel(sportsChannel)

        // Register handlers for Live Updates.
        shared().register("sports", SampleLiveUpdate())
        shared().register("sports-async", SampleAsyncLiveUpdate())
        shared().register("medals-widget", SampleAppWidgetLiveUpdate())

        airship.channel.editTags().addTag("joshdroid").apply()
        
        MessageCenter.shared().setOnShowMessageCenterListener { messageId: String? ->
            // Use an implicit navigation deep link for now as explicit deep links are broken
            // with multi navigation host fragments
            val uri = if (messageId != null) {
                Uri.parse("vnd.urbanairship.sample://deepLink/inbox/message/$messageId")
            } else {
                Uri.parse("vnd.urbanairship.sample://deepLink/inbox")
            }

            val intent = Intent(Intent.ACTION_VIEW, uri).setPackage(UAirship.getPackageName())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            UAirship.getApplicationContext().startActivity(intent)
            true
        }

        MainScope().launch {
            airship.pushManager.pushNotificationStatusFlow.collect {
                UALog.e("Updated $it")
            }
        }

        val airshipListener = AirshipListener()
        airship.pushManager.addPushListener(airshipListener)
        airship.pushManager.addPushTokenListener(airshipListener)
        airship.pushManager.notificationListener = airshipListener
        airship.channel.addChannelListener(airshipListener)

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            UAirship.shared().analytics.events
                .filter { it.type == EventType.CUSTOM_EVENT }
                .collect { event ->
                    // Do what you need to do, probably want the event.body. Event
                    // value in a custom event is multiplied by 1000000 to preserve
                    // decimals during storage, so to get the original value you
                    // have to divide the value.
                    print("Custom event: $event)")
                }
        }

        val event = CustomEvent.newBuilder("my-cool-event")
            .setEventValue(100)
            .setProperties(
                jsonMapOf(
                    "my" to "property"
                )
            )
            .build()
        UAirship.shared().analytics.recordCustomEvent(event)

        // Register the "squareview" InApp Message Content Extender
        register()
    }

    override fun createAirshipConfigOptions(context: Context): AirshipConfigOptions? {/*
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
