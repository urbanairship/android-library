/* Copyright Airship and Contributors */

package com.urbanairship;

import android.os.Looper;
import androidx.annotation.Nullable;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

public class PendingResultTest extends BaseTestCase {

    private TestResultCallback<Boolean> resultCallback;
    private PendingResult<Boolean> pendingResult;

    private Looper looper;

    @Before
    public void setup() {
        looper = Looper.myLooper();
        resultCallback = new TestResultCallback<>();
        pendingResult = new PendingResult<>();
        pendingResult.addResultCallback(Looper.myLooper(), resultCallback);
    }

    @Test
    public void testGet() throws ExecutionException, InterruptedException {
        setResultAsync(true);

        // Verify we get the result
        assertTrue(pendingResult.get());
        assertTrue(pendingResult.isDone());
        assertFalse(pendingResult.isCancelled());

        // Verify callback is still called
        Shadows.shadowOf(looper).runToEndOfTasks();
        assertEquals(1, resultCallback.results.size());
        assertTrue(resultCallback.results.get(0));

        // Verify we can still get the result
        assertTrue(pendingResult.get());
    }

    @Test
    public void testCancel() throws ExecutionException, InterruptedException {
        pendingResult.cancel();
        assertTrue(pendingResult.isDone());
        assertTrue(pendingResult.isCancelled());

        setResultAsync(true);

        // Verify the result in get is null
        assertNull(pendingResult.get());

        // Callback should not be called
        assertEquals(0, resultCallback.results.size());
    }

    private void setResultAsync(final boolean result) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                pendingResult.setResult(result);
            }
        }).start();
    }

    private class TestResultCallback<T> implements ResultCallback<T> {

        List<T> results = new ArrayList<>();

        @Override
        public void onResult(@Nullable T result) {
            this.results.add(result);
        }

    }

}