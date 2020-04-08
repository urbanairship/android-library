/* Copyright Airship and Contributors */

package com.urbanairship.iam.banner;

import com.urbanairship.iam.ButtonInfo;
import com.urbanairship.iam.MediaInfo;
import com.urbanairship.iam.TextInfo;
import com.urbanairship.json.JsonException;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;

/**
 * {@link BannerDisplayContent} tests.
 */
@RunWith(AndroidJUnit4.class)
public class BannerDisplayContentTest {

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

        BannerDisplayContent fromJson = BannerDisplayContent.fromJson(content.toJsonValue());
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

    @Test
    public void testMedia() {
        BannerDisplayContent.newBuilder()
                            .setMedia(MediaInfo.newBuilder()
                                               .setUrl("https://www.example.com/image.jpg")
                                               .setType(MediaInfo.TYPE_IMAGE)
                                               .setDescription("kitty cat")
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
                            .build();
    }

}
