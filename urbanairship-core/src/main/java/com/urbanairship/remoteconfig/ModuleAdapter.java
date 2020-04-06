/* Copyright Airship and Contributors */

package com.urbanairship.remoteconfig;

import com.urbanairship.AirshipComponent;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.json.JsonMap;
import com.urbanairship.messagecenter.MessageCenter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
    public void setComponentEnabled(@NonNull String module, boolean enabled) {
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
    public void onNewConfig(@NonNull String module, @Nullable JsonMap config) {
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
    @NonNull
    private Collection<? extends AirshipComponent> findAirshipComponents(@NonNull String module) {
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
                return Arrays.asList(MessageCenter.shared());

            case Modules.PUSH_MODULE:
                return Collections.singletonList(UAirship.shared().getPushManager());

            case Modules.NAMED_USER_MODULE:
                return Collections.singletonList(UAirship.shared().getNamedUser());

            case Modules.CHANNEL_MODULE:
                return Collections.singletonList(UAirship.shared().getChannel());
        }

        Logger.verbose("ModuleAdapter - Unable to find module: %s", module);
        return Collections.emptyList();
    }

}
