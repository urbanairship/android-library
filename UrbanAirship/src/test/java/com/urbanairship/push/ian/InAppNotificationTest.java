package com.urbanairship.push.ian;

import android.graphics.Color;
import android.os.Parcel;

import com.urbanairship.RobolectricGradleTestRunner;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

@RunWith(RobolectricGradleTestRunner.class)
public class InAppNotificationTest {

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
     * Test building the notification.
     */
    @Test
    public void testBuildNotification() throws JsonException {
        Map<String, Object> extras = new HashMap<>();
        extras.put("key", "value");
        JsonMap jsonExtras = JsonValue.wrap(extras).getMap();

        Map<String, ActionValue> actionValueMap = new HashMap<>();
        actionValueMap.put("action name", new ActionValue());

        InAppNotification notification = new InAppNotification.Builder()
                .setAlert("alert")
                .setId("id")
                .setDuration(100l)
                .setExpiry(200l)
                .setPrimaryColor(Color.BLACK)
                .setSecondaryColor(Color.BLUE)
                .setPosition(InAppNotification.POSITION_TOP)
                .setExtras(jsonExtras)
                .setButtonActionValues("button one", actionValueMap)
                .setButtonGroupId("button group")
                .setClickActionValues(actionValueMap)
                .create();

        // Verify everything is set on the notification
        assertEquals("alert", notification.getAlert());
        assertEquals("id", notification.getId());
        assertEquals(100l, (long) notification.getDuration());
        assertEquals(200, notification.getExpiry());
        assertEquals(Color.BLACK, (int) notification.getPrimaryColor());
        assertEquals(Color.BLUE, (int) notification.getSecondaryColor());
        assertEquals(InAppNotification.POSITION_TOP, notification.getPosition());
        assertEquals(jsonExtras, notification.getExtras());
        assertEquals(actionValueMap, notification.getButtonActionValues("button one"));
        assertEquals("button group", notification.getButtonGroupId());
        assertEquals(actionValueMap, notification.getClickActionValues());
    }

    /**
     * Test building an empty notification does not throw any exceptions and defaults to proper values.
     */
    @Test
    public void testBuildEmptyNotification() {
        InAppNotification notification = new InAppNotification.Builder().create();

        // Verify defaults
        assertNull(notification.getAlert());
        assertNull(notification.getId());
        assertNull(notification.getDuration());
        assertEquals(InAppNotification.POSITION_BOTTOM, notification.getPosition());
        assertTrue(notification.getExtras().isEmpty());
        assertNull(notification.getButtonActionValues("button one"));
        assertNull(notification.getButtonGroupId());
        assertTrue(notification.getClickActionValues().isEmpty());
        assertNull(notification.getPrimaryColor());
        assertNull(notification.getSecondaryColor());
    }

    /**
     * Test building a notification from an notification message copied all the attributes
     * to the new notification.
     */
    @Test
    public void testBuildFromExistingNotification() throws JsonException {
        Map<String, Object> extras = new HashMap<>();
        extras.put("key", "value");
        JsonMap jsonExtras = JsonValue.wrap(extras).getMap();

        Map<String, ActionValue> actionValueMap = new HashMap<>();
        actionValueMap.put("action name", new ActionValue());

        InAppNotification originalNotification = new InAppNotification.Builder()
                .setAlert("alert")
                .setId("id")
                .setDuration(100l)
                .setExpiry(200l)
                .setPosition(InAppNotification.POSITION_TOP)
                .setExtras(jsonExtras)
                .setButtonActionValues("button one", actionValueMap)
                .setButtonGroupId("button group")
                .setClickActionValues(actionValueMap)
                .create();

        InAppNotification newNotification = new InAppNotification.Builder(originalNotification)
                .setId("other id")
                .create();

        // Verify everything is set on the new notification
        assertEquals("alert", newNotification.getAlert());
        assertEquals("other id", newNotification.getId());
        assertEquals(100l, (long)newNotification.getDuration());
        assertEquals(200, newNotification.getExpiry());
        assertEquals(InAppNotification.POSITION_TOP, newNotification.getPosition());
        assertEquals(jsonExtras, newNotification.getExtras());
        assertEquals(actionValueMap, newNotification.getButtonActionValues("button one"));
        assertEquals("button group", newNotification.getButtonGroupId());
        assertEquals(actionValueMap, newNotification.getClickActionValues());
    }

    /**
     * Test setting a position other than TOP or BOTTOM throws an exception.
     */
    @Test
    public void testBuilderSetInvalidPosition() {
        exception.expect(IllegalArgumentException.class);
        new InAppNotification.Builder().setPosition(2);
    }

