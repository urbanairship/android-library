/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.iam;

import android.graphics.Color;
import android.os.Parcel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

public class InAppMessageTest extends BaseTestCase {

    // Taken from the push api spec
    public static String VALID_JSON = "{\"display\": {\"primary_color\": \"#FF0000\"," +
            "\"duration\": 10, \"secondary_color\": \"#00FF00\", \"type\": \"banner\"," +
            "\"alert\": \"Oh hi!\"}, \"actions\": {\"button_group\": \"ua_yes_no\"," +
            "\"button_actions\": {\"yes\": {\"^+t\": \"yes_tag\"}, \"no\": {\"^+t\": \"no_tag\"}}," +
            "\"on_click\": {\"^d\": \"someurl\"}}, \"expiry\": \"2015-12-12T12:00:00\", \"extra\":" +
            "{\"wat\": 123, \"Tom\": \"Selleck\"}}";

    @Rule
    public ExpectedException exception = ExpectedException.none();

    /**
     * Test building the in-app message.
     */
    @Test
    public void testBuildNotification() throws JsonException {
        Map<String, Object> extras = new HashMap<>();
        extras.put("key", "value");
        JsonMap jsonExtras = JsonValue.wrap(extras).getMap();

        Map<String, ActionValue> actionValueMap = new HashMap<>();
        actionValueMap.put("action name", new ActionValue());

        InAppMessage message = new InAppMessage.Builder()
                .setAlert("alert")
                .setId("id")
                .setDuration(10l)
                .setExpiry(200l)
                .setPrimaryColor(Color.BLACK)
                .setSecondaryColor(Color.BLUE)
                .setPosition(InAppMessage.POSITION_TOP)
                .setExtras(jsonExtras)
                .setButtonActionValues("button one", actionValueMap)
                .setButtonGroupId("button group")
                .setClickActionValues(actionValueMap)
                .create();

        // Verify everything is set on the notification
        assertEquals("alert", message.getAlert());
        assertEquals("id", message.getId());
        assertEquals(10l, (long) message.getDuration());
        assertEquals(200, message.getExpiry());
        assertEquals(Color.BLACK, (int) message.getPrimaryColor());
        assertEquals(Color.BLUE, (int) message.getSecondaryColor());
        assertEquals(InAppMessage.POSITION_TOP, message.getPosition());
        assertEquals(jsonExtras, message.getExtras());
        assertEquals(actionValueMap, message.getButtonActionValues("button one"));
        assertEquals("button group", message.getButtonGroupId());
        assertEquals(actionValueMap, message.getClickActionValues());
    }

    /**
     * Test building an empty in-app message does not throw any exceptions and defaults to proper values.
     */
    @Test
    public void testBuildEmptyNotification() {
        InAppMessage message = new InAppMessage.Builder().create();

        // Verify defaults
        assertNull(message.getAlert());
        assertNull(message.getId());
        assertNull(message.getDuration());
        assertEquals(InAppMessage.POSITION_BOTTOM, message.getPosition());
        assertTrue(message.getExtras().isEmpty());
        assertNull(message.getButtonActionValues("button one"));
        assertNull(message.getButtonGroupId());
        assertTrue(message.getClickActionValues().isEmpty());
        assertNull(message.getPrimaryColor());
        assertNull(message.getSecondaryColor());
    }

    /**
     * Test building a in-app message from an existing in-app message copied all the attributes
     * to the new message.
     */
    @Test
    public void testBuildFromExistingNotification() throws JsonException {
        Map<String, Object> extras = new HashMap<>();
        extras.put("key", "value");
        JsonMap jsonExtras = JsonValue.wrap(extras).getMap();

        Map<String, ActionValue> actionValueMap = new HashMap<>();
        actionValueMap.put("action name", new ActionValue());

        InAppMessage original = new InAppMessage.Builder()
                .setAlert("alert")
                .setId("id")
                .setDuration(100l)
                .setExpiry(200l)
                .setPosition(InAppMessage.POSITION_TOP)
                .setExtras(jsonExtras)
                .setButtonActionValues("button one", actionValueMap)
                .setButtonGroupId("button group")
                .setClickActionValues(actionValueMap)
                .create();

        InAppMessage extended = new InAppMessage.Builder(original)
                .setId("other id")
                .create();

        // Verify everything is set on the new notification
        assertEquals("alert", extended.getAlert());
        assertEquals("other id", extended.getId());
        assertEquals(100l, (long) extended.getDuration());
        assertEquals(200, extended.getExpiry());
        assertEquals(InAppMessage.POSITION_TOP, extended.getPosition());
        assertEquals(jsonExtras, extended.getExtras());
        assertEquals(actionValueMap, extended.getButtonActionValues("button one"));
        assertEquals("button group", extended.getButtonGroupId());
        assertEquals(actionValueMap, extended.getClickActionValues());
    }

    /**
     * Test setting a position other than TOP or BOTTOM throws an exception.
     */
    @Test
    public void testBuilderSetInvalidPosition() {
        exception.expect(IllegalArgumentException.class);
        new InAppMessage.Builder().setPosition(2);
    }

    /**
     * Test setting a negative duration or 0 duration throws an exception.
     */
    @Test
    public void testBuilderSetInvalidDuration() {
        exception.expect(IllegalArgumentException.class);
        new InAppMessage.Builder().setDuration(0l);
    }

