/* Copyright Airship and Contributors */
package com.urbanairship.actions

import androidx.core.os.bundleOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.BaseTestCase
import com.urbanairship.actions.Action.Situation
import com.urbanairship.analytics.Analytics
import com.urbanairship.analytics.CustomEvent
import com.urbanairship.analytics.EventTestUtils
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonValue
import com.urbanairship.push.PushMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.json.JSONException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor

@RunWith(AndroidJUnit4::class)
public class AddCustomEventActionTest {

    private val events = mutableListOf<CustomEvent>()
    private val action = AddCustomEventAction {
        events.add(it)
    }

    private val acceptedSituations = arrayOf(
        Situation.PUSH_OPENED,
        Situation.MANUAL_INVOCATION,
        Situation.WEB_VIEW_INVOCATION,
        Situation.PUSH_RECEIVED,
        Situation.BACKGROUND_NOTIFICATION_ACTION_BUTTON,
        Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON,
        Situation.AUTOMATION
    )

    /**
     * Test custom event action accepts all the situations.
     */
    @Test
    public fun testAcceptsArgumentsAllSituations() {
        val map = mapOf("event_name" to "event name")

        // Check every accepted situation
        for (situation in acceptedSituations) {
            val args: ActionArguments = ActionTestUtils.createArgs(situation, map)
            assertTrue(
                "Should accept arguments in situation $situation", action.acceptsArguments(args)
            )
        }
    }

    @Test
    public fun testAcceptsArgumentsNewNameAllSituations() {
        val map = mapOf("name" to "new event name")

        // Check every accepted situation
        for (situation in acceptedSituations) {
            val args: ActionArguments = ActionTestUtils.createArgs(situation, map)
            assertTrue(
                "Should accept arguments in situation $situation", action.acceptsArguments(args)
            )
        }
    }

