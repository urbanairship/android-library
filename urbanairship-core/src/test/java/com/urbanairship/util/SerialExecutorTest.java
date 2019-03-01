package com.urbanairship.util;

import com.urbanairship.BaseTestCase;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;

public class SerialExecutorTest extends BaseTestCase {

    Executor executor;

    @Before
    public void setup() {
        executor = new SerialExecutor(Executors.newFixedThreadPool(4));
    }

    @Test
    public void testOrder() throws InterruptedException {
        final List<Integer> actual = new ArrayList<>();
        final List<Integer> expected = new ArrayList<>();

        final CountDownLatch latch = new CountDownLatch(100);

        for (int i = 1; i <= 100; i++) {
            final int count = i;
            expected.add(count);
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    actual.add(count);
                    latch.countDown();
                }
            });
        }

        latch.await();
        assertEquals(expected, actual);
    }

}