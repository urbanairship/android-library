/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.UAirship;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.actions.ActionValueException;
import com.urbanairship.actions.OpenRichPushInboxAction;
import com.urbanairship.actions.OverlayRichPushMessageAction;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.iam.InAppMessage;

import junit.framework.Assert;

import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PushMessageTest extends BaseTestCase {

    /**
     * Test when the message expired.
     */
    @Test
    public void testIsExpired() {
        Bundle extras = new Bundle();
        // Set expiration in the past (Sun, 09 Sep 2001 01:46:40 GMT)
        extras.putString(PushMessage.EXTRA_EXPIRATION, String.valueOf(1000000));

        PushMessage pushMessage = new PushMessage(extras);
        assertTrue("Message should have expired.", pushMessage.isExpired());
    }

    /**
     * Test when message does not have an expiration.
     */
    @Test
    public void testNoExpiration() {
        Bundle extras = new Bundle();
        PushMessage pushMessage = new PushMessage(extras);
        assertFalse("Message should not have an expiration.", pushMessage.isExpired());
    }

    /**
     * Test when the message has not expired.
     */
    @Test
    public void testNotExpired() {
        Bundle extras = new Bundle();
        // Set expiration in the future
        String expiration = String.valueOf((System.currentTimeMillis() + 10000) / 1000);
        extras.putString(PushMessage.EXTRA_EXPIRATION, expiration);

        PushMessage pushMessage = new PushMessage(extras);
        assertFalse("Message has not expired.", pushMessage.isExpired());
    }

    /**
     * Test the message is ping.
     */
    @Test
    public void testIsPing() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_PING, "testPing");

        PushMessage pushMessage = new PushMessage(extras);
        assertTrue("The message is ping.", pushMessage.isPing());
    }

    /**
     * Test the message is not ping.
     */
    @Test
    public void testIsNotPing() {
        Bundle extras = new Bundle();
        PushMessage pushMessage = new PushMessage(extras);
        assertFalse("The message is not ping.", pushMessage.isPing());
    }

    /**
     * Test get the message's canonical push ID.
     */
    @Test
    public void testGetCanonicalPushId() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_PUSH_ID, "testPushID");

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("The push ID should match.", "testPushID", pushMessage.getCanonicalPushId());
    }

    /**
     * Test get the rich push message ID.
     */
    @Test
    public void testGetRichPushMessageId() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_RICH_PUSH_ID, "testRichPushID");

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("The rich push ID should match.", "testRichPushID",
                pushMessage.getRichPushMessageId());
    }

    /**
     * Test get the notification alert.
     */
    @Test
    public void testGetAlert() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_ALERT, "Test Push Alert!");

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("The notification alert should match.", "Test Push Alert!",
                pushMessage.getAlert());
    }

    /**
     * Test get push send ID.
     */
    @Test
    public void testGetSendId() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_SEND_ID, "testSendID");

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("The push send ID should match.", "testSendID", pushMessage.getSendId());
    }

    /**
     * Test get push metadata.
     */
    @Test
    public void testGetMetadata() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_METADATA, "testMetadata");

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("The push send metadata should match.", "testMetadata", pushMessage.getMetadata());
    }

    /**
     * Test get push bundle.
     */
    @Test
    public void testGetPushBundle() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_ALERT, "Test Push Alert!");
        extras.putString(PushMessage.EXTRA_PUSH_ID, "testPushID");

        PushMessage pushMessage = new PushMessage(extras);
        assertBundlesEquals("The push bundle should match.", extras, pushMessage.getPushBundle());
    }

    /**
     * Test get notification title.
     */
    @Test
    public void testGetTitle() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_TITLE, "Test Title");

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("The notification title should match.", "Test Title", pushMessage.getTitle());
    }

    /**
     * Test get notification summary.
     */
    @Test
    public void testGetSummary() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_SUMMARY, "Test Summary");

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("The notification summary should match.", "Test Summary", pushMessage.getSummary());
    }

    /**
     * Test get wearable payload.
     */
    @Test
    public void testGetWearablePayload() {
        String wearable = " \"wearable\": { \"background_image\": \"http://example.com/background.jpg\", \"extra_pages\": [{ \"title\": \"Page 1 title\", \"alert\": \"Page 1 title, alert\"}] }";
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_WEARABLE, wearable);

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("The wearable payload should match.", wearable, pushMessage.getWearablePayload());
    }

    /**
     * Test get notification style payload.
     */
    @Test
    public void testGetStylePayload() {
        String bigTextStyle = "\"type\":\"big_text\", \"big_text\":\"big text\", \"title\":\"big text title\", \"summary\":\"big text summary\"";
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_STYLE, bigTextStyle);

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("The style payload should match.", bigTextStyle, pushMessage.getStylePayload());
    }

    /**
     * Test isLocalOnly.
     */
    @Test
    public void testIsLocalOnly() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_LOCAL_ONLY, "true");

        PushMessage pushMessage = new PushMessage(extras);
        assertTrue("IsLocalOnly should return true", pushMessage.isLocalOnly());
    }

    /**
     * Test getPriority at the MAX_PRIORITY.
     */
    @Test
    public void testGetPriorityMax() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_PRIORITY, String.valueOf(PushMessage.MAX_PRIORITY));

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("Should constrain to the max.", pushMessage.getPriority(), PushMessage.MAX_PRIORITY);
    }

    /**
     * Test getPriority above the MAX_PRIORITY.
     */
    @Test
    public void testGetPriorityAboveMax() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_PRIORITY, String.valueOf(PushMessage.MAX_PRIORITY + 1));

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("Should constrain to the max.", pushMessage.getPriority(), PushMessage.MAX_PRIORITY);
    }

    /**
     * Test getPriority below the MAX_PRIORITY.
     */
    @Test
    public void testGetPriorityBelowMax() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_PRIORITY, String.valueOf(PushMessage.MAX_PRIORITY - 1));

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("Should allow values between the min and max.", pushMessage.getPriority(), PushMessage.MAX_PRIORITY - 1);
    }

    /**
     * Test getPriority at the MIN_PRIORITY.
     */
    @Test
    public void testGetPriorityMin() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_PRIORITY, String.valueOf(PushMessage.MIN_PRIORITY));

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("Should constrain to the min.", pushMessage.getPriority(), PushMessage.MIN_PRIORITY);
    }

    /**
     * Test getPriority above the MIN_PRIORITY.
     */
    @Test
    public void testGetPriorityAboveMin() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_PRIORITY, String.valueOf(PushMessage.MIN_PRIORITY + 1));

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("Should allow values between the min and max.", pushMessage.getPriority(), PushMessage.MIN_PRIORITY + 1);
    }

    /**
     * Test getPriority below the MIN_PRIORITY.
     */
    @Test
    public void testGetPriorityBelowMin() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_PRIORITY, String.valueOf(PushMessage.MIN_PRIORITY - 1));

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("Should constrain to the min.", pushMessage.getPriority(), PushMessage.MIN_PRIORITY);
    }

    /**
     * Test getVisibility at the MAX_VISIBILITY.
     */
    @Test
    public void testGetVisibilityMax() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_VISIBILITY, String.valueOf(PushMessage.MAX_VISIBILITY));

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("Should constrain to the max.", pushMessage.getVisibility(), PushMessage.MAX_VISIBILITY);
    }

    /**
     * Test getVisibility above MAX_VISIBILITY.
     */
    @Test
    public void testGetVisibilityAboveMax() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_VISIBILITY, String.valueOf(PushMessage.MAX_VISIBILITY + 1));

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("Should constrain to the max.", pushMessage.getVisibility(), PushMessage.MAX_VISIBILITY);
    }

    /**
     * Test getVisibility below MAX_VISIBILITY.
     */
    @Test
    public void testGetVisibilityBelowMax() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_VISIBILITY, String.valueOf(PushMessage.MAX_VISIBILITY - 1));

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("Should allow values between the min and max.", pushMessage.getVisibility(), PushMessage.MAX_VISIBILITY - 1);
    }

    /**
     * Test getVisibility at the MIN_VISIBILITY.
     */
    @Test
    public void testGetVisibilityMin() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_VISIBILITY, String.valueOf(PushMessage.MIN_VISIBILITY));

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("Should constrain to the min.", pushMessage.getVisibility(), PushMessage.MIN_VISIBILITY);
    }

    /**
     * Test getVisibility above MIN_VISIBILITY.
     */
    @Test
    public void testGetVisibilityAboveMin() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_VISIBILITY, String.valueOf(PushMessage.MIN_VISIBILITY + 1));

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("Should allow values between the min and max.", pushMessage.getVisibility(), PushMessage.MIN_VISIBILITY + 1);
    }

    /**
     * Test getVisibility below MIN_VISIBILITY.
     */
    @Test
    public void testGetVisibilityBelowMin() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_VISIBILITY, String.valueOf(PushMessage.MIN_VISIBILITY - 1));

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("Should constrain to the min.", pushMessage.getVisibility(), PushMessage.MIN_VISIBILITY);
    }

    /**
     * Test getCategory.
     */
    @Test
    public void testGetCategory() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_CATEGORY, "promo");

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("The category should match.", pushMessage.getCategory(), "promo");
    }

    /**
     * Test getting the in-app message from the push payload.
     */
    @Test
    public void getGetInAppMessage() throws JsonException {
        String inAppJson = "{\"display\": {\"primary_color\": \"#FF0000\"," +
                "\"duration\": 10, \"secondary_color\": \"#00FF00\", \"type\": \"banner\"," +
                "\"alert\": \"Oh hi!\"}, \"actions\": {\"button_group\": \"ua_yes_no\"," +
                "\"button_actions\": {\"yes\": {\"^+t\": \"yes_tag\"}, \"no\": {\"^+t\": \"no_tag\"}}," +
                "\"on_click\": {\"^d\": \"someurl\"}}, \"expiry\": \"2015-12-12T12:00:00\", \"extra\":" +
                "{\"wat\": 123, \"Tom\": \"Selleck\"}}";

        InAppMessage expected = new InAppMessage.Builder(InAppMessage.parseJson(inAppJson))
                .setId("send id")
                .create();

        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_IN_APP_MESSAGE, inAppJson);
        extras.putString(PushMessage.EXTRA_SEND_ID, "send id");

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals(expected, pushMessage.getInAppMessage());
    }

    /**
     * Test getting the in-app message from the push payload amends the message
     * to include the open MCRAP action if the push also contains a rich push message ID.
     */
    @Test
    public void getGetInAppMessageAmendsOpenMcRap() throws JsonException, ActionValueException {
        String inAppJson = "{\"display\": {\"primary_color\": \"#FF0000\"," +
                "\"duration\": 10, \"secondary_color\": \"#00FF00\", \"type\": \"banner\"," +
                "\"alert\": \"Oh hi!\"}, \"actions\": {\"button_group\": \"ua_yes_no\"," +
                "\"button_actions\": {\"yes\": {\"^+t\": \"yes_tag\"}, \"no\": {\"^+t\": \"no_tag\"}}," +
                "\"on_click\": {\"^d\": \"someurl\"}}, \"expiry\": \"2015-12-12T12:00:00\", \"extra\":" +
                "{\"wat\": 123, \"Tom\": \"Selleck\"}}";


        InAppMessage rawMessage = InAppMessage.parseJson(inAppJson);

        HashMap<String, ActionValue> actions = new HashMap<>(rawMessage.getClickActionValues());
        actions.put(OpenRichPushInboxAction.DEFAULT_REGISTRY_SHORT_NAME, ActionValue.wrap("message_id"));

        InAppMessage expected = new InAppMessage.Builder(rawMessage)
                .setId("send id")
                .setClickActionValues(actions)
                .create();

        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_IN_APP_MESSAGE, inAppJson);
        extras.putString(PushMessage.EXTRA_SEND_ID, "send id");
        extras.putString(PushMessage.EXTRA_RICH_PUSH_ID, "message_id");

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals(expected, pushMessage.getInAppMessage());
    }

    /**
     * Test get public notification payload.
     */
    @Test
    public void testGetPublicNotificationPayload() {
        String publicNotification = "\"title\":\"test title\", \"alert\":\"test alert\", \"summary\":\"test summary\"";
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_PUBLIC_NOTIFICATION, publicNotification);

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("The public notification payload should match.", publicNotification, pushMessage.getPublicNotificationPayload());
    }

    /**
     * Test saving and reading a push message from a parcel.
     */
    @Test
    public void testParcelable() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_ALERT, "Test Push Alert!");
        extras.putString("a random extra", "value");

        PushMessage message = new PushMessage(extras);

        // Write the push message to a parcel
        Parcel parcel = Parcel.obtain();
        message.writeToParcel(parcel, 0);

        // Reset the parcel so we can read it
        parcel.setDataPosition(0);

        // Create the message from the parcel
        PushMessage fromParcel = PushMessage.CREATOR.createFromParcel(parcel);
        assertEquals("value", fromParcel.getPushBundle().getString("a random extra"));
        assertEquals("Test Push Alert!", fromParcel.getAlert());
    }

    /**
     * Test get actions returns a Map of action names to action values.
     */
    @Test
    public void testGetActions() throws JsonException {
        Map<String, ActionValue> actions = new HashMap<>();
        actions.put("action_name", ActionValue.wrap("action_value"));
        actions.put("oh", ActionValue.wrap("hi"));

        Bundle bundle = new Bundle();
        bundle.putString(PushMessage.EXTRA_ACTIONS, JsonValue.wrap(actions).toString());
        PushMessage message = new PushMessage(bundle);

        assertEquals(actions, message.getActions());
    }

    /**
     * Test get actions returns an empty map if its unable to parse the actions payload.
     */
    @Test
    public void testGetActionsInvalidPayload() {
        Bundle bundle = new Bundle();
        bundle.putString(PushMessage.EXTRA_ACTIONS, "}}what{{");
        PushMessage message = new PushMessage(bundle);

        assertTrue(message.getActions().isEmpty());
    }

    /**
     * Test get actions appends a OpenRichPushInboxAction if it contains a message ID and does
     * not already define a inbox action.
     */
    @Test
    public void testGetActionAppendsInboxAction() throws JsonException {
        Map<String, ActionValue> actions = new HashMap<>();
        actions.put("action_name", ActionValue.wrap("action_value"));
        actions.put("oh", ActionValue.wrap("hi"));

        Bundle bundle = new Bundle();
        bundle.putString(PushMessage.EXTRA_ACTIONS, JsonValue.wrap(actions).toString());
        bundle.putString(PushMessage.EXTRA_RICH_PUSH_ID, "message ID");
        PushMessage message = new PushMessage(bundle);

        actions.put(OpenRichPushInboxAction.DEFAULT_REGISTRY_SHORT_NAME, ActionValue.wrap("message ID"));
        assertEquals(actions, message.getActions());
    }

    /**
     * Test get actions when the payload defines an inbox action that it does not append the
     * OpenRichPushInboxAction action.
     */
    @Test
    public void testGetActionsContainsInboxAction() throws JsonException {
        Map<String, ActionValue> actions = new HashMap<>();
        actions.put("action_name", ActionValue.wrap("action_value"));
        actions.put("oh", ActionValue.wrap("hi"));
        actions.put(OpenRichPushInboxAction.DEFAULT_REGISTRY_SHORT_NAME, ActionValue.wrap("some other message ID"));

        Bundle bundle = new Bundle();
        bundle.putString(PushMessage.EXTRA_ACTIONS, JsonValue.wrap(actions).toString());
        bundle.putString(PushMessage.EXTRA_RICH_PUSH_ID, "message ID");
        PushMessage message = new PushMessage(bundle);

        assertEquals(actions, message.getActions());
    }

    /**
     * Test get actions when the payload defines an overlay rich push message action that it does not append the
     * OpenRichPushInboxAction action.
     */
    @Test
    public void testGetActionsContainsOverlayMessageAction() throws JsonException {
        Map<String, ActionValue> actions = new HashMap<>();
        actions.put("action_name", ActionValue.wrap("action_value"));
        actions.put("oh", ActionValue.wrap("hi"));
        actions.put(OverlayRichPushMessageAction.DEFAULT_REGISTRY_NAME, ActionValue.wrap("some other message ID"));

        Bundle bundle = new Bundle();
        bundle.putString(PushMessage.EXTRA_ACTIONS, JsonValue.wrap(actions).toString());
        bundle.putString(PushMessage.EXTRA_RICH_PUSH_ID, "message ID");
        PushMessage message = new PushMessage(bundle);

        assertEquals(actions, message.getActions());
    }

    /**
     * Test get notification sound.
     */
    @Test
    public void testGetSound() {
        Context context = Mockito.spy(UAirship.getApplicationContext());
        Resources resources = Mockito.mock(Resources.class);
        Mockito.when(resources.getIdentifier(Mockito.eq("test_sound"), Mockito.anyString(), Mockito.anyString())).thenReturn(5);
        Mockito.when(context.getResources()).thenReturn(resources);

        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_SOUND, "test_sound");
        PushMessage pushMessage = new PushMessage(extras);

        Uri expected = Uri.parse("android.resource://" + context.getPackageName() + "/" + 5);
        Assert.assertEquals("The sound should match.", expected, pushMessage.getSound(context));
    }

    /**
     * Test get notification sound is null when not found.
     */
    @Test
    public void testGetSoundNull() {
        Context context = UAirship.getApplicationContext();
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_SOUND, "test_sound");
        PushMessage pushMessage = new PushMessage(extras);

        Assert.assertNull("The sound should be null.", pushMessage.getSound(context));
    }

    /**
     * Test get the notification icon.
     */
    public void testGetIcon() {
        Context context = Mockito.spy(UAirship.getApplicationContext());
        Resources resources = Mockito.mock(Resources.class);
        Mockito.when(resources.getIdentifier(Mockito.eq("icon"), Mockito.anyString(), Mockito.anyString())).thenReturn(5);
        Mockito.when(context.getResources()).thenReturn(resources);

        Bundle extras = new Bundle();

        extras.putString(PushMessage.EXTRA_ICON, "icon");
        PushMessage pushMessage = new PushMessage(extras);

        assertEquals("The notification icon resource should match", 5, pushMessage.getIcon(context, 1));
    }

    /**
     * Test get notification icon color.
     */
    @Test
    public void testGetIconColor() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_ICON_COLOR, "red");

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("The notification icon color should match.", -65536, pushMessage.getIconColor(0));
    }

    /**
     * Test fromIntent creates a PushMessage instance if a bundle exists under PushManager.EXTRA_PUSH_MESSAGE_BUNDLE.
     */
    @Test
    public void testFromIntent() {
        Bundle bundle = new Bundle();
        Intent intent = new Intent();
        intent.putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, bundle);

        PushMessage message = PushMessage.fromIntent(intent);
        assertNotNull(message);
        assertBundlesEquals(bundle, message.getPushBundle());
    }

    /**
     * Test fromIntent returns null if its unable to find a bundle extra under PushManager.EXTRA_PUSH_MESSAGE_BUNDLE.
     */
    @Test
    public void testFromIntentInvalid() {
        assertNull(PushMessage.fromIntent(null));
        assertNull(PushMessage.fromIntent(new Intent()));
        assertNull(PushMessage.fromIntent(new Intent().putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, "not a bundle")));
    }

    /**
     * Test that if the push contains any key that starts with "com.urbanairship" containsAirshipKeys
     * returns true.
     */
    @Test
    public void testContainsAirshipKeys() {
        Bundle bundle = new Bundle();
        bundle.putString("cool", "story");

        PushMessage message = new PushMessage(bundle);
        assertFalse(message.containsAirshipKeys());


        bundle.putString("com.urbanairship.whatever", "value");
        message = new PushMessage(bundle);
        assertTrue(message.containsAirshipKeys());
    }
}

