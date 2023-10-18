package com.urbanairship.sample;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.RemoteViews;

import com.bumptech.glide.Glide;
import com.urbanairship.json.JsonMap;
import com.urbanairship.liveupdate.CallbackLiveUpdateNotificationHandler;
import com.urbanairship.liveupdate.LiveUpdate;
import com.urbanairship.liveupdate.LiveUpdateEvent;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;

/**
 * Sample sports live update handler, with support for asynchronously fetching team images during
 * onUpdate.
 */
public class SampleAsyncLiveUpdate implements CallbackLiveUpdateNotificationHandler {

    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onUpdate(@NonNull Context context,
                         @NonNull LiveUpdateEvent event,
                         @NonNull LiveUpdate update,
                         @NonNull LiveUpdateResultCallback callback) {

        Log.d("SampleAsyncLiveUpdate", "onUpdate: action=" + event + ", update=" + update);

        if (event == LiveUpdateEvent.END) {
            // Dismiss the live update on END. The default behavior will leave the Live Update
            // in the notification tray until the dismissal time is reached or the user dismisses it.
            callback.cancel();
            return;
        }

        JsonMap content = update.getContent();
        int teamOneScore = content.opt("team_one_score").getInt(0);
        int teamTwoScore = content.opt("team_two_score").getInt(0);
        String teamOneName = content.opt("team_one_name").getString("Foxes");
        String teamTwoName = content.opt("team_two_name").getString("Tigers");
        String teamOneImageUrl = content.opt("team_one_image").getString("");
        String teamTwoImageUrl = content.opt("team_two_image").getString("");
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

        PendingIntent contentIntent = PendingIntent.getActivity(
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

        if (teamOneImageUrl.isEmpty() && teamTwoImageUrl.isEmpty()) {
            callback.ok(builder);
        } else {
            fetchTeamIcons(context, teamOneImageUrl, teamTwoImageUrl, (teamOneBmp, teamTwoBmp) -> {
                if (teamOneBmp != null && teamTwoBmp != null) {
                    smallLayout.setImageViewBitmap(R.id.teamTwoImage, teamTwoBmp);
                    smallLayout.setImageViewBitmap(R.id.teamOneImage, teamOneBmp);
                    bigLayout.setImageViewBitmap(R.id.teamOneImage, teamOneBmp);
                    bigLayout.setImageViewBitmap(R.id.teamTwoImage, teamTwoBmp);
                }

                NotificationResult result = callback.ok(builder);

                // ...
                result.getNotification();
                result.getNotificationId();
                result.getNotificationTag();
            });
        }
    }

    private void fetchTeamIcons(Context context, String teamOneUrl, String teamTwoUrl, TeamIconsCallback callback) {
        backgroundExecutor.execute(() -> {
            try {
                Bitmap teamOneBitmap = fetchBitmap(context, teamOneUrl);
                Bitmap teamTwoBitmap = fetchBitmap(context, teamTwoUrl);

                mainHandler.post(() -> callback.onIconsReady(teamOneBitmap, teamTwoBitmap));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onIconsReady(null, null));
            }
        });
    }

    @WorkerThread
    private Bitmap fetchBitmap(Context context, String url) throws ExecutionException, InterruptedException {
        return Glide.with(context)
                    .asBitmap()
                    .load(url)
                    .submit()
                    .get();
    }

    private interface TeamIconsCallback {
        @MainThread
        void onIconsReady(@Nullable Bitmap teamOneBitmap, @Nullable Bitmap teamTwoBitmap);
    }
}
