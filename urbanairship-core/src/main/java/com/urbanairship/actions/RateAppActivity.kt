package com.urbanairship.actions

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import com.urbanairship.Autopilot
import com.urbanairship.R
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.activity.ThemedActivity
import com.urbanairship.util.AppStoreUtils

/**
 * An activity that displays a Rate App prompt that links to an app store.
 */
public class RateAppActivity public constructor() : ThemedActivity() {

    private var dialog: AlertDialog? = null

    @SuppressLint("NewApi")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Autopilot.automaticTakeOff(application)

        if (!UAirship.isTakingOff && !UAirship.isFlying) {
            UALog.e("RateAppActivity - unable to create activity, takeOff not called.")
            finish()
        }
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        UALog.d("New intent received for rate app activity")
        restartActivity(intent.data, intent.extras)
    }

    @SuppressLint("NewApi")
    public override fun onResume() {
        super.onResume()
        displayDialog()
    }

    @SuppressLint("NewApi")
    public override fun onPause() {
        super.onPause()
    }

    /**
     * Finishes the activity.
     *
     * @param view The view that was clicked.
     */
    public fun onCloseButtonClick(view: View) {
        this.finish()
    }

    /**
     * Displays the Rate App prompt that links to an app store.
     */
    private fun displayDialog() {
        if (dialog?.isShowing == true) {
            return
        }

        val intent = intent ?: run {
            UALog.e("RateAppActivity - Started activity with null intent.")
            finish()
            return
        }

        val context: Context = this
        val builder = AlertDialog.Builder(context)

        val title = intent.getStringExtra(RateAppAction.TITLE_KEY)
            ?: context.getString(R.string.ua_rate_app_action_default_title, appName)
        builder.setTitle(title)

        val body = intent.getStringExtra(RateAppAction.BODY_KEY) ?: run {
            val positiveButtonTitle =
                context.getString(R.string.ua_rate_app_action_default_rate_positive_button)

            context.getString(R.string.ua_rate_app_action_default_body, positiveButtonTitle)
        }
        builder.setMessage(body)

        builder.setPositiveButton(
            context.getString(R.string.ua_rate_app_action_default_rate_positive_button)
        ) { dialog, _ ->
            try {
                val airship = UAirship.shared()
                val openLinkIntent = AppStoreUtils.getAppStoreIntent(
                    context, airship.platformType, airship.airshipConfigOptions
                )
                startActivity(openLinkIntent)
            } catch (e: ActivityNotFoundException) {
                UALog.e(e, "No web browser available to handle request to open the store link.")
            }
            dialog.cancel()
            finish()
        }

        builder.setNegativeButton(
            context.getString(R.string.ua_rate_app_action_default_rate_negative_button)
        ) { dialog, _ ->
            dialog.cancel()
            finish()
        }

        builder.setOnCancelListener { dialog ->
            dialog.cancel()
            finish()
        }

        dialog = builder.create()
        dialog?.setCancelable(true)
        dialog?.show()
    }

    /**
     * Relaunches the activity.
     *
     * @param uri The URI of the intent.
     * @param extras The extras bundle.
     */
    private fun restartActivity(uri: Uri?, extras: Bundle?) {
        UALog.d("Relaunching activity")

        finish()

        val restartIntent = Intent()
            .setClass(this, this.javaClass)
            .setData(uri)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        extras?.let(restartIntent::putExtras)

        this.startActivity(restartIntent)
    }

    private val appName: String
        get() {
            val packageName = UAirship.applicationContext.packageName
            val packageManager = UAirship.applicationContext.packageManager

            try {
                val info =
                    packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                return packageManager.getApplicationLabel(info) as String
            } catch (e: PackageManager.NameNotFoundException) {
                return ""
            }
        }
}
