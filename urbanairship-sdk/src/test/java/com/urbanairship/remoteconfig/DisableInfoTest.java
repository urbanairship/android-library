/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.remoteconfig;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

public class DisableInfoTest extends BaseTestCase {

    @Test
    public void testParseJson() throws JsonException {
        HashSet<String> modules = new HashSet<>();
        modules.add("push");
        modules.add("message_center");

        HashSet<String> versions = new HashSet<>();
        versions.add("+");

        JsonMap jsonMap = JsonMap.newBuilder()
                                 .put("modules", JsonValue.wrapOpt(new ArrayList<>(modules)))
                                 .put("sdk_versions", JsonValue.wrapOpt(new ArrayList<>(versions)))
                                 .put("remote_data_refresh_interval", 10)
                                 .build();

        DisableInfo disableInfo = DisableInfo.parseJson(jsonMap.toJsonValue());


        assertEquals(disableInfo.getDisabledModules(), modules);
        assertEquals(disableInfo.getSdkVersionConstraints(), versions);
        assertEquals(disableInfo.getRemoteDataRefreshInterval(), 10000);
    }

    @Test
    public void testParseJsonAllModules() throws JsonException {
        JsonMap jsonMap = JsonMap.newBuilder()
                                 .put("modules", "all")
                                 .build();

        DisableInfo disableInfo = DisableInfo.parseJson(jsonMap.toJsonValue());

        assertEquals(disableInfo.getDisabledModules(), new HashSet<>(DisableInfo.ALL_MODULES));
        assertEquals(disableInfo.getRemoteDataRefreshInterval(), 0);
    }

    @Test
    public void testCollapse() {
        DisableInfo pushModule = new DisableInfo(Collections.singleton("push"), 100, Collections.<String>emptySet());
        DisableInfo messageCenter = new DisableInfo(Collections.singleton("message_center"), 100, Collections.singleton("6.0.0"));
        DisableInfo allModules = new DisableInfo(new HashSet<String>(DisableInfo.ALL_MODULES), 50000, Collections.singleton("5.0.+"));


        // Should match push
        DisableInfo result = DisableInfo.collapse(Arrays.asList(pushModule, messageCenter, allModules), "8.0.0");
        assertEquals(result.getDisabledModules(), pushModule.getDisabledModules());
        assertEquals(result.getRemoteDataRefreshInterval(), pushModule.getRemoteDataRefreshInterval());

        // Should match push and message center
        result = DisableInfo.collapse(Arrays.asList(pushModule, messageCenter, allModules), "6.0.0");
        assertEquals(result.getDisabledModules(), new HashSet<>(Arrays.asList("push", "message_center")));
        assertEquals(result.getRemoteDataRefreshInterval(), messageCenter.getRemoteDataRefreshInterval());

        // Should match push and all
        result = DisableInfo.collapse(Arrays.asList(pushModule, messageCenter, allModules), "5.0.4");
        assertEquals(result.getDisabledModules(), new HashSet<>(DisableInfo.ALL_MODULES));
        assertEquals(result.getRemoteDataRefreshInterval(), allModules.getRemoteDataRefreshInterval());

        // No match
        result = DisableInfo.collapse(Arrays.asList(messageCenter, allModules), "9.0.4");
        assertTrue(result.getDisabledModules().isEmpty());
        assertEquals(0, result.getRemoteDataRefreshInterval());
    }
}