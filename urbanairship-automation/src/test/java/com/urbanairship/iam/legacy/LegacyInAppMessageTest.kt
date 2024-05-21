package com.urbanairship.iam.legacy

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.iam.content.Banner
import com.urbanairship.json.jsonMapOf
import com.urbanairship.push.PushMessage
import com.urbanairship.util.DateUtils
import java.util.concurrent.TimeUnit
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class LegacyInAppMessageTest {

    @Test
    public fun testFromPush() {
        val payload = """
        {
           "actions":{
              "on_click":{
                 "onclick":"action"
              },
              "button_group":"ua_yes_no_background",
              "button_actions":{
                 "yes":{
                    "action_one":123
                 }
              }
           },
           "display":{
              "primary_color":"#ABCDEF",
              "secondary_color":"#FEDCBA",
              "duration":100,
              "alert":"test iam",
              "type":"banner",
              "position":"top"
           },
           "expiry":"2024-08-13T23:33:04",
           "message_type":"transactional",
           "campaigns":{
              "test-campaign":"json"
           },
           "extra":{
              "one":2
           }
        }
        """
        val pushMessage: PushMessage = mockk {
            every { sendId } returns "test-send-id"
            every { richPushMessageId } returns null
            every { getExtra(PushMessage.EXTRA_IN_APP_MESSAGE) } returns payload
        }

        val legacyInAppMessage = LegacyInAppMessage.fromPush(pushMessage)
        val expected = LegacyInAppMessage(
            id = "test-send-id",
            placement = Banner.Placement.TOP,
            alert = "test iam",
            displayDurationMs = TimeUnit.SECONDS.toMillis(100),
            expiryMs = DateUtils.parseIso8601("2024-08-13T23:33:04"),
            clickActionValues = jsonMapOf("onclick" to "action"),
            buttonGroupId = "ua_yes_no_background",
            buttonActionValues = mapOf("yes" to jsonMapOf("action_one" to 123)),
            primaryColor = Color.parseColor("#ABCDEF"),
            secondaryColor = Color.parseColor("#FEDCBA"),
            messageType = "transactional",
            campaigns = jsonMapOf("test-campaign" to "json").toJsonValue(),
            extras = jsonMapOf("one" to 2)
        )

        assertEquals(expected, legacyInAppMessage)
    }

    @Test
    public fun testFromPushMinPayload() {
        val payload = """
        {
           "display":{
              "type":"banner"
           }
        }
        """

        val pushMessage: PushMessage = mockk {
            every { sendId } returns "test-send-id"
            every { richPushMessageId } returns null
            every { getExtra(PushMessage.EXTRA_IN_APP_MESSAGE) } returns payload
        }

        val legacyInAppMessage = LegacyInAppMessage.fromPush(pushMessage)
        val expected = LegacyInAppMessage(
            id = "test-send-id",
            placement = Banner.Placement.TOP
        )

        assertEquals(expected, legacyInAppMessage)
    }

    @Test
    public fun testFromPushNoSendId() {
        val payload = """
        {
           "display":{
              "type":"banner"
           }
        }
        """

        val pushMessage: PushMessage = mockk {
            every { sendId } returns null
            every { richPushMessageId } returns null
            every { getExtra(PushMessage.EXTRA_IN_APP_MESSAGE) } returns payload
        }

        val legacyInAppMessage = LegacyInAppMessage.fromPush(pushMessage)

        assertNull(legacyInAppMessage)
    }

    @Test
    public fun testFromPushNoIam() {
        val pushMessage: PushMessage = mockk {
            every { sendId } returns "test-send-id"
            every { richPushMessageId } returns null
            every { getExtra(PushMessage.EXTRA_IN_APP_MESSAGE) } returns null
        }

        val legacyInAppMessage = LegacyInAppMessage.fromPush(pushMessage)

        assertNull(legacyInAppMessage)
    }

    @Test
    public fun testAppendMessageCenterAction() {
        val payload = """
        {
           "actions":{
              "on_click":{
                 "onclick":"action"
              }
           },
           "display":{
              "type":"banner"
           }
        }
        """

        val pushMessage: PushMessage = mockk {
            every { sendId } returns "test-send-id"
            every { richPushMessageId } returns "some-message-id"
            every { getExtra(PushMessage.EXTRA_IN_APP_MESSAGE) } returns payload
        }

        val legacyInAppMessage = LegacyInAppMessage.fromPush(pushMessage)
        val expected = LegacyInAppMessage(
            id = "test-send-id",
            placement = Banner.Placement.TOP,
            clickActionValues = jsonMapOf("onclick" to "action", "^mc" to "some-message-id")
        )

        assertEquals(expected, legacyInAppMessage)
    }

    @Test
    public fun testMessageCenterAction() {
        val payload = """
        {
           "display":{
              "type":"banner"
           }
        }
        """

        val pushMessage: PushMessage = mockk {
            every { sendId } returns "test-send-id"
            every { richPushMessageId } returns "some-message-id"
            every { getExtra(PushMessage.EXTRA_IN_APP_MESSAGE) } returns payload
        }

        val legacyInAppMessage = LegacyInAppMessage.fromPush(pushMessage)
        val expected = LegacyInAppMessage(
            id = "test-send-id",
            placement = Banner.Placement.TOP,
            clickActionValues = jsonMapOf("^mc" to "some-message-id")
        )

        assertEquals(expected, legacyInAppMessage)
    }
}
