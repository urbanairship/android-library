/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.location;

import android.location.Location;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PendingResult;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BaseSingleLocationRequestTest extends BaseTestCase {

    PendingLocationResult request;
    private boolean onCancelCalled;

    @Before
    public void setUp() {
        onCancelCalled = false;

        request = new PendingLocationResult() {
            @Override
            protected void onCancel() {
                onCancelCalled = true;
            }
        };
    }

    /**
     * Test that setting the result callback does not trigger if the result is
     * not set.
     */
    @Test
    public void testSetCallbackNoLocation() {
        TestResultCallback resultCallback = new TestResultCallback();
        Location location = new Location("test");

        request.onResult(resultCallback);

        assertFalse("Callback should not be called until we have a result.", resultCallback.resultCallbackCalled);

        request.setResult(location);
        assertEquals("Callback should receive location.", location, resultCallback.result);
    }

    /**
     * Test that setting a callback when the result is already set will trigger
     * the callback with the result.
     */
    @Test
    public void testSetCallbackWithLocation() {
        Location location = new Location("test");
        TestResultCallback resultCallback = new TestResultCallback();

        request.setResult(location);
        request.onResult(resultCallback);

        assertTrue("Callback should be called even if the result is already set.", resultCallback.resultCallbackCalled);
        assertEquals("Callback should receive location.", location, resultCallback.result);
    }

    /**
     * Test that setting a callback does not trigger it if the request is canceled.
     */
    @Test
    public void testSetCallbackCanceled() {
        TestResultCallback resultCallback = new TestResultCallback();

        request.setResult(new Location("test"));
        request.cancel();

        request.onResult(resultCallback);

        assertFalse("Callback should not be called when pending result is canceled", resultCallback.resultCallbackCalled);
    }


    /**
     * Test that setting a result does not trigger the callback if the request is canceled.
     */
    @Test
    public void testSetResultCanceled() {
        Location location = new Location("test");
        TestResultCallback resultCallback = new TestResultCallback();

        request.onResult(resultCallback);
        request.cancel();

        request.setResult(location);

        assertFalse("Callback should not be called if request is canceled.", resultCallback.resultCallbackCalled);
    }

    /**
     * Test that setting the result when a callback is already set triggers the callback.
     */
    @Test
    public void testSetResult() {
        TestResultCallback resultCallback = new TestResultCallback();
        Location location = new Location("test");

        request.onResult(resultCallback);
        request.setResult(location);

        assertEquals("Callback should receive location.", location, resultCallback.result);
    }

    /**
     * Test canceling a request.
     */
    @Test
    public void testCancel() {
        assertFalse(request.isCanceled());

        request.cancel();
        assertTrue("onCancel should be called when result is canceled.", onCancelCalled);
        assertTrue(request.isCanceled());

        onCancelCalled = false;
        request.cancel();
        assertFalse("onCancel should only be called on the first cancel", onCancelCalled);
    }

    /**
     * Helper class that tracks the result and if the callback was called.
     */
    class TestResultCallback implements PendingResult.ResultCallback<Location> {

        Location result;
        boolean resultCallbackCalled;

        @Override
        public void onResult(Location result) {
            this.result = result;
            resultCallbackCalled = true;
        }
    }
}
