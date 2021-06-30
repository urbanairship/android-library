/* Copyright Airship and Contributors */

package com.urbanairship.remoteconfig;

import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipComponentGroups;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.json.JsonMap;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Used by {@link RemoteConfigManager} to handle mapping modules to airship components.
 */
class ModuleAdapter {

    private SparseArray<Set<AirshipComponent>> componentGroupMap = null;

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
                return getComponentsByGroup(AirshipComponentGroups.LOCATION);

            case Modules.ANALYTICS_MODULE:
                return getComponentsByGroup(AirshipComponentGroups.ANALYTICS);

            case Modules.AUTOMATION_MODULE:
                return getComponentsByGroup(AirshipComponentGroups.ACTION_AUTOMATION);

            case Modules.IN_APP_MODULE:
                return getComponentsByGroup(AirshipComponentGroups.IN_APP);

            case Modules.MESSAGE_CENTER:
                return getComponentsByGroup(AirshipComponentGroups.MESSAGE_CENTER);

            case Modules.PUSH_MODULE:
                return getComponentsByGroup(AirshipComponentGroups.PUSH);

            case Modules.NAMED_USER_MODULE:
                return getComponentsByGroup(AirshipComponentGroups.NAMED_USER);

            case Modules.CHANNEL_MODULE:
                return getComponentsByGroup(AirshipComponentGroups.CHANNEL);

            case Modules.CHAT_MODULE:
                return getComponentsByGroup(AirshipComponentGroups.CHAT);

            case Modules.CONTACT_MODULE:
                return getComponentsByGroup(AirshipComponentGroups.CONTACT);

            case Modules.PREFERENCE_CENTER_MODULE:
                return getComponentsByGroup(AirshipComponentGroups.PREFERENCE_CENTER);
        }

        Logger.verbose("Unable to find module: %s", module);
        return Collections.emptyList();
    }

    @NonNull
    private Set<AirshipComponent> getComponentsByGroup(@AirshipComponentGroups.Group int group) {
        if (componentGroupMap == null) {
            componentGroupMap = createComponentGroupMap(UAirship.shared().getComponents());
        }

        return componentGroupMap.get(group, Collections.<AirshipComponent>emptySet());
    }

    @NonNull
    private static SparseArray<Set<AirshipComponent>> createComponentGroupMap(@NonNull Collection<AirshipComponent> components) {
        SparseArray<Set<AirshipComponent>> componentMap = new SparseArray<>();
        for (AirshipComponent component : components) {
            Set<AirshipComponent> group = componentMap.get(component.getComponentGroup());
            if (group == null) {
                group = new HashSet<>();
                componentMap.put(component.getComponentGroup(), group);
            }
            group.add(component);
        }

        return componentMap;
    }

}
