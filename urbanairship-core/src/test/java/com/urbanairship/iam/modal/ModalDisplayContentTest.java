/* Copyright Airship and Contributors */

package com.urbanairship.iam.modal;

import com.urbanairship.BaseTestCase;
import com.urbanairship.iam.ButtonInfo;
import com.urbanairship.iam.MediaInfo;
import com.urbanairship.iam.TextInfo;
import com.urbanairship.json.JsonException;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * {@link ModalDisplayContent} tests.
 */
public class ModalDisplayContentTest extends BaseTestCase {

    @Test
    public void testJson() throws JsonException {
        ModalDisplayContent content = ModalDisplayContent.newBuilder()
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

        ModalDisplayContent fromJson = ModalDisplayContent.fromJson(content.toJsonValue());
        assertEquals(content, fromJson);
        assertEquals(content.hashCode(), fromJson.hashCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoHeaderOrBody() {
        ModalDisplayContent.newBuilder()
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
        ModalDisplayContent.newBuilder()
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

}