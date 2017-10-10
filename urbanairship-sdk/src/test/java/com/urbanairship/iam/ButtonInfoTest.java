/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;


/**
 * {@link ButtonInfo} tests.
 */
public class ButtonInfoTest extends BaseTestCase {


    @Test
    public void testJson() throws JsonException {
        ButtonInfo original = ButtonInfo.newBuilder()
                                    .setLabel(TextInfo.newBuilder()
                                            .setText("hi")
                                            .build())
                                    .addAction("cool", JsonValue.wrap("story"))
                                    .build();


        ButtonInfo fromJson = ButtonInfo.parseJson(original.toJsonValue());

        assertEquals(original, fromJson);
        assertEquals(original.hashCode(), fromJson.hashCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingLabel() {
        ButtonInfo.newBuilder()
                .build();
    }

}