package com.urbanairship;

import android.util.Log;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;

public class LoggerTest extends BaseTestCase {

    /**
     * Test the logger listener.
     */
    @Test
    public void testListener() {

        final String errorMessage = "This is a test";
        final Throwable error = new IllegalArgumentException("Oh no");
        final ArrayList<String> called = new ArrayList<>();

        LogHandler myLogger = (tag, logLevel, throwable, message) -> {
            Assert.assertEquals(logLevel, Log.ERROR);
            Assert.assertEquals(error, throwable);
            Assert.assertEquals(errorMessage, message.invoke());

            called.add(message.invoke());
        };

        Logger.setLogHandler(myLogger);

        Logger.error(error, errorMessage);

        Assert.assertEquals(called.size(), 1);
        Assert.assertEquals(called.get(0), errorMessage);

        Logger.setLogHandler(new DefaultLogHandler());
    }

    @Test
    public void testClassNamePrefix() {
        Logger.setLogLevel(Log.VERBOSE);

        final String rawMessage = "This is another test";
        final String prefixedMessage = String.format("%s - %s", getClass().getSimpleName(), rawMessage);
        final ArrayList<String> called = new ArrayList<>();

        LogHandler myLogger = (tag, logLevel, throwable, message) -> {
            Assert.assertNull(throwable);

            if (logLevel == Log.DEBUG || logLevel == Log.VERBOSE) {
                Assert.assertEquals(prefixedMessage, message.invoke());
            } else {
                Assert.assertEquals(rawMessage, message.invoke());
            }

            called.add(message.invoke());
        };

        Logger.setLogHandler(myLogger);

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

        Logger.setLogHandler(new DefaultLogHandler());
    }
}
