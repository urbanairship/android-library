/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import androidx.core.os.bundleOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.analytics.EventTestUtils.validateEventValue
import com.urbanairship.analytics.EventTestUtils.validateNestedEventValue
import com.urbanairship.push.NotificationActionButtonInfo
import com.urbanairship.push.NotificationInfo
import com.urbanairship.push.PushMessage
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InteractiveNotificationEventTest {

    private var mockPushMessage: PushMessage = mockk {
        every { sendId } returns "send id"
        every { interactiveNotificationType } returns "interactive notification type"
    }

    /**
     * Test the interactive notification event data is populated properly.
     */
    @Test
    public fun testData() {
        val notificationInfo = NotificationInfo(mockPushMessage, 100, "tag")
        val actionButtonInfo =
            NotificationActionButtonInfo("button id", false, null, "button description")

        val event = InteractiveNotificationEvent(notificationInfo, actionButtonInfo)

        validateEventValue(event, "button_id", "button id")
        validateEventValue(event, "button_description", "button description")
        validateEventValue(event, "button_group", "interactive notification type")
        validateEventValue(event, "foreground", false)
        validateEventValue(event, "send_id", "send id")
    }

    /**
     * Test the interactive notification event data is populated properly.
     */
    @Test
    public fun testDatWithRemoteInput() {
        val remoteInput = bundleOf(
            "input_one" to "cool",
            "input_two" to "story"
        )

        val notificationInfo = NotificationInfo(mockPushMessage, 100, "tag")
        val actionButtonInfo =
            NotificationActionButtonInfo("button id", false, remoteInput, "button description")

        val event = InteractiveNotificationEvent(notificationInfo, actionButtonInfo)

        validateEventValue(event, "button_id", "button id")
        validateEventValue(event, "button_description", "button description")
        validateEventValue(event, "button_group", "interactive notification type")
        validateEventValue(event, "foreground", false)
        validateEventValue(event, "send_id", "send id")
        validateNestedEventValue(event, "user_input", "input_one", "cool")
        validateNestedEventValue(event, "user_input", "input_two", "story")
    }
}
