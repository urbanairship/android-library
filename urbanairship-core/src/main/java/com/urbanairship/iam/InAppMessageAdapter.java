/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.IntDef;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * In-app message adapter. An adapter is responsible for displaying a particular type of in-app message.
 */
public interface InAppMessageAdapter {


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
        InAppMessageAdapter createAdapter(InAppMessage message);
    }

    @IntDef({ RETRY, OK, CANCEL })
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
     * Called before {@link #onDisplay(Activity, boolean, DisplayHandler)} to prepare the message to be displayed.
     *
     * @param context The application context.
     * @return {@link #OK} if the in-app message is ready to be displayed, {@link #RETRY} if the message
     * was unable to be prepared and needs to be retried, or {@link #CANCEL} if the message was unable to
     * be prepared and should be canceled.
     */
    @WorkerThread
    @PrepareResult
    int onPrepare(@NonNull Context context);

    /**
     * Called to display an in-app message. The display handler's {@link DisplayHandler#requestDisplayLock(Activity)} must
     * be called during `onStart()` in either the activity or fragment, and if the request is denied must
     * immediately dismiss the component without any other calls to the display handler. Once the activity
     * or fragment is finished being displayed call {@link DisplayHandler#finished(ResolutionInfo)}.
     *
     * @param activity The current resumed activity.
     * @param isRedisplay {@code true} If the in-app message is being redisplayed, otherwise {@code false}.
     * @param displayHandler The display handler.
     * @return {@code true} if the in-app message was able to be displayed, otherwise {@code false} to
     * try again later.
     */
    @MainThread
    boolean onDisplay(@NonNull Activity activity, boolean isRedisplay, DisplayHandler displayHandler);

    /**
     * Called after the in-app message is finished displaying.
     * Perform any cache clean up here.
     */
    @WorkerThread
    void onFinish();
}
