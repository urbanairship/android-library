package com.urbanairship.remoteconfig;
/* Copyright 2018 Urban Airship and Contributors */


import android.support.annotation.RestrictTo;

import com.urbanairship.AirshipComponent;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.reactive.Subscriber;
import com.urbanairship.reactive.Subscription;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.remotedata.RemoteDataPayload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Remote config manager.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteConfigManager extends AirshipComponent {

    // Remote config types
    private static final String CONFIG_TYPE_COMMON = "app_config";
    private static final String CONFIG_TYPE_ANDROID = "app_config:android";
    private static final String CONFIG_TYPE_AMAZON = "app_config:amazon";

    // Disable config key
    private static final String DISABLE_INFO_KEY = "disable_features";

    private final RemoteData remoteData;
    private Subscription subscription;

    /**
     * Default constructor.
     *
     * @param dataStore The preference data store.
     * @param remoteData The remote data manager.
     */
    public RemoteConfigManager(PreferenceDataStore dataStore, RemoteData remoteData) {
        super(dataStore);
        this.remoteData = remoteData;


    }

    @Override
    protected void init() {
        super.init();

        String platformConfig = UAirship.shared().getPlatformType() == UAirship.AMAZON_PLATFORM ? CONFIG_TYPE_AMAZON : CONFIG_TYPE_ANDROID;
        subscription = remoteData.payloadsForTypes(CONFIG_TYPE_COMMON, platformConfig)
                                 .subscribe(new Subscriber<Collection<RemoteDataPayload>>() {
                                     @Override
                                     public void onNext(Collection<RemoteDataPayload> remoteDataPayloads) {
                                         try {
                                             processRemoteData(remoteDataPayloads);
                                         } catch (Exception e) {
                                             Logger.error("Failed process remote data", e);
                                         }
                                     }
                                 });
    }

    /**
     * Processes the remote data payloads.
     *
     * @param remoteDataPayloads The remote data payloads.
     */
    private void processRemoteData(Collection<RemoteDataPayload> remoteDataPayloads) {
        List<DisableInfo> disableInfos = new ArrayList<>();
        for (RemoteDataPayload payload : remoteDataPayloads) {
            for (JsonValue value : payload.getData().opt(DISABLE_INFO_KEY).optList()) {
                try {
                    disableInfos.add(DisableInfo.parseJson(value));
                } catch (JsonException e) {
                    Logger.error("Failed to parse remote config: " + payload, e);
                }
            }
        }

        apply(DisableInfo.collapse(disableInfos, UAirship.getVersion()));
    }

    @Override
    protected void tearDown() {
        super.tearDown();

        if (this.subscription != null) {
            this.subscription.cancel();
        }
    }

    /**
     * Disables and enables airship components.
     *
     * @param disableInfo The disable info.
     */

    private void apply(DisableInfo disableInfo) {
        for (String module : disableInfo.getDisabledModules()) {
            for (AirshipComponent component : findAirshipComponents(module)) {
                component.setComponentEnabled(false);
            }
        }

        for (String module : disableInfo.getEnabledModules()) {
            for (AirshipComponent component : findAirshipComponents(module)) {
                component.setComponentEnabled(true);
            }
        }

        remoteData.setForegroundRefreshInterval(disableInfo.getRemoteDataRefreshInterval());
    }

    /**
     * Maps the disable info module to airship components.
     *
     * @param module The module.
     * @return The matching airship components.
     */
    static Collection<? extends AirshipComponent> findAirshipComponents(String module) {
        switch (module) {
            case DisableInfo.LOCATION_MODULE:
                return Collections.singleton(UAirship.shared().getLocationManager());

            case DisableInfo.ANALYTICS_MODULE:
                return Collections.singleton(UAirship.shared().getAnalytics());

            case DisableInfo.AUTOMATION_MODULE:
                return Collections.singleton(UAirship.shared().getAutomation());

            case DisableInfo.IN_APP_MODULE:
                return Collections.singleton(UAirship.shared().getInAppMessagingManager());

            case DisableInfo.MESSAGE_CENTER:
                return Arrays.asList(UAirship.shared().getInbox(), UAirship.shared().getMessageCenter());

            case DisableInfo.PUSH_MODULE:
                return Collections.singletonList(UAirship.shared().getPushManager());

            case DisableInfo.NAMED_USER_MODULE:
                return Collections.singletonList(UAirship.shared().getNamedUser());
        }

        return Collections.emptySet();
    }

}
