/* Copyright Airship and Contributors */

package com.urbanairship.remoteconfig;

import com.urbanairship.ApplicationMetrics;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.ValueMatcher;
import com.urbanairship.util.VersionUtils;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class DisableInfoTest extends BaseTestCase {

    private ApplicationMetrics mockMetrics;

    @Before
    public void setup() {
        mockMetrics = mock(ApplicationMetrics.class);
        TestApplication.getApplication().setApplicationMetrics(mockMetrics);
    }

    @Test
    public void testParseJson() throws JsonException {
        HashSet<String> modules = new HashSet<>();
        modules.add("push");
        modules.add("message_center");

        HashSet<String> sdkVersions = new HashSet<>();
        sdkVersions.add("+");

        DisableInfo original = DisableInfo.newBuilder()
                                          .setDisabledModules(modules)
                                          .setRemoteDataInterval(10)
                                          .setSDKVersionConstraints(sdkVersions)
                                          .setAppVersionPredicate(VersionUtils.createVersionPredicate(ValueMatcher.newNumberRangeMatcher(1.0, 1.0)))
                                          .build();

        DisableInfo fromJson = DisableInfo.fromJson(original.toJsonValue());

        assertEquals(original.getDisabledModules(), modules);
        assertEquals(original.getSdkVersionConstraints(), sdkVersions);

        assertEquals(original.getRemoteDataRefreshInterval(), 10);
        // Parsing from json will result in conversion from seconds to milliseconds
        assertEquals(fromJson.getRemoteDataRefreshInterval(), 10000);
    }

    @Test
    public void testParseJsonAllModules() throws JsonException {
        JsonMap jsonMap = JsonMap.newBuilder()
                                 .put("modules", "all")
                                 .build();

        DisableInfo disableInfo = DisableInfo.fromJson(jsonMap.toJsonValue());

        assertEquals(disableInfo.getDisabledModules(), new HashSet<>(Modules.ALL_MODULES));
        assertEquals(disableInfo.getRemoteDataRefreshInterval(), 0);
    }

    @Test
    public void testFilter() {
        DisableInfo pushModule = DisableInfo.newBuilder()
                                            .setDisabledModules(Collections.singleton("push"))
                                            .build();

        DisableInfo messageCenter = DisableInfo.newBuilder()
                                               .setDisabledModules(Collections.singleton("message_center"))
                                               .setRemoteDataInterval(100)
                                               .setSDKVersionConstraints(Collections.singleton("6.0.0"))
                                               .build();

        DisableInfo allModules = DisableInfo.newBuilder()
                                            .setDisabledModules(Modules.ALL_MODULES)
                                            .setRemoteDataInterval(50000)
                                            .setSDKVersionConstraints(Collections.singleton("5.0.+"))
                                            .build();

        DisableInfo sdkVersion = DisableInfo.newBuilder()
                                            .setDisabledModules(Modules.ALL_MODULES)
                                            .setAppVersionPredicate(VersionUtils.createVersionPredicate(ValueMatcher.newNumberRangeMatcher(5.0, 6.0)))
                                            .build();

        // Should match push
        List<DisableInfo> result = DisableInfo.filter(Arrays.asList(pushModule, messageCenter, allModules, sdkVersion), "8.0.0", 1);
        assertTrue(result.contains(pushModule));

        // Should match push and message center
        result = DisableInfo.filter(Arrays.asList(pushModule, messageCenter, allModules, sdkVersion), "6.0.0", 1);
        assertTrue(result.contains(pushModule));
        assertTrue(result.contains(messageCenter));

        // Should match push and all
        result = DisableInfo.filter(Arrays.asList(pushModule, messageCenter, allModules, sdkVersion), "5.0.4", 1);
        assertTrue(result.contains(pushModule));
        assertTrue(result.contains(allModules));

        // No match
        result = DisableInfo.filter(Arrays.asList(messageCenter, allModules, sdkVersion), "9.0.4", 1);
        assertTrue(result.isEmpty());

        // SDK Version 5
        result = DisableInfo.filter(Arrays.asList(messageCenter, allModules, sdkVersion), "9.0.4", 5);
        assertTrue(result.contains(sdkVersion));
    }

}