package com.urbanairship;

import androidx.annotation.Nullable;
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

}
