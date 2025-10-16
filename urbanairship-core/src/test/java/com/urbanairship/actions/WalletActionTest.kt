/* Copyright Airship and Contributors */
package com.urbanairship.actions

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.Platform
import com.urbanairship.UrlAllowList
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class WalletActionTest {

    private val testArgs = ActionTestUtils.createArgs(
        situation = Action.Situation.MANUAL_INVOCATION,
        value = "https://some.example.com")

    private val urlAllowList: UrlAllowList = mockk(relaxed = true)
    private var platformProvider: () -> Platform = { Platform.ANDROID }
    private val action = WalletAction(
        allowListProvider =  { urlAllowList },
        contextProvider = { ApplicationProvider.getApplicationContext() },
        platformProvider = platformProvider
    )

    /**
     * Test action accepts kitkat+ devices.
     */
    @Test
    public fun testAcceptsKitKat() {
        every { urlAllowList.isAllowed(any(), UrlAllowList.Scope.OPEN_URL) } returns true
        assertTrue(action.acceptsArguments(testArgs))
    }

    /**
     * Test action rejects Amazon/ADM platform.
     */
    @Test
    public fun testRejectsAdmPlatform() {
        platformProvider = { Platform.AMAZON }
        assertFalse(action.acceptsArguments(testArgs))
    }

    /**
     * Test rejects arguments for URLs that are not allowed.
     */
    @Test
    public fun testUrlAllowList() {
        every {
            urlAllowList.isAllowed("https://some.example.com", UrlAllowList.Scope.OPEN_URL)
        } returns false

        assertFalse(action.acceptsArguments(testArgs))
    }
}
