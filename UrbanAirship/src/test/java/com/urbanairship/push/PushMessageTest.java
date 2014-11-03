package com.urbanairship.push;

import android.os.Bundle;

import com.urbanairship.RobolectricGradleTestRunner;
import com.urbanairship.richpush.RichPushManager;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricGradleTestRunner.class)
public class PushMessageTest {

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
        String expiration = String.valueOf((System.currentTimeMillis() + 10000)/1000);
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
        extras.putString(RichPushManager.RICH_PUSH_KEY, "testRichPushID");

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
     * Test get push bundle.
     */
    @Test
    public void testGetPushBundle() {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_ALERT, "Test Push Alert!");
        extras.putString(PushMessage.EXTRA_PUSH_ID, "testPushID");

        PushMessage pushMessage = new PushMessage(extras);
        assertEquals("The push bundle should match.", extras, pushMessage.getPushBundle());
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
}
