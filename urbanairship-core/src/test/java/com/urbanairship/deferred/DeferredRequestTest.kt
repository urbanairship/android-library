package com.urbanairship.deferred

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.json.JsonValue
import com.urbanairship.locale.LocaleManager
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

    @Test
    public fun testAutomationWrapperFull(): TestResult = runTest {
        val infoProvider: DeviceInfoProvider = mockk()
        coEvery { infoProvider.getStableContactId() } returns "test-contact-id"
        every { infoProvider.isNotificationsOptedIn } returns true

        val userLocale: Locale = mockk()
        val localeManager: LocaleManager = mockk()
        every { localeManager.locale } returns userLocale

        val expected = DeferredRequest(
            uri = Uri.parse("https://example.com"),
            channelID = "test-channel-id",
            contactID = "test-contact-id",
            triggerContext = DeferredTriggerContext("trigger-type", 22.0, JsonValue.wrap("event")),
            locale = userLocale,
            notificationOptIn = true
        )

        val actual = DeferredRequest.automation(
            uri = expected.uri,
            channelID = expected.channelID,
            infoProvider = infoProvider,
            triggerType = expected.triggerContext?.type,
            triggerEvent = expected.triggerContext?.event,
            triggerGoal = expected.triggerContext?.goal ?: 1.0,
            localeManager = localeManager
        ).get()

        assertEquals(expected, actual)
    }

    @Test
    public fun testAutomationWrapperNoTrigger(): TestResult = runTest {
        val infoProvider: DeviceInfoProvider = mockk()
        coEvery { infoProvider.getStableContactId() } returns "test-contact-id"
        every { infoProvider.isNotificationsOptedIn } returns true

        val userLocale: Locale = mockk()
        val localeManager: LocaleManager = mockk()
        every { localeManager.locale } returns userLocale

        val expected = DeferredRequest(
            uri = Uri.parse("https://example.com"),
            channelID = "test-channel-id",
            contactID = "test-contact-id",
            triggerContext = null,
            locale = userLocale,
            notificationOptIn = true
        )

        var actual = DeferredRequest.automation(
            uri = expected.uri,
            channelID = expected.channelID,
            infoProvider = infoProvider,
            triggerType = null,
            triggerEvent = JsonValue.NULL,
            triggerGoal = 1.0,
            localeManager = localeManager
        ).get()

        assertEquals(expected, actual)

        actual = DeferredRequest.automation(
            uri = expected.uri,
            channelID = expected.channelID,
            infoProvider = infoProvider,
            triggerType = "event-type",
            triggerEvent = null,
            triggerGoal = 1.0,
            localeManager = localeManager
        ).get()

        assertEquals(expected, actual)
    }
}
