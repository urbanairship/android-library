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

package com.urbanairship.richpush;

import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestApplication;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

public class RichPushUserPreferencesTest extends RichPushBaseTestCase {

    private static final String KEY_PREFIX = "com.urbanairship.user";
    private static final String USER_ID_KEY = KEY_PREFIX + ".ID";
    private static final String USER_PASSWORD_KEY = KEY_PREFIX + ".PASSWORD";
    private static final String USER_TOKEN_KEY = KEY_PREFIX + ".USER_TOKEN";

    private RichPushUserPreferences userPreferences;
    private PreferenceDataStore preferenceDataStore;

    private final String fakeUserId = "XfJcGT_XQhqBtEn6opvLNA";
    private final String fakeUserToken = "g0GU1-jtQfSmhqaeqcAVZA";
    private final String shortFakeUserId = "shortFakeUserId";
    private final String longFakeUserId = "-Really_Super-Duper_Long-FakeUserId";
    private final String shortFakeUserToken = "shortFakeUserToken";
    private final String longFakeUserToken = "_Really-Super_Duper-Long_FakeToken";

    @Override
    public void setUp() {
        super.setUp();
        userPreferences = new RichPushUserPreferences(TestApplication.getApplication().preferenceDataStore);
        preferenceDataStore = TestApplication.getApplication().preferenceDataStore;
    }

    /**
     * Test setUserCredentials with valid user ID and user token
     */
    @Test
    public void testSetUserCredentials() {
        userPreferences.setUserCredentials(fakeUserId, fakeUserToken);
        assertEquals("User ID should match.", fakeUserId, userPreferences.getUserId());
        assertEquals("User token should match.", fakeUserToken, userPreferences.getUserToken());
    }

    /**
     * Test setUserCredentials with null user ID
     */
    @Test
    public void testSetUserCredentialsNoUser() {
        userPreferences.setUserCredentials(null, fakeUserToken);
        assertNull("User ID should be null.", userPreferences.getUserId());
        assertNull("User token should be null.", userPreferences.getUserToken());
    }

    /**
     * Test setUserCredentials with null user token
     */
    @Test
    public void testSetUserCredentialsNoUserToken() {
        userPreferences.setUserCredentials(fakeUserId, null);
        assertEquals("User ID should match.", fakeUserId, userPreferences.getUserId());
        assertNull("User token should be null.", userPreferences.getUserToken());
    }

    /**
     * Test getUserId returns user ID
     */
    @Test
    public void testGetUserId() {
        userPreferences.setUserCredentials(fakeUserId, fakeUserToken);
        assertEquals("User ID should match.", fakeUserId, userPreferences.getUserId());
    }

    /**
     * Test getUserId returns null when no user ID exist
     */
    @Test
    public void testGetUserIdNoUser() {
        // Clear any user or user token
        userPreferences.setUserCredentials(null, null);
        assertNull("User ID should be null.", userPreferences.getUserId());
    }

    /**
     * Test encrypted user token does not match user token
     */
    @Test
    public void testEncryptedUserToken() {
        // Set the user and user token
        userPreferences.setUserCredentials(fakeUserId, fakeUserToken);

        assertNotSame("Encrypted user token should not match user token.", fakeUserToken,
                preferenceDataStore.getString(USER_TOKEN_KEY, null));
    }

    /**
     * Test encrypted user token where user ID key is shorter
     */
    @Test
    public void testEncryptedUserTokenShortKey() {
        // Set the user and user token
        userPreferences.setUserCredentials(shortFakeUserId, fakeUserToken);

        assertNotNull("Encrypted user token should not be null",
                preferenceDataStore.getString(USER_TOKEN_KEY, null));

        assertNotSame("Encrypted user token should not match user token.", fakeUserToken,
                preferenceDataStore.getString(USER_TOKEN_KEY, null));

        assertEquals("User token should match.", fakeUserToken,
                userPreferences.getUserToken());
    }

    /**
     * Test encrypted user token where user ID key is longer
     */
    @Test
    public void testEncryptedUserTokenLongKey() {
        // Set the user and user token
        userPreferences.setUserCredentials(longFakeUserId, fakeUserToken);

        assertNotNull("Encrypted user token should not be null",
                preferenceDataStore.getString(USER_TOKEN_KEY, null));

        assertNotSame("Encrypted user token should not match user token.", fakeUserToken,
                preferenceDataStore.getString(USER_TOKEN_KEY, null));

        assertEquals("User token should match.", fakeUserToken,
                userPreferences.getUserToken());
    }

    /**
     * Test encrypted user token where user token is shorter
     */
    @Test
    public void testEncryptedUserTokenShort() {
        // Set the user and user token
        userPreferences.setUserCredentials(fakeUserId, shortFakeUserToken);

        assertNotNull("Encrypted user token should not be null",
                preferenceDataStore.getString(USER_TOKEN_KEY, null));

        assertNotSame("Encrypted user token should not match user token.", shortFakeUserToken,
                preferenceDataStore.getString(USER_TOKEN_KEY, null));

        assertEquals("User token should match.", shortFakeUserToken,
                userPreferences.getUserToken());
    }

    /**
     * Test encrypted user token where user token is longer
     */
    @Test
    public void testEncryptedUserTokenLong() {
        // Set the user and user token
        userPreferences.setUserCredentials(fakeUserId, longFakeUserToken);

        assertNotNull("Encrypted user token should not be null",
                preferenceDataStore.getString(USER_TOKEN_KEY, null));

        assertNotSame("Encrypted user token should not match user token.", longFakeUserToken,
                preferenceDataStore.getString(USER_TOKEN_KEY, null));

        assertEquals("User token should match.", longFakeUserToken,
                userPreferences.getUserToken());
    }

    /**
     * Test getUserToken when encrypted user token exist
     */
    @Test
    public void testGetUserToken() {
        userPreferences.setUserCredentials(fakeUserId, fakeUserToken);
        assertEquals("User token should match.", fakeUserToken, userPreferences.getUserToken());
    }

    /**
     * Test getUserToken when non encrypted password exists
     */
    @Test
    public void testGetUserTokenExistingPassword() {
        // Clear the encrypted user token.
        userPreferences.setUserCredentials(fakeUserId, null);
        assertNull("User token should be null.", userPreferences.getUserToken());

        // Set the non encrypted password
        preferenceDataStore.putSync(USER_PASSWORD_KEY, fakeUserToken);

        userPreferences = new RichPushUserPreferences(TestApplication.getApplication().preferenceDataStore);
        assertEquals("User token should match.", fakeUserToken, userPreferences.getUserToken());
    }

    /**
     * Test getUserToken when encrypted user token doesn't exist
     */
    @Test
    public void testGetUserTokenNull() {
        // Clear any user or user token
        userPreferences.setUserCredentials(null, null);
        assertNull("User token should be null.", userPreferences.getUserToken());
    }
}
