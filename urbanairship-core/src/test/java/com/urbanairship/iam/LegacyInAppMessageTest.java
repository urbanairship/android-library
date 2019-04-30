/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import android.graphics.Color;
import android.os.Bundle;

import com.urbanairship.BaseTestCase;
import com.urbanairship.iam.banner.BannerDisplayContent;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.PushMessage;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertEquals;

/**
 * {@link LegacyInAppMessage} tests.
 */
public class LegacyInAppMessageTest extends BaseTestCase {

    // Taken from the push api spec
    public static String VALID_JSON = "{\"display\": {\"primary_color\": \"#FF0000\"," +
            "\"duration\": 10, \"secondary_color\": \"#00FF00\", \"type\": \"banner\"," +
            "\"alert\": \"Oh hi!\"}, \"actions\": {\"button_group\": \"ua_yes_no\"," +
            "\"button_actions\": {\"yes\": {\"^+t\": \"yes_tag\"}, \"no\": {\"^+t\": \"no_tag\"}}," +
            "\"on_click\": {\"^d\": \"someurl\"}}, \"expiry\": \"2015-12-12T12:00:00\", \"extra\":" +
            "{\"wat\": 123, \"Tom\": \"Selleck\"}}";

    /**
     * Test building the in-app message.
     */
    @Test
    public void testBuildNotification() throws JsonException {
        Map<String, Object> extras = new HashMap<>();
        extras.put("key", "value");
        JsonMap jsonExtras = JsonValue.wrap(extras).getMap();

        Map<String, JsonValue> actionValueMap = new HashMap<>();
        actionValueMap.put("action name", JsonValue.wrap("value"));

        LegacyInAppMessage message = LegacyInAppMessage.newBuilder()
                                                       .setAlert("alert")
                                                       .setId("id")
                                                       .setDuration(10l)
                                                       .setExpiry(200l)
                                                       .setPrimaryColor(Color.BLACK)
                                                       .setSecondaryColor(Color.BLUE)
                                                       .setPlacement(BannerDisplayContent.PLACEMENT_TOP)
                                                       .setExtras(jsonExtras)
                                                       .setButtonActionValues("button one", actionValueMap)
                                                       .setButtonGroupId("button group")
                                                       .setClickActionValues(actionValueMap)
                                                       .build();

        // Verify everything is set on the notification
        assertEquals("alert", message.getAlert());
        assertEquals("id", message.getId());
        assertEquals(10l, (long) message.getDuration());
        assertEquals(200, message.getExpiry());
        assertEquals(Color.BLACK, (int) message.getPrimaryColor());
        assertEquals(Color.BLUE, (int) message.getSecondaryColor());
        assertEquals(BannerDisplayContent.PLACEMENT_TOP, message.getPlacement());
        assertEquals(jsonExtras, message.getExtras());
        assertEquals(actionValueMap, message.getButtonActionValues("button one"));
        assertEquals("button group", message.getButtonGroupId());
        assertEquals(actionValueMap, message.getClickActionValues());
    }

    /**
     * Test parsing a in-app message from a JSON payload.
     */
    @Test
    public void testFromPush() throws JsonException {
        Bundle pushBundle = new Bundle();
        pushBundle.putString(PushMessage.EXTRA_IN_APP_MESSAGE, VALID_JSON);
        PushMessage pushMessage = new PushMessage(pushBundle);

        LegacyInAppMessage message = LegacyInAppMessage.fromPush(pushMessage);

        assertEquals("Oh hi!", message.getAlert());
        assertEquals(10000l, (long) message.getDuration());
        assertEquals(1449921600000l, message.getExpiry());
        assertEquals("ua_yes_no", message.getButtonGroupId());
        assertEquals(BannerDisplayContent.PLACEMENT_BOTTOM, message.getPlacement());
        assertEquals(Color.parseColor("#FF0000"), (int) message.getPrimaryColor());
        assertEquals(Color.parseColor("#00FF00"), (int) message.getSecondaryColor());

        // Verify extras
        JsonMap extras = message.getExtras();
        assertEquals(2, extras.size());
        assertEquals("Selleck", extras.get("Tom").getString());
        assertEquals(123, extras.get("wat").getInt(0));

        // Verify on click action values
        Map<String, JsonValue> onClickActionValues = message.getClickActionValues();
        assertEquals(1, onClickActionValues.size());
        assertEquals("someurl", onClickActionValues.get("^d").getString());

        // Verify yes button action values
        Map<String, JsonValue> yesActionValues = message.getButtonActionValues("yes");
        assertEquals(1, yesActionValues.size());
        assertEquals("yes_tag", yesActionValues.get("^+t").getString());

        // Verify no button action values
        Map<String, JsonValue> noActionValues = message.getButtonActionValues("no");
        assertEquals(1, noActionValues.size());
        assertEquals("no_tag", noActionValues.get("^+t").getString());
    }

    /**
     * Test getting the in-app message from the push payload amends the message
     * to include the open MCRAP action if the push also contains a rich push message ID.
     */
    @Test
    public void testFromPushAmendsMCRAPAction() throws JsonException {
        Bundle pushBundle = new Bundle();
        pushBundle.putString(PushMessage.EXTRA_IN_APP_MESSAGE, VALID_JSON);
        pushBundle.putString(PushMessage.EXTRA_SEND_ID, "send id");
        pushBundle.putString(PushMessage.EXTRA_RICH_PUSH_ID, "message_id");

        PushMessage pushMessage = new PushMessage(pushBundle);

        LegacyInAppMessage message = LegacyInAppMessage.fromPush(pushMessage);

        assertEquals("message_id", message.getClickActionValues().get("^mc").getString());
    }

    /**
     * Test parsing a in-app message from a push message that includes a type other than banner returns null.
     */
    @Test(expected = JsonException.class)
    public void testParseJsonDisplayNotBanner() throws JsonException {
        // Same as VALID_JSON json but "modal" instead of "banner" display type
        String invalidMessage = "{\"display\": {\"primary_color\": \"#FF0000\"," +
                "\"duration\": 10, \"secondary_color\": \"#00FF00\", \"type\": \"modal\"," +
                "\"alert\": \"Oh hi!\"}, \"actions\": {\"button_group\": \"ua_yes_no\"," +
                "\"button_actions\": {\"yes\": {\"^+t\": \"yes_tag\"}, \"no\": {\"^+t\": \"no_tag\"}}," +
                "\"on_click\": {\"^d\": \"someurl\"}}, \"expiry\": \"2015-12-12T12:00:00\", \"extra\":" +
                "{\"wat\": 123, \"Tom\": \"Selleck\"}}";

        Bundle pushBundle = new Bundle();
        pushBundle.putString(PushMessage.EXTRA_IN_APP_MESSAGE, invalidMessage);
        PushMessage pushMessage = new PushMessage(pushBundle);

        LegacyInAppMessage.fromPush(pushMessage);
    }

    /**
     * Test parsing a in-app message from a JSON invalid JSON payload throws an exception.
     */
    @Test(expected = JsonException.class)
    public void testParseJsonInvalidPayload() throws JsonException {
        Bundle pushBundle = new Bundle();
        pushBundle.putString(PushMessage.EXTRA_IN_APP_MESSAGE, JsonValue.wrap("wat").toString());
        PushMessage pushMessage = new PushMessage(pushBundle);

        LegacyInAppMessage.fromPush(pushMessage);
    }

}