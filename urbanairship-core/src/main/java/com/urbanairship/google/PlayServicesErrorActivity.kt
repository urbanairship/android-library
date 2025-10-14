/* Copyright Airship and Contributors */
package com.urbanairship.google

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.urbanairship.UALog
import com.urbanairship.Airship
import com.urbanairship.google.GooglePlayServicesUtilWrapper.isGooglePlayServicesAvailable
import com.urbanairship.google.GooglePlayServicesUtilWrapper.isUserRecoverableError
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * Activity that handles errors when trying to use Google Play services. Using this
 * activity is completely optional. Instead Google Play services errors can be resolved
 * by following the Google Play Service setup instructions
 * (http://developer.android.com/google/play-services/setup.html).
 *
 *
 * To use this activity, add `<activity android:name="com.urbanairship.google.PlayServicesErrorActivity"></activity>`
 * under the application tag in the AndroidManifest.xml. Then in the main activity's
 * onStart call [com.urbanairship.google.PlayServicesUtils.handleAnyPlayServicesError]
 * to handle any play service errors if there are any. [PlayServicesErrorActivity]
 * will then show the user any resolution dialogs that it can, and automatically
 * restart push if needed.
 *
 *
 * The error dialog will be shown on a blank activity with the default theme. To
 * show the error dialog on top of one of the application's activities, set the theme
 * for the activity in the AndroidManifest to `android:theme="@android:style/Theme.Translucent.NoTitleBar"`.
 * This will force the activity to be completely translucent making the error dialogs
 * appear to be on top of the calling activity.
 *
 *
 * This activity requires the Android Support v4 library.
 */
public class PlayServicesErrorActivity public constructor() : FragmentActivity() {

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_RESOLVE_ERROR) {
            return
        }

        when(resultCode) {
            RESULT_OK -> {
                UALog.d("Google Play services resolution received result ok.")
                checkPlayServices()
            }
            else -> {
                UALog.d("Google Play services resolution canceled.")
                finish()
            }
        }
    }

    override fun onStart() {
        super.onStart()

        if (supportFragmentManager.findFragmentByTag(DIALOG_TAG) == null) {
            checkPlayServices()
        }
    }

    override fun onStop() {
        super.onStop()

        if (isFinishing && Airship.isFlying) {
            // Check if we still have an error
            val error = isGooglePlayServicesAvailable(this)
            if (error == ConnectionResult.SUCCESS && Airship.shared().pushManager.isPushEnabled) {
                // Resolved the error, make sure the service is started
                Airship.shared().channel.updateRegistration()
            }
        }
    }

    /**
     * Checks if Google Play services is still in error. Shows an error
     * dialog if it is, otherwise it will finish the activity.
     */
    private fun checkPlayServices() {
        UALog.i("Checking Google Play services.")

        val result = isGooglePlayServicesAvailable(this)
        if (result == ConnectionResult.SUCCESS) {
            UALog.d("Google Play services available!")
            finish()
        } else if (isUserRecoverableError(result)) {
            UALog.d("Google Play services recoverable error: $result")
            ErrorDialogFragment.createInstance(result).show(supportFragmentManager, DIALOG_TAG)
        } else {
            UALog.e("Unrecoverable Google Play services error: $result")
            finish()
        }
    }

    /**
     * A DialogFragment that wraps Google Play services error dialogs.
     */
    public class ErrorDialogFragment public constructor() : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            // Get the error code and retrieve the appropriate dialog
            val errorCode = arguments?.getInt(DIALOG_ERROR) ?: 0
            val activity = this.activity ?: return super.onCreateDialog(savedInstanceState)

            return GoogleApiAvailability.getInstance()
                .getErrorDialog(activity, errorCode, REQUEST_RESOLVE_ERROR)
                ?: super.onCreateDialog(savedInstanceState)
        }

        override fun onCancel(dialog: DialogInterface) {
            super.onCancel(dialog)

            // Finish the activity to return back to the calling activity. This
            // only happens when the user presses back.
            activity?.finish()
        }

        public companion object {

            private const val DIALOG_ERROR = "dialog_error"

            /**
             * Creates an instance of the error dialog with a given error code.
             *
             * @param error The error code.
             * @return An ErrorDialogFragment instance.
             */
            public fun createInstance(error: Int): ErrorDialogFragment {
                return ErrorDialogFragment().also {
                    it.arguments = bundleOf(DIALOG_ERROR to error)
                }
            }
        }
    }

    internal companion object {
        private const val REQUEST_RESOLVE_ERROR = 1000
        private const val DIALOG_TAG = "error_dialog"
    }
}
