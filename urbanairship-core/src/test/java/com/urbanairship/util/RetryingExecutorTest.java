/* Copyright Airship and Contributors */

package com.urbanairship.util;

import android.os.Handler;
import android.os.Looper;

import com.urbanairship.BaseTestCase;
import com.urbanairship.ShadowAirshipExecutorsLegacy;
import com.urbanairship.shadow.ShadowNotificationManagerExtension;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link RetryingExecutor}.
 */
@Config(
        sdk = 28,
        shadows = { ShadowNotificationManagerExtension.class, ShadowAirshipExecutorsLegacy.class }
)
@LooperMode(LooperMode.Mode.LEGACY)
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
        TestOperation operation = new TestOperation(RetryingExecutor.finishedResult());
        executor.execute(operation);
        assertEquals(1, operation.runCount);
    }

    @Test
    public void testExecuteChainedOperations() {
        TestOperation fistOperation = new TestOperation(RetryingExecutor.finishedResult());
        TestOperation secondOperation = new TestOperation(RetryingExecutor.retryResult());
        TestOperation thirdOperation = new TestOperation(RetryingExecutor.finishedResult());

        executor.execute(fistOperation, secondOperation, thirdOperation);
        assertEquals(1, fistOperation.runCount);
        assertEquals(1, secondOperation.runCount);
        assertEquals(0, thirdOperation.runCount);

        secondOperation.result = RetryingExecutor.finishedResult();
        advanceLooper(30000);

        assertEquals(1, fistOperation.runCount);
        assertEquals(2, secondOperation.runCount);
        assertEquals(1, thirdOperation.runCount);
    }

    @Test
    public void testExecuteChainedOperationsCancel() {
        TestOperation fistOperation = new TestOperation(RetryingExecutor.cancelResult());
        TestOperation secondOperation = new TestOperation(RetryingExecutor.finishedResult());
        TestOperation thirdOperation = new TestOperation(RetryingExecutor.finishedResult());

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
        TestOperation operation = new TestOperation(RetryingExecutor.retryResult());
        executor.execute(operation);
        assertEquals(1, operation.runCount);

        // Initial backoff
        advanceLooper(30000);
        assertEquals(2, operation.runCount);

        advanceLooper(60000);
        assertEquals(3, operation.runCount);

        operation.result = RetryingExecutor.finishedResult();
        advanceLooper(120000);
        assertEquals(4, operation.runCount);

        // Run the looper to the end of tasks, make sure the run count is still 4
        Shadows.shadowOf(mainLooper).runToEndOfTasks();
        assertEquals(4, operation.runCount);
    }

    @Test
    public void testExecuteOperationRetryWithBackOff() {
        TestOperation operation = new TestOperation(RetryingExecutor.retryResult(10));
        executor.execute(operation);
        assertEquals(1, operation.runCount);

        // Initial backoff
        advanceLooper(10);
        assertEquals(2, operation.runCount);

        operation.result = RetryingExecutor.retryResult();

        // Still have old backoff
        advanceLooper(10);
        assertEquals(3, operation.runCount);

        operation.result = RetryingExecutor.retryResult();

        advanceLooper(19);
        assertEquals(3, operation.runCount);

        advanceLooper(1);
        assertEquals(4, operation.runCount);

        operation.result = RetryingExecutor.finishedResult();
        advanceLooper(39);
        assertEquals(4, operation.runCount);

        advanceLooper(1);
        assertEquals(5, operation.runCount);

        // Run the looper to the end of tasks, make sure the run count is still 5
        Shadows.shadowOf(mainLooper).runToEndOfTasks();
        assertEquals(5, operation.runCount);
    }

    @Test
    public void testPause() {
        TestOperation operation = new TestOperation(RetryingExecutor.retryResult());

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

        RetryingExecutor.Result result;
        int runCount;

        public TestOperation(RetryingExecutor.Result result) {
            this.result = result;
        }

        @Override
        public RetryingExecutor.Result run() {
            runCount++;
            return result;
        }
    }
}
