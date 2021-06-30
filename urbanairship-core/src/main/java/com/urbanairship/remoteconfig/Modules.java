/* Copyright Airship and Contributors */

package com.urbanairship.remoteconfig;

import java.util.Arrays;
import java.util.List;

/**
 * Module names.
 */
interface Modules {

    /**
     * Push module.
     */
    String PUSH_MODULE = "push";

    /**
     * Analytics module.
     */
    String ANALYTICS_MODULE = "analytics";

    /**
     * Message center module.
     */
    String MESSAGE_CENTER = "message_center";

    /**
     * In-app module.
     */
    String IN_APP_MODULE = "in_app_v2";

    /**
     * Automation module.
     */
    String AUTOMATION_MODULE = "automation";

    /**
     * Named user module.
     */
    String NAMED_USER_MODULE = "named_user";

    /**
     * Location module
     */
    String LOCATION_MODULE = "location";

    /**
     * Channel module
     */
    String CHANNEL_MODULE = "channel";

    /**
     * Chat module
     */
    String CHAT_MODULE = "chat";

    /**
     * Contact module.
     */
    String CONTACT_MODULE = "contact";

    /**
     * Preference Center module.
     */
    String PREFERENCE_CENTER_MODULE = "preference_center";

    List<String> ALL_MODULES = Arrays.asList(PUSH_MODULE, ANALYTICS_MODULE, MESSAGE_CENTER,
            IN_APP_MODULE, AUTOMATION_MODULE, NAMED_USER_MODULE, LOCATION_MODULE, CHANNEL_MODULE,
            CHAT_MODULE, CONTACT_MODULE, PREFERENCE_CENTER_MODULE);
}
