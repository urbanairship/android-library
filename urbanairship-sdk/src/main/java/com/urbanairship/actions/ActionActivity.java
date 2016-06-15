/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;

/**
 * An activity that is used by the Action framework to enable starting other activities
 * for results. Ordinarily this class should not be used directly. Instead, see
 * {@link com.urbanairship.actions.Action#startActivityForResult(android.content.Intent)}.
 */
public class ActionActivity extends Activity {

    /**
     * Intent extra holding the permissions.
     */
    public static final String PERMISSIONS_EXTRA = "com.urbanairship.actions.actionactivity.PERMISSIONS_EXTRA";

    /**
     * Intent extra holding an activity result receiver.
     */
    public static final String RESULT_RECEIVER_EXTRA = "com.urbanairship.actions.actionactivity.RESULT_RECEIVER_EXTRA";

    /**
     * Intent extra holding activity result intent.
     */
    public static final String RESULT_INTENT_EXTRA = "com.urbanairship.actions.actionactivity.RESULT_INTENT_EXTRA";

    /**
     * Intent extra holding the intent for an activity to be started.
     */
    public static final String START_ACTIVITY_INTENT_EXTRA = "com.urbanairship.actions.START_ACTIVITY_INTENT_EXTRA";


    private ResultReceiver actionResultReceiver;

    private static int requestCode = 0;

    @Override
    public final void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Autopilot.automaticTakeOff(getApplication());

        if (!UAirship.isTakingOff() && !UAirship.isFlying()) {
            Logger.error("ActionActivity - unable to create activity, takeOff not called.");
            finish();
            return;
        }

        Intent intent = getIntent();

        if (intent == null) {
            Logger.warn("ActionActivity - Started with null intent");
            finish();
            return;
        }

        if (savedInstanceState == null) {
            Intent startActivityIntent = intent.getParcelableExtra(START_ACTIVITY_INTENT_EXTRA);
            String[] permissions = intent.getStringArrayExtra(PERMISSIONS_EXTRA);

            if (startActivityIntent != null) {
                actionResultReceiver = intent.getParcelableExtra(RESULT_RECEIVER_EXTRA);
                startActivityForResult(startActivityIntent, ++requestCode);
            } else if (Build.VERSION.SDK_INT >= 23 && permissions != null) {
                actionResultReceiver = intent.getParcelableExtra(RESULT_RECEIVER_EXTRA);
                requestPermissions(permissions, ++requestCode);
            } else {
                Logger.warn("ActionActivity - Started without START_ACTIVITY_INTENT_EXTRA or PERMISSIONS_EXTRA extra.");
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (actionResultReceiver != null) {
            Bundle bundledData = new Bundle();
            bundledData.putParcelable(RESULT_INTENT_EXTRA, data);
            actionResultReceiver.send(resultCode, bundledData);
        }

        super.onActivityResult(requestCode, resultCode, data);
        this.finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (actionResultReceiver != null) {
            Bundle bundledData = new Bundle();
            bundledData.putIntArray(RESULT_INTENT_EXTRA, grantResults);
            actionResultReceiver.send(Activity.RESULT_OK, bundledData);
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Activity instrumentation for analytic tracking
        Analytics.activityStarted(this);
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Activity instrumentation for analytic tracking
        Analytics.activityStopped(this);
    }
}
