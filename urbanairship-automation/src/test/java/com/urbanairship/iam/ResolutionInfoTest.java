/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * {@link ResolutionInfo} tests.
 */
@RunWith(AndroidJUnit4.class)
public class ResolutionInfoTest {

    private static String INVALID_BUTTON_ID = UAStringUtil.repeat("a", 101, "");
    private static ButtonInfo VALID_BUTTON_INFO = ButtonInfo.newBuilder()
            .setId("foo")
            .setLabel(TextInfo.newBuilder()
                    .setText("bar")
                    .build())
            .addAction("cool", JsonValue.wrap("story"))
            .build();
    private static ResolutionInfo VALID_RESOLUTION_INFO = ResolutionInfo.buttonPressed(VALID_BUTTON_INFO);

    @Test
    public void testJson() throws JsonException {
        ResolutionInfo original = VALID_RESOLUTION_INFO;
        ResolutionInfo fromJson = ResolutionInfo.fromJson(original.toJsonValue());

        assertEquals(original, fromJson);
        assertEquals(original.hashCode(), fromJson.hashCode());
    }

    @Test(expected = JsonException.class)
    public void testInvalidLongButtonIdJson() throws JsonException {
        JsonMap invalidButtonInfo = JsonMap.newBuilder()
                .putAll(VALID_BUTTON_INFO.toJsonValue().requireMap())
                .put("id", INVALID_BUTTON_ID)
                .build();

        JsonMap invalidResolutionInfo = JsonMap.newBuilder()
                .putAll(VALID_RESOLUTION_INFO.toJsonValue().requireMap())
                .put("button_info", invalidButtonInfo)
                .build();

        ResolutionInfo.fromJson(invalidResolutionInfo.toJsonValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidLongButtonId() {
        ButtonInfo.newBuilder()
                .setId(INVALID_BUTTON_ID)
                .setLabel(TextInfo.newBuilder()
                        .setText("bar")
                        .build())
                .addAction("cool", JsonValue.wrap("story"))
                .build();
    }

}