    /**
     * Test setting a negative duration or 0 duration throws an exception.
     */
    @Test
    public void testBuilderSetInvalidDuration() {
        exception.expect(IllegalArgumentException.class);
        new InAppNotification.Builder().setDuration(0l);
    }

    /**
     * Test parsing a notification from a JSON payload.
     */
    @Test
    public void testParseJson() throws JsonException {
        InAppNotification notification = InAppNotification.parseJson(VALID_JSON);
        assertEquals("Oh hi!", notification.getAlert());
        assertEquals(10000l, (long) notification.getDuration());
        assertEquals(1449921600000l, notification.getExpiry());
        assertEquals("ua_yes_no", notification.getButtonGroupId());
        assertEquals(InAppNotification.POSITION_BOTTOM, notification.getPosition());
        assertEquals(Color.parseColor("#FF0000"), (int) notification.getPrimaryColor());
        assertEquals(Color.parseColor("#00FF00"), (int) notification.getSecondaryColor());

        // Verify extras
        JsonMap extras = notification.getExtras();
        assertEquals(2, extras.size());
        assertEquals("Selleck", extras.get("Tom").getString());
        assertEquals(123, extras.get("wat").getInt(0));

        // Verify on click action values
        Map<String, ActionValue> onClickActionValues = notification.getClickActionValues();
        assertEquals(1, onClickActionValues.size());
        assertEquals("someurl", onClickActionValues.get("^d").getString());

        // Verify yes button action values
        Map<String, ActionValue> yesActionValues = notification.getButtonActionValues("yes");
        assertEquals(1, yesActionValues.size());
        assertEquals("yes_tag", yesActionValues.get("^+t").getString());

        // Verify no button action values
        Map<String, ActionValue> noActionValues = notification.getButtonActionValues("no");
        assertEquals(1, noActionValues.size());
        assertEquals("no_tag", noActionValues.get("^+t").getString());
    }

    /**
     * Test parsing a notification from a JSON payload that includes a type other than banner returns null.
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

        assertNull(InAppNotification.parseJson(invalidJson));
    }

    /**
     * Test parsing a notification from a JSON payload that does not define a "display" object returns null.
     */
    @Test
    public void testParseJsonNoDisplay() throws JsonException {
        // Same as VALID_JSON json but without the display object
        String invalidJson = "{\"actions\": {\"button_group\": \"ua_yes_no\"," +
                "\"button_actions\": {\"yes\": {\"^+t\": \"yes_tag\"}, \"no\": {\"^+t\": \"no_tag\"}}," +
                "\"on_click\": {\"^d\": \"someurl\"}}, \"expiry\": \"2015-12-12T12:00:00\", \"extra\":" +
                "{\"wat\": 123, \"Tom\": \"Selleck\"}}";

        assertNull(InAppNotification.parseJson(invalidJson));
    }

    /**
     * Test parsing a notification from a JSON invalid JSON payload throws an exception.
     */
    @Test
    public void testParseJsonInvalidPayload() throws JsonException {
        exception.expect(JsonException.class);
        InAppNotification.parseJson("{\"wat\":}");
    }

    /**
     * Test equals method works.
     */
    @Test
    public void testEquals() throws JsonException {
        InAppNotification original = InAppNotification.parseJson(VALID_JSON);
        InAppNotification same = InAppNotification.parseJson(VALID_JSON);
        InAppNotification different = new InAppNotification.Builder(original).setId("id").create();

        assertTrue(original.equals(same));
        assertFalse(original.equals(different));
    }

    /**
     * Test reading and writing from a parcel.
     */
    @Test
    public void testParcelable() throws JsonException {
        InAppNotification notification = InAppNotification.parseJson(VALID_JSON);

        // Write the notification to a parcel
        Parcel parcel = Parcel.obtain();
        notification.writeToParcel(parcel, 0);

        // Reset the parcel so we can read it
        parcel.setDataPosition(0);

        // Create the notification from the parcel
        InAppNotification fromParcel = InAppNotification.CREATOR.createFromParcel(parcel);
        assertEquals(notification, fromParcel);
    }

    /**
     * Test getting the IAN as Json.
     */
    @Test
    public void testToJson() throws JsonException {
        InAppNotification original = InAppNotification.parseJson(VALID_JSON);

        // Create another notification from the JSON of the original notification
        InAppNotification same = InAppNotification.parseJson(original.toJsonValue().toString());

        // They should be the same
        assertEquals(original, same);
    }

}
