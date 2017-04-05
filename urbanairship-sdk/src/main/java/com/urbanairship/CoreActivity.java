/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * An activity that forwards the notification proxy intents to the CoreReceiver.
 */
public class CoreActivity extends Activity {

    public final void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Autopilot.automaticTakeOff(getApplication());

        Intent intent = getIntent();

        if (intent != null) {
            Logger.verbose("CoreActivity - Received intent: " + intent.getAction());
            CoreReceiver receiver = new CoreReceiver();
            receiver.onReceive(getApplicationContext(), intent);
        }

        finish();
    }
}
