/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

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

package com.urbanairship.richpush;

import com.urbanairship.TestApplication;

import org.json.JSONException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RichPushUserTest extends RichPushBaseTestCase {

    private final String fakeUserId = "XfJcGT_XQhqBtEn6opvLNA";
    private final String fakeToken = "g0GU1-jtQfSmhqaeqcAVZA";

    RichPushUser user;
    RichPushUserPreferences preferences;

    @Override
    public void setUp() {
        super.setUp();
        this.user = new RichPushUser(TestApplication.getApplication().preferenceDataStore);
        this.preferences = user.preferences;
    }

    /**
     * Test isCreated returns true when user has been created.
     */
    @Test
    public void testIsCreatedTrue() {
        RichPushManager.shared().getRichPushUser().preferences.setUserCredentials(fakeUserId, fakeToken);
        assertTrue("Should return true.", RichPushUser.isCreated());
    }

    /**
     * Test isCreated returns false when user has not been created.
     */
    @Test
    public void testIsCreatedFalse() {
        // Clear any user or user token
        RichPushManager.shared().getRichPushUser().preferences.setUserCredentials(null, null);
        assertFalse("Should return false.", RichPushUser.isCreated());
    }

    /**
     * Test isCreated returns false when user token doesn't exist.
     */
    @Test
    public void testIsCreatedFalseNoUserToken() {
        RichPushManager.shared().getRichPushUser().preferences.setUserCredentials(fakeUserId, null);
        assertFalse("Should return false.", RichPushUser.isCreated());
    }

    /**
     * Test setUser
     */
    @Test
    public void testSetUser() throws JSONException {
        // Clear any user or user token
        preferences.setUserCredentials(null, null);
        user.setUser(fakeUserId, fakeToken);

        assertEquals("User ID should match", fakeUserId, user.getId());
        assertEquals("User password should match", fakeToken, user.getPassword());
    }

    /**
     * Test setUser missing token
     */
    @Test
    public void testSetUserMissingToken() throws JSONException {
        user.setUser(fakeUserId, null);

        assertNull("User ID should be null.", user.getId());
        assertNull("User token should be null.", user.getPassword());
    }


    /**
     * Test setUser missing id
     */
    @Test
    public void testSetUserMissingId() throws JSONException {
        preferences.setUserCredentials(null, null);

        user.setUser(null, fakeToken);

        assertNull("User ID should be null.", user.getId());
        assertNull("User token should be null.", user.getPassword());
    }


    /**
     * Test getId
     */
    @Test
    public void testGetId() {

        // Set user ID
        preferences.setUserCredentials(fakeUserId, fakeToken);
        assertEquals("User ID should match.", fakeUserId, user.getId());
    }

    /**
     * Test getId when user doesn't exist
     */
    @Test
    public void testGetIdNoUser() {
        // Set no user ID
        preferences.setUserCredentials(null, fakeToken);
        assertNull("User ID should be null.", user.getId());
    }

    /**
     * Test getPassword returns user token
     */
    @Test
    public void testGetPassword() {
        // Set user
        preferences.setUserCredentials(fakeUserId, fakeToken);
        assertEquals("User token should match.", fakeToken, user.getPassword());
    }

    /**
     * Test getPassword when user token doesn't exist
     */
    @Test
    public void testGetPasswordNull() {
        // Set no user token
        preferences.setUserCredentials(fakeUserId, null);
        assertNull("User token should be null.", user.getPassword());
    }

    /**
     * Test getLastUpdateTime default value
     */
    @Test
    public void testGetLastUpdateTimeDefault() {
        RichPushUser newUser = new RichPushUser(TestApplication.getApplication().preferenceDataStore);
        assertEquals("Default last update time should be 0.", 0, newUser.getLastUpdateTime());
    }

    /**
     * Test getLastUpdateTime
     */
    @Test
    public void testGetLastUpdateTime() {
        user.setLastUpdateTime(500);
        assertEquals("Last update time should match.", 500, user.getLastUpdateTime());
    }
}
