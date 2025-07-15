/* Copyright Airship and Contributors */
package com.urbanairship.actions

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.BaseTestCase
import com.urbanairship.TestApplication
import com.urbanairship.UAirship
import com.urbanairship.UrlAllowList
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class WalletActionTest {

    private val testArgs = ActionTestUtils.createArgs(
        situation = Action.Situation.MANUAL_INVOCATION,
        value = "https://some.example.com")

    private val urlAllowList: UrlAllowList = mockk()
    private val action = WalletAction { urlAllowList }

    @Before
    public fun setup() {
        // Default the platform to Android
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM)
    }

    /**
     * Test action accepts kitkat+ devices.
     */
    @Test
    public fun testAcceptsKitKat() {
        every { urlAllowList.isAllowed(any(), UrlAllowList.SCOPE_OPEN_URL) } returns true
        assertTrue(action.acceptsArguments(testArgs))
    }

    /**
     * Test action rejects Amazon/ADM platform.
     */
    @Test
    public fun testRejectsAdmPlatform() {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM)
        assertFalse(action.acceptsArguments(testArgs))
    }

    /**
     * Test rejects arguments for URLs that are not allowed.
     */
    @Test
    public fun testUrlAllowList() {
        every {
            urlAllowList.isAllowed("https://some.example.com", UrlAllowList.SCOPE_OPEN_URL)
        } returns false

        assertFalse(action.acceptsArguments(testArgs))
    }
}
