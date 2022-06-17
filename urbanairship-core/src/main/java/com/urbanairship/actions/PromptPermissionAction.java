/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.provider.Settings;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.app.GlobalActivityMonitor;
import com.urbanairship.app.SimpleApplicationListener;
import com.urbanairship.base.Supplier;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.permission.Permission;
import com.urbanairship.permission.PermissionRequestResult;
import com.urbanairship.permission.PermissionStatus;
import com.urbanairship.permission.PermissionsManager;
import com.urbanairship.push.AirshipNotificationManager;

import java.util.Objects;
import java.util.concurrent.ExecutionException;

import androidx.annotation.Keep;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * An action that prompts for permission.
 * <p>
 * Expected value:
 * - permission: post_notifications, contacts, bluetooth, location, media, mic, or camera
 * - fallback_system_settings: {@code true} to navigate to app settings if the permission is silently denied.
 * - allow_airship_usage: If the permission is granted, any Airship features that depend on the
 * permission will be enabled as well, e.g., enable user notifications on PushManager and push feature
 * on privacy Manager if notifications are allowed.
 * <p>
 * Accepted situations: SITUATION_PUSH_OPENED, SITUATION_WEB_VIEW_INVOCATION,
 * SITUATION_MANUAL_INVOCATION, SITUATION_AUTOMATION, and SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 * <p>
 * Default Result: empty. The actual permission result can be received using a ResultReceiver in the metadata.
 * <p>
 * Default Registration Names: {@link #DEFAULT_REGISTRY_NAME}, {@link #DEFAULT_REGISTRY_SHORT_NAME}
 */
public class PromptPermissionAction extends Action {

    /**
     * Metadata key for a result receiver. Use {@link PermissionResultReceiver} to simplify parsing the result.
     */
    public static final String RECEIVER_METADATA = "com.urbanairship.actions.PromptPermissionActionReceiver";

    /**
     * Default registry name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_NAME = "prompt_permission_action";

    /**
     * Default registry short name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^pp";

    /**
     * Permission argument key.
     */
    @NonNull
    public static final String PERMISSION_ARG_KEY = "permission";

    /**
     * Enable airship usage argument key.
     */
    @NonNull
    public static final String ENABLE_AIRSHIP_USAGE_ARG_KEY = "enable_airship_usage";

    /**
     * Fallback system settings argument key.
     */
    @NonNull
    public static final String FALLBACK_SYSTEM_SETTINGS_ARG_KEY = "fallback_system_settings";

    /**
     * Permissions result key when using a result receiver.
     */
    @NonNull
    public static final String PERMISSION_RESULT_KEY = "permission";

    /**
     * The starting permission status key when using a result receiver.
     */
    @NonNull
    public static final String BEFORE_PERMISSION_STATUS_RESULT_KEY = "before";

    /**
     * Resulting permission status key when using a result receiver.
     */
    @NonNull
    public static final String AFTER_PERMISSION_STATUS_RESULT_KEY = "after";

    private final Supplier<PermissionsManager> permissionsManagerSupplier;

    public PromptPermissionAction(@NonNull Supplier<PermissionsManager> permissionsManagerSupplier) {
        this.permissionsManagerSupplier = permissionsManagerSupplier;
    }

    @Keep
    public PromptPermissionAction() {
        this(() -> UAirship.shared().getPermissionsManager());
    }

    @Override
    public boolean acceptsArguments(@NonNull ActionArguments arguments) {
        // Validate situation
        switch (arguments.getSituation()) {
            case SITUATION_PUSH_OPENED:
            case SITUATION_WEB_VIEW_INVOCATION:
            case SITUATION_MANUAL_INVOCATION:
            case SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON:
            case SITUATION_AUTOMATION:
                return true;

            case SITUATION_PUSH_RECEIVED:
            case SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON:
            default:
                return false;
        }
    }

    @NonNull
    @Override
    public final ActionResult perform(@NonNull ActionArguments arguments) {
        ResultReceiver resultReceiver = arguments.getMetadata().getParcelable(RECEIVER_METADATA);
        try {
            Args args = parseArg(arguments);
            prompt(args, resultReceiver);
            return ActionResult.newEmptyResult();
        } catch (Exception e) {
            return ActionResult.newErrorResult(e);
        }
    }

    protected Args parseArg(ActionArguments arguments) throws JsonException, IllegalArgumentException {
        return Args.fromJson(arguments.getValue().toJsonValue());
    }

    protected void prompt(@NonNull Args args, @Nullable ResultReceiver resultReceiver) throws ExecutionException, InterruptedException {
        PermissionsManager permissionsManager = Objects.requireNonNull(permissionsManagerSupplier.get());

        permissionsManager.checkPermissionStatus(args.permission, before -> {
            permissionsManager.requestPermission(args.permission, args.enableAirshipUsage, requestResult -> {
                if (shouldFallbackToAppSettings(args, requestResult)) {
                    navigatePermissionSettings(args.permission);
                    GlobalActivityMonitor activityMonitor = GlobalActivityMonitor.shared(UAirship.getApplicationContext());
                    activityMonitor.addApplicationListener(new SimpleApplicationListener() {
                        @Override
                        public void onForeground(long time) {
                            permissionsManager.checkPermissionStatus(args.permission, after -> {
                                sendResult(args.permission, before, after, resultReceiver);
                                activityMonitor.removeApplicationListener(this);
                            });
                        }
                    });
                } else {
                    sendResult(args.permission, before, requestResult.getPermissionStatus(), resultReceiver);
                }
            });
        });
    }

    public void sendResult(@NonNull Permission permission,
                           @NonNull PermissionStatus before,
                           @NonNull PermissionStatus after,
                           @Nullable ResultReceiver resultReceiver) {

        if (resultReceiver != null) {
            Bundle bundle = new Bundle();
            bundle.putString(PERMISSION_RESULT_KEY, permission.toJsonValue().toString());
            bundle.putString(BEFORE_PERMISSION_STATUS_RESULT_KEY, before.toJsonValue().toString());
            bundle.putString(AFTER_PERMISSION_STATUS_RESULT_KEY, after.toJsonValue().toString());
            resultReceiver.send(Activity.RESULT_OK, bundle);
        }
    }

    @Override
    public boolean shouldRunOnMainThread() {
        return true;
    }

    boolean shouldFallbackToAppSettings(@NonNull Args args, @NonNull PermissionRequestResult result) {
        return args.fallbackSystemSettings &&
                result.getPermissionStatus() == PermissionStatus.DENIED &&
                result.isSilentlyDenied();
    }

    @MainThread
    private static void navigatePermissionSettings(@NonNull Permission permission) {
        if (permission == Permission.DISPLAY_NOTIFICATIONS) {
            navigateToNotificationSettings();
        } else {
            navigateToAppSettings();
        }
    }

    @MainThread
    private static void navigateToNotificationSettings() {
        Context context = UAirship.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, UAirship.getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            try {
                context.startActivity(intent);
                return;
            } catch (ActivityNotFoundException e) {
                Logger.debug(e, "Failed to launch notification settings.");
            }
        }

        Intent intent = new Intent("android.settings.APP_NOTIFICATION_SETTINGS")
                .putExtra("app_package", UAirship.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("app_uid", UAirship.getAppInfo().uid);

        try {
            context.startActivity(intent);
            return;
        } catch (ActivityNotFoundException e) {
            Logger.debug(e, "Failed to launch notification settings.");
        }

        navigateToAppSettings();
    }

    private static void navigateToAppSettings() {
        Context context = UAirship.getApplicationContext();
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setData(Uri.parse("package:" + UAirship.getPackageName()));

        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Logger.error(e, "Unable to launch settings details activity.");
        }

        intent = new Intent(Settings.ACTION_APPLICATION_SETTINGS)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setData(Uri.parse("package:" + UAirship.getPackageName()));

        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Logger.error(e, "Unable to launch settings activity.");
        }
    }

    protected static class Args {

        public final boolean enableAirshipUsage;
        public final boolean fallbackSystemSettings;
        public final Permission permission;

        protected Args(@NonNull Permission permission, boolean enableAirshipUsage, boolean fallbackSystemSettings) {
            this.permission = permission;
            this.enableAirshipUsage = enableAirshipUsage;
            this.fallbackSystemSettings = fallbackSystemSettings;
        }

        @NonNull
        protected static Args fromJson(JsonValue value) throws JsonException {
            Permission permission = Permission.fromJson(value.requireMap().opt(PERMISSION_ARG_KEY));
            boolean enableAirshipUsage = value.requireMap().opt(ENABLE_AIRSHIP_USAGE_ARG_KEY).getBoolean(false);
            boolean fallbackSystemSettings = value.requireMap().opt(FALLBACK_SYSTEM_SETTINGS_ARG_KEY).getBoolean(false);
            return new Args(permission, enableAirshipUsage, fallbackSystemSettings);
        }

    }

}
