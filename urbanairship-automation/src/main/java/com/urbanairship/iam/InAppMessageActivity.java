/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import android.os.Bundle;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.activity.ThemedActivity;
import com.urbanairship.iam.assets.Assets;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * In-app message activity.
 */
public abstract class InAppMessageActivity extends ThemedActivity {

    /**
     * Display handler intent extra key.
     */
    @NonNull
    public static final String DISPLAY_HANDLER_EXTRA_KEY = "display_handler";

    /**
     * In-app message extra key.
     */
    @NonNull
    public static final String IN_APP_MESSAGE_KEY = "in_app_message";

    /**
     * Assets intent extra key.
     */
    @NonNull
    public static final String IN_APP_ASSETS = "assets";

    /**
     * Display time instance state key.
     */
    private static final String DISPLAY_TIME_KEY = "display_time";

    private DisplayHandler displayHandler;
    private InAppMessage inAppMessage;
    private Assets assets;

    private long resumeTime = 0;
    private long displayTime = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Autopilot.automaticTakeOff(this.getApplicationContext());

        super.onCreate(savedInstanceState);

        if (getIntent() == null || getIntent().getExtras() == null) {
            finish();
            return;
        }

        displayHandler = getIntent().getParcelableExtra(DISPLAY_HANDLER_EXTRA_KEY);
        inAppMessage = getIntent().getParcelableExtra(IN_APP_MESSAGE_KEY);
        assets = getIntent().getParcelableExtra(IN_APP_ASSETS);

        if (displayHandler == null || inAppMessage == null) {
            Logger.error("%s unable to show message. Missing display handler or in-app message", getClass());
            finish();
            return;
        }

        if (!displayHandler.isDisplayAllowed(this)) {
            finish();
            return;
        }

        if (savedInstanceState != null) {
            displayTime = savedInstanceState.getLong(DISPLAY_TIME_KEY, 0);
        }

        onCreateMessage(savedInstanceState);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (!displayHandler.isDisplayAllowed(this)) {
            finish();
        }
    }

    /**
     * Called during {@link #onCreate(Bundle)} after the in-app message and display handler are parsed
     * from the intent.
     *
     * @param savedInstanceState The saved instance state.
     */
    protected abstract void onCreateMessage(@Nullable Bundle savedInstanceState);

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(DISPLAY_TIME_KEY, displayTime);
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
        displayHandler.finished(ResolutionInfo.dismissed(), getDisplayTime());
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
    @Nullable
    protected InAppMessage getMessage() {
        return inAppMessage;
    }

    /**
     * Gets the display handler.
     *
     * @return The display handler.
     */
    @Nullable
    protected DisplayHandler getDisplayHandler() {
        return displayHandler;
    }

    /**
     * Gets the in-app message assets.
     *
     * @return The in-app message assets.
     */
    @Nullable
    protected Assets getMessageAssets() {
        return assets;
    }

}
