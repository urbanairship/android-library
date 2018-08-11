package com.urbanairship.remoteconfig;
/* Copyright 2018 Urban Airship and Contributors */

import com.urbanairship.AirshipComponent;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.json.JsonList;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * Used by {@link RemoteConfigManager} to handle mapping modules to airship components.
 */
class ModuleAdapter {

    /**
     * Enables/disables airship components that map to the module name.
     *
     * @param module The module name.
     * @param enabled {@code true} to enable, {@code false} to disable.
     */
    public void setComponentEnabled(String module, boolean enabled) {
        for (AirshipComponent component : findAirshipComponents(module)) {
            component.setComponentEnabled(enabled);
        }
    }

    /**
     * Notifies airship components that they have new config.
     *
     * @param module The module name.
     * @param config The config
     */
    public void onNewConfig(String module, JsonList config) {
        for (AirshipComponent component : findAirshipComponents(module)) {
            if (component.isComponentEnabled()) {
                component.onNewConfig(config);
            }
        }
    }

    /**
     * Maps the disable info module to airship components.
     *
     * @param module The module.
     * @return The matching airship components.
     */
    static Collection<? extends AirshipComponent> findAirshipComponents(String module) {
        switch (module) {
            case Modules.LOCATION_MODULE:
                return Collections.singleton(UAirship.shared().getLocationManager());

            case Modules.ANALYTICS_MODULE:
                return Collections.singleton(UAirship.shared().getAnalytics());

            case Modules.AUTOMATION_MODULE:
                return Collections.singleton(UAirship.shared().getAutomation());

            case Modules.IN_APP_MODULE:
                return Collections.singleton(UAirship.shared().getInAppMessagingManager());

            case Modules.MESSAGE_CENTER:
                return Arrays.asList(UAirship.shared().getInbox(), UAirship.shared().getMessageCenter());

            case Modules.PUSH_MODULE:
                return Collections.singletonList(UAirship.shared().getPushManager());

            case Modules.NAMED_USER_MODULE:
                return Collections.singletonList(UAirship.shared().getNamedUser());
        }

        Logger.error("ModuleAdapter - Unknown module: " + module);
        return null;
    }
}
