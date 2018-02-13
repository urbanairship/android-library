/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.os.Parcel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.iam.banner.BannerDisplayContent;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.iam.fullscreen.FullScreenDisplayContent;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * {@link InAppMessage} tests.
 */
public class InAppMessageTest extends BaseTestCase {

    private CustomDisplayContent customDisplayContent;
    private BannerDisplayContent bannerDisplayContent;
    private FullScreenDisplayContent fullScreenDisplayContent;

    @Before
    public void setup() {
        customDisplayContent = new CustomDisplayContent(JsonValue.NULL);
        bannerDisplayContent = BannerDisplayContent.newBuilder()
                                                   .setBody(TextInfo.newBuilder()
                                                                    .setText("oh hi")
                                                                    .build())
                                                   .addButton(ButtonInfo.newBuilder()
                                                                        .setLabel(TextInfo.newBuilder()
                                                                                          .setText("Oh hi")
                                                                                          .build())
                                                                        .setId("id")
                                                                        .build())
                                                   .addAction("action_name", JsonValue.wrap("action_value"))
                                                   .build();

        fullScreenDisplayContent = FullScreenDisplayContent.newBuilder()
                                                   .setBody(TextInfo.newBuilder()
                                                                    .setText("oh hi")
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
    public void testMissingId() {
        InAppMessage.newBuilder()
                    .setDisplayContent(customDisplayContent)
                    .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidId() {
        InAppMessage.newBuilder()
                    .setId(UAStringUtil.repeat("a", 101, ""))
                    .setDisplayContent(customDisplayContent)
                    .build();
    }


    @Test(expected = IllegalArgumentException.class)
    public void testMissingDisplayContent() {
        InAppMessage.newBuilder()
                    .setId("messageId")
                    .build();
    }

    @Test
    public void testBannerDisplayContent() throws JsonException {
        InAppMessage message = InAppMessage.newBuilder()
                                           .setDisplayContent(bannerDisplayContent)
                                           .setId("messageId")
                                           .addAction("action_name", JsonValue.wrap(100))
                                           .build();

        assertEquals(InAppMessage.TYPE_BANNER, message.getType());
        assertEquals(bannerDisplayContent, message.getDisplayContent());

        verifyParcelable(message);
        verifyJsonSerialization(message);
    }

    @Test
    public void testCustomDisplayContent() throws JsonException {
        InAppMessage message = InAppMessage.newBuilder()
                                           .setDisplayContent(customDisplayContent)
                                           .setId("messageId")
                                           .build();

        assertEquals(InAppMessage.TYPE_CUSTOM, message.getType());
        assertEquals(customDisplayContent, message.getDisplayContent());

        verifyParcelable(message);
        verifyJsonSerialization(message);
    }

    @Test
    public void testFullScreenDisplayContent() throws JsonException {
        InAppMessage message = InAppMessage.newBuilder()
                                           .setDisplayContent(fullScreenDisplayContent)
                                           .setId("messageId")
                                           .build();

        assertEquals(InAppMessage.TYPE_FULLSCREEN, message.getType());
        assertEquals(fullScreenDisplayContent, message.getDisplayContent());

        verifyParcelable(message);
        verifyJsonSerialization(message);
    }

    @Test
    public void testFromJson() throws JsonException {
        JsonMap actionsMap = JsonMap.newBuilder().put("action_value", "action_name").build();
        JsonMap jsonMap = JsonMap.newBuilder()
                                 .put("display_type", "custom")
                                 .put("display", customDisplayContent.toJsonValue())
                                 .put("message_id", "messageId")
                                 .put("actions", actionsMap)
                                 .build();

        InAppMessage message = InAppMessage.fromJson(jsonMap.toJsonValue());

        assertEquals("messageId", message.getId());
        assertEquals(InAppMessage.TYPE_CUSTOM, message.getType());
        assertEquals(customDisplayContent, message.getDisplayContent());
        assertEquals(actionsMap.getMap(), message.getActions());
    }

    @Test(expected = JsonException.class)
    public void testFromJsonInvalidDisplayType() throws JsonException {
        JsonMap jsonMap = JsonMap.newBuilder()
                                 .put("display_type", "invalid")
                                 .put("display", customDisplayContent.toJsonValue())
                                 .put("message_id", "messageId")
                                 .build();

        InAppMessage.fromJson(jsonMap.toJsonValue());
    }

    private void verifyParcelable(InAppMessage message) {
        // Write the message to a parcel
        Parcel parcel = Parcel.obtain();
        message.writeToParcel(parcel, 0);

        // Reset the parcel so we can read it
        parcel.setDataPosition(0);

        // Create the message from the parcel
        InAppMessage fromParcel = InAppMessage.CREATOR.createFromParcel(parcel);

        // Validate the data
        assertEquals(message, fromParcel);
    }

    public void verifyJsonSerialization(InAppMessage message) throws JsonException {
        JsonValue jsonValue = message.toJsonValue();
        InAppMessage fromJson = InAppMessage.fromJson(jsonValue);
        assertEquals(message, fromJson);
    }
}