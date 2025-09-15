/* Copyright Airship and Contributors */
package com.urbanairship

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.concurrent.Volatile
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper

@RunWith(AndroidJUnit4::class)
public class CancelableOperationTest {

    private val looper = Shadows.shadowOf(Looper.myLooper())
    private var operation = TestOperation(Looper.myLooper())

    /**
     * Test that the onRun method is executed on the looper's message queue.
     */
    @Test
    public fun testRun() {
        // Pause the looper to prevent messages from being processed
        looper.pause()

        // Run it
        operation.run()

        Assert.assertFalse(operation.isDone())
        Assert.assertFalse(operation.onRunCalled)

        // Process all the messages
        looper.runToEndOfTasks()

        Assert.assertTrue(operation.onRunCalled)
        Assert.assertTrue(operation.isDone())
        Assert.assertFalse(operation.isCancelled())
    }

    /**
     * Test canceling an operation cancels it immediately and runs the onCancel
     * on the looper's message queue.
     */
    @Test
    public fun testCancelRun() {
        // Pause the looper to prevent messages from being processed
        looper.pause()

        // Run it
        operation.run()
        operation.cancel()

        Assert.assertFalse(operation.onRunCalled)
        Assert.assertFalse(operation.onCancelCalled)

        Assert.assertTrue(operation.isDone())
        Assert.assertTrue(operation.isCancelled())

        // Process all the messages
        looper.runToEndOfTasks()

        Assert.assertTrue(operation.onCancelCalled)
        Assert.assertTrue(operation.isDone())
        Assert.assertTrue(operation.isCancelled())
        Assert.assertFalse(operation.onRunCalled)
    }

    /**
     * Test trying to run an operation after it has already run.
     */
    @Test
    public fun testRunAfterDone() {
        // Run it
        operation.run()
        looper.idle()
        Assert.assertTrue(operation.isDone())
        Assert.assertTrue(operation.onRunCalled)

        // Reset onRunCalled
        operation.onRunCalled = false
        operation.run()

        Assert.assertFalse(operation.onRunCalled)
    }

    /**
     * Test trying to run an operation after it has already been canceled.
     */
    @Test
    public fun testRunAfterCancel() {
        // Cancel it
        operation.cancel()
        Assert.assertTrue(operation.isCancelled())

        // Try to run it
        operation.run()
        looper.idle()

        Assert.assertFalse(operation.onRunCalled)
        Assert.assertTrue(operation.onCancelCalled)
        Assert.assertTrue(operation.isDone())
        Assert.assertTrue(operation.isCancelled())
    }

    @Test
    public fun testAddRunnable() {
        val onRun = TestOperation(Looper.myLooper())
        operation.addOnRun(onRun)

        // Run it
        operation.run()

        // Process all the messages
        looper.runToEndOfTasks()

        Assert.assertTrue(onRun.onRunCalled)
    }

    @Test
    public fun testAddRunnableAfterRun() {
        // Run it
        operation.run()

        // Process all the messages
        looper.runToEndOfTasks()

        Assert.assertTrue(operation.onRunCalled)

        val onRun = TestOperation(Looper.myLooper())
        operation.addOnRun(onRun)

        // Process all the messages
        looper.runToEndOfTasks()

        Assert.assertTrue(operation.onRunCalled)
    }

    @Test
    public fun testAddOnCancel() {
        val onCancel = TestOperation(Looper.myLooper())
        operation.addOnCancel(onCancel)

        // Cancel it
        operation.cancel()

        // Process all the messages
        looper.runToEndOfTasks()

        Assert.assertTrue(onCancel.onCancelCalled)
    }

    @Test
    public fun testAddOnCancelAfterCancelled() {
        // Cancel it
        operation.cancel()

        // Process all the messages
        looper.runToEndOfTasks()

        val onCancel = TestOperation(Looper.myLooper())
        operation.addOnCancel(onCancel)

        // Process all the messages
        looper.runToEndOfTasks()

        Assert.assertTrue(onCancel.onCancelCalled)
    }

    /**
     * Implementation of CancelableOperation for testing.
     */
    public class TestOperation(looper: Looper?) : CancelableOperation(looper) {

        @Volatile
        public var onRunCalled: Boolean = false

        @Volatile
        public var onCancelCalled: Boolean = false

        override fun onRun() {
            onRunCalled = true
        }

        override fun onCancel() {
            onCancelCalled = true
        }
    }
}
