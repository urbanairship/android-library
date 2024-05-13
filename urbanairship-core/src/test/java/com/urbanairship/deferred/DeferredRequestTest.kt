package com.urbanairship.deferred

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.json.JsonValue
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.Locale
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class DeferredRequestTest {

    private val infoProvider: DeviceInfoProvider = mockk {
        coEvery { this@mockk.getStableContactId() } returns "test-contact-id"
        coEvery { this@mockk.getChannelId() } returns "test-channel-id"
        every { this@mockk.isNotificationsOptedIn } returns true
        every { this@mockk.locale } returns Locale.CANADA
        every { this@mockk.appVersionName } returns "test-app-version"
    }

    @Test
    public fun testAutomationWrapperFull(): TestResult = runTest {
        val expected = DeferredRequest(
            uri = Uri.parse("https://example.com"),
            channelID = "test-channel-id",
            contactID = "test-contact-id",
            triggerContext = DeferredTriggerContext("trigger-type", 22.0, JsonValue.wrap("event")),
            locale = Locale.CANADA,
            notificationOptIn = true,
            appVersionName = "test-app-version"
        )

        val actual = DeferredRequest.automation(
            uri = expected.uri,
            infoProvider = infoProvider,
            triggerType = expected.triggerContext?.type,
            triggerEvent = expected.triggerContext?.event,
            triggerGoal = expected.triggerContext?.goal ?: 1.0,
        ).get()

        assertEquals(expected, actual)
    }

    @Test
    public fun testAutomationWrapperNoTrigger(): TestResult = runTest {

        val expected = DeferredRequest(
            uri = Uri.parse("https://example.com"),
            channelID = "test-channel-id",
            contactID = "test-contact-id",
            triggerContext = null,
            locale = Locale.CANADA,
            notificationOptIn = true,
            appVersionName = "test-app-version"
        )

        var actual = DeferredRequest.automation(
            uri = expected.uri,
            infoProvider = infoProvider,
            triggerType = null,
            triggerEvent = JsonValue.NULL,
            triggerGoal = 1.0,
        ).get()

        assertEquals(expected, actual)

        actual = DeferredRequest.automation(
            uri = expected.uri,
            infoProvider = infoProvider,
            triggerType = "event-type",
            triggerEvent = null,
            triggerGoal = 1.0,
        ).get()

        assertEquals(expected, actual)
    }
}
