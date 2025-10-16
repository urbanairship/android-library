/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import android.content.Context
import androidx.core.os.bundleOf
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.BaseTestCase
import com.urbanairship.analytics.CustomEvent.Companion.newBuilder
import com.urbanairship.json.JsonValue
import com.urbanairship.json.requireField
import com.urbanairship.push.PushMessage
import java.math.BigDecimal
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class CustomEventTest public constructor() : BaseTestCase() {

    private val context: Context = ApplicationProvider.getApplicationContext()


    /**
     * Test creating a custom event.
     */
    @Test
    public fun testCustomEvent() {
        val eventName = createFixedSizeString('a', 255)
        val interactionId = createFixedSizeString('b', 255)
        val interactionType = createFixedSizeString('c', 255)
        val transactionId = createFixedSizeString('d', 255)
        val templateType = createFixedSizeString('e', 255)

        val event = newBuilder(eventName)
            .setTransactionId(transactionId)
            .setInteraction(interactionType, interactionId)
            .setEventValue(100.123456)
            .setTemplateType(templateType)
            .setInAppContext(JsonValue.wrap("some in-app context"))
            .build()

        EventTestUtils.validateEventValue(event, "event_name", eventName)
        EventTestUtils.validateEventValue(event, "event_value", 100123456L)
        EventTestUtils.validateEventValue(event, "transaction_id", transactionId)
        EventTestUtils.validateEventValue(event, "interaction_id", interactionId)
        EventTestUtils.validateEventValue(event, "interaction_type", interactionType)
        EventTestUtils.validateEventValue(event, "template_type", templateType)
        EventTestUtils.validateEventValue(event, "in_app", "some in-app context")
    }

    /**
     * Test creating a custom event with an empty event name makes it invalid.
     */
    @Test
    public fun testEmptyEventName() {
        val event = newBuilder("").build()
        assertFalse(event.isValid())
    }

    /**
     * Test creating a custom event with a name longer than 255 characters makes it invalid.
     */
    @Test
    public fun testEventNameExceedsMaxLength() {
        val event = newBuilder(createFixedSizeString('a', 256)).build()
        assertFalse(event.isValid())
    }

    /**
     * Test setting a interaction ID that is longer than 255 characters makes it invalid
     */
    @Test
    public fun testInteractionIDExceedsMaxLength() {
        val event = newBuilder("event name")
            .setInteraction("interaction type", createFixedSizeString('a', 256))
            .build()

        assertFalse(event.isValid())
    }

    /**
     * Test setting a attribution type that is longer than 255 characters makes it invalid.
     */
    @Test
    public fun testInteractionTypeExceedsMaxLength() {
        val event = newBuilder("event name")
            .setInteraction(createFixedSizeString('a', 256), "interaction id")
            .build()

        assertFalse(event.isValid())
    }

    /**
     * Test setting a transaction ID that is longer than 255 characters makes it invalid.
     */
    @Test
    public fun testTransactionIDExceedsMaxLength() {
        val event = newBuilder("event name")
            .setTransactionId(createFixedSizeString('a', 256))
            .build()

        assertFalse(event.isValid())
    }

    /**
     * Test creating a custom event includes the hard conversion send id if set.
     */
    @Test
    public fun testHardConversionId() {
        val event = newBuilder("event name").build()
        val conversionData = ConversionData("send id", "send metadata", "last metadata")

        val eventData = event.getEventData(context, conversionData)
        assertEquals("send id", eventData.requireField<String>("conversion_send_id"))
    }

    /**
     * Test creating a custom event includes the hard conversion metadata if set.
     */
    @Test
    public fun testHardConversionMetadata() {
        val event = newBuilder("event name").build()
        val conversionData = ConversionData("send id", "send metadata", "last metadata")

        val eventData = event.getEventData(context, conversionData)
        assertEquals(
            eventData.requireField<String>("conversion_metadata"),
            "send metadata"
        )
    }

    /**
     * Test creating a custom event includes the last received metadata.
     */
    @Test
    public fun testLastMetadata() {
        val event = newBuilder("event name").build()
        val conversionData = ConversionData("send id", null, "last metadata")

        val eventData = event.getEventData(context, conversionData)
        assertEquals(
            eventData.requireField<String>("last_received_metadata"),
            "last metadata"
        )
    }

    /**
     * Test creating a custom event includes only the hard metadata if set and not the last send.
     */
    @Test
    public fun testHardConversionMetadataAndLastMetadata() {
        val event = newBuilder("event name").build()
        val conversionData = ConversionData("send id", "send metadata", "last metadata")

        val eventData = event.getEventData(context, conversionData)
        assertNull(eventData["last_received_metadata"])
        assertEquals(
            eventData.requireField<String>("conversion_metadata"),
            "send metadata"
        )
    }

    /**
     * Test adding a custom event with interaction from a message.
     */
    @Test
    public fun testInteractionFromMessage() {
        val event = newBuilder("event name")
            .setMessageCenterInteraction("message id")
            .build()

        EventTestUtils.validateEventValue(event, "interaction_id", "message id")
        EventTestUtils.validateEventValue(event, "interaction_type", "ua_mcrap")
    }

    /**
     * Test adding a custom event with a custom interaction.
     */
    @Test
    public fun testCustomInteraction() {
        val event = newBuilder("event name")
            .setInteraction("interaction type", "interaction id")
            .build()

        EventTestUtils.validateEventValue(event, "interaction_id", "interaction id")
        EventTestUtils.validateEventValue(event, "interaction_type", "interaction type")
    }

    /**
     * Test adding an interaction with a null id is allowed.
     */
    @Test
    public fun testCustomInteractionNullID() {
        val event = newBuilder("event name")
            .setInteraction("interaction type", null)
            .build()

        EventTestUtils.validateEventValue(event, "interaction_id", null)
        EventTestUtils.validateEventValue(event, "interaction_type", "interaction type")
    }

    /**
     * Test adding an interaction with a null type is allowed.
     */
    @Test
    public fun testCustomInteractionNullType() {
        val event = newBuilder("event name")
            .setInteraction(null, "interaction id")
            .build()

        EventTestUtils.validateEventValue(event, "interaction_type", null)
        EventTestUtils.validateEventValue(event, "interaction_id", "interaction id")
    }

    /**
     * Test creating a custom event without an interaction, last send id, or
     * conversion push id will be empty.
     */
    @Test
    public fun testCustomInteractionEmpty() {
        val event = newBuilder("event name").build()

        EventTestUtils.validateEventValue(event, "interaction_type", null)
        EventTestUtils.validateEventValue(event, "interaction_id", null)
        EventTestUtils.validateEventValue(event, "template_type", null)
    }

    /**
     * Test setting the event value to various valid values.
     */
    @Test
    public fun testSetEventValue() {
        // Max integer
        var event = newBuilder("event name").setEventValue(Int.MAX_VALUE).build()
        EventTestUtils.validateEventValue(event, "event_value", 2147483647000000L)

        // Min integer
        event = newBuilder("event name").setEventValue(Int.MIN_VALUE).build()
        EventTestUtils.validateEventValue(event, "event_value", -2147483648000000L)

        // 0
        event = newBuilder("event name").setEventValue(0).build()
        EventTestUtils.validateEventValue(event, "event_value", 0)

        // Min double (very small number) - should be 0.
        event = newBuilder("event name").setEventValue(Double.MIN_VALUE).build()
        EventTestUtils.validateEventValue(event, "event_value", 0)

        // Max supported double
        event = newBuilder("event name").setEventValue(Int.MAX_VALUE.toDouble()).build()
        EventTestUtils.validateEventValue(event, "event_value", 2147483647000000L)

        // Min supported double
        event = newBuilder("event name").setEventValue(Int.MIN_VALUE.toDouble()).build()
        EventTestUtils.validateEventValue(event, "event_value", -2147483648000000L)

        // Max supported String
        event = newBuilder("event name").setEventValue(Int.MAX_VALUE.toString()).build()
        EventTestUtils.validateEventValue(event, "event_value", 2147483647000000L)

        // Min supported String
        event = newBuilder("event name").setEventValue(Int.MIN_VALUE.toString()).build()
        EventTestUtils.validateEventValue(event, "event_value", -2147483648000000L)

        // "0"
        event = newBuilder("event name").setEventValue("0").build()
        EventTestUtils.validateEventValue(event, "event_value", 0)

        // null String
        event = newBuilder("event name").setEventValue(null as String?).build()
        EventTestUtils.validateEventValue(event, "event_value", null)

        // Some Big Decimal
        event = newBuilder("event name").setEventValue(BigDecimal(123)).build()
        EventTestUtils.validateEventValue(event, "event_value", 123000000L)

        // Max supported Big Decimal
        val maxDecimal = BigDecimal(Int.MAX_VALUE)
        event = newBuilder("event name").setEventValue(maxDecimal).build()
        EventTestUtils.validateEventValue(event, "event_value", 2147483647000000L)

        // Min supported Big Decimal
        val minDecimal = BigDecimal(Int.MIN_VALUE)
        event = newBuilder("event name").setEventValue(minDecimal).build()
        EventTestUtils.validateEventValue(event, "event_value", -2147483648000000L)

        // null Big Decimal
        event = newBuilder("event name").setEventValue(null as BigDecimal?).build()
        EventTestUtils.validateEventValue(event, "event_value", null)
    }

    /**
     * Test setting event value to positive infinity throws an exception.
     */
    @Test(expected = NumberFormatException::class)
    public fun testSetEventValuePositiveInfinity() {
        newBuilder("event name").setEventValue(Double.POSITIVE_INFINITY)
    }

    /**
     * Test setting event value to negative infinity throws an exception.
     */
    @Test(expected = NumberFormatException::class)
    public fun testSetEventValueNegativeInfinity() {
        newBuilder("event name").setEventValue(Double.NEGATIVE_INFINITY)
    }

    /**
     * Test setting event value to Double.NaN throws an exception.
     */
    @Test(expected = NumberFormatException::class)
    public fun testSetEventValueDoubleNAN() {
        newBuilder("event name").setEventValue(Double.NaN)
    }

    /**
     * Test setting event value above the max allowed makes the event invalid.
     */
    @Test
    public fun testEventValueDoubleAboveMax() {
        val event = newBuilder("event name")
            .setEventValue(Int.MAX_VALUE + .000001)
            .build()

        assertFalse(event.isValid())
    }

    /**
     * Test setting event value below the min allowed makes the event invalid
     */
    @Test
    public fun testEventValueDoubleBelowMin() {
        val event = newBuilder("event name")
            .setEventValue(Int.MIN_VALUE - .000001)
            .build()

        assertFalse(event.isValid())
    }

    /**
     * Test setting event value to a string that is not a number throws an exception.
     */
    @Test(expected = NumberFormatException::class)
    public fun testEventValueStringNAN() {
        newBuilder("event name").setEventValue("not a number!")
    }

    /**
     * Test setting event value above the max allowed makes the event invalid.
     */
    @Test
    public fun testEventValueStringAboveMax() {
        val event = newBuilder("event name")
            .setEventValue((Int.MAX_VALUE + 0.000001).toString())
            .build()

        assertFalse(event.isValid())
    }

    /**
     * Test setting event value below the min allowed makes the event invalid
     */
    @Test
    public fun testEventValueStringBelowMin() {
        val event = newBuilder("event name")
            .setEventValue((Int.MIN_VALUE - 0.000001).toString())
            .build()

        assertFalse(event.isValid())
    }

    /**
     * Test setting event value above the max allowed makes the event invalid.
     */
    @Test
    public fun testEventValueBigDecimalAboveMax() {
        val event = newBuilder("event name")
            .setEventValue(
                value = BigDecimal(Int.MAX_VALUE)
                    .add(BigDecimal.valueOf(0.000001))
        ).build()

        assertFalse(event.isValid())
    }

    /**
     * Test setting event value below the min allowed makes the event invalid.
     */
    @Test
    public fun testEventValueBigDecimalBelowMin() {
        val event = newBuilder("event name")
            .setEventValue(
                value = BigDecimal(Int.MIN_VALUE)
                    .subtract(BigDecimal.valueOf(0.000001))
            )
            .build()

        assertFalse(event.isValid())
    }

    /**
     * Test setting the attribution directly from a push message.
     */
    @Test
    public fun testAttributionFromPushMessage() {
        val pushMessage = PushMessage(bundleOf(PushMessage.EXTRA_SEND_ID to "send id"))

        val event = newBuilder("event name")
            .setAttribution(pushMessage)
            .build()

        EventTestUtils.validateEventValue(event, "conversion_send_id", "send id")
    }

    /**
     * Test properties are all stringified except for arrays of Strings.
     */
    @Test
    public fun testPropertiesValues() {
        val event = newBuilder("event name")
            .addProperty("true_boolean", true)
            .addProperty("false_boolean", false)
            .addProperty("double", 1234567.498765)
            .addProperty("string", "some string value")
            .addProperty("int", Int.MIN_VALUE)
            .addProperty("long", Long.MAX_VALUE)
            .addProperty("array", JsonValue.wrap(mutableListOf("value", "another value")))
            .build()

        assertTrue(event.isValid())

        // Validate the custom properties
        EventTestUtils.validateNestedEventValue(event, "properties", "true_boolean", "true")
        EventTestUtils.validateNestedEventValue(event, "properties", "false_boolean", "false")
        EventTestUtils.validateNestedEventValue(event, "properties", "double", "1234567.498765")
        EventTestUtils.validateNestedEventValue(event, "properties", "string", "some string value")
        EventTestUtils.validateNestedEventValue(event, "properties", "int", "-2147483648")
        EventTestUtils.validateNestedEventValue(event, "properties", "long", "9223372036854775807")

        // Validate the custom String[] property
        val array = event
            .getEventData(context, ConversionData(null, null, null))
            .get("properties")
            ?.map
            ?.get("array")
            ?.list

        assertEquals(2, array?.size())
        assertEquals("value", array?.get(0)?.string)
        assertEquals("another value", array?.get(1)?.string)
    }

    /**
     * Test adding a property that causes total payload to exceed [CustomEvent.MAX_TOTAL_PROPERTIES_SIZE]
     * invalidates the event.
     */
    @Test
    public fun testTotalPropertiesExceedsMaxSize() {
        // Add a property name resulting in acceptable total properties size
        val eventBuilder = newBuilder("event name")
            .addProperty("whatever", "value")

        // Make sure its valid
        assertTrue(eventBuilder.build().isValid())

        // Generate a string greater than {@link CustomEvent#MAX_TOTAL_PROPERTIES_SIZE} in bytes
        val tooBig = createFixedSizeString('a', CustomEvent.MAX_TOTAL_PROPERTIES_SIZE + 1)

        // Add property which increases total size past total size limit
        eventBuilder.addProperty(tooBig, "value")

        // Verify test string is over size limit in bytes
        assertTrue(tooBig.toByteArray().size >= CustomEvent.MAX_TOTAL_PROPERTIES_SIZE)

        // Verify its now invalid
        assertFalse(eventBuilder.build().isValid())
    }

    /**
     * Helper method to create a fixed size string with a repeating character.
     *
     * @param repeat The character to repeat.
     * @param length Length of the String.
     * @return A fixed size string.
     */
    private fun createFixedSizeString(repeat: Char, length: Int): String {
        val builder = StringBuilder(length)
        for (i in 0..<length) {
            builder.append(repeat)
        }

        return builder.toString()
    }
}
