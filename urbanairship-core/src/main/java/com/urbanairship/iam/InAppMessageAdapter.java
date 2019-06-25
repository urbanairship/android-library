/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import android.content.Context;
import androidx.annotation.IntDef;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.urbanairship.iam.assets.Assets;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * In-app message adapter. An adapter is responsible for displaying a particular type of in-app message.
 */
public interface InAppMessageAdapter {

    @IntDef({ OK, RETRY, CANCEL })
    @Retention(RetentionPolicy.SOURCE)
    @interface PrepareResult {}

    /**
     * Indicates a successful result.
     */
    int OK = 0;

    /***
     * Indicates that the the prepare step should be retried.
     */
    int RETRY = 1;

    /***
     * Indicates that the prepare step failed and the schedule should be canceled.
     */
    int CANCEL = 2;

    /**
     * Factory interface for InAppMessageAdapters.
     */
    interface Factory {

        /**
         * Creates an InAppMessageAdapter for the given message.
         *
         * @param message The in-app message.
         * @return A InAppMessageAdapter.
         */
        @NonNull
        InAppMessageAdapter createAdapter(@NonNull InAppMessage message);

    }

    /**
     * Called to prepare the message to be displayed.
     *
     * @param context The application context.
     * @param assets Any assets that were prepared for the message.
     * @return {@link #OK} if the in-app message is ready to be displayed, {@link #RETRY} if the message
     * was unable to be prepared and needs to be retried, or {@link #CANCEL} if the message was unable to
     * be prepared and should be canceled.
     */
    @WorkerThread
    @PrepareResult
    int onPrepare(@NonNull Context context, @NonNull Assets assets);

    /**
     * Called before displaying but after the message is prepared.
     *
     * @param context The application context.
     * @return {@code true} if the message is ready to be displayed, otherwise {@code false}.
     */
    boolean isReady(@NonNull Context context);

    /**
     * Called to display an in-app message.
     *
     * @param context The application context.
     * @param displayHandler The display handler.
     */
    @MainThread
    void onDisplay(@NonNull Context context, @NonNull DisplayHandler displayHandler);

    /**
     * Called after the in-app message is finished displaying.
     * Perform any cache clean up here.
     *
     * @param context The application context.
     */
    @WorkerThread
    void onFinish(@NonNull Context context);

}
