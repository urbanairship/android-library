/* Copyright Airship and Contributors */
package com.urbanairship.actions

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestApplication
import com.urbanairship.actions.Action.Situation
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.contacts.Contact
import com.urbanairship.json.JsonValue
import com.urbanairship.push.PushManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class FetchDeviceInfoActionTest {

    private var airshipChannel: AirshipChannel = mockk()
    private var pushManager: PushManager = mockk()
    private var contact: Contact = mockk()
    private var action = FetchDeviceInfoAction()

    private val acceptedSituations = arrayOf(
        Situation.PUSH_OPENED,
        Situation.MANUAL_INVOCATION,
        Situation.WEB_VIEW_INVOCATION,
        Situation.PUSH_RECEIVED,
        Situation.BACKGROUND_NOTIFICATION_ACTION_BUTTON,
        Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON,
        Situation.AUTOMATION
    )

    @Before
    public fun setUp() {
        TestApplication.getApplication().apply {
            setChannel(airshipChannel)
            setPushManager(pushManager)
            setContact(contact)
        }
    }

    /**
     * Test accepts arguments.
     */
    @Test
    public fun testAcceptsArguments() {
        acceptedSituations.forEach { situation ->
            val args = ActionTestUtils.createArgs(situation, ActionValue())
            assertTrue(
                "Should accept arguments in situation $situation",
                action.acceptsArguments(args)
            )
        }
    }

    /**
     * Test perform with valid JSON.
     */
    @Test
    public fun testPerform() {
        val tags = listOf("tag1", "tag2")
        val channelId = "channel_id"
        val namedUserId = "named_user"

        every { airshipChannel.id } returns channelId
        every { pushManager.isOptIn } returns true
        every { airshipChannel.tags } returns tags.toSet()
        every { contact.namedUserId } returns namedUserId

        val result = action.perform(ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, ActionValue())).value.map

        assertEquals(channelId, result?.opt(FetchDeviceInfoAction.CHANNEL_ID_KEY)?.string)
        assertEquals(true, result?.get(FetchDeviceInfoAction.PUSH_OPT_IN_KEY)?.getBoolean(false))
        assertEquals(JsonValue.wrap(tags), result?.get(FetchDeviceInfoAction.TAGS_KEY))
        assertEquals(namedUserId, result?.get(FetchDeviceInfoAction.NAMED_USER_ID_KEY)?.string)
    }

    /**
     * Test perform with valid JSON.
     */
    @Test
    public fun testPerformWithoutTags() {
        val channelId = "channel_id"
        val namedUserId = "named_user"

        every { airshipChannel.id } returns channelId
        every { pushManager.isOptIn } returns true
        every { airshipChannel.tags } returns emptySet()
        every { contact.namedUserId } returns namedUserId

        val result = action.perform(
                ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, ActionValue())
            ).value.map

        assertEquals(channelId, result?.get(FetchDeviceInfoAction.CHANNEL_ID_KEY)?.string)
        assertEquals(true, result?.get(FetchDeviceInfoAction.PUSH_OPT_IN_KEY)?.getBoolean(false))
        assertNull(result?.get(FetchDeviceInfoAction.TAGS_KEY))
        assertEquals(namedUserId, result?.get(FetchDeviceInfoAction.NAMED_USER_ID_KEY)?.string)
    }
}
