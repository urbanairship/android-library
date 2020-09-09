/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * Attributes mutation tests.
 */
public class AttributeMutationTest extends BaseTestCase {

    @Test
    public void testSetMutation() {
        AttributeMutation mutation = AttributeMutation.newSetAttributeMutation("expected_key", JsonValue.wrapOpt("expected_value"), 100);

        JsonValue expected = JsonMap.newBuilder()
                                    .put("action", "set")
                                    .put("value", "expected_value")
                                    .put("key", "expected_key")
                                    .put("timestamp", DateUtils.createIso8601TimeStamp(100))
                                    .build()
                                    .toJsonValue();

        assertEquals(expected, mutation.toJsonValue());
    }

    @Test
    public void testRemoveMutation() {
        AttributeMutation mutation = AttributeMutation.newRemoveAttributeMutation("expected_key", 100);

        JsonValue expected = JsonMap.newBuilder()
                                    .put("action", "remove")
                                    .put("key", "expected_key")
                                    .put("timestamp", DateUtils.createIso8601TimeStamp(100))
                                    .build()
                                    .toJsonValue();

        assertEquals(expected, mutation.toJsonValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThrowsOnJsonList() {
        AttributeMutation.newSetAttributeMutation("something", JsonList.EMPTY_LIST.toJsonValue(), 100);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThrowsJsonMap() {
        AttributeMutation.newSetAttributeMutation("something", JsonMap.EMPTY_MAP.toJsonValue(), 100);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThrowsBoolean() {
        AttributeMutation.newSetAttributeMutation("something", JsonValue.wrapOpt(true), 100);
    }
}
