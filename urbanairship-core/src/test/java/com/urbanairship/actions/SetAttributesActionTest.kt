package com.urbanairship.actions

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestApplication
import com.urbanairship.TestClock
import com.urbanairship.actions.ActionValue.Companion.wrap
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.channel.AttributeEditor
import com.urbanairship.channel.AttributeMutation
import com.urbanairship.contacts.Contact
import com.urbanairship.json.JsonValue
import java.util.Date
import io.mockk.every
import io.mockk.mockk
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class SetAttributesActionTest {

    private val action: SetAttributesAction = SetAttributesAction()
    private val channel: AirshipChannel = mockk()
    private val contact: Contact = mockk()
    private val clock = TestClock().apply { currentTimeMillis = 1000 }

    @Before
    public fun setup() {
        TestApplication.getApplication().setChannel(channel)
        TestApplication.getApplication().setContact(contact)
    }

    @Test
    public fun testAcceptsArguments() {
        // Channel attributes
        val channelSet = JSONObject()
        channelSet.put("channel_attribute_1", 1)
        channelSet.put("channel_attribute_2", "value2")

        val channelRemove = JSONArray(arrayOf("nothing", "not_important"))

        val channelObject = JSONObject()
        channelObject.put("set", channelSet)
        channelObject.put("remove", channelRemove)

        // Named User attributes
        val namedUserSet = JSONObject()
        namedUserSet.put("named_user_attribute_1", 4.99)
        namedUserSet.put("named_user_attribute_2", Date(clock.currentTimeMillis).time)

        val namedUserRemove = JSONArray(arrayOf("useless", "not_needed"))

        val namedUserObject = JSONObject()
        namedUserObject.put("set", namedUserSet)
        namedUserObject.put("remove", namedUserRemove)

        // Action Value
        val actionValue = JSONObject()
        actionValue.put("channel", channelObject)
        actionValue.put("named_user", namedUserObject)

        val args = ActionTestUtils.createArgs(Action.Situation.MANUAL_INVOCATION, wrap(actionValue))
        assertTrue(action.acceptsArguments(args))
    }

    @Test
    public fun testAcceptsArgumentsChannel() {
        // Channel attributes
        val channelSet = JSONObject()
        channelSet.put("channel_attribute_1", 1)
        channelSet.put("channel_attribute_2", "value2")

        val channelRemove = JSONArray(arrayOf("nothing", "not_important"))

        val channelObject = JSONObject()
        channelObject.put("set", channelSet)
        channelObject.put("remove", channelRemove)

        // Action Value
        val actionValue = JSONObject()
        actionValue.put("channel", channelObject)

        val args = ActionTestUtils.createArgs(Action.Situation.MANUAL_INVOCATION, wrap(actionValue))

        assertTrue(action.acceptsArguments(args))
    }

    @Test
    public fun testAcceptsArgumentsNamedUser() {
        // Named User attributes
        val namedUserSet = JSONObject()
        namedUserSet.put("named_user_attribute_1", 4.99)
        namedUserSet.put("named_user_attribute_2", Date(clock.currentTimeMillis).time)

        val namedUserRemove = JSONArray(arrayOf("useless", "not_needed"))

        val namedUserObject = JSONObject()
        namedUserObject.put("set", namedUserSet)
        namedUserObject.put("remove", namedUserRemove)

        // Action Value
        val actionValue = JSONObject()
        actionValue.put("named_user", namedUserObject)

        val args = ActionTestUtils.createArgs(Action.Situation.MANUAL_INVOCATION, wrap(actionValue))

        assertTrue(action.acceptsArguments(args))
    }

    @Test
    public fun testAcceptsArgumentsBothMixed() {
        // Channel attributes
        val channelSet = JSONObject()
        channelSet.put("channel_attribute_1", 1)

        val channelObject = JSONObject()
        channelObject.put("set", channelSet)

        val namedUserRemove = JSONArray(arrayOf("useless"))

        val namedUserObject = JSONObject()
        namedUserObject.put("remove", namedUserRemove)

        // Action Value
        val actionValue = JSONObject()
        actionValue.put("channel", channelObject)
        actionValue.put("named_user", namedUserObject)

        val args = ActionTestUtils.createArgs(Action.Situation.MANUAL_INVOCATION, wrap(actionValue))

        assertTrue(action.acceptsArguments(args))
    }

    @Test
    public fun testAcceptsArgumentsWrongKeyNames() {
        // Channel attributes
        val channelSet = JSONObject()
        channelSet.put("channel_attribute_1", 1)
        channelSet.put("channel_attribute_2", "value2")

        val channelRemove = JSONArray(arrayOf("nothing", "not_important"))

        val channelObject = JSONObject()
        channelObject.put("set", channelSet)
        channelObject.put("remove", channelRemove)

        // Named User attributes
        val namedUserSet = JSONObject()
        namedUserSet.put("named_user_attribute_1", 4.99)
        namedUserSet.put("named_user_attribute_2", Date(clock.currentTimeMillis).time)

        val namedUserRemove = JSONArray(arrayOf("useless", "not_needed"))

        val namedUserObject = JSONObject()
        namedUserObject.put("set", namedUserSet)
        namedUserObject.put("remove", namedUserRemove)

        // Action Value
        val actionValue = JSONObject()
        actionValue.put("chanel", channelObject)
        actionValue.put("nameduser", namedUserObject)

        val args = ActionTestUtils.createArgs(Action.Situation.MANUAL_INVOCATION, wrap(actionValue))

        assertFalse(action.acceptsArguments(args))
    }

    @Test
    public fun testAcceptsArgumentsWrongSetObjectType() {
        // Channel attributes
        val channelSet = JSONArray(arrayOf("channel_attribute_1", "channel_attribute_2"))

        val channelRemove = JSONArray(arrayOf("nothing", "not_important"))

        val channelObject = JSONObject()
        channelObject.put("set", channelSet)
        channelObject.put("remove", channelRemove)

        // Named User attributes
        val namedUserSet = JSONObject()
        namedUserSet.put("named_user_attribute_1", 4.99)
        namedUserSet.put("named_user_attribute_2", Date(clock.currentTimeMillis).time)

        val namedUserRemove = JSONArray(arrayOf("useless", "not_needed"))

        val namedUserObject = JSONObject()
        namedUserObject.put("set", namedUserSet)
        namedUserObject.put("remove", namedUserRemove)

        // Action Value
        val actionValue = JSONObject()
        actionValue.put("channel", channelObject)
        actionValue.put("named_user", namedUserObject)

        val args = ActionTestUtils.createArgs(Action.Situation.MANUAL_INVOCATION, wrap(actionValue))

        assertFalse(action.acceptsArguments(args))
    }

    @Test
    public fun testAcceptsArgumentsWrongRemoveObjectType() {
        // Channel attributes
        val channelSet = JSONObject()
        channelSet.put("channel_attribute_1", 1)
        channelSet.put("channel_attribute_2", "value2")

        val channelRemove = JSONObject()
        channelSet.put("nothing", "not_important")

        val channelObject = JSONObject()
        channelObject.put("set", channelSet)
        channelObject.put("remove", channelRemove)

        // Named User attributes
        val namedUserSet = JSONObject()
        namedUserSet.put("named_user_attribute_1", 4.99)
        namedUserSet.put("named_user_attribute_2", Date(clock.currentTimeMillis).time)

        val namedUserRemove = JSONArray(arrayOf("useless", "not_needed"))

        val namedUserObject = JSONObject()
        namedUserObject.put("set", namedUserSet)
        namedUserObject.put("remove", namedUserRemove)

        // Action Value
        val actionValue = JSONObject()
        actionValue.put("channel", channelObject)
        actionValue.put("named_user", namedUserObject)

        val args = ActionTestUtils.createArgs(Action.Situation.MANUAL_INVOCATION, wrap(actionValue))

        assertFalse(action.acceptsArguments(args))
    }

    @Test
    public fun testPerformWithChannelAttributes() {
        val attributeMutations = mutableSetOf<AttributeMutation>()
        val attributeEditor = object : AttributeEditor(clock) {
            override fun onApply(collapsedMutations: List<AttributeMutation>) {
                attributeMutations.addAll(collapsedMutations)
            }
        }

        every { channel.editAttributes() } returns attributeEditor

        // Channel attributes
        val channelSet = JSONObject()
        channelSet.put("channel_attribute_1", 1)
        channelSet.put("channel_attribute_2", "value2")

        val channelRemove = JSONArray(arrayOf("nothing", "not_important"))

        val channelObject = JSONObject()
        channelObject.put("set", channelSet)
        channelObject.put("remove", channelRemove)

        // Action Value
        val actionValue = JSONObject()
        actionValue.put("channel", channelObject)

        val args = ActionTestUtils.createArgs(Action.Situation.MANUAL_INVOCATION, wrap(actionValue))
        val result = action.perform(args)

        assertTrue(result.value.isNull)

        val expectedAttributeMutations = setOf(
            AttributeMutation.newSetAttributeMutation(
                "channel_attribute_1", JsonValue.wrap(1), 1000
            ),
            AttributeMutation.newSetAttributeMutation(
                "channel_attribute_2", JsonValue.wrap("value2"), 1000
            ),
            AttributeMutation.newRemoveAttributeMutation(
                "nothing", 1000
            ),
            AttributeMutation.newRemoveAttributeMutation(
                "not_important", 1000
            )
        )

        assertEquals(expectedAttributeMutations, attributeMutations)
    }

    @Test
    public fun testPerformWithNamedUserAttributes() {

        val attributeMutations = mutableSetOf<AttributeMutation>()
        val attributeEditor = object : AttributeEditor(clock!!) {
            override fun onApply(collapsedMutations: List<AttributeMutation>) {
                attributeMutations.addAll(collapsedMutations)
            }
        }

        every { contact.editAttributes() } returns attributeEditor

        // Named User attributes
        val namedUserSet = JSONObject()
        namedUserSet.put("named_user_attribute_1", 4.99)
        namedUserSet.put("named_user_attribute_2", Date(clock.currentTimeMillis).time)

        val namedUserRemove = JSONArray(arrayOf("useless", "not_needed"))

        val namedUserObject = JSONObject()
        namedUserObject.put("set", namedUserSet)
        namedUserObject.put("remove", namedUserRemove)

        // Action Value
        val actionValue = JSONObject()
        actionValue.put("named_user", namedUserObject)

        val args = ActionTestUtils.createArgs(Action.Situation.MANUAL_INVOCATION, wrap(actionValue))
        val result = action.perform(args)

        assertTrue(result.value.isNull)

        val expectedAttributeMutations = setOf(
            AttributeMutation.newSetAttributeMutation(
                "named_user_attribute_1", JsonValue.wrap(4.99), 1000
            ),
            AttributeMutation.newSetAttributeMutation(
                "named_user_attribute_2", JsonValue.wrap(1000), 1000
            ),
            AttributeMutation.newRemoveAttributeMutation(
                "useless", 1000
            ),
            AttributeMutation.newRemoveAttributeMutation(
                "not_needed", 1000
            )
        )

        assertEquals(expectedAttributeMutations, attributeMutations)
    }

    @Test
    public fun testPerform() {
        val attributeMutations = mutableSetOf<AttributeMutation>()
        val attributeEditor = object : AttributeEditor(clock) {
            override fun onApply(collapsedMutations: List<AttributeMutation>) {
                attributeMutations.addAll(collapsedMutations)
            }
        }

        every { channel.editAttributes() } returns attributeEditor
        every { contact.editAttributes() } returns attributeEditor

        // Channel attributes
        val channelSet = JSONObject()
        channelSet.put("channel_attribute_1", 1)
        channelSet.put("channel_attribute_2", "value2")

        val channelRemove = JSONArray(arrayOf("nothing", "not_important"))

        val channelObject = JSONObject()
        channelObject.put("set", channelSet)
        channelObject.put("remove", channelRemove)

        // Named User attributes
        val namedUserSet = JSONObject()
        namedUserSet.put("named_user_attribute_1", 4.99)
        namedUserSet.put("named_user_attribute_2", Date(clock.currentTimeMillis).time)

        val namedUserRemove = JSONArray(arrayOf("useless", "not_needed"))

        val namedUserObject = JSONObject()
        namedUserObject.put("set", namedUserSet)
        namedUserObject.put("remove", namedUserRemove)

        // Action Value
        val actionValue = JSONObject()
        actionValue.put("channel", channelObject)
        actionValue.put("named_user", namedUserObject)

        val args = ActionTestUtils.createArgs(Action.Situation.MANUAL_INVOCATION, wrap(actionValue))
        val result = action.perform(args)

        assertTrue(result.value.isNull)

        val expectedAttributeMutations = setOf(
            AttributeMutation.newSetAttributeMutation(
                "channel_attribute_1", JsonValue.wrap(1), 1000
            ),
            AttributeMutation.newSetAttributeMutation(
                "channel_attribute_2", JsonValue.wrap("value2"), 1000
            ),
            AttributeMutation.newRemoveAttributeMutation(
                "nothing", 1000
            ),
            AttributeMutation.newRemoveAttributeMutation(
                "not_important", 1000
            ),
            AttributeMutation.newSetAttributeMutation(
                "named_user_attribute_1", JsonValue.wrap(4.99), 1000
            ),
            AttributeMutation.newSetAttributeMutation(
                "named_user_attribute_2", JsonValue.wrap(1000), 1000
            ),
            AttributeMutation.newRemoveAttributeMutation(
                "useless", 1000
            ),
            AttributeMutation.newRemoveAttributeMutation(
                "not_needed", 1000
            )
        )

        assertEquals(expectedAttributeMutations, attributeMutations)
    }
}
