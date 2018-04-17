/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.os.Bundle;
import android.support.annotation.Nullable;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.messagecenter.ThemedActivity;

/**
 * In-app message activity.
 */
public abstract class InAppMessageActivity extends ThemedActivity {

    /**
     * Display handler intent extra key.
     */
    public static final String DISPLAY_HANDLER_EXTRA_KEY = "display_handler";

    /**
     * In-app message extra key.
     */
    public static final String IN_APP_MESSAGE_KEY = "in_app_message";

    /**
     * Cache intent extra key.
     */
    public static final String IN_APP_CACHE_KEY = "cache";

    /**
     * Display time instance state key.
     */
    private static final String DISPLAY_TIME_KEY = "display_time";

    private DisplayHandler displayHandler;
    private InAppMessage inAppMessage;
    private InAppMessageCache cache;

    private long resumeTime = 0;
    private long displayTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Autopilot.automaticTakeOff(this.getApplicationContext());

        super.onCreate(savedInstanceState);


        if (getIntent() == null || getIntent().getExtras() == null) {
            finish();
            return;
        }

        displayHandler = getIntent().getParcelableExtra(DISPLAY_HANDLER_EXTRA_KEY);
        inAppMessage = getIntent().getParcelableExtra(IN_APP_MESSAGE_KEY);
        cache = getIntent().getParcelableExtra(IN_APP_CACHE_KEY);

        if (displayHandler == null || inAppMessage == null) {
            Logger.error(getClass() + " unable to show message. Missing display handler or in-app message.");
            finish();
            return;
        }

        if (!displayHandler.requestDisplayLock(this)) {
            finish();
            return;
        }

        if (savedInstanceState != null) {
            displayTime = savedInstanceState.getLong(DISPLAY_TIME_KEY, 0);
        }

        onCreateMessage(savedInstanceState);
    }

    /**
     * Called during {@link #onCreate(Bundle)} after the in-app message and display handler are parsed
     * from the intent.
     *
     * @param savedInstanceState The saved instance state.
     */
    protected abstract void onCreateMessage(@Nullable Bundle savedInstanceState);

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(DISPLAY_TIME_KEY, displayTime);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!displayHandler.requestDisplayLock(this)) {
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumeTime = System.currentTimeMillis();
    }

    @Override
    protected void onPause() {
        super.onPause();
        displayTime += System.currentTimeMillis() - resumeTime;
        resumeTime = 0;
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
        displayHandler.finished(ResolutionInfo.dismissed(getDisplayTime()));
        finish();
    }

    /**
     * Gets the total display time.
     *
     * @return The display time.
     */
    protected long getDisplayTime() {
        long time = displayTime;

        if (resumeTime > 0) {
            time += System.currentTimeMillis() - resumeTime;
        }

        return time;
    }

    /**
     * Gets the in-app message.
     *
     * @return The in-app message.
     */
    protected InAppMessage getMessage() {
        return inAppMessage;
    }

    /**
     * Gets the display handler.
     *
     * @return The display handler.
     */
    protected DisplayHandler getDisplayHandler() {
        return displayHandler;
    }

    /**
     * Gets the in-app message cache.
     *
     * @return The in-app message cache.
     */
    @Nullable
    protected InAppMessageCache getCache() {
        return cache;
    }
}
