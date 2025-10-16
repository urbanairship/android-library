/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InstallReceiverTest {

    private var events = mutableListOf<InstallAttributionEvent>()

    private val receiver = InstallReceiver { event ->
        events.add(event)
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * Test the referrer action creates an install attribution event.
     */
    @Test
    public fun testCreateInstallAttributionEvent() {
        val intent = Intent("com.android.vending.INSTALL_REFERRER")
            .putExtra("referrer", "some value")

        receiver.onReceive(context, intent)

        assertEquals(events, listOf(InstallAttributionEvent("some value")))
    }

    /**
     * Test missing referrer does not create an install attribution event.
     */
    @Test
    public fun testMissingReferrer() {
        val intent = Intent("com.android.vending.INSTALL_REFERRER")

        receiver.onReceive(context, intent)
        assertTrue(events.isEmpty())
    }

    /**
     * Test invalid referrer action does not create an install attribution event.
     */
    @Test
    public fun testInvalidAction() {
        val intent = Intent("action")
            .putExtra("referrer", "some value")

        receiver.onReceive(context, intent)
        assertTrue(events.isEmpty())
    }

    @Test
    public fun testEmptyReferrer() {
        val intent = Intent("com.android.vending.INSTALL_REFERRER")
            .putExtra("referrer", "")

        receiver.onReceive(context, intent)
        assertTrue(events.isEmpty())
    }
}
