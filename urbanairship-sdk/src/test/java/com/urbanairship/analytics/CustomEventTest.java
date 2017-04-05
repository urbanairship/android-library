/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import android.os.Bundle;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.json.JsonList;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.PushMessage;
import com.urbanairship.richpush.RichPushMessage;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CustomEventTest extends BaseTestCase {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    PushManager pushManager;
    Analytics analytics;

    @Before
    public void setup() {
        analytics = mock(Analytics.class);
        TestApplication.getApplication().setAnalytics(analytics);

        pushManager = mock(PushManager.class);
        TestApplication.getApplication().setPushManager(pushManager);
    }

    /**
     * Test creating a custom event.
     */
    @Test
    public void testCustomEvent() throws JSONException {
        String eventName = createFixedSizeString('a', 255);
        String interactionId = createFixedSizeString('b', 255);
        String interactionType = createFixedSizeString('c', 255);
        String transactionId = createFixedSizeString('d', 255);
        String templateType = createFixedSizeString('e', 255);

        CustomEvent event = new CustomEvent.Builder(eventName)
                .setTransactionId(transactionId)
                .setInteraction(interactionType, interactionId)
                .setEventValue(100.123456)
                .setTemplateType(templateType)
                .create();

        EventTestUtils.validateEventValue(event, "event_name", eventName);
        EventTestUtils.validateEventValue(event, "event_value", 100123456L);
        EventTestUtils.validateEventValue(event, "transaction_id", transactionId);
        EventTestUtils.validateEventValue(event, "interaction_id", interactionId);
        EventTestUtils.validateEventValue(event, "interaction_type", interactionType);
        EventTestUtils.validateEventValue(event, "template_type", templateType);
    }

    /**
     * Test creating a custom event with a null event makes it invalid.
     */
    @Test
    public void testNullEventName() {
        //noinspection ResourceType
        CustomEvent event = new CustomEvent.Builder(null).create();
        assertFalse(event.isValid());
    }

    /**
     * Test creating a custom event with an empty event name makes it invalid.
     */
    @Test
    public void testEmptyEventName() {
        //noinspection ResourceType
        CustomEvent event = new CustomEvent.Builder("").create();
        assertFalse(event.isValid());
    }

    /**
     * Test creating a custom event with a name longer than 255 characters makes it invalid.
     */
    @Test
    public void testEventNameExceedsMaxLength() {
        CustomEvent event = new CustomEvent.Builder(createFixedSizeString('a', 256)).create();
        assertFalse(event.isValid());
    }

    /**
     * Test setting a interaction ID that is longer than 255 characters makes it invalid
     */
    @Test
    public void testInteractionIDExceedsMaxLength() {
        CustomEvent event = new CustomEvent.Builder("event name")
                .setInteraction("interaction type", createFixedSizeString('a', 256))
                .create();

        assertFalse(event.isValid());
    }

    /**
     * Test setting a attribution type that is longer than 255 characters makes it invalid.
     */
    @Test
    public void testInteractionTypeExceedsMaxLength() {
        CustomEvent event = new CustomEvent.Builder("event name")
                .setInteraction(createFixedSizeString('a', 256), "interaction id")
                .create();

        assertFalse(event.isValid());
    }

    /**
     * Test setting a transaction ID that is longer than 255 characters makes it invalid.
     */
    @Test
    public void testTransactionIDExceedsMaxLength() {
        CustomEvent event = new CustomEvent.Builder("event name")
                .setTransactionId(createFixedSizeString('a', 256))
                .create();

        assertFalse(event.isValid());
    }

    /**
     * Test track adds the event to analytics.
     */
    @Test
    public void testTrack() {
        CustomEvent event = new CustomEvent.Builder("event name").create();
        event.track();

        ArgumentCaptor<Event> argument = ArgumentCaptor.forClass(Event.class);
        verify(analytics).addEvent(argument.capture());

        assertEquals("Add event should add the event.", event, argument.getValue());
    }

    /**
     * Test creating a custom event includes the hard conversion send id if set.
     */
    @Test
    public void testHardConversionId() throws JSONException {
        CustomEvent event = new CustomEvent.Builder("event name").create();
        when(analytics.getConversionSendId()).thenReturn("send id");
        EventTestUtils.validateEventValue(event, "conversion_send_id", "send id");
    }

    /**
     * Test creating a custom event includes the hard conversion metadata if set.
     */
    @Test
    public void testHardConversionMetadata() throws JSONException {
        CustomEvent event = new CustomEvent.Builder("event name").create();
        when(analytics.getConversionMetadata()).thenReturn("metadata");
        EventTestUtils.validateEventValue(event, "conversion_metadata", "metadata");
    }

    /**
     * Test creating a custom event includes the last received metadata.
     */
    @Test
    public void testLastMetadata() throws JSONException {
        when(pushManager.getLastReceivedMetadata()).thenReturn("last metadata");

        CustomEvent event = new CustomEvent.Builder("event name").create();

        EventTestUtils.validateEventValue(event, "last_received_metadata", "last metadata");
    }

    /**
     * Test creating a custom event includes only the hard metadata if set and not the last send.
     */
    @Test
    public void testHardConversionMetadataAndLastMetadata() throws JSONException {
        when(analytics.getConversionMetadata()).thenReturn("metadata");
        when(pushManager.getLastReceivedMetadata()).thenReturn("last metadata");

        CustomEvent event = new CustomEvent.Builder("event name").create();

        EventTestUtils.validateEventValue(event, "last_received_metadata", null);
        EventTestUtils.validateEventValue(event, "conversion_metadata", "metadata");
    }

    /**
     * Test adding a custom event with interaction from a message.
     */
    @Test
    public void testInteractionFromMessage() throws JSONException {
        RichPushMessage message = mock(RichPushMessage.class);
        when(message.getMessageId()).thenReturn("message id");

        CustomEvent event = new CustomEvent.Builder("event name")
                .setInteraction(message)
                .create();

        EventTestUtils.validateEventValue(event, "interaction_id", "message id");
        EventTestUtils.validateEventValue(event, "interaction_type", "ua_mcrap");
    }

    /**
     * Test adding a custom event with a custom interaction.
     */
    @Test
    public void testCustomInteraction() throws JSONException {
        CustomEvent event = new CustomEvent.Builder("event name")
                .setInteraction("interaction type", "interaction id")
                .create();

        EventTestUtils.validateEventValue(event, "interaction_id", "interaction id");
        EventTestUtils.validateEventValue(event, "interaction_type", "interaction type");
    }

    /**
     * Test adding an interaction with a null id is allowed.
     */
    @Test
    public void testCustomInteractionNullID() throws JSONException {
        CustomEvent event = new CustomEvent.Builder("event name")
                .setInteraction("interaction type", null)
                .create();

        EventTestUtils.validateEventValue(event, "interaction_id", null);
        EventTestUtils.validateEventValue(event, "interaction_type", "interaction type");
    }

    /**
     * Test adding an interaction with a null type is allowed.
     */
    @Test
    public void testCustomInteractionNullType() throws JSONException {
        CustomEvent event = new CustomEvent.Builder("event name")
                .setInteraction(null, "interaction id")
                .create();

        EventTestUtils.validateEventValue(event, "interaction_type", null);
        EventTestUtils.validateEventValue(event, "interaction_id", "interaction id");
    }

    /**
     * Test creating a custom event without an interaction, last send id, or
     * conversion push id will be empty.
     */
    @Test
    public void testCustomInteractionEmpty() throws JSONException {
        when(pushManager.getLastReceivedMetadata()).thenReturn("last metadata");

        CustomEvent event = new CustomEvent.Builder("event name")
                .create();

        EventTestUtils.validateEventValue(event, "interaction_type", null);
        EventTestUtils.validateEventValue(event, "interaction_id", null);
        EventTestUtils.validateEventValue(event, "template_type", null);
    }

    /**
     * Test setting the event value to various valid values.
     */
    @Test
    public void testSetEventValue() throws JSONException {
        CustomEvent event;

        // Max integer
        event = new CustomEvent.Builder("event name").setEventValue(Integer.MAX_VALUE).create();
        EventTestUtils.validateEventValue(event, "event_value", 2147483647000000L);

        // Min integer
        event = new CustomEvent.Builder("event name").setEventValue(Integer.MIN_VALUE).create();
        EventTestUtils.validateEventValue(event, "event_value", -2147483648000000L);

        // 0
        event = new CustomEvent.Builder("event name").setEventValue(0).create();
        EventTestUtils.validateEventValue(event, "event_value", 0);

        // Min double (very small number) - should be 0.
        event = new CustomEvent.Builder("event name").setEventValue(Double.MIN_VALUE).create();
        EventTestUtils.validateEventValue(event, "event_value", 0);

        // Max supported double
        event = new CustomEvent.Builder("event name").setEventValue((double) Integer.MAX_VALUE).create();
        EventTestUtils.validateEventValue(event, "event_value", 2147483647000000L);

        // Min supported double
        event = new CustomEvent.Builder("event name").setEventValue((double) Integer.MIN_VALUE).create();
        EventTestUtils.validateEventValue(event, "event_value", -2147483648000000L);

        // Max supported String
        event = new CustomEvent.Builder("event name").setEventValue(String.valueOf(Integer.MAX_VALUE)).create();
        EventTestUtils.validateEventValue(event, "event_value", 2147483647000000L);

        // Min supported String
        event = new CustomEvent.Builder("event name").setEventValue(String.valueOf(Integer.MIN_VALUE)).create();
        EventTestUtils.validateEventValue(event, "event_value", -2147483648000000L);

        // "0"
        event = new CustomEvent.Builder("event name").setEventValue("0").create();
        EventTestUtils.validateEventValue(event, "event_value", 0);

        // null String
        event = new CustomEvent.Builder("event name").setEventValue((String) null).create();
        EventTestUtils.validateEventValue(event, "event_value", null);

        // Some Big Decimal
        event = new CustomEvent.Builder("event name").setEventValue(new BigDecimal(123)).create();
        EventTestUtils.validateEventValue(event, "event_value", 123000000L);

        // Max supported Big Decimal
        BigDecimal maxDecimal = new BigDecimal(Integer.MAX_VALUE);
        event = new CustomEvent.Builder("event name").setEventValue(maxDecimal).create();
        EventTestUtils.validateEventValue(event, "event_value", 2147483647000000L);

        // Min supported Big Decimal
        BigDecimal minDecimal = new BigDecimal(Integer.MIN_VALUE);
        event = new CustomEvent.Builder("event name").setEventValue(minDecimal).create();
        EventTestUtils.validateEventValue(event, "event_value", -2147483648000000L);

        // null Big Decimal
        event = new CustomEvent.Builder("event name").setEventValue((BigDecimal) null).create();
        EventTestUtils.validateEventValue(event, "event_value", null);
    }


    /**
     * Test setting event value to positive infinity throws an exception.
     */
    @Test
    public void testSetEventValuePositiveInfinity() {
        exception.expect(NumberFormatException.class);
        new CustomEvent.Builder("event name").setEventValue(Double.POSITIVE_INFINITY);
    }

    /**
     * Test setting event value to negative infinity throws an exception.
     */
    @Test
    public void testSetEventValueNegativeInfiinty() {
        exception.expect(NumberFormatException.class);
        new CustomEvent.Builder("event name").setEventValue(Double.NEGATIVE_INFINITY);
    }

    /**
     * Test setting event value to Double.NaN throws an exception.
     */
    @Test
    public void testSetEventValueDoubleNAN() {
        exception.expect(NumberFormatException.class);
        new CustomEvent.Builder("event name").setEventValue(Double.NaN);
    }

    /**
     * Test setting event value above the max allowed makes the event invalid.
     */
    @Test
    public void testEventValueDoubleAboveMax() {
        CustomEvent event = new CustomEvent.Builder("event name")
                .setEventValue(Integer.MAX_VALUE + .000001)
                .create();

        assertFalse(event.isValid());
    }

    /**
     * Test setting event value below the min allowed makes the event invalid
     */
    @Test
    public void testEventValueDoubleBelowMin() {
        CustomEvent event = new CustomEvent.Builder("event name")
                .setEventValue(Integer.MIN_VALUE - .000001)
                .create();

        assertFalse(event.isValid());
    }

    /**
     * Test setting event value to a string that is not a number throws an exception.
     */
    @Test
    public void testEventValueStringNAN() {
        exception.expect(NumberFormatException.class);
        new CustomEvent.Builder("event name").setEventValue("not a number!");
    }

    /**
     * Test setting event value above the max allowed makes the event invalid.
     */
    @Test
    public void testEventValueStringAboveMax() {
        CustomEvent event = new CustomEvent.Builder("event name")
                .setEventValue(String.valueOf(Integer.MAX_VALUE + 0.000001))
                .create();

        assertFalse(event.isValid());
    }

    /**
     * Test setting event value below the min allowed makes the event invalid
     */
    @Test
    public void testEventValueStringBelowMin() {
        CustomEvent event = new CustomEvent.Builder("event name")
                .setEventValue(String.valueOf(Integer.MIN_VALUE - 0.000001))
                .create();

        assertFalse(event.isValid());
    }

    /**
     * Test setting event value above the max allowed makes the event invalid.
     */
    @Test
    public void testEventValueBigDecimalAboveMax() {
        CustomEvent event = new CustomEvent.Builder("event name")
                .setEventValue(new BigDecimal(Integer.MAX_VALUE).add(BigDecimal.valueOf(0.000001)))
                .create();

        assertFalse(event.isValid());
    }

    /**
     * Test setting event value below the min allowed makes the event invalid.
     */
    @Test
    public void testEventValueBigDecimalBelowMin() {
        CustomEvent event = new CustomEvent.Builder("event name")
                .setEventValue(new BigDecimal(Integer.MIN_VALUE).subtract(BigDecimal.valueOf(0.000001)))
                .create();

        assertFalse(event.isValid());

    }

    /**
     * Test setting the attribution directly from a push message.
     */
    @Test
    public void testAttributionFromPushMessage() throws JSONException {
        Bundle pushBundle = new Bundle();
        pushBundle.putString(PushMessage.EXTRA_SEND_ID, "send id");
        PushMessage pushMessage = new PushMessage(pushBundle);

        CustomEvent event = new CustomEvent.Builder("event name")
                .setAttribution(pushMessage)
                .create();

        EventTestUtils.validateEventValue(event, "conversion_send_id", "send id");
    }

    /**
     * Test properties are all stringified except for arrays of Strings.
     */
    @Test
    public void testPropertiesValues() throws JSONException {
        CustomEvent event = new CustomEvent.Builder("event name")
                .addProperty("true_boolean", true)
                .addProperty("false_boolean", false)
                .addProperty("double", 1234567.498765)
                .addProperty("string", "some string value")
                .addProperty("int", Integer.MIN_VALUE)
                .addProperty("long", Long.MAX_VALUE)
                .addProperty("array", Arrays.asList("value", "another value"))
                .create();

        assertTrue(event.isValid());

        // Validate the custom properties
        EventTestUtils.validateNestedEventValue(event, "properties", "true_boolean", "true");
        EventTestUtils.validateNestedEventValue(event, "properties", "false_boolean", "false");
        EventTestUtils.validateNestedEventValue(event, "properties", "double", "1234567.498765");
        EventTestUtils.validateNestedEventValue(event, "properties", "string", "\"some string value\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "int", "-2147483648");
        EventTestUtils.validateNestedEventValue(event, "properties", "long", "9223372036854775807");

        // Validate the custom String[] property
        JsonList array = event.getEventData().get("properties").getMap().get("array").getList();
        assertEquals(2, array.size());
        assertEquals("value", array.get(0).getString());
        assertEquals("another value", array.get(1).getString());
    }

    /**
     * Test adding more than {@link CustomEvent#MAX_PROPERTIES} properties invalidates the event.
     */
    @Test
    public void testPropertiesExceedsMaxCount() throws JSONException {
        CustomEvent.Builder eventBuilder = new CustomEvent.Builder("event name");

        // Add the max number of events
        for (int i = 0; i < CustomEvent.MAX_PROPERTIES; i++) {
            eventBuilder.addProperty(UUID.randomUUID().toString(), "some value");
        }

        // Verify its still valid
        assertTrue(eventBuilder.create().isValid());

        // Add another event
        eventBuilder.addProperty(UUID.randomUUID().toString(), "some value");

        // Verify its now invalid
        assertFalse(eventBuilder.create().isValid());
    }

    /**
     * Test adding a property with a name that exceeds {@link CustomEvent#MAX_CHARACTER_LENGTH} invalidates
     * the event.
     */
    @Test
    public void testPropertyNameLengthExceedsMaxLength() throws JSONException {
        // Add a property name at max length
        CustomEvent.Builder eventBuilder = new CustomEvent.Builder("event name")
                .addProperty(createFixedSizeString('a', CustomEvent.MAX_CHARACTER_LENGTH), "value");

        // Make sure its valid
        assertTrue(eventBuilder.create().isValid());

        // Add a property name above max length
        eventBuilder.addProperty(createFixedSizeString('a', CustomEvent.MAX_CHARACTER_LENGTH + 1), "value");

        // Verify its now invalid
        assertFalse(eventBuilder.create().isValid());
    }

    /**
     * Test adding a property with a String value that exceeds {@link CustomEvent#MAX_CHARACTER_LENGTH} invalidates
     * the event.
     */
    @Test
    public void testPropertyStringValueExceedsMaxLength() throws JSONException {
        // Add a property value at max length
        CustomEvent.Builder eventBuilder = new CustomEvent.Builder("event name")
                .addProperty("at max", createFixedSizeString('a', CustomEvent.MAX_CHARACTER_LENGTH));

        // Make sure its valid
        assertTrue(eventBuilder.create().isValid());

        // Add a property name above max length
        eventBuilder.addProperty("exceeds max", createFixedSizeString('a', CustomEvent.MAX_CHARACTER_LENGTH + 1));

        // Verify its now invalid
        assertFalse(eventBuilder.create().isValid());
    }

    /**
     * Test adding a property with a String[] that is larger than {@link CustomEvent#MAX_PROPERTY_COLLECTION_SIZE}
     * invalidates the event.
     */
    @Test
    public void testPropertyStringArrayExceedsMaxCount() throws JSONException {
        // Add a property value at max length
        CustomEvent.Builder eventBuilder = new CustomEvent.Builder("event name")
                .addProperty("at max", Arrays.asList(new String[CustomEvent.MAX_PROPERTY_COLLECTION_SIZE]));

        // Make sure its valid
        assertTrue(eventBuilder.create().isValid());

        // Add a property name above max length
        eventBuilder.addProperty("exceeds max", Arrays.asList(new String[CustomEvent.MAX_PROPERTY_COLLECTION_SIZE + 1]));

        // Verify its now invalid
        assertFalse(eventBuilder.create().isValid());
    }

    /**
     * Test adding a property with a string array that contains a String value that
     * exceeds {@link CustomEvent#MAX_CHARACTER_LENGTH} invalidates the event.
     */
    @Test
    public void testPropertyStringArrayValueExceedsMaxLength() throws JSONException {
        // Add a property value at max length
        CustomEvent.Builder eventBuilder = new CustomEvent.Builder("event name")
                .addProperty("at max", Arrays.asList(createFixedSizeString('a', CustomEvent.MAX_CHARACTER_LENGTH)));

        // Make sure its valid
        assertTrue(eventBuilder.create().isValid());

        // Add a property name above max length
        eventBuilder.addProperty("exceeds max", Arrays.asList(createFixedSizeString('a', CustomEvent.MAX_CHARACTER_LENGTH + 1)));

        // Verify its now invalid
        assertFalse(eventBuilder.create().isValid());
    }

    /**
     * Helper method to create a fixed size string with a repeating character.
     *
     * @param repeat The character to repeat.
     * @param length Length of the String.
     * @return A fixed size string.
     */
    private String createFixedSizeString(char repeat, int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(repeat);
        }
        return builder.toString();
    }
}