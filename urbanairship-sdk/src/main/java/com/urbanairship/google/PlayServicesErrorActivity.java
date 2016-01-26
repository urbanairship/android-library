/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

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

package com.urbanairship.google;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;

/**
 * Activity that handles errors when trying to use Google Play services. Using this
 * activity is completely optional. Instead Google Play services errors can be resolved
 * by following the Google Play Service setup instructions
 * <a href="http://developer.android.com/google/play-services/setup.html"></a>.
 * <p/>
 * To use this activity, add `<activity android:name="com.urbanairship.google.PlayServicesErrorActivity"/>
 * under the application tag in the AndroidManifest.xml. Then in the main activity's
 * onStart call {@link com.urbanairship.google.PlayServicesUtils#handleAnyPlayServicesError(android.content.Context)}
 * to handle any play service errors if there are any. PlayServicesErrorActivity
 * will then show the user any resolution dialogs that it can, and automatically
 * restart push if needed.
 * <p/>
 * The error dialog will be shown on a blank activity with the default theme. To
 * show the error dialog on top of one of the application's activities, set the theme
 * for the activity in the AndroidManifest to `android:theme="@android:style/Theme.Translucent.NoTitleBar"`.
 * This will force the activity to be completely translucent making the error dialogs
 * appear to be on top of the calling activity.
 * <p/>
 * This activity requires the Android Support v4 library.
 */
public class PlayServicesErrorActivity extends FragmentActivity {

    private static final int REQUEST_RESOLVE_ERROR = 1000;
    private static final String DIALOG_TAG = "error_dialog";

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_RESOLVE_ERROR) {
            if (resultCode == RESULT_OK) {
                Logger.info("Google Play services resolution received result ok.");
                checkPlayServices();
            } else {
                Logger.error("Google Play services resolution canceled.");
                finish();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Analytics.activityStarted(this);

        if (getSupportFragmentManager().findFragmentByTag(DIALOG_TAG) == null) {
            checkPlayServices();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Analytics.activityStopped(this);

        if (isFinishing()) {
            // Check if we still have an error
            int error = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
            if (error == ConnectionResult.SUCCESS && UAirship.shared().getPushManager().isPushEnabled()) {
                // Resolved the error, make sure the service is started
                UAirship.shared().getPushManager().updateRegistration();
            }
        }
    }

    /**
     * Checks if Google Play services is still in error. Shows an error
     * dialog if it is, otherwise it will finish the activity.
     */
    private void checkPlayServices() {
        Logger.info("Checking Google Play services.");

        int error = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (error == ConnectionResult.SUCCESS) {
            Logger.info("Google Play services available!");
            finish();
        } else if (GooglePlayServicesUtil.isUserRecoverableError(error)) {
            Logger.info("Google Play services recoverable error: " + error);
            ErrorDialogFragment.createInstance(error).show(getSupportFragmentManager(), DIALOG_TAG);
        } else {
            Logger.error("Unrecoverable Google Play services error: " + error);
            finish();
        }
    }

    /**
     * A DialogFragment that wraps Google Play services error dialogs.
     */
    public static class ErrorDialogFragment extends DialogFragment {
        private static final String DIALOG_ERROR = "dialog_error";

        /**
         * Default constructor.
         */
        public ErrorDialogFragment() {
        }

        /**
         * Creates an instance of the error dialog with a given error code.
         *
         * @param error The error code.
         * @return An ErrorDialogFragment instance.
         */
        public static ErrorDialogFragment createInstance(int error) {
            // Create a fragment for the error dialog
            ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
            // Pass the error that should be displayed
            Bundle args = new Bundle();
            args.putInt(DIALOG_ERROR, error);
            dialogFragment.setArguments(args);

            return dialogFragment;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Get the error code and retrieve the appropriate dialog
            int errorCode = this.getArguments().getInt(DIALOG_ERROR);
            return GooglePlayServicesUtil.getErrorDialog(errorCode,
                    this.getActivity(), REQUEST_RESOLVE_ERROR);
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            super.onCancel(dialog);

            // Finish the activity to return back to the calling activity. This
            // only happens when the user presses back.
            getActivity().finish();
        }
    }
}
