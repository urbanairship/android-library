/* Copyright Airship and Contributors */
package com.urbanairship.analytics.data

import androidx.core.util.Consumer
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class BatchedQueryHelperTest {

    private val callback = TestConsumer()

    @Test
    public fun runsMultipleBatches() {
        BatchedQueryHelper.runBatched(3, LIST, callback)
        Assert.assertEquals(4, callback.batches.size.toLong())
        val expected = listOf(
            listOf(0, 1, 2),
            listOf(3, 4, 5),
            listOf(6, 7, 8),
            listOf(9, 10)
        )
        Assert.assertEquals(expected, callback.batches)
    }

    @Test
    public fun runsMultipleSingleItemBatches() {
        BatchedQueryHelper.runBatched(1, LIST, callback)
        Assert.assertEquals(11, callback.batches.size.toLong())
        val expected = LIST.map { listOf(it) }
        Assert.assertEquals(expected, callback.batches)
    }

    @Test
    public fun runsSingleBatch() {
        BatchedQueryHelper.runBatched(LIST.size, LIST, callback)
        Assert.assertEquals(1, callback.batches.size.toLong())
        Assert.assertEquals(LIST, callback.batches[0])
    }

    @Test(expected = IllegalArgumentException::class)
    public fun throwsOnZeroBatchSize() {
        BatchedQueryHelper.runBatched(0, LIST, callback)
    }


    private class TestConsumer : Consumer<List<Int>> {

        var batches: MutableList<List<Int>> = mutableListOf()

        override fun accept(value: List<Int>) {
            batches.add(value)
        }
    }

    internal companion object {
        private val LIST: List<Int> = mutableListOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    }
}
