package com.urbanairship.liveupdate.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.urbanairship.UALog
import com.urbanairship.liveupdate.LiveUpdateManager
import com.urbanairship.push.PushManager

/** Receiver for Live Update notifications. */
public class LiveUpdateNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent): Unit = with(intent) {
        val name = getStringExtra(EXTRA_ACTIVITY_NAME)
        if (name == null) {
            // This shouldn't happen.
            UALog.e("Received Live Update notification broadcast without a name!")
            return
        }

        // Handle the broadcast.
        when (action) {
            ACTION_NOTIFICATION_DISMISSED -> {
                // Stop updates for this live activity.
                LiveUpdateManager.shared().end(name)
                UALog.v("Ended live updates for: $name")
            }
            else -> UALog.w("Received unknown Live Update broadcast: $action")
        }

        // Call through to the original delete intent, if one was provided.
        getParcelableExtraCompat<PendingIntent>(PushManager.EXTRA_NOTIFICATION_DELETE_INTENT)?.let { intent ->
            try {
                intent.send()
            } catch (e: PendingIntent.CanceledException) {
                UALog.d("Failed to send notification's deleteIntent, already canceled.")
            }
        }
    }

    internal companion object {
        private const val ACTION_NOTIFICATION_DISMISSED =
            "com.urbanairship.liveupdate.NOTIFICATION_DISMISSED"
        private const val EXTRA_ACTIVITY_NAME: String = "activity_name"

        internal fun deleteIntent(context: Context, name: String): Intent =
            receiverIntent(context, ACTION_NOTIFICATION_DISMISSED, name)

        /**
         * Builds an intent targeting this receiver.
         *
         * These intents are wrapped in `PendingIntent`s that are handed to the notification
         * manager, so they must never be implicit. The target component
         * is set via the constructor, and the package is pinned as well so the intent cannot
         * resolve outside this app even if the component were dropped.
         *
         * Built with discrete statements rather than a fluent chain so that the explicit
         * targeting is unmistakable at the point of construction.
         */
        private fun receiverIntent(context: Context, action: String, name: String): Intent {
            val intent = Intent(context, LiveUpdateNotificationReceiver::class.java)
            intent.setPackage(context.packageName)
            intent.action = action
            intent.putExtra(EXTRA_ACTIVITY_NAME, name)
            intent.addCategory(name)
            return intent
        }
    }
}

private inline fun <reified T> Intent.getParcelableExtraCompat(key: String): T? = when {
    // The new getParcelableExtra API was added in API 33, but came with a bug that could lead to
    // NPEs. This was fixed in API 34, but we still need to use the deprecated API for 33.
    // see: https://issuetracker.google.com/issues/240585930
    Build.VERSION.SDK_INT > 33 -> getParcelableExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelableExtra(key) as? T
}
