package com.urbanairship

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class UALogTest {

    /**
     * Test the logger listener.
     */
    @Test
    public fun testListener() {
        val errorMessage = "This is a test"
        val error: Throwable = IllegalArgumentException("Oh no")
        val called = ArrayList<String>()

        val myLogger =
            AirshipLogHandler { tag, logLevel, throwable, message ->
                Assert.assertEquals(logLevel.toLong(), Log.ERROR.toLong())
                Assert.assertEquals(error, throwable)
                Assert.assertEquals(errorMessage, message.invoke())
                called.add(message.invoke())
            }

        UALog.logHandler = myLogger

        UALog.e(error, errorMessage)

        Assert.assertEquals(called.size.toLong(), 1)
        Assert.assertEquals(called[0], errorMessage)

        UALog.logHandler = DefaultLogHandler()
    }

    @Test
    public fun testClassNamePrefix() {
        UALog.logLevel = Log.VERBOSE

        val rawMessage = "This is another test"
        val prefixedMessage = String.format("%s - %s", javaClass.simpleName, rawMessage)
        val called = ArrayList<String>()

        val myLogger =
            AirshipLogHandler { tag, logLevel, throwable, message ->
                Assert.assertNull(throwable)
                if (logLevel == Log.DEBUG || logLevel == Log.VERBOSE) {
                    Assert.assertEquals(prefixedMessage, message.invoke())
                } else {
                    Assert.assertEquals(rawMessage, message.invoke())
                }
                called.add(message.invoke())
            }

        UALog.logHandler = myLogger

        // Make sure we properly add the prefix to the raw message
        UALog.v(rawMessage)
        Assert.assertEquals(1, called.size.toLong())
        Assert.assertEquals(prefixedMessage, called[0])

        // Make sure we don't add an extra prefix if the message was already prefixed manually
        UALog.d(prefixedMessage)
        Assert.assertEquals(2, called.size.toLong())
        Assert.assertEquals(prefixedMessage, called[1])

        // Make sure we don't muck with log levels above debug
        UALog.i(rawMessage)
        Assert.assertEquals(3, called.size.toLong())
        Assert.assertEquals(rawMessage, called[2])

        UALog.w(rawMessage)
        Assert.assertEquals(4, called.size.toLong())
        Assert.assertEquals(rawMessage, called[3])

        UALog.e(rawMessage)
        Assert.assertEquals(5, called.size.toLong())
        Assert.assertEquals(rawMessage, called[4])

        UALog.logHandler = DefaultLogHandler()
    }
}
