package com.urbanairship.sample;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.NotificationTarget;
import com.urbanairship.json.JsonMap;
import com.urbanairship.liveupdate.CallbackLiveUpdateNotificationHandler;
import com.urbanairship.liveupdate.LiveUpdate;
import com.urbanairship.liveupdate.LiveUpdateEvent;
import com.urbanairship.util.PendingIntentCompat;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

/**
 * Sample sports live update handler, with support for loading team images via Glide, in the
 * optional onNotificationPosted callback.
 */
public class SampleLiveUpdate implements CallbackLiveUpdateNotificationHandler {

    @SuppressLint("MissingPermission")
    @Override
    public void onUpdate(@NonNull Context context, @NonNull LiveUpdateEvent event, @NonNull LiveUpdate update, @NonNull LiveUpdateResultCallback resultCallback) {

        Log.d("SampleLiveUpdate", "onUpdate: action=" + event + ", update=" + update);

        if (event == LiveUpdateEvent.END) {
            // Dismiss the live update on END. The default behavior will leave the Live Update
            // in the notification tray until the dismissal time is reached or the user dismisses it.
            resultCallback.cancel();
            return;
        }

        JsonMap content = update.getContent();

        String teamOneName = content.opt("team_one_name").getString("Foxes");
        String teamTwoName = content.opt("team_two_name").getString("Tigers");

        String teamOneImageUrl = content.opt("team_one_image").getString("");
        String teamTwoImageUrl = content.opt("team_two_image").getString("");

        int teamOneScore = content.opt("team_one_score").getInt(0);
        int teamTwoScore = content.opt("team_two_score").getInt(0);

        String statusUpdate = content.opt("status_update").optString();

        RemoteViews bigLayout = new RemoteViews(context.getPackageName(), R.layout.live_update_notification_big);
        bigLayout.setTextViewText(R.id.teamOneName, teamOneName);
        bigLayout.setTextViewText(R.id.teamTwoName, teamTwoName);
        bigLayout.setTextViewText(R.id.teamOneScore, String.valueOf(teamOneScore));
        bigLayout.setTextViewText(R.id.teamTwoScore, String.valueOf(teamTwoScore));
        bigLayout.setTextViewText(R.id.statusUpdate, statusUpdate);

        RemoteViews smallLayout = new RemoteViews(context.getPackageName(), R.layout.live_update_notification_small);
        smallLayout.setTextViewText(R.id.teamOneScore, String.valueOf(teamOneScore));
        smallLayout.setTextViewText(R.id.teamTwoScore, String.valueOf(teamTwoScore));

        Intent launchIntent = context.getPackageManager()
                                     .getLaunchIntentForPackage(context.getPackageName())
                                     .addCategory(update.getName())
                                     .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                     .setPackage(null);

        PendingIntent contentIntent = PendingIntentCompat.INSTANCE.getActivity(
                context, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, "sports")
                        .setSmallIcon(R.drawable.ic_notification)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_EVENT)
                        .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                        .setCustomContentView(smallLayout)
                        .setCustomBigContentView(bigLayout)
                        .setContentIntent(contentIntent);

        NotificationResult result = resultCallback.ok(builder);
        if (result == null) {
            return;
        }

        Notification notification = result.getNotification();
        int id = result.getNotificationId();
        String tag = result.getNotificationTag();

        if (!teamOneImageUrl.isEmpty()) {
            Glide.with(context)
                 .asBitmap()
                 .load(teamOneImageUrl)
                 .into(new NotificationTarget(context,
                         R.id.teamOneImage, notification.contentView, notification, id, tag));
        }

        if (!teamTwoImageUrl.isEmpty()) {
            Glide.with(context)
                 .asBitmap()
                 .load(teamTwoImageUrl)
                 .into(new NotificationTarget(context,
                         R.id.teamTwoImage, notification.contentView, notification, id, tag));
        }
    }
}
