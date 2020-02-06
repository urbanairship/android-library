/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.BaseTestCase;
import com.urbanairship.iam.tags.TagGroupManager;
import com.urbanairship.json.JsonMap;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link InAppRemoteConfig} tests.
 */
public class InAppRemoteConfigTest extends BaseTestCase {

    @Test
    public void testFromJsonSingle() {
        JsonMap json = JsonMap.newBuilder()
                              .put("tag_groups", JsonMap.newBuilder()
                                                        .put("enabled", true)
                                                        .put("cache_max_age_seconds", 1)
                                                        .put("cache_stale_read_age_seconds", 2)
                                                        .put("cache_prefer_local_until_seconds", 3)
                                                        .build())
                              .build();

        InAppRemoteConfig config = InAppRemoteConfig.fromJsonMap(json);
        assertNotNull(config);
        assertNotNull(config.tagGroupsConfig);
        assertTrue(config.tagGroupsConfig.isEnabled);
        assertEquals(1, config.tagGroupsConfig.cacheMaxAgeInSeconds);
        assertEquals(2, config.tagGroupsConfig.cacheStaleReadTimeSeconds);
        assertEquals(3, config.tagGroupsConfig.cachePreferLocalTagDataTimeSeconds);
    }

    @Test
    public void testFromNullJson() {
        InAppRemoteConfig config = InAppRemoteConfig.fromJsonMap(null);

        assertNotNull(config);
        assertEquals("Config from null JsonValue should be default config", config, InAppRemoteConfig.defaultConfig());
    }

    @Test
    public void testFromJsonEmpty() {
        InAppRemoteConfig config = InAppRemoteConfig.fromJsonMap(JsonMap.EMPTY_MAP);

        assertNotNull(config);
        assertEquals("Config from empty config should be default config", config, InAppRemoteConfig.defaultConfig());
    }

    @Test
    public void testDefaultTagGroupConfig() {
        JsonMap json = JsonMap.newBuilder()
                              .put("tag_groups", JsonMap.EMPTY_MAP)
                              .build();
        InAppRemoteConfig config = InAppRemoteConfig.fromJsonMap(json);

        assertNotNull(config);
        assertEquals("Config from empty tag_groups should be default config", config, InAppRemoteConfig.defaultConfig());
    }

    @Test
    public void testDefaultConfig() {
        InAppRemoteConfig config = InAppRemoteConfig.defaultConfig();

        assertNotNull(config);
        assertNotNull(config.tagGroupsConfig);
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(TagGroupManager.DEFAULT_CACHE_MAX_AGE_TIME_MS), config.tagGroupsConfig.cacheMaxAgeInSeconds);
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(TagGroupManager.DEFAULT_CACHE_STALE_READ_TIME_MS), config.tagGroupsConfig.cacheStaleReadTimeSeconds);
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(TagGroupManager.DEFAULT_PREFER_LOCAL_DATA_TIME_MS), config.tagGroupsConfig.cachePreferLocalTagDataTimeSeconds);
    }

}
