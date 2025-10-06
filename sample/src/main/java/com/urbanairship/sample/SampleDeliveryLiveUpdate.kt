package com.urbanairship.sample

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.requireField
import com.urbanairship.liveupdate.CallbackLiveUpdateNotificationHandler
import com.urbanairship.liveupdate.LiveUpdate
import com.urbanairship.liveupdate.LiveUpdateEvent
import com.urbanairship.util.PendingIntentCompat
import java.util.Date
import com.bumptech.glide.Glide

class SampleDeliveryLiveUpdate : CallbackLiveUpdateNotificationHandler {

    internal class Attributes(
        internal val stops: Int,
        internal val currentStop: Int,
        internal val orderNumber: String,
    ) {

        constructor(content: JsonMap) : this(
            content.requireField("stops"),
            content.requireField("current_stop"),
            content.requireField("order_number")
        )

        fun applyCompat(view: RemoteViews, context: Context, expandedMode: Boolean = true) {
            val stopsLayout = RemoteViews(context.packageName, R.layout.delivery_stops)
            val currentStopLayout = RemoteViews(context.packageName, R.layout.circle_stop_layout)
            val finalStopLayout = RemoteViews(context.packageName, R.layout.circle_final_stop_layout)
            val remainingStopsLayout = RemoteViews(context.packageName, R.layout.stop_layout)
            val stopsAway = stops - currentStop

            view.apply {
                for (i in 1..stops) {
                    if (i == currentStop) {
                        stopsLayout.addView(R.id.stopsLayout, currentStopLayout)
                        stopsLayout.addView(R.id.stopsLayout, remainingStopsLayout)
                    } else if (i == stops) {
                        stopsLayout.addView(R.id.stopsLayout, finalStopLayout)
                    } else {
                        stopsLayout.addView(R.id.stopsLayout, remainingStopsLayout)
                    }
                }
                if (expandedMode) {
                    addView(R.id.delivery_notification_big, stopsLayout)
                    setTextViewText(R.id.order_number, "Order: #$orderNumber")
                    if (stopsAway > 1) {
                        setTextViewText(R.id.stops_away, "$stopsAway stops away")
                    } else {
                        setTextViewText(R.id.stops_away, "$stopsAway stop away")
                    }
                } else {
                    addView(R.id.delivery_notification_small, stopsLayout)
                    setTextViewText(R.id.order_number, "#$orderNumber")
                }
            }
        }

        fun toJsonMap(): JsonMap {
            return JsonMap
                .newBuilder()
                .put("stops", stops)
                .put("current_stop", currentStop)
                .put("order_number", orderNumber)
                .build()
        }
    }

    override fun onUpdate(
        context: Context,
        event: LiveUpdateEvent,
        update: LiveUpdate,
        resultCallback: CallbackLiveUpdateNotificationHandler.LiveUpdateResultCallback
    ) {
        UALog.d("SampleDeliveryLiveUpdate - onUpdate: action=$event, update=$update")

        if (event == LiveUpdateEvent.END) {
            resultCallback.cancel()
        }

        try {
            val attributes = Attributes(update.content)

            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addCategory(update.name)
                ?.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                ?.setPackage(null)

            val contentIntent = PendingIntentCompat.getActivity(
                context, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= 36) {
                UALog.d { "SampleDeliveryLiveUpdate - Android 16 detected, using new progress style" }
                val builder = NotificationCompat.Builder(context, "delivery")
                    .setSmallIcon(R.drawable.ic_notification)
                    .setPriority(NotificationManager.IMPORTANCE_HIGH)
                    .setCategory(Notification.CATEGORY_EVENT)
                    .setContentTitle("Delivery !")
                    .setContentText("Order #" + attributes.orderNumber + " on the way")
                    .setContentIntent(contentIntent)
                    .setStyle(buildBaseProgressStyle(context, attributes.stops, attributes.currentStop))

                resultCallback.ok(builder)
            } else {
                val bigLayout = RemoteViews(context.packageName, R.layout.live_update_delivery_notification_big)
                attributes.applyCompat(bigLayout, context)

                val smallLayout = RemoteViews(context.packageName, R.layout.live_update_delivery_notification_small)
                attributes.applyCompat(smallLayout, context, false)

                val builder = NotificationCompat.Builder(context, "delivery")
                    .setSmallIcon(R.drawable.ic_notification)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_EVENT)
                    .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                    .setCustomContentView(smallLayout)
                    .setCustomBigContentView(bigLayout)
                    .setContentIntent(contentIntent)
                resultCallback.ok(builder)
            }

        } catch (ex: JsonException) {
            resultCallback.cancel()
        }
    }

    private fun fetchBitmap(context: Context, imageUrl: String): Bitmap {
        return Glide.with(context)
            .asBitmap()
            .load(imageUrl)
            .submit()
            .get()
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    fun buildBaseProgressStyle(context: Context, stops: Int, currentStop: Int): NotificationCompat.ProgressStyle {
        val stopLength = 100/stops
        val progressStyle = NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(stopLength*currentStop)
            .setProgressTrackerIcon(IconCompat.createWithResource(context, com.urbanairship.R.drawable.ua_ic_notification_button_send))
            createAndSetProgressSegments(progressStyle, stops, stopLength)

        return progressStyle
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    fun createAndSetProgressSegments(progressStyle: NotificationCompat.ProgressStyle, stops: Int, stopLength: Int) : NotificationCompat.ProgressStyle {
        var segmentsList = ArrayList<NotificationCompat.ProgressStyle.Segment>()
        for (i in 1..stops) {
            segmentsList.add(NotificationCompat.ProgressStyle.Segment(stopLength).setColor(Color.BLUE))
        }
        progressStyle.progressSegments = segmentsList
        return progressStyle
    }

    companion object {
        fun generate(type: String): LiveUpdate {
            return LiveUpdate(
                name = "Delivery",
                type = type,
                content = JsonMap
                    .newBuilder()
                    .put("stops", 10)
                    .put("current_stop", 7)
                    .put("order_number", "Z1Z78")
                    .build(),
                lastStateChangeTime = Date().time,
                lastContentUpdateTime = Date().time
            )
        }
    }
}
