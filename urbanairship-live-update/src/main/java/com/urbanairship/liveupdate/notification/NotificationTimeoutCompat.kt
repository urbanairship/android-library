package com.urbanairship.liveupdate.notification

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import com.urbanairship.Airship
import com.urbanairship.util.Clock
import com.urbanairship.util.PendingIntentCompat

/**
 * Compat helper that sets the timeout for a notification.
 *
 * On O+, the timeout is set using [NotificationCompat.Builder.setTimeoutAfter].
 * On earlier versions, an alarm is set to dismiss the notification.
 */
internal class NotificationTimeoutCompat(
    private val context: Context = Airship.application,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
) {
    private val alarmManager: AlarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    internal fun setTimeoutAt(
        builder: NotificationCompat.Builder,
        timeoutAt: Long,
        name: String
    ): NotificationCompat.Builder {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use the NotificationCompat.Builder APIs to set the timeout, if we're on O+.
            val timeoutAfter = timeoutAt - clock.currentTimeMillis()
            builder.setTimeoutAfter(timeoutAfter)
        } else {
            // Otherwise, fall back to setting an alarm to dismiss the notification.
            setTimeoutAlarm(name, timeoutAt)
        }
        return builder
    }

    private fun setTimeoutAlarm(name: String, timeoutAt: Long) {
        val intent = LiveUpdateNotificationReceiver.timeoutCompatIntent(context, name)
        val operation = PendingIntentCompat.getBroadcast(context, 0, intent, 0)

        AlarmManagerCompat.setExact(alarmManager, AlarmManager.RTC, timeoutAt, operation)
    }
}
