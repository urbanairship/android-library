/* Copyright Airship and Contributors */

package com.urbanairship.remoteconfig;

import androidx.annotation.NonNull;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.reactive.Subject;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.remotedata.RemoteDataPayload;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
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

    private TestModuleAdapter testModuleAdapter;

    @Before
    public void setup() {
        this.testModuleAdapter = new TestModuleAdapter();
        this.remoteData = mock(RemoteData.class);
        this.updates = Subject.create();
        when(remoteData.payloadsForTypes("app_config", "app_config:android")).thenReturn(updates);

        this.remoteConfigManager = new RemoteConfigManager(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore, remoteData, testModuleAdapter);
        this.remoteConfigManager.init();
    }

    @Test
    public void testDisableComponents() {
        RemoteDataPayload common = createDisablePayload("app_config:common", 0, Modules.ALL_MODULES);
        RemoteDataPayload platform = createDisablePayload("app_config:android", 0, Modules.PUSH_MODULE);

        // Notify the updates
        updates.onNext(Arrays.asList(common, platform));

        // Verify they are all disabled
        assertTrue(testModuleAdapter.disabledModules.containsAll(Modules.ALL_MODULES));

        // Clear common
        common = createRemoteDataPayload("app_config:common", 0, JsonMap.EMPTY_MAP);
        testModuleAdapter.reset();

        // Notify the updates
        updates.onNext(Arrays.asList(common, platform));

        // Verify only push is disabled
        List<String> modules = new ArrayList<>(Modules.ALL_MODULES);
        modules.remove(Modules.PUSH_MODULE);

        assertTrue(testModuleAdapter.enabledModules.containsAll(modules));
        assertTrue(testModuleAdapter.disabledModules.contains(Modules.PUSH_MODULE));
        assertTrue(testModuleAdapter.sentConfig.isEmpty());

    }

    @Test
    public void testRemoteDataForegroundRefreshInterval() {
        RemoteDataPayload common = createDisablePayload("app_config:common", 9, Modules.ALL_MODULES);
        RemoteDataPayload platform = createDisablePayload("app_config:android", 10, Modules.PUSH_MODULE);

        // Notify the updates
        updates.onNext(Arrays.asList(common, platform));

        // Verify interval is set
        verify(remoteData).setForegroundRefreshInterval(10000);
    }

    @Test
    public void testRemoteConfig() {
        JsonMap commonData = JsonMap.newBuilder()
                                    .put("module_one", "some_config")
                                    .put("module_two", "some_other_config")
                                    .build();

        JsonMap androidData = JsonMap.newBuilder()
                                     .put("module_one", "android")
                                     .build();

        RemoteDataPayload common = createRemoteDataPayload("app_config:common", System.currentTimeMillis(), commonData);
        RemoteDataPayload android = createRemoteDataPayload("app_config:android", System.currentTimeMillis(), androidData);

        // Notify the updates
        updates.onNext(Arrays.asList(common, android));

        Map<String, JsonList> config = testModuleAdapter.sentConfig;

        assertEquals(2, config.size());

        JsonList expectedModuleOneConfig = JsonValue.wrapOpt(Arrays.asList("some_config", "android")).optList();
        assertEquals(expectedModuleOneConfig, config.get("module_one"));

        JsonList expectedModuleTwoConfig = JsonValue.wrapOpt(Arrays.asList("some_other_config")).optList();
        assertEquals(expectedModuleTwoConfig, config.get("module_two"));
    }

    static RemoteDataPayload createRemoteDataPayload(String type, long timeStamp, JsonMap data) {
        return RemoteDataPayload.newBuilder()
                                .setType(type)
                                .setTimeStamp(timeStamp)
                                .setData(data)
                                .build();
    }

    static RemoteDataPayload createDisablePayload(String type, long refreshInterval, Collection<String> modules) {
        JsonMap disable = JsonMap.newBuilder()
                                 .put("modules", JsonValue.wrapOpt(modules))
                                 .put("remote_data_refresh_interval", refreshInterval)
                                 .build();

        JsonMap data = JsonMap.newBuilder()
                              .put("disable_features", JsonValue.wrapOpt(Collections.singletonList(disable)))
                              .build();

        return createRemoteDataPayload(type, System.currentTimeMillis(), data);
    }

    static RemoteDataPayload createDisablePayload(String type, long refreshInterval, String... modules) {
        return createDisablePayload(type, refreshInterval, Arrays.asList(modules));
    }

    /**
     * Module adapter that just tracks calls from the data manager to the adapter.
     */
    public class TestModuleAdapter extends ModuleAdapter {

        List<String> enabledModules = new ArrayList<>();
        List<String> disabledModules = new ArrayList<>();
        Map<String, JsonList> sentConfig = new HashMap<>();

        @Override
        public void setComponentEnabled(@NonNull String module, boolean enabled) {
            if (enabled) {
                enabledModules.add(module);
            } else {
                disabledModules.add(module);
            }
        }

        void reset() {
            enabledModules.clear();
            disabledModules.clear();
            sentConfig.clear();
        }

        @Override
        public void onNewConfig(@NonNull String module, @NonNull JsonList config) {
            if (sentConfig.get(module) != null) {
                throw new IllegalStateException("Make sure to reset test sender between checks");
            }

            sentConfig.put(module, config);
        }

    }

}