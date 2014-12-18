/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

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

package com.urbanairship.actions;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.analytics.Analytics;

/**
 * An activity that is used by the Action framework to enable starting other activities
 * for results. Ordinarily this class should not be used directly. Instead, see
 * {@link com.urbanairship.actions.Action#startActivityForResult(android.content.Intent)}.
 */
public class ActionActivity extends Activity {

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

        Intent intent = getIntent();

        if (intent == null) {
            Logger.warn("ActionActivity - Started with null intent");
            finish();
            return;
        }

        if (savedInstanceState == null) {
            Intent startActivityIntent = intent.getParcelableExtra(START_ACTIVITY_INTENT_EXTRA);
            if (startActivityIntent != null) {
                actionResultReceiver = intent.getParcelableExtra(RESULT_RECEIVER_EXTRA);
                startActivityForResult(startActivityIntent, ++requestCode);
            } else {
                Logger.warn("ActionActivity - Started without START_ACTIVITY_INTENT_EXTRA extra.");
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