    /**
     * Test parsing a in-app message from a JSON payload.
     */
    @Test
    public void testParseJson() throws JsonException {
        InAppMessage message = InAppMessage.parseJson(VALID_JSON);
        assertEquals("Oh hi!", message.getAlert());
        assertEquals(10000l, (long) message.getDuration());
        assertEquals(1449921600000l, message.getExpiry());
        assertEquals("ua_yes_no", message.getButtonGroupId());
        assertEquals(InAppMessage.POSITION_BOTTOM, message.getPosition());
        assertEquals(Color.parseColor("#FF0000"), (int) message.getPrimaryColor());
        assertEquals(Color.parseColor("#00FF00"), (int) message.getSecondaryColor());

        // Verify extras
        JsonMap extras = message.getExtras();
        assertEquals(2, extras.size());
        assertEquals("Selleck", extras.get("Tom").getString());
        assertEquals(123, extras.get("wat").getInt(0));

        // Verify on click action values
        Map<String, ActionValue> onClickActionValues = message.getClickActionValues();
        assertEquals(1, onClickActionValues.size());
        assertEquals("someurl", onClickActionValues.get("^d").getString());

        // Verify yes button action values
        Map<String, ActionValue> yesActionValues = message.getButtonActionValues("yes");
        assertEquals(1, yesActionValues.size());
        assertEquals("yes_tag", yesActionValues.get("^+t").getString());

        // Verify no button action values
        Map<String, ActionValue> noActionValues = message.getButtonActionValues("no");
        assertEquals(1, noActionValues.size());
        assertEquals("no_tag", noActionValues.get("^+t").getString());
    }

    /**
     * Test parsing a in-app message from a JSON payload that includes a type other than banner returns null.
     */
    @Test
    public void testParseJsonDisplayNotBanner() throws JsonException {
        // Same as VALID_JSON json but "modal" instead of "banner" display type
        String invalidJson = "{\"display\": {\"primary_color\": \"#FF0000\"," +
                "\"duration\": 10, \"secondary_color\": \"#00FF00\", \"type\": \"modal\"," +
                "\"alert\": \"Oh hi!\"}, \"actions\": {\"button_group\": \"ua_yes_no\"," +
                "\"button_actions\": {\"yes\": {\"^+t\": \"yes_tag\"}, \"no\": {\"^+t\": \"no_tag\"}}," +
                "\"on_click\": {\"^d\": \"someurl\"}}, \"expiry\": \"2015-12-12T12:00:00\", \"extra\":" +
                "{\"wat\": 123, \"Tom\": \"Selleck\"}}";

        assertNull(InAppMessage.parseJson(invalidJson));
    }

    /**
     * Test parsing a in-app message from a JSON payload that does not define a "display" object returns null.
     */
    @Test
    public void testParseJsonNoDisplay() throws JsonException {
        // Same as VALID_JSON json but without the display object
        String invalidJson = "{\"actions\": {\"button_group\": \"ua_yes_no\"," +
                "\"button_actions\": {\"yes\": {\"^+t\": \"yes_tag\"}, \"no\": {\"^+t\": \"no_tag\"}}," +
                "\"on_click\": {\"^d\": \"someurl\"}}, \"expiry\": \"2015-12-12T12:00:00\", \"extra\":" +
                "{\"wat\": 123, \"Tom\": \"Selleck\"}}";

        assertNull(InAppMessage.parseJson(invalidJson));
    }

    /**
     * Test parsing a in-app message from a JSON invalid JSON payload throws an exception.
     */
    @Test
    public void testParseJsonInvalidPayload() throws JsonException {
        exception.expect(JsonException.class);
        InAppMessage.parseJson("{\"wat\":}");
    }

    /**
     * Test equals method works.
     */
    @Test
    public void testEquals() throws JsonException {
        InAppMessage original = InAppMessage.parseJson(VALID_JSON);
        InAppMessage same = InAppMessage.parseJson(VALID_JSON);
        InAppMessage different = new InAppMessage.Builder(original).setId("id").create();

        assertTrue(original.equals(same));
        assertFalse(original.equals(different));
    }

    /**
     * Test reading and writing from a parcel.
     */
    @Test
    public void testParcelable() throws JsonException {
        InAppMessage message = InAppMessage.parseJson(VALID_JSON);

        // Write the message to a parcel
        Parcel parcel = Parcel.obtain();
        message.writeToParcel(parcel, 0);

        // Reset the parcel so we can read it
        parcel.setDataPosition(0);

        // Create the message from the parcel
        InAppMessage fromParcel = InAppMessage.CREATOR.createFromParcel(parcel);
        assertEquals(message, fromParcel);
    }

    /**
     * Test writing and reading the same empty in-app message from a parcel does not produce a NPE.
     * MB-1188
     */
    @Test
    public void testParcelExtrasNPE() throws JsonException {
        InAppMessage message = new InAppMessage.Builder().create();

        for (int i = 0; i < 2; i++) {
            // Write the message to a parcel
            Parcel parcel = Parcel.obtain();
            message.writeToParcel(parcel, 0);

            // Reset the parcel so we can read it
            parcel.setDataPosition(0);

            message = InAppMessage.CREATOR.createFromParcel(parcel);
        }
    }

    /**
     * Test getting the in-app message as Json.
     */
    @Test
    public void testToJson() throws JsonException {
        InAppMessage original = InAppMessage.parseJson(VALID_JSON);

        // Create another message from the JSON of the original message
        InAppMessage same = InAppMessage.parseJson(original.toJsonValue().toString());

        // They should be the same
        assertEquals(original, same);
    }

    /**
     * Test expiry and duration do not suffer from sub-second precision loss when serializing to json.
     */
    @Test
    public void testNoPrecisionLoss() throws JsonException {
        InAppMessage message = new InAppMessage.Builder()
                .setDuration(12345l)
                .setExpiry(6789l)
                .create();

        InAppMessage jsonMessage = InAppMessage.parseJson(message.toJsonValue().toString());

        // They should be the same
        assertEquals(message, jsonMessage);
        assertEquals((long) jsonMessage.getDuration(), 12345l);
        assertEquals(jsonMessage.getExpiry(), 6789l);
    }

}
