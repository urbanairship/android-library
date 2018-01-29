/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.graphics.Color;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * {@link TextInfo} tests.
 */
public class TextInfoTest extends BaseTestCase {

    @Test
    public void testJson() throws JsonException {
        TextInfo original = TextInfo.newBuilder()
                                    .setAlignment(TextInfo.ALIGNMENT_CENTER)
                                    .setFontSize(3000)
                                    .setDrawable(100)
                                    .addFontFamily("cool_font")
                                    .setColor(Color.RED)
                                    .setText("OH hi")
                                    .build();


        TextInfo fromJson = TextInfo.parseJson(original.toJsonValue());

        assertEquals(original, fromJson);
        assertEquals(original.hashCode(), fromJson.hashCode());
    }


    @Test(expected = IllegalArgumentException.class)
    public void testMissingTextAndDrawable() {
        TextInfo.newBuilder()
                .build();
    }
}