    /**
     * Test that it rejects empty argument values.
     */
    @Test
    public fun testAcceptsArgumentsEmptyArgs() {
        val args = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, ActionValue())
        assertFalse("Should reject empty args", action!!.acceptsArguments(args))
    }

    /**
     * Test that it rejects an empty map.
     */
    @Test
    public fun testAcceptsArgumentsEmptyMap() {
        val args =
            ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, HashMap<Any?, Any?>())
        assertFalse("Should reject empty map", action.acceptsArguments(args))
    }

    /**
     * Test that it rejects non-map values.
     */
    @Test
    public fun testAcceptsArgumentsInvalidValue() {
        val args = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, "not valid")
        assertFalse(
            "Should reject non-map action argument values", action.acceptsArguments(args)
        )
    }

    /**
     * Test performing the action actually creates and adds the event.
     */
    @Test
    @Throws(JSONException::class)
    public fun testPerform() {
        val map = mapOf(
            CustomEvent.TRANSACTION_ID to "transaction id",
            CustomEvent.EVENT_VALUE to 123.45,
            CustomEvent.INTERACTION_TYPE to "interaction type",
            CustomEvent.INTERACTION_ID to "interaction id",
            CustomEvent.EVENT_NAME to "event name"
        )

        val args = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, map)

        val result = action.perform(args)
        assertEquals("Action should've completed", ActionResult.Status.COMPLETED, result.status)

        assertEquals(1, events.size)
        val event: CustomEvent = events.first()
        EventTestUtils.validateEventValue(event, CustomEvent.TRANSACTION_ID, "transaction id")
        EventTestUtils.validateEventValue(event, CustomEvent.EVENT_VALUE, 123450000L)
        EventTestUtils.validateEventValue(event, CustomEvent.INTERACTION_TYPE, "interaction type")
        EventTestUtils.validateEventValue(event, CustomEvent.INTERACTION_ID, "interaction id")
        EventTestUtils.validateEventValue(event, CustomEvent.EVENT_NAME, "event name")
    }

    @Test
    @Throws(JSONException::class)
    public fun testPerformPreferNewNames() {
        val map = mapOf(
            CustomEvent.TRANSACTION_ID to "transaction id",
            CustomEvent.EVENT_VALUE to 123.45,
            CustomEvent.INTERACTION_TYPE to "interaction type",
            CustomEvent.INTERACTION_ID to "interaction id",
            CustomEvent.EVENT_NAME to "event name",
            AddCustomEventAction.KEY_NAME to "new event name",
            AddCustomEventAction.KEY_VALUE to 321.21
        )

        val args = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, map)
        action.perform(args)

        assertEquals(1, events.size)
        val event: CustomEvent = events.first()

        EventTestUtils.validateEventValue(event, CustomEvent.TRANSACTION_ID, "transaction id")
        EventTestUtils.validateEventValue(event, CustomEvent.EVENT_VALUE, 321210000L)
        EventTestUtils.validateEventValue(event, CustomEvent.INTERACTION_TYPE, "interaction type")
        EventTestUtils.validateEventValue(event, CustomEvent.INTERACTION_ID, "interaction id")
        EventTestUtils.validateEventValue(event, CustomEvent.EVENT_NAME, "new event name")
    }

    @Test
    public fun testPerformInAppContext() {
        val map = mapOf(
            CustomEvent.TRANSACTION_ID to "transaction id",
            CustomEvent.EVENT_VALUE to 123.45,
            CustomEvent.INTERACTION_TYPE to "interaction type",
            CustomEvent.INTERACTION_ID to "interaction id",
            CustomEvent.EVENT_NAME to "event name"
        )

        val metadata = bundleOf(
            AddCustomEventAction.IN_APP_CONTEXT_METADATA_KEY to JsonValue.wrap("some-context").toString()
        )

        val args = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, map, metadata)
        val result = action.perform(args)
        assertEquals("Action should've completed", ActionResult.Status.COMPLETED, result.status)

        assertEquals(1, events.size)
        val event: CustomEvent = events.first()

        EventTestUtils.validateEventValue(event, CustomEvent.TRANSACTION_ID, "transaction id")
        EventTestUtils.validateEventValue(event, CustomEvent.EVENT_VALUE, 123450000L)
        EventTestUtils.validateEventValue(event, CustomEvent.INTERACTION_TYPE, "interaction type")
        EventTestUtils.validateEventValue(event, CustomEvent.INTERACTION_ID, "interaction id")
        EventTestUtils.validateEventValue(event, CustomEvent.EVENT_NAME, "event name")
        EventTestUtils.validateEventValue(event, "in_app", "some-context")
    }

    /**
     * Test performing in a MCRAP will auto fill the interaction.
     */
    @Test
    @Throws(JSONException::class)
    public fun testPerformMCRAP() {
        val map = mapOf(
            CustomEvent.EVENT_NAME to "event name"
        )

        val metadata = bundleOf(
            ActionArguments.RICH_PUSH_ID_METADATA to "message id"
        )

        val args = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, map, metadata)
        val result = action.perform(args)
        assertEquals("Action should've completed", ActionResult.Status.COMPLETED, result.status)

        assertEquals(1, events.size)
        val event: CustomEvent = events.first()

        EventTestUtils.validateEventValue(event, CustomEvent.EVENT_NAME, "event name")
        EventTestUtils.validateEventValue(event, CustomEvent.INTERACTION_TYPE, "ua_mcrap")
        EventTestUtils.validateEventValue(event, CustomEvent.INTERACTION_ID, "message id")
    }

    /**
     * Test performing in a MCRAP will not fill the interaction if its set.
     */
    @Test
    @Throws(JSONException::class)
    public fun testPerformMCRAPInteractionSet() {
        val map = mapOf(
            CustomEvent.EVENT_NAME to "event name",
            CustomEvent.INTERACTION_TYPE to "interaction type"
        )

        val metadata = bundleOf(
            ActionArguments.RICH_PUSH_ID_METADATA to "message id"
        )

        val args = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, map, metadata)
        val result = action.perform(args)
        assertEquals("Action should've completed", ActionResult.Status.COMPLETED, result.status)

        assertEquals(1, events.size)
        val event: CustomEvent = events.first()

        EventTestUtils.validateEventValue(
            event,
            CustomEvent.INTERACTION_TYPE,
            "interaction type"
        )
        EventTestUtils.validateEventValue(event, CustomEvent.INTERACTION_ID, null)
    }

    /**
     * Test invalid event values does not throw an exception, instead has an execution error.
     */
    @Test
    public fun testRunInvalidCustomEventValue() {
        val map = mapOf(
            CustomEvent.EVENT_NAME to "event name",
            CustomEvent.EVENT_VALUE to Double.MAX_VALUE
        )

        val args = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, map)

        // Should fail to create the event and result in error
        val result = action.run(args)
        assertEquals("Action should've fail", ActionResult.Status.EXECUTION_ERROR, result.status)
    }

    /**
     * Test performing with the PushMessage metadata creates an event with the send id from the message.
     */
    @Test
    @Throws(JSONException::class)
    public fun testPerformPushMessageMetadata() {
        val map = mapOf(
            CustomEvent.TRANSACTION_ID to "transaction id",
            CustomEvent.EVENT_VALUE to 123.45,
            CustomEvent.INTERACTION_TYPE to "interaction type",
            CustomEvent.INTERACTION_ID to "interaction id",
            CustomEvent.EVENT_NAME to "event name"
        )

        val pushBundle = bundleOf(PushMessage.EXTRA_SEND_ID to "send id")

        val metadata = bundleOf(ActionArguments.PUSH_MESSAGE_METADATA to PushMessage(pushBundle))

        val args = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, map, metadata)

        val result = action.perform(args)
        assertEquals("Action should've completed", ActionResult.Status.COMPLETED, result.status)

        assertEquals(1, events.size)
        val event: CustomEvent = events.first()

        EventTestUtils.validateEventValue(event, CustomEvent.TRANSACTION_ID, "transaction id")
        EventTestUtils.validateEventValue(event, CustomEvent.EVENT_VALUE, 123450000L)
        EventTestUtils.validateEventValue(event, CustomEvent.INTERACTION_TYPE, "interaction type")
        EventTestUtils.validateEventValue(event, CustomEvent.INTERACTION_ID, "interaction id")
        EventTestUtils.validateEventValue(event, CustomEvent.EVENT_NAME, "event name")
        EventTestUtils.validateEventValue(event, CustomEvent.CONVERSION_SEND_ID, "send id")
    }

    /**
     * Test performing with the PushMessage metadata creates an event with the send id from the message.
     */
    @Test
    @Throws(JSONException::class)
    public fun testPerformWithProperties() {
        val map = mapOf(
            CustomEvent.TRANSACTION_ID to "transaction id",
            CustomEvent.EVENT_VALUE to 123.45,
            CustomEvent.INTERACTION_TYPE to "interaction type",
            CustomEvent.INTERACTION_ID to "interaction id",
            CustomEvent.EVENT_NAME to "event name",
            CustomEvent.PROPERTIES to mapOf(
                "boolean" to true,
                "double" to 124.49,
                "string" to "some string value",
                "int" to Int.MIN_VALUE,
                "long" to Long.MAX_VALUE,
                "array" to listOf("string value", true, 124)
            )
        )

        val args = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, map)

        val result = action.perform(args)
        assertEquals("Action should've completed", ActionResult.Status.COMPLETED, result.status)

        assertEquals(1, events.size)
        val event: CustomEvent = events.first()

        EventTestUtils.validateEventValue(event, CustomEvent.TRANSACTION_ID, "transaction id")
        EventTestUtils.validateEventValue(event, CustomEvent.EVENT_VALUE, 123450000L)
        EventTestUtils.validateEventValue(event, CustomEvent.INTERACTION_TYPE, "interaction type")
        EventTestUtils.validateEventValue(event, CustomEvent.INTERACTION_ID, "interaction id")
        EventTestUtils.validateEventValue(event, CustomEvent.EVENT_NAME, "event name")

        EventTestUtils.validateNestedEventValue(event, "properties", "boolean", "true")
        EventTestUtils.validateNestedEventValue(event, "properties", "double", "124.49")
        EventTestUtils.validateNestedEventValue(event, "properties", "string", "some string value")
        EventTestUtils.validateNestedEventValue(event, "properties", "int", "-2147483648")
        EventTestUtils.validateNestedEventValue(event, "properties", "long", "9223372036854775807")

        // Validate the custom JsonList property
        val array = EventTestUtils.getEventData(event)["properties"]?.map?.get("array")?.list ?: JsonList.EMPTY_LIST

        assertEquals(3, array.size())
        assertEquals("string value", array[0].string)
        assertTrue(array[1].getBoolean(false))
        assertEquals(124.0, array[2].getDouble(0.0))
    }
}
