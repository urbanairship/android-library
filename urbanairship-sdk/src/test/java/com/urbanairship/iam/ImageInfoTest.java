/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * {@link ImageInfo} tests.
 */
public class ImageInfoTest extends BaseTestCase {

    @Test
    public void testJson() throws JsonException {
        ImageInfo original = ImageInfo.newBuilder()
                                    .setUrl("cool://story")
                                    .build();


        ImageInfo fromJson = ImageInfo.parseJson(original.toJsonValue());

        assertEquals(original, fromJson);
        assertEquals(original.hashCode(), fromJson.hashCode());
    }


    @Test(expected = IllegalArgumentException.class)
    public void testMissingUrl() {
        ImageInfo.newBuilder()
                .build();
    }
}