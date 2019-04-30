/* Copyright Airship and Contributors */

package com.urbanairship.iam.fullscreen;

import com.urbanairship.BaseTestCase;
import com.urbanairship.iam.ButtonInfo;
import com.urbanairship.iam.DisplayContent;
import com.urbanairship.iam.MediaInfo;
import com.urbanairship.iam.TextInfo;
import com.urbanairship.json.JsonException;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * {@link FullScreenDisplayContent} tests.
 */
public class FullScreenDisplayContentTest extends BaseTestCase {

    @Test
    public void testJson() throws JsonException {
        FullScreenDisplayContent content = FullScreenDisplayContent.newBuilder()
                                                                   .setHeading(TextInfo.newBuilder()
                                                                                       .setText("oh hi")
                                                                                       .build())
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
                                                                   .setFooter(ButtonInfo.newBuilder()
                                                                                        .setLabel(TextInfo.newBuilder()
                                                                                                          .setText("Oh hi")
                                                                                                          .build())
                                                                                        .setId("footer")
                                                                                        .build())
                                                                   .setMedia(MediaInfo.newBuilder()
                                                                                      .setUrl("http://example.com/jpeg.jpeg")
                                                                                      .setDescription("Image")
                                                                                      .setType(MediaInfo.TYPE_IMAGE)
                                                                                      .build())
                                                                   .build();

        FullScreenDisplayContent fromJson = FullScreenDisplayContent.fromJson(content.toJsonValue());
        assertEquals(content, fromJson);
        assertEquals(content.hashCode(), fromJson.hashCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoHeaderOrBody() {
        FullScreenDisplayContent.newBuilder()
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
    public void testTooManyButtons() {
        FullScreenDisplayContent.newBuilder()
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

    /**
     * Tests that if more than 2 button are added, it will default to stacked regardless of the supplied button layout.
     */
    @Test
    public void testOverrideButtonLayoutForStacked() {
        FullScreenDisplayContent displayContent = FullScreenDisplayContent.newBuilder()
                                                                          .setButtonLayout(DisplayContent.BUTTON_LAYOUT_JOINED)
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

        assertEquals(DisplayContent.BUTTON_LAYOUT_STACKED, displayContent.getButtonLayout());
    }

}