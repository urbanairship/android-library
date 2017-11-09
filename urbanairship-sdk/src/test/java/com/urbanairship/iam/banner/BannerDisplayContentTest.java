/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam.banner;

import com.urbanairship.BaseTestCase;
import com.urbanairship.iam.ButtonInfo;
import com.urbanairship.iam.DisplayContent;
import com.urbanairship.iam.TextInfo;
import com.urbanairship.json.JsonException;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * {@link BannerDisplayContent} tests.
 */
public class BannerDisplayContentTest extends BaseTestCase {

    @Test
    public void testJson() throws JsonException {
        BannerDisplayContent content = BannerDisplayContent.newBuilder()
                            .setBody(TextInfo.newBuilder()
                                             .setText("oh hi")
                                             .build())
                            .addButton(ButtonInfo.newBuilder()
                                                 .setLabel(TextInfo.newBuilder()
                                                                   .setText("Oh hi")
                                                                   .build())
                                                 .setId("id")
                                                 .build())
                            .addButton(ButtonInfo.newBuilder()
                                                 .setLabel(TextInfo.newBuilder()
                                                                   .setText("Oh hi")
                                                                   .build())
                                                 .setId("id")
                                                 .build())
                            .build();

        BannerDisplayContent fromJson = BannerDisplayContent.parseJson(content.toJsonValue());
        assertEquals(content, fromJson);
        assertEquals(content.hashCode(), fromJson.hashCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTooManyButtons() {
        BannerDisplayContent.newBuilder()
                            .setBody(TextInfo.newBuilder()
                                             .setText("oh hi")
                                             .build())
                            .addButton(ButtonInfo.newBuilder()
                                                 .setLabel(TextInfo.newBuilder()
                                                                   .setText("Oh hi")
                                                                   .build())
                                                 .setId("id")
                                                 .build())
                            .addButton(ButtonInfo.newBuilder()
                                                 .setLabel(TextInfo.newBuilder()
                                                                   .setText("Oh hi")
                                                                   .build())
                                                 .setId("id")
                                                 .build())
                            .addButton(ButtonInfo.newBuilder()
                                                 .setLabel(TextInfo.newBuilder()
                                                                   .setText("Oh hi")
                                                                   .build())
                                                 .setId("id")
                                                 .build())
                            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoHeaderOrBody() {
        BannerDisplayContent.newBuilder()
                            .addButton(ButtonInfo.newBuilder()
                                                 .setLabel(TextInfo.newBuilder()
                                                                   .setText("Oh hi")
                                                                   .build())
                                                 .setId("id")
                                                 .build())
                            .addButton(ButtonInfo.newBuilder()
                                                 .setLabel(TextInfo.newBuilder()
                                                                   .setText("Oh hi")
                                                                   .build())
                                                 .setId("id")
                                                 .build())
                            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testStackedButtonLayout() {
        BannerDisplayContent.newBuilder()
                            .setBody(TextInfo.newBuilder()
                                             .setText("oh hi")
                                             .build())
                            .setButtonLayout(DisplayContent.BUTTON_LAYOUT_STACKED)
                            .addButton(ButtonInfo.newBuilder()
                                                 .setLabel(TextInfo.newBuilder()
                                                                   .setText("Oh hi")
                                                                   .build())
                                                 .setId("id")
                                                 .build())
                            .addButton(ButtonInfo.newBuilder()
                                                 .setLabel(TextInfo.newBuilder()
                                                                   .setText("Oh hi")
                                                                   .build())
                                                 .setId("id")
                                                 .build())
                            .build();
    }
}