/* Copyright Airship and Contributors */

package com.urbanairship.google;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.urbanairship.UALog;
import com.urbanairship.UAirship;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

/**
 * Activity that handles errors when trying to use Google Play services. Using this
 * activity is completely optional. Instead Google Play services errors can be resolved
 * by following the Google Play Service setup instructions
 * <a href="http://developer.android.com/google/play-services/setup.html"></a>.
 * <p>
 * To use this activity, add `<activity android:name="com.urbanairship.google.PlayServicesErrorActivity"/>
 * under the application tag in the AndroidManifest.xml. Then in the main activity's
 * onStart call {@link com.urbanairship.google.PlayServicesUtils#handleAnyPlayServicesError(android.content.Context)}
 * to handle any play service errors if there are any. PlayServicesErrorActivity
 * will then show the user any resolution dialogs that it can, and automatically
 * restart push if needed.
 * <p>
 * The error dialog will be shown on a blank activity with the default theme. To
 * show the error dialog on top of one of the application's activities, set the theme
 * for the activity in the AndroidManifest to `android:theme="@android:style/Theme.Translucent.NoTitleBar"`.
 * This will force the activity to be completely translucent making the error dialogs
 * appear to be on top of the calling activity.
 * <p>
 * This activity requires the Android Support v4 library.
 */
public class PlayServicesErrorActivity extends FragmentActivity {

    private static final int REQUEST_RESOLVE_ERROR = 1000;
    private static final String DIALOG_TAG = "error_dialog";

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_RESOLVE_ERROR) {
            if (resultCode == RESULT_OK) {
                UALog.d("Google Play services resolution received result ok.");
                checkPlayServices();
            } else {
                UALog.d("Google Play services resolution canceled.");
                finish();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (getSupportFragmentManager().findFragmentByTag(DIALOG_TAG) == null) {
            checkPlayServices();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (isFinishing() && UAirship.isFlying()) {
            // Check if we still have an error
            int error = GooglePlayServicesUtilWrapper.isGooglePlayServicesAvailable(this);
            if (error == ConnectionResult.SUCCESS && UAirship.shared().getPushManager().isPushEnabled()) {
                // Resolved the error, make sure the service is started
                UAirship.shared().getChannel().updateRegistration();
            }
        }
    }

    /**
     * Checks if Google Play services is still in error. Shows an error
     * dialog if it is, otherwise it will finish the activity.
     */
    private void checkPlayServices() {
        UALog.i("Checking Google Play services.");

        int error = GooglePlayServicesUtilWrapper.isGooglePlayServicesAvailable(this);
        if (error == ConnectionResult.SUCCESS) {
            UALog.d("Google Play services available!");
            finish();
        } else if (GooglePlayServicesUtilWrapper.isUserRecoverableError(error)) {
            UALog.d("Google Play services recoverable error: %s", error);
            ErrorDialogFragment.createInstance(error).show(getSupportFragmentManager(), DIALOG_TAG);
        } else {
            UALog.e("Unrecoverable Google Play services error: %s", error);
            finish();
        }
    }

    /**
     * A DialogFragment that wraps Google Play services error dialogs.
     */
    public static class ErrorDialogFragment extends DialogFragment {

        @NonNull
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
        @NonNull
        public static ErrorDialogFragment createInstance(int error) {
            // Create a fragment for the error dialog
            ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
            // Pass the error that should be displayed
            Bundle args = new Bundle();
            args.putInt(DIALOG_ERROR, error);
            dialogFragment.setArguments(args);

            return dialogFragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            // Get the error code and retrieve the appropriate dialog
            int errorCode = getArguments() != null ? this.getArguments().getInt(DIALOG_ERROR) : 0;
            return GoogleApiAvailability.getInstance().getErrorDialog(this.getActivity(), errorCode,
                    REQUEST_RESOLVE_ERROR);
        }

        @Override
        public void onCancel(@NonNull DialogInterface dialog) {
            super.onCancel(dialog);

            // Finish the activity to return back to the calling activity. This
            // only happens when the user presses back.
            if (getActivity() != null) {
                getActivity().finish();
            }
        }

    }

}
