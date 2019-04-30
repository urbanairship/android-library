/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.BaseTestCase;
import com.urbanairship.iam.tags.TagGroupManager;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link InAppRemoteConfig} tests.
 */
public class InAppRemoteConfigTest extends BaseTestCase {

    @Test
    public void testFromJsonSingle() {
        JsonList json = new JsonList(
                Arrays.asList(JsonMap.newBuilder()
                                     .put("tag_groups", JsonMap.newBuilder()
                                                               .put("enabled", true)
                                                               .put("cache_max_age_seconds", 1)
                                                               .put("cache_stale_read_age_seconds", 2)
                                                               .put("cache_prefer_local_until_seconds", 3)
                                                               .build())
                                     .build().toJsonValue()));

        InAppRemoteConfig config = InAppRemoteConfig.fromJsonList(json);
        assertNotNull(config);
        assertNotNull(config.tagGroupsConfig);
        assertTrue(config.tagGroupsConfig.isEnabled);
        assertEquals(1, config.tagGroupsConfig.cacheMaxAgeInSeconds);
        assertEquals(2, config.tagGroupsConfig.cacheStaleReadTimeSeconds);
        assertEquals(3, config.tagGroupsConfig.cachePreferLocalTagDataTimeSeconds);
    }

    @Test
    public void testFromJsonMultiple() {
        JsonList json = new JsonList(Arrays.asList(
                JsonMap.newBuilder()
                       .put("tag_groups", JsonMap.newBuilder()
                                                 .put("enabled", true)
                                                 .put("cache_max_age_seconds", 100)
                                                 .put("cache_stale_read_age_seconds", 11)
                                                 .put("cache_prefer_local_until_seconds", 1)
                                                 .build())
                       .build().toJsonValue(),
                JsonMap.newBuilder()
                       .put("tag_groups", JsonMap.newBuilder()
                                                 .put("enabled", false)
                                                 .put("cache_max_age_seconds", 1)
                                                 .put("cache_stale_read_age_seconds", 11)
                                                 .put("cache_prefer_local_until_seconds", 200)
                                                 .build())
                       .build().toJsonValue()));

        InAppRemoteConfig config = InAppRemoteConfig.fromJsonList(json);
        assertNotNull(config);
        assertNotNull(config.tagGroupsConfig);
        assertFalse(config.tagGroupsConfig.isEnabled);
        assertEquals(100, config.tagGroupsConfig.cacheMaxAgeInSeconds);
        assertEquals(11, config.tagGroupsConfig.cacheStaleReadTimeSeconds);
        assertEquals(200, config.tagGroupsConfig.cachePreferLocalTagDataTimeSeconds);
    }

    @Test
    public void testFromJsonEmpty() {
        InAppRemoteConfig config = InAppRemoteConfig.fromJsonList(JsonList.EMPTY_LIST);
        assertNull(config);
    }

    @Test
    public void testDefaults() {
        JsonList json = new JsonList(Arrays.asList(JsonMap.EMPTY_MAP.toJsonValue()));
        InAppRemoteConfig config = InAppRemoteConfig.fromJsonList(json);

        assertNotNull(config);
        assertNull(config.tagGroupsConfig);
    }

    @Test
    public void testDefaultTagGroupConfig() {
        JsonList json = new JsonList(Arrays.asList(JsonMap.newBuilder()
                                                          .put("tag_groups", JsonMap.EMPTY_MAP)
                                                          .build().toJsonValue()));
        InAppRemoteConfig config = InAppRemoteConfig.fromJsonList(json);

        assertNotNull(config);
        assertNotNull(config.tagGroupsConfig);
        assertEquals(TagGroupManager.DEFAULT_CACHE_MAX_AGE_TIME_MS, config.tagGroupsConfig.cacheMaxAgeInSeconds);
        assertEquals(TagGroupManager.DEFAULT_CACHE_STALE_READ_TIME_MS, config.tagGroupsConfig.cacheStaleReadTimeSeconds);
        assertEquals(TagGroupManager.DEFAULT_PREFER_LOCAL_DATA_TIME_MS, config.tagGroupsConfig.cachePreferLocalTagDataTimeSeconds);
    }

}