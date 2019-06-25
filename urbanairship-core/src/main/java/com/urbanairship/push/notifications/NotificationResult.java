package com.urbanairship.push.notifications;

import android.app.Notification;
import android.content.Context;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Results for {@link NotificationProvider#onCreateNotification(Context, NotificationArguments)}.
 */
public class NotificationResult {

    @IntDef({ OK, RETRY, CANCEL })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {}

    /**
     * Indicates that a Notification was successfully created.
     */
    public static final int OK = 0;

    /**
     * Indicates that a Notification was not successfully created and that a job should be
     * scheduled to retry later.
     */
    public static final int RETRY = 1;

    /**
     * Indicates that a Notification was not successfully created and no work should be scheduled
     * to retry.
     */
    public static final int CANCEL = 2;

    private final Notification notification;
    @Status
    private final int status;

    /**
     * NotificationFactory.Result constructor.
     *
     * @param notification The Notification.
     * @param status The status.
     */
    private NotificationResult(@Nullable Notification notification, @Status int status) {
        this.notification = notification;

        if (notification == null && status == OK) {
            this.status = CANCEL;
        } else {
            this.status = status;
        }
    }

    /**
     * Creates a new result with a notification and a <code>OK</code> status code.
     *
     * @param notification The notification.
     * @return An instance of NotificationFactory.Result.
     */
    @NonNull
    public static NotificationResult notification(@NonNull Notification notification) {
        return new NotificationResult(notification, OK);
    }

    /**
     * Creates a new result with a <code>CANCEL</code> status code.
     *
     * @return An instance of NotificationFactory.Result.
     */
    @NonNull
    public static NotificationResult cancel() {
        return new NotificationResult(null, CANCEL);
    }

    /**
     * Creates a new result with a <code>RETRY</code> status code.
     *
     * @return An instance of NotificationFactory.Result.
     */
    @NonNull
    public static NotificationResult retry() {
        return new NotificationResult(null, RETRY);
    }

    /**
     * Gets the Notification.
     *
     * @return The Notification.
     */
    @Nullable
    public Notification getNotification() {
        return notification;
    }

    /**
     * Gets the status.
     *
     * @return The status.
     */
    public @Status
    int getStatus() {
        return status;
    }

}
