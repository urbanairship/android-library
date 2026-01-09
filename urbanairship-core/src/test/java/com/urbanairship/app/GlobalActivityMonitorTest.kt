/* Copyright Airship and Contributors */
package com.urbanairship.app

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowLooper

/**
 * This class tests the monitoring the activities going into the foreground
 */
@RunWith(AndroidJUnit4::class)
public class GlobalActivityMonitorTest {

    private var activityMonitor = GlobalActivityMonitor()
    private var isForeground = false

    private val application: Context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    public fun setUp() {

        activityMonitor.registerListener(application)
        activityMonitor.addApplicationListener(object : SimpleApplicationListener() {
            override fun onForeground(milliseconds: Long) {
                isForeground = true
            }

            override fun onBackground(milliseconds: Long) {
                isForeground = false
            }
        })
    }

    @After
    public fun teardown() {
        activityMonitor.unregisterListener(application)
    }

    /**
     * This test verifies adding an activity calls the onForeground delegate call
     *
     */
    @Test
    public fun testActivityStarted() {
        Robolectric.buildActivity(Activity::class.java).create().start()
        ShadowLooper.shadowMainLooper().runToEndOfTasks()

        assertTrue(isForeground)
    }

    /**
     * This test verifies removing an activity calls the onBackground delegate call
     *
     */
    @Test
    public fun testActivityStopped() {
        Robolectric.buildActivity(Activity::class.java).create().start().stop()
        ShadowLooper.shadowMainLooper().runToEndOfTasks()
        assertFalse(isForeground)
    }

    /**
     * This test verifies removing an activity after multiple adds doesn't call the onBackground delegate call
     */
    @Test
    public fun testRemoveAfterAddMultipleActivity() {
        val activity1 = Robolectric.buildActivity(Activity::class.java)
            .create()
            .start()

        Robolectric.buildActivity(Activity::class.java).create().start()

        activity1.stop()

        ShadowLooper.shadowMainLooper().runToEndOfTasks()

        assertTrue(isForeground)
    }

    /**
     * This test verifies the multiple activities behavior calls the expected delegate calls
     */
    @Test
    public fun testMultipleActivities() {
        val activity1 = Robolectric.buildActivity(Activity::class.java)
            .create()
            .start()

        val activity2 = Robolectric.buildActivity(Activity::class.java)
            .create()
            .start()

        ShadowLooper.shadowMainLooper().runToEndOfTasks()
        assertTrue(isForeground)

        activity1.stop()

        ShadowLooper.shadowMainLooper().runToEndOfTasks()
        assertTrue(isForeground)

        activity2.stop()

        ShadowLooper.shadowMainLooper().runToEndOfTasks()
        assertFalse(isForeground)
    }
}
