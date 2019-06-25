/* Copyright Airship and Contributors */

package com.urbanairship.util;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;

import com.urbanairship.BaseTestCase;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.Shadows;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link RetryingExecutor}.
 */
public class RetryingExecutorTest extends BaseTestCase {

    private RetryingExecutor executor;
    private Looper mainLooper;

    @Before
    public void setup() {
        executor = new RetryingExecutor(new Handler(Looper.getMainLooper()), new Executor() {
            @Override
            public void execute(@NonNull Runnable runnable) {
                runnable.run();
            }
        });

        mainLooper = Looper.getMainLooper();
    }

    @Test
    public void testExecuteRunnable() {
        Runnable runnable = mock(Runnable.class);

        executor.execute(runnable);
        verify(runnable).run();
    }

    @Test
    public void testExecuteOperation() {
        TestOperation operation = new TestOperation(RetryingExecutor.RESULT_FINISHED);
        executor.execute(operation);
        assertEquals(1, operation.runCount);
    }

    @Test
    public void testExecuteChainedOperations() {
        TestOperation fistOperation = new TestOperation(RetryingExecutor.RESULT_FINISHED);
        TestOperation secondOperation = new TestOperation(RetryingExecutor.RESULT_RETRY);
        TestOperation thirdOperation = new TestOperation(RetryingExecutor.RESULT_FINISHED);

        executor.execute(fistOperation, secondOperation, thirdOperation);
        assertEquals(1, fistOperation.runCount);
        assertEquals(1, secondOperation.runCount);
        assertEquals(0, thirdOperation.runCount);

        secondOperation.result = RetryingExecutor.RESULT_FINISHED;
        advanceLooper(30000);

        assertEquals(1, fistOperation.runCount);
        assertEquals(2, secondOperation.runCount);
        assertEquals(1, thirdOperation.runCount);
    }

    @Test
    public void testExecuteChainedOperationsCancel() {
        TestOperation fistOperation = new TestOperation(RetryingExecutor.RESULT_CANCEL);
        TestOperation secondOperation = new TestOperation(RetryingExecutor.RESULT_FINISHED);
        TestOperation thirdOperation = new TestOperation(RetryingExecutor.RESULT_FINISHED);

        executor.execute(fistOperation, secondOperation, thirdOperation);
        assertEquals(1, fistOperation.runCount);
        assertEquals(0, secondOperation.runCount);
        assertEquals(0, thirdOperation.runCount);

        advanceLooper(30000);
        assertEquals(1, fistOperation.runCount);
        assertEquals(0, secondOperation.runCount);
        assertEquals(0, thirdOperation.runCount);
    }

    @Test
    public void testExecuteOperationRetry() {
        TestOperation operation = new TestOperation(RetryingExecutor.RESULT_RETRY);
        executor.execute(operation);
        assertEquals(1, operation.runCount);

        // Initial backoff
        advanceLooper(30000);
        assertEquals(2, operation.runCount);

        advanceLooper(60000);
        assertEquals(3, operation.runCount);

        operation.result = RetryingExecutor.RESULT_FINISHED;
        advanceLooper(120000);
        assertEquals(4, operation.runCount);

        // Run the looper to the end of tasks, make sure the run count is still 4
        Shadows.shadowOf(mainLooper).runToEndOfTasks();
        assertEquals(4, operation.runCount);
    }

    @Test
    public void testPause() {
        TestOperation operation = new TestOperation(RetryingExecutor.RESULT_RETRY);

        // Pause the executor
        executor.setPaused(true);

        // Try to run the operation
        executor.execute(operation);
        assertEquals(0, operation.runCount);

        // Resume
        executor.setPaused(false);
        assertEquals(1, operation.runCount);

        // Pause again
        executor.setPaused(true);

        // Make sure the retry does not execute the operation
        advanceLooper(30000);
        assertEquals(1, operation.runCount);

        // Resume
        executor.setPaused(false);
        assertEquals(2, operation.runCount);
    }

    private void advanceLooper(long millis) {
        Shadows.shadowOf(mainLooper).getScheduler().advanceBy(millis, TimeUnit.MILLISECONDS);
    }

    public static class TestOperation implements RetryingExecutor.Operation {

        int result;
        int runCount;

        public TestOperation(int result) {
            this.result = result;
        }

        @Override
        public int run() {
            runCount++;
            return result;
        }

    }

}