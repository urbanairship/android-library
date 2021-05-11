package com.urbanairship;

import android.util.Log;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;

import androidx.annotation.Nullable;

public class LoggerTest extends BaseTestCase {

    /**
     * Test the logger listener.
     */
    @Test
    public void testListener() {

        final String errorMessage = "This is a test";
        final Throwable error = new IllegalArgumentException("Oh no");
        final ArrayList<String> called = new ArrayList<>();

        LoggerListener myListener = new LoggerListener() {
            @Override
            public void onLog(int priority, @Nullable Throwable throwable, @Nullable String message) {

                Assert.assertEquals(priority, Log.ERROR);
                Assert.assertEquals(error, throwable);
                Assert.assertEquals(errorMessage, message);

                called.add(message);
            }
        };

        Logger.addListener(myListener);

        Logger.error(error, errorMessage);

        Assert.assertEquals(called.size(), 1);
        Assert.assertEquals(called.get(0), errorMessage);

        Logger.removeListener(myListener);
    }

    @Test
    public void testClassNamePrefix() {
        Logger.setLogLevel(Log.VERBOSE);

        final String rawMessage = "This is another test";
        final String prefixedMessage = String.format("%s - %s", getClass().getSimpleName(), rawMessage);
        final ArrayList<String> called = new ArrayList<>();

        LoggerListener listener = new LoggerListener() {
            @Override
            public void onLog(int priority, @Nullable Throwable throwable, @Nullable String message) {
                Assert.assertNull(throwable);

                if (priority == Log.DEBUG || priority == Log.VERBOSE) {
                    Assert.assertEquals(prefixedMessage, message);
                } else {
                    Assert.assertEquals(rawMessage, message);
                }

                called.add(message);
            }
        };

        Logger.addListener(listener);

        // Make sure we properly add the prefix to the raw message
        Logger.verbose(rawMessage);
        Assert.assertEquals(1, called.size());
        Assert.assertEquals(prefixedMessage, called.get(0));

        // Make sure we don't add an extra prefix if the message was already prefixed manually
        Logger.debug(prefixedMessage);
        Assert.assertEquals(2, called.size());
        Assert.assertEquals(prefixedMessage, called.get(1));

        // Make sure we don't muck with log levels above debug
        Logger.info(rawMessage);
        Assert.assertEquals(3, called.size());
        Assert.assertEquals(rawMessage, called.get(2));

        Logger.warn(rawMessage);
        Assert.assertEquals(4, called.size());
        Assert.assertEquals(rawMessage, called.get(3));

        Logger.error(rawMessage);
        Assert.assertEquals(5, called.size());
        Assert.assertEquals(rawMessage, called.get(4));

        Logger.removeListener(listener);
    }
}
