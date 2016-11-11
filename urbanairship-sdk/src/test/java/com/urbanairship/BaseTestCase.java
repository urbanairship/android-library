/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship;

import android.os.Bundle;

import org.junit.Assert;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(TestRunner.class)
@Config(sdk = 18)
public abstract class BaseTestCase {


    public static void assertBundlesEquals(Bundle expected, Bundle actual) {
        assertBundlesEquals(null, expected, actual);
    }

    public static void assertBundlesEquals(String message, Bundle expected, Bundle actual) {
        if (!areEqual(expected, actual)) {
            Assert.fail(message + " <" + expected.toString() + "> is not equal to <" + actual.toString() + ">");
        }
    }

    public static boolean areEqual(Bundle expected, Bundle actual) {
        if (expected == null) {
            return actual == null;
        }

        if(expected.size() != actual.size()) {
            return false;
        }

        for(String key : expected.keySet()) {
            if (!actual.containsKey(key)) {
                return false;
            }

            Object expectedValue = expected.get(key);
            Object actualValue = actual.get(key);

            if (expectedValue == null) {
                if (actualValue != null) {
                    return false;
                }

                continue;
            }

            if (expectedValue instanceof Bundle && actualValue instanceof Bundle) {
                if (!areEqual((Bundle) expectedValue, (Bundle) actualValue)) {
                    return false;
                }

                continue;
            }

            if (!expectedValue.equals(actualValue)) {
                return false;
            }
        }

        return true;
    }
}
