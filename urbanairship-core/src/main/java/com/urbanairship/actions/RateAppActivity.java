package com.urbanairship.actions;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.UAirship;
import com.urbanairship.activity.ThemedActivity;
import com.urbanairship.util.AppStoreUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * An activity that displays a Rate App prompt that links to an app store.
 */
public class RateAppActivity extends ThemedActivity {

    private AlertDialog dialog;

    @SuppressLint("NewApi")
    @Override
    public final void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Autopilot.automaticTakeOff(getApplication());

        if (!UAirship.isTakingOff() && !UAirship.isFlying()) {
            Logger.error("RateAppActivity - unable to create activity, takeOff not called.");
            finish();
        }
    }

    @Override
    public void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        Logger.debug("New intent received for rate app activity");
        restartActivity(intent.getData(), intent.getExtras());
    }

    @SuppressLint("NewApi")
    @Override
    public void onResume() {
        super.onResume();
        displayDialog();
    }

    @SuppressLint("NewApi")
    @Override
    public void onPause() {
        super.onPause();
    }

    /**
     * Finishes the activity.
     *
     * @param view The view that was clicked.
     */
    public void onCloseButtonClick(@NonNull View view) {
        this.finish();
    }

    /**
     * Displays the Rate App prompt that links to an app store.
     */
    private void displayDialog() {
        if (dialog != null && dialog.isShowing()) {
            return;
        }

        Intent intent = getIntent();
        if (intent == null) {
            Logger.error("RateAppActivity - Started activity with null intent.");
            finish();
            return;
        }


        AlertDialog.Builder builder;
        Context context = this;
        builder = new AlertDialog.Builder(context);

        if (intent.getStringExtra(RateAppAction.TITLE_KEY) != null) {
            builder.setTitle(intent.getStringExtra(RateAppAction.TITLE_KEY));
        } else {
            String title = context.getString(R.string.ua_rate_app_action_default_title, getAppName());
            builder.setTitle(title);
        }

        if (intent.getStringExtra(RateAppAction.BODY_KEY) != null) {
            builder.setMessage(intent.getStringExtra(RateAppAction.BODY_KEY));
        } else {
            String positiveButtonTitle = context.getString(R.string.ua_rate_app_action_default_rate_positive_button);
            String body = context.getString(R.string.ua_rate_app_action_default_body, positiveButtonTitle);
            builder.setMessage(body);
        }

        builder.setPositiveButton(
                context.getString(R.string.ua_rate_app_action_default_rate_positive_button),
                new DialogInterface.OnClickListener() {
                    public void onClick(@NonNull DialogInterface dialog, int id) {
                        try {
                            UAirship airship = UAirship.shared();
                            Intent openLinkIntent = AppStoreUtils.getAppStoreIntent(context, airship.getPlatformType(), airship.getAirshipConfigOptions());
                            startActivity(openLinkIntent);
                        } catch (ActivityNotFoundException e) {
                            Logger.error(e, "No web browser available to handle request to open the store link.");
                        }

                        dialog.cancel();
                        finish();
                    }
                });

        builder.setNegativeButton(
                context.getString(R.string.ua_rate_app_action_default_rate_negative_button),
                new DialogInterface.OnClickListener() {
                    public void onClick(@NonNull DialogInterface dialog, int id) {
                        dialog.cancel();
                        finish();
                    }
                });

        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(@NonNull DialogInterface dialog) {
                dialog.cancel();
                finish();
            }
        });

        dialog = builder.create();
        dialog.setCancelable(true);
        dialog.show();
    }

    /**
     * Relaunches the activity.
     *
     * @param uri The URI of the intent.
     * @param extras The extras bundle.
     */
    private void restartActivity(@Nullable Uri uri, @Nullable Bundle extras) {
        Logger.debug("Relaunching activity");

        finish();

        Intent restartIntent = new Intent()
                .setClass(this, this.getClass())
                .setData(uri)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (extras != null) {
            restartIntent.putExtras(extras);
        }

        this.startActivity(restartIntent);
    }

    @NonNull
    private String getAppName() {
        String packageName = UAirship.getApplicationContext().getPackageName();
        PackageManager packageManager = UAirship.getApplicationContext().getPackageManager();

        try {
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            return (String) packageManager.getApplicationLabel(info);
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

}
