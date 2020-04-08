/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.json.JsonException;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;

/**
 * {@link MediaInfo} tests.
 */
@RunWith(AndroidJUnit4.class)
public class MediaInfoTest {

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
