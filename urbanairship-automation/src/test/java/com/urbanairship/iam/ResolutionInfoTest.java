/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;

/**
 * {@link ResolutionInfo} tests.
 */
@RunWith(AndroidJUnit4.class)
public class ResolutionInfoTest {

    @Test
    public void testJson() throws JsonException {
        ButtonInfo button = ButtonInfo.newBuilder()
                                      .setId("foo")
                                      .setLabel(TextInfo.newBuilder()
                                                        .setText("bar")
                                                        .build())
                                      .addAction("cool", JsonValue.wrap("story"))
                                      .build();

        ResolutionInfo original = ResolutionInfo.buttonPressed(button);
        ResolutionInfo fromJson = ResolutionInfo.fromJson(original.toJsonValue());

        assertEquals(original, fromJson);
        assertEquals(original.hashCode(), fromJson.hashCode());
    }

}
