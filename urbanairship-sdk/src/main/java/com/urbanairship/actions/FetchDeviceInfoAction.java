/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.support.annotation.NonNull;

import com.urbanairship.UAirship;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.util.Set;

/**
 * Action to fetch a map of device properties.
 * <p/>
 * Accepted situations: all.
 * <p/>
 * Accepted argument value - none.
 * <p/>
 * Result value: {@link JsonMap} containing the device's channel ID, named user ID, push opt-in status,
 * location enabled status, and tags. An example response as JSON:
 * {
 *     "channel_id": "9c36e8c7-5a73-47c0-9716-99fd3d4197d5",
 *     "push_opt_in": true,
 *     "location_enabled": true,
 *     "named_user": "cool_user",
 *     "tags": ["tag1", "tag2, "tag3"]
 * }
 * <p/>
 * Default Registration Names: {@link #DEFAULT_REGISTRY_NAME}, {@link #DEFAULT_REGISTRY_SHORT_NAME}
 * <p/>
 * Default Registration Predicate: only accepts SITUATION_WEB_VIEW_INVOCATION and SITUATION_MANUAL_INVOCATION
 */
public class FetchDeviceInfoAction extends Action {

    /**
     * Default registry name
     */
    public static final String DEFAULT_REGISTRY_NAME = "fetch_device_info";

    /**
     * Default registry short name
     */
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^fdi";

    /**
     * Channel ID response key.
     */
    public static final String CHANNEL_ID_KEY = "channel_id";

    /**
     * Named user response key.
     */
    public static final String NAMED_USER_ID_KEY = "named_user";

    /**
     * Tags response key.
     */
    public static final String TAGS_KEY = "tags";

    /**
     * Push opt-in response key.
     */
    public static final String PUSH_OPT_IN_KEY = "push_opt_in";

    /**
     * Location enabled response key.
     */
    public static final String LOCATION_ENABLED_KEY = "location_enabled";

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {
        JsonMap.Builder properties = JsonMap.newBuilder()
                .put(CHANNEL_ID_KEY, UAirship.shared().getPushManager().getChannelId())
                .put(PUSH_OPT_IN_KEY, UAirship.shared().getPushManager().isOptIn())
                .put(LOCATION_ENABLED_KEY, UAirship.shared().getLocationManager().isLocationUpdatesEnabled())
                .putOpt(NAMED_USER_ID_KEY, UAirship.shared().getNamedUser().getId());

        Set<String> tags = UAirship.shared().getPushManager().getTags();
        if (!tags.isEmpty()) {
            properties.put(TAGS_KEY, JsonValue.wrapOpt(tags));
        }

        return ActionResult.newResult(new ActionValue(properties.build().toJsonValue()));
    }

    /**
     * Default {@link FetchDeviceInfoAction} predicate.
     */
    public static class FetchDeviceInfoPredicate implements ActionRegistry.Predicate {

        @Override
        public boolean apply(ActionArguments arguments) {
            return arguments.getSituation() == Action.SITUATION_WEB_VIEW_INVOCATION ||
                    arguments.getSituation() == Action.SITUATION_MANUAL_INVOCATION;
        }

    }
}
