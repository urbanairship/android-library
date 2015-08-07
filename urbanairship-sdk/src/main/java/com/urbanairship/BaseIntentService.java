/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.content.WakefulBroadcastReceiver;

import java.util.HashMap;
import java.util.Map;

/**
 * The BaseIntentService delegates all work to a {@link BaseIntentService.Delegate} created
 * by the service in {@link #getServiceDelegate(String, PreferenceDataStore)}. All intents that have
 * been started using a {@link WakefulBroadcastReceiver#completeWakefulIntent(Intent)} will automatically
 * be released after the delegate has a chance to process the intent.
 *
 * @hide
 */
public abstract class BaseIntentService extends IntentService {

    /**
     * Intent extra to track the backoff delay.
     */
    private static final String EXTRA_BACK_OFF_MS = "com.urbanairship.EXTRA_BACK_OFF_MS";

    /**
     * The default starting back off time for retries in milliseconds
     */
    protected static final long DEFAULT_STARTING_BACK_OFF_TIME_MS = 10000; // 10 seconds.

    /**
     * The default max back off time for retries in milliseconds.
     */
    protected static final long DEFAULT_MAX_BACK_OFF_TIME_MS = 5120000; // About 85 mins.

    private final Map<String, Delegate> delegateMap = new HashMap<>();

    public BaseIntentService(String name) {
        super(name);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Autopilot.automaticTakeOff(getApplicationContext());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        try {
            String action = intent.getAction();
            if (action == null) {
                return;
            }


            Delegate delegate = delegateMap.get(action);
            if (delegate == null) {
                delegate = getServiceDelegate(action, UAirship.shared().preferenceDataStore);
            }

            if (delegate == null) {
                Logger.debug("BaseIntentService - No delegate for intent action: " + action);
                return;
            }

            delegateMap.put(action, delegate);
            delegate.onHandleIntent(intent);
        } finally {
            WakefulBroadcastReceiver.completeWakefulIntent(intent);
        }
    }

    /**
     * Gets the worker for the given intent action. The worker's
     * {@link BaseIntentService.Delegate#onHandleIntent(Intent)} will be called
     * with the received intent. The delegate returned for the action will be
     * cached for the lifetime of the service.
     *
     * @param intentAction The intent's action.
     * @param dataStore The preference data store.
     * @return The worker that is able to handle the specified intent action.
     */
    protected abstract Delegate getServiceDelegate(@NonNull String intentAction, @NonNull PreferenceDataStore dataStore);

    /**
     * Service delegate that handle any incoming intents from the {@link BaseIntentService}.
     */
    public static abstract class Delegate {

        private final PreferenceDataStore dataStore;
        private final Context context;

        public Delegate(Context context, PreferenceDataStore dataStore) {
            this.context = context;
            this.dataStore = dataStore;
        }

        /**
         * Called when the worker needs to handle the received intent.
         *
         * @param intent The intent.
         */
        protected abstract void onHandleIntent(Intent intent);

        /**
         * Gets the application's context.
         *
         * @return The application's context.
         */
        protected Context getContext() {
            return context;
        }

        /**
         * Gets the preference data store.
         *
         * @return The preference data store.
         */
        protected PreferenceDataStore getDataStore() {
            return dataStore;
        }

        /**
         * Gets the initial backoff time for an intent retry.
         * <p/>
         * Defaults to {@link #DEFAULT_STARTING_BACK_OFF_TIME_MS}
         *
         * @param intent The intent to be retried.
         * @return The initial backoff time in milliseconds.
         */
        protected long getInitialBackoff(@NonNull Intent intent) {
            return DEFAULT_STARTING_BACK_OFF_TIME_MS;
        }

        /**
         * Gets the max backoff time for an intent retry.
         * <p/>
         * Defaults to {@link #DEFAULT_MAX_BACK_OFF_TIME_MS}
         *
         * @param intent The intent to be retried.
         * @return The max backoff time in milliseconds.
         */
        protected long getMaxBackOff(@NonNull Intent intent) {
            return DEFAULT_MAX_BACK_OFF_TIME_MS;
        }

        /**
         * Schedules the intent to be retried with exponential backoff.
         * </p>
         * Retries work by adding {@link #EXTRA_BACK_OFF_MS} to the intent to track the backoff.
         * This makes retries not work if its called with a new intent or an intent with its extras cleared.
         *
         * @param intent The intent to retry.
         */
        public void retryIntent(@NonNull Intent intent) {
            // Copy it so we don't modify the original intent
            intent = new Intent(intent);

            // Remove the wakeful broadcast extra so it does not log a warning that it already
            // handled the wake lock. Since this value is private, we have to hard code it. In the
            // unlikely case that this value changes in the future it will only cause any wakeful
            // intents to log a warning.
            intent.removeExtra("android.support.content.wakelockid");

            // Calculate the backoff
            long delay = intent.getLongExtra(EXTRA_BACK_OFF_MS, 0);
            if (delay <= 0) {
                delay = getInitialBackoff(intent);
            } else {
                delay = Math.min(delay * 2, getMaxBackOff(intent));
            }

            // Store the backoff in the intent
            intent.putExtra(EXTRA_BACK_OFF_MS, delay);

            // Schedule the intent
            Logger.verbose("BaseIntentService - Scheduling intent " + intent.getAction() + " in " + delay + " milliseconds.");
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
            alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + delay, pendingIntent);
        }
    }
}