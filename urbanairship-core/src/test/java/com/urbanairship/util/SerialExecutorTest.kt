package com.urbanairship.util

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import org.junit.Assert
import org.junit.Test

class SerialExecutorTest {

    private var executor: Executor = SerialExecutor(Executors.newFixedThreadPool(4))

    @Test
    fun testOrder() {
        val actual = mutableListOf<Int>()
        val expected = mutableListOf<Int>()

        val latch = CountDownLatch(100)

        for (i in 1..100) {
            expected.add(i)
            executor.execute {
                actual.add(i)
                latch.countDown()
            }
        }

        latch.await()
        Assert.assertEquals(expected, actual)
    }
}
