/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * In-app message adapter. An adapter is responsible for displaying and fetching assets for a particular type
 * of in-app message.
 */
public interface InAppMessageAdapter {

    @IntDef({ RETRY, OK })
    @Retention(RetentionPolicy.SOURCE)
    @interface AdapterResult {}

    /**
     * Indicates a successful result.
     */
    int OK = 0;

    /***
     * Indicates a failure result that needs to be retried.
     */
    int RETRY = 1;

    /**
     * Called to display an in-app message. The display handler's {@link DisplayHandler#requestDisplayLock(Activity)} must
     * be called during `onStart()` in either the activity or fragment, and if the request is denied must
     * immediately dismiss the component without any other calls to the display handler. Once the activity
     * or fragment is finished being displayed call {@link DisplayHandler#finished()}.
     *
     * @param activity The current resumed activity.
     * @param arguments The in-app message arguments.
     * @return {@link #OK} if the in-app message was able to be displayed, otherwise {@link #RETRY} to
     * try again later.
     */
    @MainThread
    @AdapterResult
    int display(@NonNull Activity activity, @NonNull DisplayArguments arguments);

    /**
     * Called to prefetch assets for an in-app message. The assets, or references to the assets should
     * be stored in the provided bundle.
     *
     * @param context The application context.
     * @param message The in-app message.
     * @param assets The assets bundle.
     * @return {@link #OK} if the assets were able to be fetched, otherwise {@link #RETRY} to
     * try again later.
     */
    @WorkerThread
    @AdapterResult
    int prefetchAssets(@NonNull Context context, @NonNull InAppMessage message, @NonNull Bundle assets);

    /**
     * Called before display or prefetch to make sure the adapter is able to handle the in-app message.
     *
     * @param message The in-app message.
     * @return {@code true} if the adapter is able to handle the message, otherwise {@code false}.
     */
    @MainThread
    boolean acceptsMessage(@NonNull InAppMessage message);
}
