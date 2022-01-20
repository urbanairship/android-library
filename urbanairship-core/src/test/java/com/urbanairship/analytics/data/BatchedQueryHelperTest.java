/* Copyright Airship and Contributors */

package com.urbanairship.analytics.data;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;

@Config(sdk = 28)
@RunWith(AndroidJUnit4.class)
public class BatchedQueryHelperTest {

    private static final List<Integer> LIST = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    private final TestConsumer callback = new TestConsumer();

    @Test
    public void runsMultipleBatches() {
        BatchedQueryHelper.runBatched(3, LIST, callback);
        assertEquals(4, callback.batches.size());
        List<List<Integer>> expected = Arrays.asList(
            Arrays.asList(0, 1, 2),
            Arrays.asList(3, 4, 5),
            Arrays.asList(6, 7, 8),
            Arrays.asList(9, 10)
        );
        assertEquals(expected, callback.batches);
    }

    @Test
    public void runsMultipleSingleItemBatches() {
        BatchedQueryHelper.runBatched(1, LIST, callback);
        assertEquals(11, callback.batches.size());
        List<List<Integer>> expected = new ArrayList<>();
        for (int i : LIST) {
            expected.add(Collections.singletonList(i));
        }
        assertEquals(expected, callback.batches);
    }

    @Test
    public void runsSingleBatch() {
        BatchedQueryHelper.runBatched(LIST.size(), LIST, callback);
        assertEquals(1, callback.batches.size());
        assertEquals(LIST, callback.batches.get(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void throwsOnZeroBatchSize() {
        BatchedQueryHelper.runBatched(0, LIST, callback);
    }

    private static class TestConsumer implements Consumer<List<Integer>> {
        List<List<Integer>> batches = new ArrayList<>();

        @Override
        public void accept(List<Integer> integers) {
            batches.add(integers);
        }

        @Override
        public Consumer<List<Integer>> andThen(Consumer<? super List<Integer>> after) {
            return null;
        }
    }
}
