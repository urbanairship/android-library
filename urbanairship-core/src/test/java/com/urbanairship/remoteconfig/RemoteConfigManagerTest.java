/* Copyright Airship and Contributors */

package com.urbanairship.remoteconfig;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
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
import static junit.framework.Assert.assertNull;
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
        RemoteDataPayload common = createDisablePayload("app_config", 0, Modules.PUSH_MODULE);
        RemoteDataPayload platform = createDisablePayload("app_config:android", 0, Modules.ALL_MODULES);

        // Notify the updates
        updates.onNext(Arrays.asList(platform, common));

        // Verify they are all disabled
        assertTrue(testModuleAdapter.disabledModules.containsAll(Modules.ALL_MODULES));

        // Clear common
        platform = createRemoteDataPayload("app_config:android", 0, JsonMap.EMPTY_MAP);
        testModuleAdapter.reset();

        // Notify the updates
        updates.onNext(Arrays.asList(common, platform));

        // Verify only push is disabled
        List<String> modules = new ArrayList<>(Modules.ALL_MODULES);
        modules.remove(Modules.PUSH_MODULE);

        assertTrue(testModuleAdapter.enabledModules.containsAll(modules));
        assertTrue(testModuleAdapter.disabledModules.contains(Modules.PUSH_MODULE));
        assertTrue(testModuleAdapter.sentConfig.keySet().containsAll(Modules.ALL_MODULES));
    }

    @Test
    public void testRemoteDataForegroundRefreshInterval() {
        RemoteDataPayload common = createDisablePayload("app_config", 9, Modules.ALL_MODULES);
        RemoteDataPayload platform = createDisablePayload("app_config:android", 10, Modules.PUSH_MODULE);

        // Notify the updates
        updates.onNext(Arrays.asList(common, platform));

        // Verify interval is set
        verify(remoteData).setForegroundRefreshInterval(10000);
    }

    @Test
    public void testRemoteConfig() {
        JsonMap module_one_config_common = JsonMap.newBuilder()
                                                  .put("some_config_name", "some_config_value")
                                                  .build();

        JsonMap module_two_config_common = JsonMap.newBuilder()
                                                  .put("some_other_config_name", "some_other_config_value")
                                                  .build();

        JsonMap commonData = JsonMap.newBuilder()
                                    .put("module_one", module_one_config_common)
                                    .put("module_two", module_two_config_common)
                                    .build();

        JsonMap module_one_config_platform_specific = JsonMap.newBuilder()
                                                             .put("some_other_config_name", "some_other_config_value")
                                                             .build();

        JsonMap androidData = JsonMap.newBuilder()
                                     .put("module_one", module_one_config_platform_specific)
                                     .build();

        RemoteDataPayload common = createRemoteDataPayload("app_config", System.currentTimeMillis(), commonData);
        RemoteDataPayload android = createRemoteDataPayload("app_config:android", System.currentTimeMillis(), androidData);

        // Notify the updates
        updates.onNext(Arrays.asList(android, common));

        Map<String, JsonMap> config = testModuleAdapter.sentConfig;

        assertEquals((2 + Modules.ALL_MODULES.size()), config.size());

        assertEquals(module_one_config_platform_specific, config.get("module_one"));

        assertEquals(module_two_config_common, config.get("module_two"));

        // All modules without config payloads should be called with a null config
        for (String module : Modules.ALL_MODULES) {
            assertNull(config.get(module));
            assertTrue(testModuleAdapter.sentConfig.keySet().contains(module));
        }
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
        Map<String, JsonMap> sentConfig = new HashMap<>();

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
        public void onNewConfig(@NonNull String module, @Nullable JsonMap config) {
            if (sentConfig.get(module) != null) {
                throw new IllegalStateException("Make sure to reset test sender between checks");
            }

            sentConfig.put(module, config);
        }

    }

}