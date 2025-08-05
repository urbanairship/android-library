/* Copyright Airship and Contributors */
package com.urbanairship

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows

@RunWith(AndroidJUnit4::class)
class PendingResultTest {

    private var resultCallback = TestResultCallback<Boolean>()
    private var pendingResult = PendingResult<Boolean>()

    private var looper = Looper.myLooper()

    @Before
    fun setup() {
        pendingResult.addResultCallback(Looper.myLooper(), resultCallback)
    }

    @Test
    fun testGet() {
        setResultAsync(true)

        // Verify we get the result
        Assert.assertTrue(pendingResult.get() == true)
        Assert.assertTrue(pendingResult.isDone)
        Assert.assertFalse(pendingResult.isCancelled)

        // Verify callback is still called
        Shadows.shadowOf(looper).runToEndOfTasks()
        Assert.assertEquals(1, resultCallback.results.size)
        Assert.assertTrue(resultCallback.results[0])

        // Verify we can still get the result
        Assert.assertTrue(pendingResult.get() == true)
    }

    @Test
    fun testCancel() {
        pendingResult.cancel()
        Assert.assertTrue(pendingResult.isDone)
        Assert.assertTrue(pendingResult.isCancelled)

        setResultAsync(true)

        // Verify the result in get is null
        Assert.assertNull(pendingResult.get())

        // Callback should not be called
        Assert.assertEquals(0, resultCallback.results.size)
    }

    private fun setResultAsync(result: Boolean) {
        Thread { pendingResult.setResult(result) }.start()
    }

    private inner class TestResultCallback<T> : ResultCallback<T> {

        var results = mutableListOf<T>()

        override fun onResult(result: T?) {
            result?.let { results.add(it) }
        }
    }
}
