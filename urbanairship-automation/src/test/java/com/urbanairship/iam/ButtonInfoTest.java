/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;

/**
 * {@link ButtonInfo} tests.
 */
@RunWith(AndroidJUnit4.class)
public class ButtonInfoTest {

    @Test
    public void testJson() throws JsonException {
        ButtonInfo original = ButtonInfo.newBuilder()
                                        .setId(UAStringUtil.repeat("a", 100, ""))
                                        .setLabel(TextInfo.newBuilder()
                                                          .setText("hi")
                                                          .build())
                                        .addAction("cool", JsonValue.wrap("story"))
                                        .build();

        ButtonInfo fromJson = ButtonInfo.fromJson(original.toJsonValue());

        assertEquals(original, fromJson);
        assertEquals(original.hashCode(), fromJson.hashCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingLabel() {
        ButtonInfo.newBuilder()
                  .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingId() {
        ButtonInfo.newBuilder()
                  .setLabel(TextInfo.newBuilder()
                                    .setText("hi")
                                    .build())
                  .addAction("cool", JsonValue.wrap("story"))
                  .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidId() {
        ButtonInfo.newBuilder()
                  .setId(UAStringUtil.repeat("a", 101, ""))
                  .setLabel(TextInfo.newBuilder()
                                    .setText("hi")
                                    .build())
                  .addAction("cool", JsonValue.wrap("story"))
                  .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidBorderRadius() {
        ButtonInfo.newBuilder()
                  .setId(UAStringUtil.repeat("a", 100, ""))
                  .setLabel(TextInfo.newBuilder()
                                    .setText("hi")
                                    .build())
                  .addAction("cool", JsonValue.wrap("story"))
                  .setBorderRadius(-1.0f)
                  .build();
    }
}
