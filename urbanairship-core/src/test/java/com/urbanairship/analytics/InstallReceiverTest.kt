/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestApplication
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InstallReceiverTest {

    private val receiver = InstallReceiver()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var mockAnalytics: Analytics = mockk()

    @Before
    public fun setup() {
        TestApplication.getApplication().setAnalytics(mockAnalytics)
    }

    /**
     * Test the referrer action creates an install attribution event.
     */
    @Test
    public fun testCreateInstallAttributionEvent() {
        val intent = Intent("com.android.vending.INSTALL_REFERRER")
            .putExtra("referrer", "some value")

        every { mockAnalytics.addEvent(any()) } answers {
            val event: InstallAttributionEvent = firstArg()
            assertEquals(
                "some value",
                event
                    .getEventData(conversionData = ConversionData(null, null, null))
                    .opt("google_play_referrer")
                    .string
            )
            true
        }

        receiver.onReceive(context, intent)

        verify { mockAnalytics.addEvent(any()) }
    }

    /**
     * Test missing referrer does not create an install attribution event.
     */
    @Test
    public fun testMissingReferrer() {
        val intent = Intent("com.android.vending.INSTALL_REFERRER")

        receiver.onReceive(context, intent)

        verify { mockAnalytics wasNot Called }
    }

    /**
     * Test invalid referrer action does not create an install attribution event.
     */
    @Test
    public fun testInvalidAction() {
        val intent = Intent("action")
            .putExtra("referrer", "some value")

        receiver.onReceive(context, intent)

        verify { mockAnalytics wasNot Called }
    }

    @Test
    public fun testEmptyReferrer() {
        val intent = Intent("com.android.vending.INSTALL_REFERRER")
            .putExtra("referrer", "")

        receiver.onReceive(context, intent)

        verify { mockAnalytics wasNot Called }
    }
}
