/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * {@link ResolutionInfo} tests.
 */
public class ResolutionInfoTest extends BaseTestCase {

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
