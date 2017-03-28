/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;

import com.urbanairship.UAirship;
import com.urbanairship.util.HelperActivity;
import com.urbanairship.util.PermissionsRequester;

import java.util.Arrays;
import java.util.List;


/**
 * An action that enables features. Running the action with value {@link #FEATURE_LOCATION} or {@link #FEATURE_BACKGROUND_LOCATION}
 * will prompt the user for permissions before enabling.
 * <p/>
 * Accepted situations: SITUATION_PUSH_OPENED, SITUATION_WEB_VIEW_INVOCATION,
 * SITUATION_MANUAL_INVOCATION, SITUATION_AUTOMATION, and SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 * <p/>
 * Accepted argument value - either {@link #FEATURE_USER_NOTIFICATIONS}, {@link #FEATURE_BACKGROUND_LOCATION},
 * or {@link #FEATURE_LOCATION}.
 * <p/>
 * Result value: {@code true} if the feature was enabled. {@code false} if the feature required user
 * permissions that were rejected by the user.
 * <p/>
 * Default Registration Names: {@link #DEFAULT_REGISTRY_NAME}, {@link #DEFAULT_REGISTRY_SHORT_NAME}
 */
public class EnableFeatureAction extends Action {

    /**
     * Default registry name
     */
    public static final String DEFAULT_REGISTRY_NAME = "enable_feature";

    /**
     * Default registry short name
     */
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^ef";

    /**
     * Action value to enable user notifications. See {@link com.urbanairship.push.PushManager#setUserNotificationsEnabled(boolean)}
     */
    public static final String FEATURE_USER_NOTIFICATIONS = "user_notifications";

    /**
     * Action value to enable location. See {@link com.urbanairship.location.UALocationManager#setLocationUpdatesEnabled(boolean)}
     */
    public static final String FEATURE_LOCATION = "location";

    /**
     * Action value to enable location with background updates. See {@link com.urbanairship.location.UALocationManager#setLocationUpdatesEnabled(boolean)}
     * and {@link com.urbanairship.location.UALocationManager#setBackgroundLocationAllowed(boolean)}
     */
    public static final String FEATURE_BACKGROUND_LOCATION = "background_location";


    private final PermissionsRequester permissionsRequester;

    public EnableFeatureAction(@NonNull PermissionsRequester permissionsRequester) {
        this.permissionsRequester = permissionsRequester;
    }

    public EnableFeatureAction() {
        this(new PermissionsRequester() {
            @NonNull
            @Override
            public int[] requestPermissions(@NonNull Context context, @NonNull List<String> permissions) {
                return HelperActivity.requestPermissions(context, permissions.toArray(new String[permissions.size()]));
            }
        });
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
                break;

            case SITUATION_PUSH_RECEIVED:
            case SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON:
            default:
                return false;
        }

        // Validate value
        switch (arguments.getValue().getString("")) {
            case FEATURE_BACKGROUND_LOCATION:
            case FEATURE_LOCATION:
            case FEATURE_USER_NOTIFICATIONS:
                return true;
            default:
                return false;
        }
    }

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {
        switch (arguments.getValue().getString("")) {
            case FEATURE_BACKGROUND_LOCATION:
                if (requestLocationPermissions()) {
                    UAirship.shared().getLocationManager().setLocationUpdatesEnabled(true);
                    UAirship.shared().getLocationManager().setBackgroundLocationAllowed(true);
                    return ActionResult.newResult(ActionValue.wrap(true));
                }

                return ActionResult.newResult(ActionValue.wrap(false));
            case FEATURE_LOCATION:
                if (requestLocationPermissions()) {
                    UAirship.shared().getLocationManager().setLocationUpdatesEnabled(true);
                    return ActionResult.newResult(ActionValue.wrap(true));
                }

                return ActionResult.newResult(ActionValue.wrap(false));
            case FEATURE_USER_NOTIFICATIONS:
                UAirship.shared().getPushManager().setUserNotificationsEnabled(true);
                return ActionResult.newResult(ActionValue.wrap(true));
        }

        return ActionResult.newResult(ActionValue.wrap(false));
    }

    private boolean requestLocationPermissions() {
        int[] result = permissionsRequester.requestPermissions(UAirship.getApplicationContext(), Arrays.asList(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION));
        for (int i : result) {
            if (i == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }
}
