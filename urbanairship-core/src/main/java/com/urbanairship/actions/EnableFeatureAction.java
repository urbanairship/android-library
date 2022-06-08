/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import com.urbanairship.UAirship;
import com.urbanairship.base.Supplier;
import com.urbanairship.json.JsonException;
import com.urbanairship.modules.location.AirshipLocationClient;
import com.urbanairship.permission.Permission;
import com.urbanairship.permission.PermissionsManager;

import androidx.annotation.NonNull;

/**
 * An action that enables features. Running the action with value {@link #FEATURE_LOCATION} or {@link #FEATURE_BACKGROUND_LOCATION}
 * will prompt the user for permissions before enabling.
 * <p>
 * Accepted situations: SITUATION_PUSH_OPENED, SITUATION_WEB_VIEW_INVOCATION,
 * SITUATION_MANUAL_INVOCATION, SITUATION_AUTOMATION, and SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 * <p>
 * Accepted argument value - either {@link #FEATURE_USER_NOTIFICATIONS}, {@link #FEATURE_BACKGROUND_LOCATION},
 * or {@link #FEATURE_LOCATION}.
 * <p>
 * Result value: {@code true} if the feature was enabled, otherwise {@code false}.
 * <p>
 * Default Registration Names: {@link #DEFAULT_REGISTRY_NAME}, {@link #DEFAULT_REGISTRY_SHORT_NAME}
 */
public class EnableFeatureAction extends PromptPermissionAction {

    /**
     * Default registry name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_NAME = "enable_feature";

    /**
     * Default registry short name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^ef";

    /**
     * Action value to enable user notifications. See {@link com.urbanairship.push.PushManager#setUserNotificationsEnabled(boolean)}
     */
    @NonNull
    public static final String FEATURE_USER_NOTIFICATIONS = "user_notifications";

    /**
     * Action value to enable location.
     */
    @NonNull
    public static final String FEATURE_LOCATION = "location";

    /**
     * Action value to enable location with background updates.
     */
    @NonNull
    public static final String FEATURE_BACKGROUND_LOCATION = "background_location";

    private final Supplier<AirshipLocationClient> locationClientSupplier;

    public EnableFeatureAction(@NonNull Supplier<PermissionsManager> permissionsManagerSupplier,
                               @NonNull Supplier<AirshipLocationClient> locationClientSupplier) {
        super(permissionsManagerSupplier);
        this.locationClientSupplier = locationClientSupplier;
    }

    public EnableFeatureAction() {
        this(() -> UAirship.shared().getPermissionsManager(), () -> UAirship.shared().getLocationClient());
    }

    @Override
    protected Args parseArg(ActionArguments arguments) throws JsonException, IllegalArgumentException {
        String feature = arguments.getValue().toJsonValue().requireString();
        switch (feature) {
            case FEATURE_BACKGROUND_LOCATION:
            case FEATURE_LOCATION:
                return new PromptPermissionAction.Args(Permission.LOCATION, true, true);

            case FEATURE_USER_NOTIFICATIONS:
                return new PromptPermissionAction.Args(Permission.DISPLAY_NOTIFICATIONS, true, true);
        }

        return super.parseArg(arguments);
    }

    @Override
    public void onStart(@NonNull ActionArguments arguments) {
        super.onStart(arguments);
        if (FEATURE_BACKGROUND_LOCATION.equalsIgnoreCase(arguments.getValue().getString(""))) {
            AirshipLocationClient client = locationClientSupplier.get();
            if (client != null) {
                client.setBackgroundLocationAllowed(true);
            }
        }
    }
}
