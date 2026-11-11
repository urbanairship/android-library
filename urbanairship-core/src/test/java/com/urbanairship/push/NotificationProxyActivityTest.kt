/* Copyright Airship and Contributors */
package com.urbanairship.push

import android.content.Intent
import android.os.Looper
import androidx.core.os.bundleOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.Airship
import com.urbanairship.AirshipStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

/**
 * [NotificationProxyActivity] tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class NotificationProxyActivityTest {

    private val responseIntent: Intent = Intent()
        .setAction(PushManager.ACTION_NOTIFICATION_RESPONSE)
        .putExtra(
            PushManager.EXTRA_PUSH_MESSAGE_BUNDLE,
            bundleOf(PushMessage.EXTRA_SEND_ID to "sendId")
        )
        .putExtra(PushManager.EXTRA_NOTIFICATION_ID, 150)

    private var waitStarted: Boolean = false
    private var requestedTimeout: Duration? = null

    @After
    public fun after() {
        unmockkObject(Airship)
    }

    /**
     * Test that the activity reaches the wait for takeOff and still finishes.
     *
     * `Theme.NoDisplay` requires finishing before `onCreate` returns, so a wait that blocked the
     * main thread would stall the launch instead of letting it through.
     */
    @Test
    public fun testFinishesWhileTakeOffIsStillPending() {
        val controller = startWithPendingTakeOff().create()
        idleMainLooper()

        Assert.assertTrue(controller.get().isFinishing)
        Assert.assertTrue(waitStarted)
    }

    /**
     * Test that the activity bounds its wait for takeOff.
     *
     * Processing resumes after the activity has finished, so an unbounded wait would leave it
     * running for as long as takeOff never completes.
     */
    @Test
    public fun testBoundsTheWaitForTakeOff() {
        startWithPendingTakeOff().create()
        idleMainLooper()

        Assert.assertEquals(NotificationProxyActivity.TAKE_OFF_TIMEOUT, requestedTimeout)
    }

    /**
     * Test that the wait stays inside Android's background activity start grace period.
     *
     * Once that period lapses the application launch is dropped, so a longer bound would leave
     * the notification tap opening nothing.
     */
    @Test
    public fun testTakeOffTimeoutStaysWithinTheGracePeriod() {
        Assert.assertTrue(NotificationProxyActivity.TAKE_OFF_TIMEOUT > Duration.ZERO)
        Assert.assertTrue(NotificationProxyActivity.TAKE_OFF_TIMEOUT < 10.seconds)
    }

    /** Builds the activity with takeOff started but never completing. */
    private fun startWithPendingTakeOff(): ActivityController<NotificationProxyActivity> {
        mockkObject(Airship)
        // isTakingOff and isFlying derive from status, so stubbing them would clobber it.
        every { Airship.status } returns AirshipStatus.TAKING_OFF
        coEvery { Airship.waitForReady(any()) } coAnswers {
            waitStarted = true
            requestedTimeout = firstArg()
            awaitCancellation()
        }

        return Robolectric.buildActivity(NotificationProxyActivity::class.java, responseIntent)
    }

    /** Robolectric pauses the looper, so the processing coroutine is queued rather than inline. */
    private fun idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
