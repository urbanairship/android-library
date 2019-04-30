/* Copyright Airship and Contributors */

package com.urbanairship;

import android.os.Looper;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CancelableOperationTest extends BaseTestCase {

    ShadowLooper looper;
    TestOperation operation;

    @Before
    public void setup() {
        looper = Shadows.shadowOf(Looper.myLooper());
        operation = new TestOperation(Looper.myLooper());
    }

    /**
     * Test that the onRun method is executed on the looper's message queue.
     */
    @Test
    public void testRun() {
        // Pause the looper to prevent messages from being processed
        looper.pause();

        // Run it
        operation.run();

        assertFalse(operation.isDone());
        assertFalse(operation.onRunCalled);

        // Process all the messages
        looper.runToEndOfTasks();

        assertTrue(operation.onRunCalled);
        assertTrue(operation.isDone());
        assertFalse(operation.isCancelled());
    }

    /**
     * Test canceling an operation cancels it immediately and runs the onCancel
     * on the looper's message queue.
     */
    @Test
    public void testCancelRun() {
        // Pause the looper to prevent messages from being processed
        looper.pause();

        // Run it
        operation.run();
        operation.cancel();

        assertFalse(operation.onRunCalled);
        assertFalse(operation.onCancelCalled);

        assertTrue(operation.isDone());
        assertTrue(operation.isCancelled());

        // Process all the messages
        looper.runToEndOfTasks();

        assertTrue(operation.onCancelCalled);
        assertTrue(operation.isDone());
        assertTrue(operation.isCancelled());
        assertFalse(operation.onRunCalled);
    }

    /**
     * Test trying to run an operation after it has already run.
     */
    @Test
    public void testRunAfterDone() {
        // Run it
        operation.run();
        assertTrue(operation.isDone());
        assertTrue(operation.onRunCalled);

        // Reset onRunCalled
        operation.onRunCalled = false;
        operation.run();

        assertFalse(operation.onRunCalled);
    }

    /**
     * Test trying to run an operation after it has already been canceled.
     */
    @Test
    public void testRunAfterCancel() {
        // Cancel it
        operation.cancel();
        assertTrue(operation.isCancelled());

        // Try to run it
        operation.run();

        assertFalse(operation.onRunCalled);
        assertTrue(operation.onCancelCalled);
        assertTrue(operation.isDone());
        assertTrue(operation.isCancelled());
    }

    @Test
    public void testAddRunnable() {
        TestOperation onRun = new TestOperation(Looper.myLooper());
        operation.addOnRun(onRun);

        // Run it
        operation.run();

        // Process all the messages
        looper.runToEndOfTasks();

        assertTrue(onRun.onRunCalled);
    }

    @Test
    public void testAddRunnableAfterRun() {
        // Run it
        operation.run();

        // Process all the messages
        looper.runToEndOfTasks();

        assertTrue(operation.onRunCalled);

        TestOperation onRun = new TestOperation(Looper.myLooper());
        operation.addOnRun(onRun);

        // Process all the messages
        looper.runToEndOfTasks();

        assertTrue(operation.onRunCalled);
    }

    @Test
    public void testAddOnCancel() {
        TestOperation onCancel = new TestOperation(Looper.myLooper());
        operation.addOnCancel(onCancel);

        // Cancel it
        operation.cancel();

        // Process all the messages
        looper.runToEndOfTasks();

        assertTrue(onCancel.onCancelCalled);
    }

    @Test
    public void testAddOnCancelAfterCancelled() {
        // Cancel it
        operation.cancel();

        // Process all the messages
        looper.runToEndOfTasks();

        TestOperation onCancel = new TestOperation(Looper.myLooper());
        operation.addOnCancel(onCancel);

        // Process all the messages
        looper.runToEndOfTasks();

        assertTrue(onCancel.onCancelCalled);
    }

    /**
     * Implementation of CancelableOperation for testing.
     */
    static class TestOperation extends CancelableOperation {

        volatile boolean onRunCalled = false;
        volatile boolean onCancelCalled = false;

        TestOperation(Looper looper) {
            super(looper);
        }

        @Override
        protected void onRun() {
            onRunCalled = true;
        }

        @Override
        protected void onCancel() {
            onCancelCalled = true;
        }

    }

}
