package com.urbanairship.sample;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonMap;
import com.urbanairship.liveupdate.LiveUpdate;
import com.urbanairship.liveupdate.LiveUpdateEvent;
import com.urbanairship.liveupdate.LiveUpdateNotificationHandler;
import com.urbanairship.liveupdate.LiveUpdateResult;
import com.urbanairship.util.PendingIntentCompat;

import org.jetbrains.annotations.NotNull;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;


public class SampleLiveUpdate implements LiveUpdateNotificationHandler {
    @Override
    @NotNull
    public LiveUpdateResult<NotificationCompat.Builder> onUpdate(@NonNull Context context, @NonNull LiveUpdateEvent event, @NonNull LiveUpdate update) {

        Logger.debug("SampleLiveUpdate - onUpdate: action=" + event + ", update=" + update);

        if (event == LiveUpdateEvent.END) {
            // Dismiss the live update on STOP. The default behavior will leave the Live Update
            // in the notification tray until the dismissal time is reached or the user dismisses it.
            return LiveUpdateResult.cancel();
        }

        JsonMap content = update.getContent();
        int teamOneScore = content.opt("team_one_score").getInt(0);
        int teamTwoScore = content.opt("team_two_score").getInt(0);
        String statusUpdate = content.opt("status_update").optString();

        RemoteViews bigLayout = new RemoteViews(context.getPackageName(), R.layout.live_update_notification_big);
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

        PendingIntent contentIntent = PendingIntentCompat.getActivity(
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

        return LiveUpdateResult.ok(builder);
    }
}
