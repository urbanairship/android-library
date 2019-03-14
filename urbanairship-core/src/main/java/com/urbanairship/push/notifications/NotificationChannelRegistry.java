package com.urbanairship.push.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.AirshipExecutors;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * Compatibility class for registering notification channels.
 */
public class NotificationChannelRegistry {

    /**
     * The registry database name.
     */
    private static final String DATABASE_NAME = "ua_notification_channel_registry.db";

    @VisibleForTesting
    final private NotificationChannelRegistryDataManager dataManager;
    private final Executor executor;
    private Context context;

    /**
     * NotificationChannelRegistry constructor.
     *
     * @param context The application context.
     * @param configOptions The airship config options.
     * @hide
     */
    public NotificationChannelRegistry(@NonNull Context context, @NonNull AirshipConfigOptions configOptions) {
        this(context,
                new NotificationChannelRegistryDataManager(context, configOptions.getAppKey(), DATABASE_NAME),
                AirshipExecutors.newSerialExecutor());
    }

    /**
     * NotificationChannelRegistry constructor.
     *
     * @param context The application context.
     * @param dataManager The data manager.
     * @hide
     */
    @VisibleForTesting
    NotificationChannelRegistry(@NonNull Context context, @NonNull NotificationChannelRegistryDataManager dataManager, @NonNull Executor executor) {
        this.context = context;
        this.dataManager = dataManager;
        this.executor = executor;
    }

    private NotificationManager getNotificationManager() {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /**
     * Gets a notification channel by identifier.
     *
     * @param id The notification channel identifier.
     * @return A PendingResult of NotificationChannelCompat.
     */
    public PendingResult<NotificationChannelCompat> getNotificationChannel(@NonNull final String id) {
        final PendingResult<NotificationChannelCompat> pendingResult = new PendingResult<>();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                NotificationChannelCompat result;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel channel = getNotificationManager().getNotificationChannel(id);
                    if (channel != null) {
                        result = new NotificationChannelCompat(channel);
                    } else {
                        result = dataManager.getChannel(id);
                        if (result != null) {
                            getNotificationManager().createNotificationChannel(result.toNotificationChannel());
                        }
                    }
                } else {
                    result = dataManager.getChannel(id);
                }

                pendingResult.setResult(result);
            }
        });

        return pendingResult;
    }

    /**
     * Gets a notification channel by identifier.
     *
     * @param id The notification channel identifier.
     * @return A NotificationChannelCompat, or null if one could not be found.
     */
    @Nullable
    @WorkerThread
    public NotificationChannelCompat getNotificationChannelSync(@NonNull String id) {
        try {
            return getNotificationChannel(id).get();
        } catch (InterruptedException e) {
            Logger.error(e, "Failed to get notification channel.");
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            Logger.error(e, "Failed to get notification channel.");
        }

        return null;
    }

    /**
     * Creates a notification channel and saves it to disk. This method is a no-op if a channel
     * is already created with the same identifier. On Android O and above, this method
     * will also register an equivalent NotificationChannel with NotificationManager.
     *
     * @param channelCompat A NotificationChannelCompat.
     */
    public void createNotificationChannel(@NonNull final NotificationChannelCompat channelCompat) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    getNotificationManager().createNotificationChannel(channelCompat.toNotificationChannel());
                }
                dataManager.createChannel(channelCompat);
            }
        });
    }

    /**
     * Deletes a notification channel, by identifier. On Android O and above, this method
     * will also delete the equivalent NotificationChannel on NotificationManager.
     *
     * @param id The notification channel identifier.
     */
    public void deleteNotificationChannel(@NonNull final String id) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    getNotificationManager().deleteNotificationChannel(id);
                }
                dataManager.deleteChannel(id);
            }
        });
    }

}
