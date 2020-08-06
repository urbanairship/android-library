/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import android.os.Parcel;

import com.urbanairship.iam.banner.BannerDisplayContent;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.iam.fullscreen.FullScreenDisplayContent;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;

/**
 * {@link InAppMessage} tests.
 */
@RunWith(AndroidJUnit4.class)
public class InAppMessageTest {

    private CustomDisplayContent customDisplayContent;
    private BannerDisplayContent bannerDisplayContent;
    private FullScreenDisplayContent fullScreenDisplayContent;
    private Map<String, JsonValue> renderedLocale;

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

        renderedLocale = new HashMap<>();
        renderedLocale.put("language", JsonValue.wrap("en"));
        renderedLocale.put("country", JsonValue.wrap("US"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidName() {
        InAppMessage.newBuilder()
                    .setName(UAStringUtil.repeat("a", 1025, ""))
                    .setDisplayContent(customDisplayContent)
                    .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingDisplayContent() {
        InAppMessage.newBuilder()
                    .build();
    }

    @Test
    public void testBannerDisplayContent() throws JsonException {
        InAppMessage message = InAppMessage.newBuilder()
                                           .setDisplayContent(bannerDisplayContent)
                                           .setName("banner message name")
                                           .addAction("action_name", JsonValue.wrap(100))
                                           .build();

        assertEquals(InAppMessage.TYPE_BANNER, message.getType());
        assertEquals(bannerDisplayContent, message.getDisplayContent());
        assertEquals("banner message name", message.getName());

        verifyParcelable(message);
        verifyJsonSerialization(message);
    }

    @Test
    public void testCustomDisplayContent() throws JsonException {
        InAppMessage message = InAppMessage.newBuilder()
                                           .setDisplayContent(customDisplayContent)
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
                                           .setName("full screen message name")
                                           .build();

        assertEquals(InAppMessage.TYPE_FULLSCREEN, message.getType());
        assertEquals(fullScreenDisplayContent, message.getDisplayContent());
        assertEquals("full screen message name", message.getName());

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
                                 .put("name", "message name")
                                 .put("actions", actionsMap)
                                 .put("reporting_enabled", false)
                                 .put("display_behavior", "default")
                                 .put("rendered_locale", JsonValue.wrap(renderedLocale))
                                 .build();

        InAppMessage message = InAppMessage.fromJson(jsonMap.toJsonValue());

        assertEquals("message name", message.getName());
        assertEquals(InAppMessage.TYPE_CUSTOM, message.getType());
        assertEquals(customDisplayContent, message.getDisplayContent());
        assertEquals(actionsMap.getMap(), message.getActions());
        assertEquals(InAppMessage.DISPLAY_BEHAVIOR_DEFAULT, message.getDisplayBehavior());
        assertFalse(message.isReportingEnabled());
        assertEquals(renderedLocale, message.getRenderedLocale());
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
