/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * {@link MediaInfo} tests.
 */
public class MediaInfoTest extends BaseTestCase {

    @Test
    public void testJson() throws JsonException {
        MediaInfo original = MediaInfo.newBuilder()
                                      .setUrl("cool://story")
                                      .setDescription("Its cool.")
                                      .setType(MediaInfo.TYPE_IMAGE)
                                      .build();

        MediaInfo fromJson = MediaInfo.fromJson(original.toJsonValue());

        assertEquals(original, fromJson);
        assertEquals(original.hashCode(), fromJson.hashCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingUrl() {
        MediaInfo.newBuilder()
                 .setDescription("Its cool.")
                 .setType(MediaInfo.TYPE_IMAGE)
                 .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingDescription() {
        MediaInfo.newBuilder()
                 .setUrl("cool://story")
                 .setType(MediaInfo.TYPE_IMAGE)
                 .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingType() {
        MediaInfo.newBuilder()
                 .setUrl("cool://story")
                 .setDescription("Its cool.")
                 .build();
    }

}