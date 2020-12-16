/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import android.graphics.Color;

import com.urbanairship.UAirship;
import com.urbanairship.json.JsonException;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;

/**
 * {@link TextInfo} tests.
 */
@RunWith(AndroidJUnit4.class)
public class TextInfoTest {

    @Test
    public void testJson() throws JsonException {
        TextInfo original = TextInfo.newBuilder()
                                    .setAlignment(TextInfo.ALIGNMENT_CENTER)
                                    .setFontSize(3000)
                                    .setDrawable(UAirship.getApplicationContext(),1)
                                    .addFontFamily("cool_font")
                                    .setColor(Color.RED)
                                    .setText("OH hi")
                                    .build();

        TextInfo fromJson = TextInfo.fromJson(original.toJsonValue());

        assertEquals(original, fromJson);
        assertEquals(original.hashCode(), fromJson.hashCode());
    }

    @Test
    public void testJsonWithContext() throws JsonException {
        TextInfo original = TextInfo.newBuilder()
                .setAlignment(TextInfo.ALIGNMENT_CENTER)
                .setFontSize(3000)
                .setDrawable(UAirship.getApplicationContext(),100)
                .addFontFamily("cool_font")
                .setColor(Color.RED)
                .setText("OH hi")
                .build();

        TextInfo fromJson = TextInfo.fromJson(original.toJsonValue());

        assertEquals(original, fromJson);
        assertEquals(original.hashCode(), fromJson.hashCode());
    }

    @Test
    public void testJsonWithDrawableName() throws JsonException {
        TextInfo original = TextInfo.newBuilder()
                .setAlignment(TextInfo.ALIGNMENT_CENTER)
                .setFontSize(3000)
                .setDrawableName("test_drawable")
                .addFontFamily("cool_font")
                .setColor(Color.RED)
                .setText("OH hi")
                .build();

        TextInfo fromJson = TextInfo.fromJson(original.toJsonValue());

        assertEquals(original, fromJson);
        assertEquals(original.hashCode(), fromJson.hashCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingTextAndDrawable() {
        TextInfo.newBuilder()
                .build();
    }

}
