/* Copyright 2017 Urban Airship and Contributors */

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
        assertFalse(operation.isCanceled());
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
        assertTrue(operation.isCanceled());

        // Process all the messages
        looper.runToEndOfTasks();

        assertTrue(operation.onCancelCalled);
        assertTrue(operation.isDone());
        assertTrue(operation.isCanceled());
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
        assertTrue(operation.isCanceled());

        // Try to run it
        operation.run();

        assertFalse(operation.onRunCalled);
        assertTrue(operation.onCancelCalled);
        assertTrue(operation.isDone());
        assertTrue(operation.isCanceled());
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
