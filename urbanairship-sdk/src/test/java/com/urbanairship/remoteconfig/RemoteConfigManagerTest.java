/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.remoteconfig;

import com.urbanairship.AirshipComponent;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.reactive.Subject;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.remotedata.RemoteDataPayload;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RemoteConfigManager} tests.
 */
public class RemoteConfigManagerTest extends BaseTestCase {
    private RemoteConfigManager remoteConfigManager;
    private RemoteData remoteData;
    private Subject<Collection<RemoteDataPayload>> updates;
    private UAirship airship;

    @Before
    public void setup() {
        this.remoteData = mock(RemoteData.class);
        this.updates = Subject.create();
        when(remoteData.payloadsForTypes("app_config", "app_config:android")).thenReturn(updates);


        this.remoteConfigManager = new RemoteConfigManager(TestApplication.getApplication().preferenceDataStore, remoteData);
        this.remoteConfigManager.init();

        this.airship = UAirship.shared();
    }

    @Test
    public void testDisableComponents() {
        verifyEnabled(airship.getAnalytics(), airship.getAutomation(), airship.getPushManager(),
                airship.getInAppMessagingManager(), airship.getMessageCenter(), airship.getInbox(),
                airship.getLocationManager());

        RemoteDataPayload common = createPayload("app_config:common", 0, DisableInfo.ALL_MODULES);
        RemoteDataPayload platform = createPayload("app_config:android", 0, DisableInfo.PUSH_MODULE);

        // Notify the updates
        updates.onNext(Arrays.asList(common, platform));

        // Verify they are all disabled
        verifyDisabled(airship.getAnalytics(), airship.getAutomation(), airship.getPushManager(),
                airship.getInAppMessagingManager(), airship.getMessageCenter(), airship.getInbox(),
                airship.getLocationManager());

        // Clear common
        common = new RemoteDataPayload("app_config:common", 0, JsonMap.EMPTY_MAP);

        // Notify the updates
        updates.onNext(Arrays.asList(common, platform));

        // Verify only push is disabled
        verifyEnabled(airship.getAnalytics(), airship.getAutomation(), airship.getInAppMessagingManager(),
                airship.getMessageCenter(), airship.getInbox(), airship.getLocationManager());
        verifyDisabled(airship.getPushManager());
    }

    @Test
    public void testRemoteDataForegroundRefreshInterval() {
        RemoteDataPayload common = createPayload("app_config:common", 9, DisableInfo.ALL_MODULES);
        RemoteDataPayload platform = createPayload("app_config:android", 10, DisableInfo.PUSH_MODULE);

        // Notify the updates
        updates.onNext(Arrays.asList(common, platform));

        // Verify interval is set
        verify(remoteData).setForegroundRefreshInterval(10000);
    }

    static void verifyEnabled(AirshipComponent... components) {
        for (AirshipComponent component : components) {
            assertTrue(component.isComponentEnabled());
        }
    }

    static void verifyDisabled(AirshipComponent... components) {
        for (AirshipComponent component : components) {
            assertFalse("Component not disabled: " + component.getClass().getName(), component.isComponentEnabled());
        }
    }

    static RemoteDataPayload createPayload(String type, long refreshInterval, Collection<String> modules) {
        JsonMap disable = JsonMap.newBuilder()
                                 .put("modules", JsonValue.wrapOpt(modules))
                                 .put("remote_data_refresh_interval", refreshInterval)
                                 .build();

        JsonMap data = JsonMap.newBuilder()
                              .put("disable_features", JsonValue.wrapOpt(Collections.singletonList(disable)))
                              .build();

        return new RemoteDataPayload(type, System.currentTimeMillis(), data);
    }

    static RemoteDataPayload createPayload(String type, long refreshInterval, String... modules) {
        return createPayload(type, refreshInterval, Arrays.asList(modules));
    }
}